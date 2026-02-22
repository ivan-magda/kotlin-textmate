package dev.textmate.grammar.rule

import dev.textmate.grammar.raw.RawGrammar
import dev.textmate.grammar.raw.RawRule
import dev.textmate.regex.IOnigLib

interface IRuleRegistry {
    fun getRule(ruleId: RuleId): Rule?
    fun <T : Rule> registerRule(factory: (RuleId) -> T): T
}

internal interface IGrammarRegistry {
    fun getExternalGrammar(scopeName: String, repository: MutableMap<String, RawRule>): RawGrammar?

    fun getExternalGrammarRepository(
        scopeName: String,
        repository: MutableMap<String, RawRule>
    ): MutableMap<String, RawRule>?
}

internal interface IRuleFactoryHelper : IRuleRegistry, IGrammarRegistry

interface IRuleRegistryOnigLib : IRuleRegistry, IOnigLib
