package dev.textmate.grammar

import dev.textmate.grammar.raw.RawGrammar
import dev.textmate.grammar.raw.RawRule
import dev.textmate.grammar.raw.deepClone
import dev.textmate.grammar.rule.IRuleFactoryHelper
import dev.textmate.grammar.rule.IRuleRegistryOnigLib
import dev.textmate.grammar.rule.Rule
import dev.textmate.grammar.rule.RuleFactory
import dev.textmate.grammar.rule.RuleId
import dev.textmate.grammar.tokenize.AttributedScopeStack
import dev.textmate.grammar.tokenize.INITIAL
import dev.textmate.grammar.tokenize.LineTokens
import dev.textmate.grammar.tokenize.StateStack
import dev.textmate.grammar.tokenize.StateStackImpl
import dev.textmate.grammar.tokenize.tokenizeString
import dev.textmate.regex.IOnigLib
import dev.textmate.regex.OnigScanner
import dev.textmate.regex.OnigString

/**
 * Main Grammar class — compiles a [RawGrammar] into rules and tokenizes lines.
 * Port of `Grammar` from vscode-textmate `grammar.ts`.
 *
 * Supports cross-grammar `include` resolution via [grammarLookup].
 * Supports injection grammars (both inline [RawGrammar.injections] and external [injectionLookup]).
 */
class Grammar(
    private val rootScopeName: String,
    private val rawGrammar: RawGrammar,
    private val onigLib: IOnigLib,
    private val grammarLookup: ((String) -> RawGrammar?)? = null,
    private val injectionLookup: (() -> List<RawGrammar>)? = null
) : IRuleFactoryHelper, IRuleRegistryOnigLib {

    private val _includedGrammars = mutableMapOf<String, RawGrammar>()
    private val _includedRepositories = mutableMapOf<String, MutableMap<String, RawRule>>()
    private var _rootId: RuleId? = null
    private var _lastRuleId = 0
    private val _ruleId2desc = mutableListOf<Rule?>(null) // index 0 unused

    private var _repository: MutableMap<String, RawRule>? = null
    private var _injections: List<InjectionRule>? = null

    internal fun getInjections(): List<InjectionRule> {
        val cached = _injections
        if (cached != null) return cached
        val result = collectInjections()
        _injections = result
        return result
    }

    private fun collectInjections(): List<InjectionRule> {
        val result = mutableListOf<InjectionRule>()
        val (_, repository) = ensureCompiled()

        // Inline injections from rawGrammar.injections map
        rawGrammar.injections?.forEach { (selector, rawRule) ->
            val matchers = InjectionSelectorParser.createMatchers(selector)
            if (matchers.isEmpty()) return@forEach
            val ruleId = RuleFactory.getCompiledRuleId(rawRule, this, repository)
            for (mwp in matchers) {
                result.add(InjectionRule(selector, mwp.matcher, mwp.priority, ruleId))
            }
        }

        // External injection grammars with injectionSelector
        injectionLookup?.invoke()?.forEach { injectorRaw ->
            if (injectorRaw.scopeName == rootScopeName) return@forEach
            val selector = injectorRaw.injectionSelector ?: return@forEach
            val matchers = InjectionSelectorParser.createMatchers(selector)
            if (matchers.isEmpty()) return@forEach
            val cloned = injectorRaw.deepClone()
            val injectorRepo = RuleFactory.initGrammarRepository(cloned)
            val injectorRule = RawRule(patterns = cloned.patterns)
            val ruleId = RuleFactory.getCompiledRuleId(injectorRule, this, injectorRepo)
            for (mwp in matchers) {
                result.add(InjectionRule(selector, mwp.matcher, mwp.priority, ruleId))
            }
        }

        result.sortBy { it.priority.value }
        return result
    }

    // --- IRuleRegistry ---

    override fun getRule(ruleId: RuleId): Rule? {
        return _ruleId2desc.getOrNull(ruleId.id)
    }

    override fun <T : Rule> registerRule(factory: (RuleId) -> T): T {
        val id = RuleId(++_lastRuleId)
        while (_ruleId2desc.size <= id.id) {
            _ruleId2desc.add(null)
        }
        val rule = factory(id)
        _ruleId2desc[id.id] = rule
        return rule
    }

    // --- IGrammarRegistry ---

    override fun getExternalGrammar(
        scopeName: String,
        repository: MutableMap<String, RawRule>
    ): RawGrammar? {
        _includedGrammars[scopeName]?.let { return it }
        val raw = grammarLookup?.invoke(scopeName) ?: return null
        val initialized = raw.deepClone()
        _includedGrammars[scopeName] = initialized
        return initialized
    }

    override fun getExternalGrammarRepository(
        scopeName: String,
        repository: MutableMap<String, RawRule>
    ): MutableMap<String, RawRule>? {
        _includedRepositories[scopeName]?.let { return it }
        val externalGrammar = getExternalGrammar(scopeName, repository) ?: return null
        val initialized = RuleFactory.initGrammarRepository(externalGrammar, base = repository["\$base"])
        _includedRepositories[scopeName] = initialized
        return initialized
    }

    // --- IOnigLib (delegate) ---

    override fun createOnigScanner(patterns: List<String>): OnigScanner {
        return onigLib.createOnigScanner(patterns)
    }

    override fun createOnigString(str: String): OnigString {
        return onigLib.createOnigString(str)
    }

    // --- Tokenization ---

    private fun ensureCompiled(): Pair<RuleId, MutableMap<String, RawRule>> {
        val rootId = _rootId
        val repo = _repository
        if (rootId != null && repo != null) {
            return rootId to repo
        }
        val newRepo = RuleFactory.initGrammarRepository(rawGrammar)
        val selfRule = newRepo["\$self"] ?: error("Grammar repository missing \$self")
        val newRootId = RuleFactory.getCompiledRuleId(selfRule, this, newRepo)
        _rootId = newRootId
        _repository = newRepo
        return newRootId to newRepo
    }

    fun tokenizeLine(lineText: String, prevState: StateStack? = null): TokenizeLineResult {
        val (rootId, _) = ensureCompiled()

        val isFirstLine: Boolean
        val state: StateStackImpl

        if (prevState == null || prevState === INITIAL) {
            isFirstLine = true

            val rootRule = getRule(rootId)
            val rootScopeName = rootRule?.getName(null, null) ?: rootScopeName

            val scopeList = AttributedScopeStack.createRoot(rootScopeName, 0)
            state = StateStackImpl(
                parent = null,
                ruleId = rootId,
                enterPos = -1,
                anchorPos = -1,
                beginRuleCapturedEOL = false,
                endRule = null,
                nameScopesList = scopeList,
                contentNameScopesList = scopeList
            )
        } else {
            isFirstLine = false
            state = prevState as? StateStackImpl
                ?: error("prevState must be a StateStack returned by a previous tokenizeLine call")
            state.reset()
        }

        val lineWithNewline = lineText + "\n"
        val onigLineText = createOnigString(lineWithNewline)
        val lineLength = onigLineText.content.length

        val lineTokens = LineTokens()
        val resultStack = tokenizeString(
            grammar = this,
            lineText = onigLineText,
            isFirstLine = isFirstLine,
            linePos = 0,
            stack = state,
            lineTokens = lineTokens,
            checkWhile = true
        )

        val tokens = lineTokens.getResult(resultStack, lineLength)

        return TokenizeLineResult(tokens = tokens, ruleStack = resultStack)
    }
}
