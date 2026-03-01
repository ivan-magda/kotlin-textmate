package dev.textmate.grammar.rule

import dev.textmate.grammar.raw.RawGrammar
import dev.textmate.grammar.raw.RawRule
import dev.textmate.regex.IOnigLib

public interface IRuleRegistry {
    public fun getRule(ruleId: RuleId): Rule?
    public fun <T : Rule> registerRule(factory: (RuleId) -> T): T
}

internal interface IGrammarRegistry {
    fun getExternalGrammar(scopeName: String, repository: MutableMap<String, RawRule>): RawGrammar?

    fun getExternalGrammarRepository(
        scopeName: String,
        repository: MutableMap<String, RawRule>
    ): MutableMap<String, RawRule>?
}

internal interface IRuleFactoryHelper : IRuleRegistry, IGrammarRegistry {
    fun getCachedRuleId(rawRule: RawRule): RuleId?
    fun setCachedRuleId(rawRule: RawRule, id: RuleId)
}

public interface IRuleRegistryOnigLib : IRuleRegistry, IOnigLib
