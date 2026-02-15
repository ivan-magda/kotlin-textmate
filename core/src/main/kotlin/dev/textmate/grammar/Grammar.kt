package dev.textmate.grammar

import dev.textmate.grammar.raw.RawGrammar
import dev.textmate.grammar.raw.RawRule
import dev.textmate.regex.IOnigLib
import dev.textmate.regex.OnigScanner
import dev.textmate.regex.OnigString

/**
 * Main Grammar class — compiles a [RawGrammar] into rules and tokenizes lines.
 * Port of `Grammar` from vscode-textmate `grammar.ts`.
 *
 * Simplified for Stage 4b: no injection grammars, no embedded languages,
 * no theme resolution, no time limits.
 */
class Grammar(
    private val rootScopeName: String,
    private val rawGrammar: RawGrammar,
    private val onigLib: IOnigLib
) : IRuleFactoryHelper, IRuleRegistryOnigLib {

    private var _rootId: RuleId? = null
    private var _lastRuleId = 0
    private val _ruleId2desc = mutableListOf<Rule?>(null) // index 0 unused

    private var _repository: MutableMap<String, RawRule>? = null

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
        return null // No embedded language support in Stage 4b
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
            lineTokens = lineTokens
        )

        val tokens = lineTokens.getResult(resultStack, lineLength)

        return TokenizeLineResult(tokens = tokens, ruleStack = resultStack)
    }
}
