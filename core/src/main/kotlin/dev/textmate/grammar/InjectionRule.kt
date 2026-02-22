package dev.textmate.grammar

import dev.textmate.grammar.rule.RuleId

/** Injection priority parsed from the selector prefix (`L:` / `R:`). */
internal enum class InjectionPriority(val value: Int) {
    /** `L:` prefix — wins ties against normal rules at the same position. */
    HIGH(-1),
    /** No prefix — normal priority. */
    DEFAULT(0),
    /** `R:` prefix — loses ties against normal rules at the same position. */
    LOW(1)
}

/**
 * A compiled injection rule: a scope selector matcher paired with a compiled rule and priority.
 * Plain class (not data class) — function-typed [matcher] has identity-based equals/hashCode on JVM.
 */
internal class InjectionRule(
    /** Original selector string, for debug logging. */
    val debugSelector: String,
    /** Returns true when the current scope stack matches this injection's target. */
    val matcher: ScopeMatcher,
    /** Injection priority parsed from selector prefix. */
    val priority: InjectionPriority,
    /** Rule compiled into the host grammar's rule space. */
    val ruleId: RuleId
)
