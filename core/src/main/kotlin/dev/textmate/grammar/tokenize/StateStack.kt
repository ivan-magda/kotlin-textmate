package dev.textmate.grammar.tokenize

import dev.textmate.grammar.rule.IRuleRegistry
import dev.textmate.grammar.rule.Rule
import dev.textmate.grammar.rule.RuleId

/**
 * Public interface for the tokenizer state stack.
 * Returned by tokenizeLine, passed back to tokenize the next line.
 */
public interface StateStack {
    public val depth: Int
    public fun clone(): StateStack
}

/**
 * Implementation of [StateStack] as an immutable linked list.
 * Port of `StateStackImpl` from vscode-textmate `grammar.ts`.
 *
 * Each element represents a "pushed" state during tokenization —
 * a rule that was entered (begin matched) and not yet exited (end not yet matched).
 */
public class StateStackImpl(
    public val parent: StateStackImpl?,
    private val ruleId: RuleId,
    enterPos: Int,
    anchorPos: Int,
    public val beginRuleCapturedEOL: Boolean,
    public val endRule: String?,
    public val nameScopesList: AttributedScopeStack?,
    public val contentNameScopesList: AttributedScopeStack?
) : StateStack {

    override val depth: Int =
        if (parent != null) parent.depth + 1 else 1

    // Transient: reset to -1 between lines, not part of equality
    private var _enterPos: Int = enterPos
    private var _anchorPos: Int = anchorPos

    public companion object {
        public val NULL: StateStackImpl = StateStackImpl(
            parent = null,
            ruleId = RuleId.NO_RULE,
            enterPos = 0,
            anchorPos = 0,
            beginRuleCapturedEOL = false,
            endRule = null,
            nameScopesList = null,
            contentNameScopesList = null
        )

        private fun structuralEquals(a: StateStackImpl?, b: StateStackImpl?): Boolean {
            var x = a
            var y = b
            while (true) {
                if (x === y) return true
                if (x == null && y == null) return true
                if (x == null || y == null) return false
                if (x.depth != y.depth || x.ruleId != y.ruleId || x.endRule != y.endRule) return false
                x = x.parent
                y = y.parent
            }
        }

        private fun deepEquals(a: StateStackImpl, b: StateStackImpl): Boolean {
            if (a === b) return true
            if (!structuralEquals(a, b)) return false
            return AttributedScopeStack.equals(a.contentNameScopesList, b.contentNameScopesList)
        }
    }

    public fun push(
        ruleId: RuleId,
        enterPos: Int,
        anchorPos: Int,
        beginRuleCapturedEOL: Boolean,
        endRule: String?,
        nameScopesList: AttributedScopeStack?,
        contentNameScopesList: AttributedScopeStack?
    ): StateStackImpl =
        StateStackImpl(
            parent = this,
            ruleId = ruleId,
            enterPos = enterPos,
            anchorPos = anchorPos,
            beginRuleCapturedEOL = beginRuleCapturedEOL,
            endRule = endRule,
            nameScopesList = nameScopesList,
            contentNameScopesList = contentNameScopesList
        )

    public fun pop(): StateStackImpl? = parent

    public fun safePop(): StateStackImpl = parent ?: this

    public fun reset() {
        var el: StateStackImpl? = this
        while (el != null) {
            el._enterPos = -1
            el._anchorPos = -1
            el = el.parent
        }
    }

    public fun getEnterPos(): Int = _enterPos

    public fun getAnchorPos(): Int = _anchorPos

    public fun getRule(grammar: IRuleRegistry): Rule? {
        return grammar.getRule(ruleId)
    }

    public fun withContentNameScopesList(contentNameScopeStack: AttributedScopeStack?): StateStackImpl {
        if (this.contentNameScopesList === contentNameScopeStack) return this
        val p = parent ?: return StateStackImpl(
            parent = null,
            ruleId = ruleId,
            enterPos = _enterPos,
            anchorPos = _anchorPos,
            beginRuleCapturedEOL = beginRuleCapturedEOL,
            endRule = endRule,
            nameScopesList = nameScopesList,
            contentNameScopesList = contentNameScopeStack
        )
        return p.push(
            ruleId = ruleId,
            enterPos = _enterPos,
            anchorPos = _anchorPos,
            beginRuleCapturedEOL = beginRuleCapturedEOL,
            endRule = endRule,
            nameScopesList = nameScopesList,
            contentNameScopesList = contentNameScopeStack
        )
    }

    public fun withEndRule(endRule: String): StateStackImpl {
        if (this.endRule == endRule) return this
        return StateStackImpl(
            parent = parent,
            ruleId = ruleId,
            enterPos = _enterPos,
            anchorPos = _anchorPos,
            beginRuleCapturedEOL = beginRuleCapturedEOL,
            endRule = endRule,
            nameScopesList = nameScopesList,
            contentNameScopesList = contentNameScopesList
        )
    }

    /** Used to detect endless loops during tokenization. */
    public fun hasSameRuleAs(other: StateStackImpl): Boolean {
        var el: StateStackImpl? = this
        while (el != null && el._enterPos == other._enterPos) {
            if (el.ruleId == other.ruleId) return true
            el = el.parent
        }
        return false
    }

    override fun clone(): StateStackImpl = this

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is StateStackImpl) return false
        return deepEquals(this, other)
    }

    override fun hashCode(): Int {
        var hash = 0
        var el: StateStackImpl? = this
        while (el != null) {
            hash = 31 * hash + el.depth
            hash = 31 * hash + el.ruleId.hashCode()
            hash = 31 * hash + (el.endRule?.hashCode() ?: 0)
            el = el.parent
        }
        // Include contentNameScopesList
        hash = 31 * hash + (contentNameScopesList?.hashCode() ?: 0)
        return hash
    }

    override fun toString(): String {
        val parts = mutableListOf<String>()
        writeString(parts)
        return "[${parts.joinToString(",")}]"
    }

    private fun writeString(res: MutableList<String>) {
        parent?.writeString(res)
        res.add("(${ruleId.id}, $nameScopesList, $contentNameScopesList)")
    }
}

/** The initial tokenizer state: an empty stack. */
public val INITIAL: StateStack = StateStackImpl.NULL
