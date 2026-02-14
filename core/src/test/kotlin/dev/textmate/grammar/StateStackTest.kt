package dev.textmate.grammar

import org.junit.Assert.*
import org.junit.Test

class StateStackTest {

    private val rootScopes = AttributedScopeStack.createRoot("source.test", 0)

    private fun makeStack(
        ruleId: RuleId = RuleId(1),
        endRule: String? = null,
        parent: StateStackImpl? = StateStackImpl.NULL,
        enterPos: Int = -1,
        anchorPos: Int = -1
    ): StateStackImpl {
        return parent!!.push(ruleId, enterPos, anchorPos, false, endRule, rootScopes, rootScopes)
    }

    // --- Depth ---

    @Test
    fun `NULL has depth 1`() {
        assertEquals(1, StateStackImpl.NULL.depth)
    }

    @Test
    fun `push increases depth`() {
        val one = makeStack()
        assertEquals(2, one.depth)
        val two = makeStack(parent = one)
        assertEquals(3, two.depth)
    }

    // --- INITIAL ---

    @Test
    fun `INITIAL is NULL`() {
        assertSame(StateStackImpl.NULL, INITIAL)
    }

    // --- Push / Pop ---

    @Test
    fun `pop returns parent`() {
        val child = makeStack()
        assertSame(StateStackImpl.NULL, child.pop())
    }

    @Test
    fun `pop on NULL returns null`() {
        assertNull(StateStackImpl.NULL.pop())
    }

    @Test
    fun `safePop on NULL returns self`() {
        assertSame(StateStackImpl.NULL, StateStackImpl.NULL.safePop())
    }

    @Test
    fun `safePop on child returns parent`() {
        val child = makeStack()
        assertSame(StateStackImpl.NULL, child.safePop())
    }

    // --- Equality ---

    @Test
    fun `structural equality same ruleId and endRule chain`() {
        val a = makeStack(ruleId = RuleId(5), endRule = "end")
        val b = makeStack(ruleId = RuleId(5), endRule = "end")
        assertEquals(a, b)
    }

    @Test
    fun `different ruleId not equal`() {
        val a = makeStack(ruleId = RuleId(5))
        val b = makeStack(ruleId = RuleId(6))
        assertNotEquals(a, b)
    }

    @Test
    fun `different endRule not equal`() {
        val a = makeStack(ruleId = RuleId(5), endRule = "end1")
        val b = makeStack(ruleId = RuleId(5), endRule = "end2")
        assertNotEquals(a, b)
    }

    @Test
    fun `enterPos and anchorPos NOT part of equality`() {
        val a = StateStackImpl.NULL.push(RuleId(5), 10, 20, false, null, rootScopes, rootScopes)
        val b = StateStackImpl.NULL.push(RuleId(5), 30, 40, false, null, rootScopes, rootScopes)
        assertEquals(a, b)
    }

    @Test
    fun `contentNameScopesList IS part of equality`() {
        val scopesA = AttributedScopeStack.createRoot("source.a", 0)
        val scopesB = AttributedScopeStack.createRoot("source.b", 0)

        val a = StateStackImpl.NULL.push(RuleId(5), -1, -1, false, null, rootScopes, scopesA)
        val b = StateStackImpl.NULL.push(RuleId(5), -1, -1, false, null, rootScopes, scopesB)
        assertNotEquals(a, b)
    }

    @Test
    fun `deep chain equality`() {
        val a = makeStack(ruleId = RuleId(1)).let { makeStack(ruleId = RuleId(2), parent = it) }
        val b = makeStack(ruleId = RuleId(1)).let { makeStack(ruleId = RuleId(2), parent = it) }
        assertEquals(a, b)
    }

    // --- Reset ---

    @Test
    fun `reset clears enterPos and anchorPos on entire chain`() {
        val child = StateStackImpl.NULL.push(RuleId(1), 5, 10, false, null, null, null)
        val grandchild = child.push(RuleId(2), 15, 20, false, null, null, null)
        assertEquals(15, grandchild.getEnterPos())
        assertEquals(5, child.getEnterPos())

        grandchild.reset()
        assertEquals(-1, grandchild.getEnterPos())
        assertEquals(-1, grandchild.getAnchorPos())
        assertEquals(-1, child.getEnterPos())
        assertEquals(-1, child.getAnchorPos())
    }

    // --- withEndRule ---

    @Test
    fun `withEndRule returns same instance if unchanged`() {
        val stack = makeStack(endRule = "end")
        assertSame(stack, stack.withEndRule("end"))
    }

    @Test
    fun `withEndRule returns new instance with different endRule`() {
        val stack = makeStack(endRule = "end1")
        val updated = stack.withEndRule("end2")
        assertNotSame(stack, updated)
        assertEquals("end2", updated.endRule)
    }

    // --- withContentNameScopesList ---

    @Test
    fun `withContentNameScopesList returns same instance if unchanged`() {
        val scopes = AttributedScopeStack.createRoot("source.test", 0)
        val stack = StateStackImpl.NULL.push(RuleId(1), -1, -1, false, null, rootScopes, scopes)
        assertSame(stack, stack.withContentNameScopesList(scopes))
    }

    @Test
    fun `withContentNameScopesList returns new instance with different scopes`() {
        val scopesA = AttributedScopeStack.createRoot("source.a", 0)
        val scopesB = AttributedScopeStack.createRoot("source.b", 0)
        val stack = StateStackImpl.NULL.push(RuleId(1), -1, -1, false, null, rootScopes, scopesA)
        val updated = stack.withContentNameScopesList(scopesB)
        assertNotSame(stack, updated)
        assertSame(scopesB, updated.contentNameScopesList)
    }

    // --- hasSameRuleAs ---

    @Test
    fun `hasSameRuleAs detects same ruleId at same enterPos`() {
        val a = StateStackImpl.NULL.push(RuleId(5), 10, 0, false, null, null, null)
        val b = StateStackImpl.NULL.push(RuleId(5), 10, 0, false, null, null, null)
        assertTrue(a.hasSameRuleAs(b))
    }

    @Test
    fun `hasSameRuleAs returns false for different ruleId`() {
        val a = StateStackImpl.NULL.push(RuleId(5), 10, 0, false, null, null, null)
        val b = StateStackImpl.NULL.push(RuleId(6), 10, 0, false, null, null, null)
        assertFalse(a.hasSameRuleAs(b))
    }

    @Test
    fun `hasSameRuleAs walks parent chain at same enterPos`() {
        val parent = StateStackImpl.NULL.push(RuleId(5), 10, 0, false, null, null, null)
        val child = parent.push(RuleId(6), 10, 0, false, null, null, null)
        val other = StateStackImpl.NULL.push(RuleId(5), 10, 0, false, null, null, null)
        assertTrue(child.hasSameRuleAs(other))
    }

    // --- getRule ---

    @Test
    fun `getRule delegates to IRuleRegistry`() {
        val helper = TestRuleFactoryHelper()
        val rule = helper.registerRule { id ->
            MatchRule(id, "test.name", "test", emptyList())
        }
        val stack = StateStackImpl.NULL.push(rule.id, -1, -1, false, null, null, null)
        assertSame(rule, stack.getRule(helper))
    }

    // --- clone ---

    @Test
    fun `clone returns this`() {
        val stack = makeStack()
        assertSame(stack, stack.clone())
    }

    // --- toString ---

    @Test
    fun `toString formats stack elements`() {
        val str = StateStackImpl.NULL.toString()
        assertTrue(str.startsWith("["))
        assertTrue(str.endsWith("]"))
    }
}
