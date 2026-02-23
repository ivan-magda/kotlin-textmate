package dev.textmate.grammar.rule

import dev.textmate.regex.CaptureIndex

public sealed class Rule(
    public val id: RuleId,
    private val _name: String?,
    private val _contentName: String?
) {
    private val _nameIsCapturing: Boolean =
        hasCaptures(_name)
    private val _contentNameIsCapturing: Boolean =
        hasCaptures(_contentName)

    public fun getName(lineText: String?, captureIndices: List<CaptureIndex>?): String? =
        if (!_nameIsCapturing || _name == null || lineText == null || captureIndices == null) {
            _name
        } else {
            replaceCaptures(_name, lineText, captureIndices)
        }

    public fun getContentName(lineText: String, captureIndices: List<CaptureIndex>): String? =
        if (!_contentNameIsCapturing || _contentName == null) {
            _contentName
        } else {
            replaceCaptures(_contentName, lineText, captureIndices)
        }

    internal abstract fun collectPatterns(grammar: IRuleRegistry, out: RegExpSourceList)
    public abstract fun compile(grammar: IRuleRegistryOnigLib, endRegexSource: String?): CompiledRule
    public abstract fun compileAG(
        grammar: IRuleRegistryOnigLib,
        endRegexSource: String?,
        allowA: Boolean,
        allowG: Boolean
    ): CompiledRule
}

public class CaptureRule internal constructor(
    id: RuleId,
    name: String?,
    contentName: String?,
    public val retokenizeCapturedWithRuleId: RuleId
) : Rule(id, name, contentName) {

    override fun collectPatterns(grammar: IRuleRegistry, out: RegExpSourceList) {
        throw UnsupportedOperationException("Not supported!")
    }

    override fun compile(grammar: IRuleRegistryOnigLib, endRegexSource: String?): CompiledRule {
        throw UnsupportedOperationException("Not supported!")
    }

    override fun compileAG(
        grammar: IRuleRegistryOnigLib,
        endRegexSource: String?,
        allowA: Boolean,
        allowG: Boolean
    ): CompiledRule {
        throw UnsupportedOperationException("Not supported!")
    }
}

public class MatchRule internal constructor(
    id: RuleId,
    name: String?,
    match: String,
    public val captures: List<CaptureRule?>
) : Rule(id, name, null) {

    private val _match = RegExpSource(match, id)
    private var _cachedCompiledPatterns: RegExpSourceList? = null

    override fun collectPatterns(grammar: IRuleRegistry, out: RegExpSourceList) {
        out.push(_match)
    }

    override fun compile(grammar: IRuleRegistryOnigLib, endRegexSource: String?): CompiledRule {
        return getCachedCompiledPatterns(grammar).compile(grammar)
    }

    override fun compileAG(
        grammar: IRuleRegistryOnigLib,
        endRegexSource: String?,
        allowA: Boolean,
        allowG: Boolean
    ): CompiledRule {
        return getCachedCompiledPatterns(grammar).compileAG(grammar, allowA, allowG)
    }

    private fun getCachedCompiledPatterns(grammar: IRuleRegistryOnigLib): RegExpSourceList {
        val cached = _cachedCompiledPatterns
        if (cached != null) return cached
        val result = RegExpSourceList()
        collectPatterns(grammar, result)
        _cachedCompiledPatterns = result
        return result
    }
}

public class IncludeOnlyRule internal constructor(
    id: RuleId,
    name: String?,
    contentName: String?,
    patterns: CompilePatternsResult
) : Rule(id, name, contentName) {

    public val patterns: List<RuleId> = patterns.patterns
    public val hasMissingPatterns: Boolean = patterns.hasMissingPatterns
    private var _cachedCompiledPatterns: RegExpSourceList? = null

    override fun collectPatterns(grammar: IRuleRegistry, out: RegExpSourceList) {
        for (patternId in patterns) {
            val rule = grammar.getRule(patternId) ?: continue
            rule.collectPatterns(grammar, out)
        }
    }

    override fun compile(grammar: IRuleRegistryOnigLib, endRegexSource: String?): CompiledRule {
        return getCachedCompiledPatterns(grammar).compile(grammar)
    }

    override fun compileAG(
        grammar: IRuleRegistryOnigLib,
        endRegexSource: String?,
        allowA: Boolean,
        allowG: Boolean
    ): CompiledRule {
        return getCachedCompiledPatterns(grammar).compileAG(grammar, allowA, allowG)
    }

    private fun getCachedCompiledPatterns(grammar: IRuleRegistryOnigLib): RegExpSourceList {
        val cached = _cachedCompiledPatterns
        if (cached != null) return cached
        val result = RegExpSourceList()
        collectPatterns(grammar, result)
        _cachedCompiledPatterns = result
        return result
    }
}

public class BeginEndRule internal constructor(
    id: RuleId,
    name: String?,
    contentName: String?,
    begin: String,
    public val beginCaptures: List<CaptureRule?>,
    end: String?,
    public val endCaptures: List<CaptureRule?>,
    public val applyEndPatternLast: Boolean,
    patterns: CompilePatternsResult
) : Rule(id, name, contentName) {

    private val _begin = RegExpSource(begin, id)
    private val _end = RegExpSource(end ?: "\uFFFF", RuleId.END_RULE)
    public val endHasBackReferences: Boolean = _end.hasBackReferences
    public val patterns: List<RuleId> = patterns.patterns
    public val hasMissingPatterns: Boolean = patterns.hasMissingPatterns
    private var _cachedCompiledPatterns: RegExpSourceList? = null

    public fun getEndWithResolvedBackReferences(lineText: String, captureIndices: List<CaptureIndex>): String {
        return _end.resolveBackReferences(lineText, captureIndices)
    }

    override fun collectPatterns(grammar: IRuleRegistry, out: RegExpSourceList) {
        out.push(_begin)
    }

    override fun compile(grammar: IRuleRegistryOnigLib, endRegexSource: String?): CompiledRule {
        return getCachedCompiledPatterns(grammar, endRegexSource).compile(grammar)
    }

    override fun compileAG(
        grammar: IRuleRegistryOnigLib,
        endRegexSource: String?,
        allowA: Boolean,
        allowG: Boolean
    ): CompiledRule {
        return getCachedCompiledPatterns(grammar, endRegexSource).compileAG(grammar, allowA, allowG)
    }

    private fun getCachedCompiledPatterns(grammar: IRuleRegistryOnigLib, endRegexSource: String?): RegExpSourceList {
        val cached = _cachedCompiledPatterns
        val result = if (cached != null) {
            cached
        } else {
            val list = RegExpSourceList()
            for (patternId in patterns) {
                grammar.getRule(patternId)?.collectPatterns(grammar, list)
            }
            val endPattern = if (_end.hasBackReferences) _end.clone() else _end
            if (applyEndPatternLast) list.push(endPattern) else list.unshift(endPattern)
            _cachedCompiledPatterns = list
            list
        }

        if (_end.hasBackReferences && endRegexSource != null) {
            val index = if (applyEndPatternLast) result.length() - 1 else 0
            result.setSource(index, endRegexSource)
        }

        return result
    }
}

public class BeginWhileRule internal constructor(
    id: RuleId,
    name: String?,
    contentName: String?,
    begin: String,
    public val beginCaptures: List<CaptureRule?>,
    whilePattern: String,
    public val whileCaptures: List<CaptureRule?>,
    patterns: CompilePatternsResult
) : Rule(id, name, contentName) {

    private val _begin = RegExpSource(begin, id)
    private val _while = RegExpSource(whilePattern, RuleId.WHILE_RULE)
    public val whileHasBackReferences: Boolean = _while.hasBackReferences
    public val patterns: List<RuleId> = patterns.patterns
    public val hasMissingPatterns: Boolean = patterns.hasMissingPatterns
    private var _cachedCompiledPatterns: RegExpSourceList? = null
    private var _cachedCompiledWhilePatterns: RegExpSourceList? = null

    public fun getWhileWithResolvedBackReferences(lineText: String, captureIndices: List<CaptureIndex>): String {
        return _while.resolveBackReferences(lineText, captureIndices)
    }

    override fun collectPatterns(grammar: IRuleRegistry, out: RegExpSourceList) {
        out.push(_begin)
    }

    override fun compile(grammar: IRuleRegistryOnigLib, endRegexSource: String?): CompiledRule {
        return getCachedCompiledPatterns(grammar).compile(grammar)
    }

    override fun compileAG(
        grammar: IRuleRegistryOnigLib,
        endRegexSource: String?,
        allowA: Boolean,
        allowG: Boolean
    ): CompiledRule {
        return getCachedCompiledPatterns(grammar).compileAG(grammar, allowA, allowG)
    }

    private fun getCachedCompiledPatterns(grammar: IRuleRegistryOnigLib): RegExpSourceList {
        val cached = _cachedCompiledPatterns
        if (cached != null) return cached
        val result = RegExpSourceList()
        for (patternId in patterns) {
            grammar.getRule(patternId)?.collectPatterns(grammar, result)
        }
        _cachedCompiledPatterns = result
        return result
    }

    public fun compileWhileAG(
        grammar: IRuleRegistryOnigLib,
        endRegexSource: String?,
        allowA: Boolean,
        allowG: Boolean
    ): CompiledRule {
        return getCachedCompiledWhilePatterns(endRegexSource).compileAG(grammar, allowA, allowG)
    }

    private fun getCachedCompiledWhilePatterns(
        endRegexSource: String?
    ): RegExpSourceList {
        val cached = _cachedCompiledWhilePatterns
        val result = if (cached != null) {
            cached
        } else {
            val list = RegExpSourceList()
            list.push(if (_while.hasBackReferences) _while.clone() else _while)
            _cachedCompiledWhilePatterns = list
            list
        }

        if (_while.hasBackReferences) {
            result.setSource(0, endRegexSource ?: "\uFFFF")
        }

        return result
    }
}
