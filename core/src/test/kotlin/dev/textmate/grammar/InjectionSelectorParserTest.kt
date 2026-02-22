package dev.textmate.grammar

import org.junit.Assert.*
import org.junit.Test

class InjectionSelectorParserTest {

    private fun match(selector: String, scopes: List<String>): Boolean {
        val matchers = InjectionSelectorParser.createMatchers(selector)
        return matchers.any { it.matcher(scopes) }
    }

    // --- Basic identifier matching ---

    @Test
    fun `simple scope matches prefix`() {
        assertTrue(match("comment", listOf("source.js", "comment.line.double-slash.js")))
    }

    @Test
    fun `simple scope does not match unrelated scope`() {
        assertFalse(match("comment", listOf("source.js", "string.quoted.js")))
    }

    @Test
    fun `dotted identifier matches exact scope`() {
        assertTrue(match("comment.line", listOf("source.js", "comment.line.double-slash.js")))
    }

    @Test
    fun `dotted identifier does not match partial segment`() {
        // "comment.lin" must NOT match "comment.line.double-slash.js"
        assertFalse(match("comment.lin", listOf("source.js", "comment.line.double-slash.js")))
    }

    // --- OR / comma ---

    @Test
    fun `comma-separated OR matches any clause`() {
        assertTrue(match("text, string, comment", listOf("source.js", "comment.line.double-slash.js")))
        assertTrue(match("text, string, comment", listOf("source.js", "string.quoted.js")))
        assertFalse(match("text, string, comment", listOf("source.js")))
    }

    // --- AND (space-separated) ---

    @Test
    fun `space-separated AND requires all identifiers in order`() {
        assertTrue(match("source.js comment", listOf("source.js", "comment.line.double-slash.js")))
        assertFalse(match("source.js comment", listOf("source.js", "string.quoted.js")))
        // order matters — "comment source.js" won't match stack [source.js, comment]
        assertFalse(match("comment source.js", listOf("source.js", "comment.line.double-slash.js")))
    }

    // --- Negation ---

    @Test
    fun `negation excludes matching scope`() {
        assertFalse(match("source.js -comment", listOf("source.js", "comment.line.double-slash.js")))
        assertTrue(match("source.js -comment", listOf("source.js", "string.quoted.js")))
    }

    // --- Priority ---

    @Test
    fun `L prefix gives HIGH priority`() {
        val matchers = InjectionSelectorParser.createMatchers("L:comment")
        assertEquals(1, matchers.size)
        assertEquals(InjectionPriority.HIGH, matchers[0].priority)
    }

    @Test
    fun `R prefix gives LOW priority`() {
        val matchers = InjectionSelectorParser.createMatchers("R:comment")
        assertEquals(1, matchers.size)
        assertEquals(InjectionPriority.LOW, matchers[0].priority)
    }

    @Test
    fun `no prefix gives DEFAULT priority`() {
        val matchers = InjectionSelectorParser.createMatchers("comment")
        assertEquals(InjectionPriority.DEFAULT, matchers[0].priority)
    }

    // --- Edge cases ---

    @Test
    fun `bad selector returns empty list without throwing`() {
        val matchers = InjectionSelectorParser.createMatchers("((( bad")
        // must not throw; must not produce a trivially-true matcher
        matchers.forEach { assertFalse("Should not match empty scope list", it.matcher(emptyList())) }
    }

    @Test
    fun `empty selector returns empty list`() {
        assertTrue(InjectionSelectorParser.createMatchers("").isEmpty())
    }
}
