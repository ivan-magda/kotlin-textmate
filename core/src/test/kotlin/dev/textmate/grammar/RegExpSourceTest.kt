package dev.textmate.grammar

import dev.textmate.regex.CaptureIndex
import org.junit.Assert.*
import org.junit.Test

class RegExpSourceTest {

    @Test
    fun `backslash z is replaced with end-of-string assertion`() {
        val source = RegExpSource("foo\\z", RuleId(1))
        assertEquals("foo\$(?!\\n)(?<!\\n)", source.source)
    }

    @Test
    fun `backslash A is detected as anchor`() {
        val source = RegExpSource("\\Afoo", RuleId(1))
        assertTrue(source.hasAnchor)
    }

    @Test
    fun `backslash G is detected as anchor`() {
        val source = RegExpSource("\\Gbar", RuleId(1))
        assertTrue(source.hasAnchor)
    }

    @Test
    fun `no anchors detected in plain pattern`() {
        val source = RegExpSource("hello", RuleId(1))
        assertFalse(source.hasAnchor)
    }

    @Test
    fun `back-references detected`() {
        val source = RegExpSource("(\\w+)\\s+\\1", RuleId(1))
        assertTrue(source.hasBackReferences)
    }

    @Test
    fun `no back-references in plain pattern`() {
        val source = RegExpSource("hello", RuleId(1))
        assertFalse(source.hasBackReferences)
    }

    @Test
    fun `resolveAnchors returns 4 variants`() {
        val source = RegExpSource("\\Afoo\\Gbar", RuleId(1))
        assertTrue(source.hasAnchor)

        val a1g1 = source.resolveAnchors(allowA = true, allowG = true)
        assertTrue(a1g1.contains("\\A"))
        assertTrue(a1g1.contains("\\G"))

        val a0g0 = source.resolveAnchors(allowA = false, allowG = false)
        assertTrue(a0g0.contains("\uFFFF"))
        assertFalse(a0g0.contains("\\A"))
        assertFalse(a0g0.contains("\\G"))

        val a1g0 = source.resolveAnchors(allowA = true, allowG = false)
        assertTrue(a1g0.contains("\\A"))
        assertFalse(a1g0.contains("\\G"))

        val a0g1 = source.resolveAnchors(allowA = false, allowG = true)
        assertFalse(a0g1.contains("\\A"))
        assertTrue(a0g1.contains("\\G"))
    }

    @Test
    fun `resolveAnchors returns source when no anchors`() {
        val source = RegExpSource("hello", RuleId(1))
        assertEquals("hello", source.resolveAnchors(allowA = true, allowG = true))
    }

    @Test
    fun `resolveBackReferences replaces capture refs`() {
        val source = RegExpSource("\\1-end", RuleId(1))
        val result = source.resolveBackReferences(
            "hello world",
            listOf(
                CaptureIndex(start = 0, end = 5, length = 5),
                CaptureIndex(start = 0, end = 5, length = 5)
            )
        )
        assertEquals("hello-end", result)
    }

    @Test
    fun `clone creates independent copy`() {
        val original = RegExpSource("test", RuleId(1))
        val cloned = original.clone()
        assertEquals(original.source, cloned.source)
        assertEquals(original.ruleId, cloned.ruleId)
        cloned.setSource("other")
        assertEquals("test", original.source)
        assertEquals("other", cloned.source)
    }

    @Test
    fun `setSource updates source`() {
        val source = RegExpSource("old", RuleId(1))
        assertEquals("old", source.source)
        source.setSource("new")
        assertEquals("new", source.source)
    }

    @Test
    fun `setSource is no-op when same source`() {
        val source = RegExpSource("same", RuleId(1))
        source.setSource("same")
        assertEquals("same", source.source)
    }

    @Test
    fun `empty source does not throw`() {
        val source = RegExpSource("", RuleId(1))
        assertEquals("", source.source)
        assertFalse(source.hasAnchor)
        assertFalse(source.hasBackReferences)
    }
}
