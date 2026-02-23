package dev.textmate.grammar.rule

@JvmInline
public value class RuleId(public val id: Int) {
    public companion object {
        public val END_RULE: RuleId = RuleId(-1)
        public val WHILE_RULE: RuleId = RuleId(-2)
        public val NO_RULE: RuleId = RuleId(0)
    }
}
