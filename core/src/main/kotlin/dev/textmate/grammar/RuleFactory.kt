package dev.textmate.grammar

import dev.textmate.grammar.raw.RawGrammar
import dev.textmate.grammar.raw.RawRule

internal data class CompilePatternsResult(
    val patterns: List<RuleId>,
    val hasMissingPatterns: Boolean
)

internal object RuleFactory {

    fun getCompiledRuleId(
        desc: RawRule,
        helper: IRuleFactoryHelper,
        repository: MutableMap<String, RawRule>
    ): RuleId {
        desc.id?.let { return RuleId(it) }

        val rule = helper.registerRule { id ->
            desc.id = id.id

            if (desc.match != null) {
                return@registerRule MatchRule(
                    id = id,
                    name = desc.name,
                    match = desc.match,
                    captures = compileCaptures(desc.captures, helper, repository)
                )
            }

            if (desc.begin == null) {
                val repo = if (desc.repository != null) {
                    val merged = LinkedHashMap(repository)
                    merged.putAll(desc.repository)
                    merged
                } else {
                    repository
                }
                val patterns = desc.patterns ?: desc.include?.let { listOf(RawRule(include = it)) }
                return@registerRule IncludeOnlyRule(
                    id = id,
                    name = desc.name,
                    contentName = desc.contentName,
                    patterns = compilePatterns(patterns, helper, repo)
                )
            }

            if (desc.whilePattern != null) {
                return@registerRule BeginWhileRule(
                    id = id,
                    name = desc.name,
                    contentName = desc.contentName,
                    begin = desc.begin,
                    beginCaptures = compileCaptures(desc.beginCaptures ?: desc.captures, helper, repository),
                    whilePattern = desc.whilePattern,
                    whileCaptures = compileCaptures(desc.whileCaptures ?: desc.captures, helper, repository),
                    patterns = compilePatterns(desc.patterns, helper, repository)
                )
            }

            return@registerRule BeginEndRule(
                id = id,
                name = desc.name,
                contentName = desc.contentName,
                begin = desc.begin,
                beginCaptures = compileCaptures(desc.beginCaptures ?: desc.captures, helper, repository),
                end = desc.end,
                endCaptures = compileCaptures(desc.endCaptures ?: desc.captures, helper, repository),
                applyEndPatternLast = (desc.applyEndPatternLast ?: 0) == 1,
                patterns = compilePatterns(desc.patterns, helper, repository)
            )
        }

        return rule.id
    }

    fun compileCaptures(
        captures: Map<String, RawRule>?,
        helper: IRuleFactoryHelper,
        repository: MutableMap<String, RawRule>
    ): List<CaptureRule?> {
        if (captures == null) {
            return emptyList()
        }

        var maximumCaptureId = 0
        for (captureId in captures.keys) {
            val numericCaptureId = captureId.toIntOrNull() ?: continue
            if (numericCaptureId > maximumCaptureId) {
                maximumCaptureId = numericCaptureId
            }
        }

        val result = MutableList<CaptureRule?>(maximumCaptureId + 1) { null }

        for ((captureId, captureRule) in captures) {
            val numericCaptureId = captureId.toIntOrNull() ?: continue
            var retokenizeCapturedWithRuleId = RuleId.NO_RULE
            if (captureRule.patterns != null) {
                retokenizeCapturedWithRuleId = getCompiledRuleId(captureRule, helper, repository)
            }
            result[numericCaptureId] = helper.registerRule { id ->
                CaptureRule(
                    id = id,
                    name = captureRule.name,
                    contentName = captureRule.contentName,
                    retokenizeCapturedWithRuleId = retokenizeCapturedWithRuleId
                )
            }
        }

        return result
    }

    fun compilePatterns(
        patterns: List<RawRule>?,
        helper: IRuleFactoryHelper,
        repository: MutableMap<String, RawRule>
    ): CompilePatternsResult {
        val r = mutableListOf<RuleId>()

        if (patterns != null) {
            for (pattern in patterns) {
                var ruleId: RuleId? = null

                if (pattern.include != null) {
                    val reference = parseInclude(pattern.include)

                    when (reference) {
                        is IncludeReference.BaseReference,
                        is IncludeReference.SelfReference -> {
                            val repoRule = repository[pattern.include]
                            if (repoRule != null) {
                                ruleId = getCompiledRuleId(repoRule, helper, repository)
                            }
                        }

                        is IncludeReference.RelativeReference -> {
                            val localIncludedRule = repository[reference.ruleName]
                            if (localIncludedRule != null) {
                                ruleId = getCompiledRuleId(localIncludedRule, helper, repository)
                            }
                        }

                        is IncludeReference.TopLevelReference -> {
                            val externalGrammar = helper.getExternalGrammar(reference.scopeName, repository)
                            if (externalGrammar != null) {
                                val extRepo = initGrammarRepository(externalGrammar)
                                ruleId = getCompiledRuleId(extRepo["\$self"]!!, helper, extRepo)
                            }
                        }

                        is IncludeReference.TopLevelRepositoryReference -> {
                            val externalGrammar = helper.getExternalGrammar(reference.scopeName, repository)
                            if (externalGrammar != null) {
                                val extRepo = initGrammarRepository(externalGrammar)
                                val externalIncludedRule = extRepo[reference.ruleName]
                                if (externalIncludedRule != null) {
                                    ruleId = getCompiledRuleId(externalIncludedRule, helper, extRepo)
                                }
                            }
                        }
                    }
                } else {
                    ruleId = getCompiledRuleId(pattern, helper, repository)
                }

                if (ruleId != null) {
                    // May be null for circular references during compilation
                    val rule = helper.getRule(ruleId)

                    val skipRule = when (rule) {
                        is IncludeOnlyRule -> rule.hasMissingPatterns && rule.patterns.isEmpty()
                        is BeginEndRule -> rule.hasMissingPatterns && rule.patterns.isEmpty()
                        is BeginWhileRule -> rule.hasMissingPatterns && rule.patterns.isEmpty()
                        else -> false
                    }

                    if (!skipRule) {
                        r.add(ruleId)
                    }
                }
            }
        }

        return CompilePatternsResult(
            patterns = r,
            hasMissingPatterns = (patterns?.size ?: 0) != r.size
        )
    }

    fun initGrammarRepository(grammar: RawGrammar, base: RawRule? = null): MutableMap<String, RawRule> {
        val repository = LinkedHashMap<String, RawRule>()

        // Copy the grammar's own repository
        grammar.repository?.let { repository.putAll(it) }

        // Add $self and $base entries pointing to a synthetic root rule
        val selfRule = RawRule(
            patterns = grammar.patterns,
            name = grammar.scopeName
        )
        repository["\$self"] = selfRule
        repository["\$base"] = base ?: selfRule

        return repository
    }
}
