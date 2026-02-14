package dev.textmate.grammar

@JvmInline
value class RuleId(val id: Int) {
    companion object {
        val END_RULE = RuleId(-1)
        val WHILE_RULE = RuleId(-2)
        val NO_RULE = RuleId(0)
    }
}
