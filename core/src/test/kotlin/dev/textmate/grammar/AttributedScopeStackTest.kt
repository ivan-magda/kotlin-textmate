package dev.textmate.grammar

import org.junit.Assert.*
import org.junit.Test

class AttributedScopeStackTest {

    @Test
    fun `createRoot creates single scope`() {
        val root = AttributedScopeStack.createRoot("source.kotlin", 0)
        assertEquals("source.kotlin", root.scopeName)
        assertNull(root.parent)
        assertEquals(listOf("source.kotlin"), root.getScopeNames())
    }

    @Test
    fun `createRoot preserves tokenAttributes`() {
        val root = AttributedScopeStack.createRoot("source.kotlin", 42)
        assertEquals(42, root.tokenAttributes)
    }

    @Test
    fun `pushAttributed adds single scope`() {
        val root = AttributedScopeStack.createRoot("source.kotlin", 0)
        val pushed = root.pushAttributed("meta.class", null)
        assertEquals(listOf("source.kotlin", "meta.class"), pushed.getScopeNames())
        assertEquals("meta.class", pushed.scopeName)
    }

    @Test
    fun `pushAttributed with null returns same instance`() {
        val root = AttributedScopeStack.createRoot("source.kotlin", 0)
        val result = root.pushAttributed(null, null)
        assertSame(root, result)
    }

    @Test
    fun `pushAttributed handles space-separated scopes`() {
        val root = AttributedScopeStack.createRoot("source.kotlin", 0)
        val pushed = root.pushAttributed("meta.class entity.name.type", null)
        assertEquals(listOf("source.kotlin", "meta.class", "entity.name.type"), pushed.getScopeNames())
    }

    @Test
    fun `equals checks scope names and attributes`() {
        val a = AttributedScopeStack.createRoot("source.kotlin", 0)
            .pushAttributed("meta.class", null)
        val b = AttributedScopeStack.createRoot("source.kotlin", 0)
            .pushAttributed("meta.class", null)
        assertTrue(AttributedScopeStack.equals(a, b))
        assertTrue(a == b)
    }

    @Test
    fun `equals detects different scope names`() {
        val a = AttributedScopeStack.createRoot("source.kotlin", 0)
        val b = AttributedScopeStack.createRoot("source.json", 0)
        assertFalse(AttributedScopeStack.equals(a, b))
    }

    @Test
    fun `equals detects different attributes`() {
        val a = AttributedScopeStack.createRoot("source.kotlin", 1)
        val b = AttributedScopeStack.createRoot("source.kotlin", 2)
        assertFalse(AttributedScopeStack.equals(a, b))
    }

    @Test
    fun `equals with both null returns true`() {
        assertTrue(AttributedScopeStack.equals(null, null))
    }

    @Test
    fun `equals with one null returns false`() {
        val a = AttributedScopeStack.createRoot("source.kotlin", 0)
        assertFalse(AttributedScopeStack.equals(a, null))
        assertFalse(AttributedScopeStack.equals(null, a))
    }

    @Test
    fun `equals checks full chain`() {
        val a = AttributedScopeStack.createRoot("source.kotlin", 0)
            .pushAttributed("meta.class", null)
            .pushAttributed("entity.name", null)
        val b = AttributedScopeStack.createRoot("source.kotlin", 0)
            .pushAttributed("meta.class", null)
            .pushAttributed("entity.name", null)
        assertTrue(a == b)

        val c = AttributedScopeStack.createRoot("source.kotlin", 0)
            .pushAttributed("meta.function", null)
            .pushAttributed("entity.name", null)
        assertFalse(a == c)
    }

    @Test
    fun `toString returns space-separated scope names`() {
        val stack = AttributedScopeStack.createRoot("source.kotlin", 0)
            .pushAttributed("meta.class", null)
        assertEquals("source.kotlin meta.class", stack.toString())
    }

    @Test
    fun `fromExtension rebuilds from frames`() {
        val root = AttributedScopeStack.createRoot("source.kotlin", 0)
        val frames = listOf(
            AttributedScopeStackFrame(0, listOf("meta.class")),
            AttributedScopeStackFrame(0, listOf("entity.name"))
        )
        val result = AttributedScopeStack.fromExtension(root, frames)
        assertNotNull(result)
        assertEquals(listOf("source.kotlin", "meta.class", "entity.name"), result!!.getScopeNames())
    }

    @Test
    fun `fromExtension with empty frames returns same instance`() {
        val root = AttributedScopeStack.createRoot("source.kotlin", 0)
        val result = AttributedScopeStack.fromExtension(root, emptyList())
        assertSame(root, result)
    }

    @Test
    fun `fromExtension on null base`() {
        val frames = listOf(
            AttributedScopeStackFrame(0, listOf("source.kotlin")),
            AttributedScopeStackFrame(0, listOf("meta.class"))
        )
        val result = AttributedScopeStack.fromExtension(null, frames)
        assertNotNull(result)
        assertEquals(listOf("source.kotlin", "meta.class"), result!!.getScopeNames())
    }

    @Test
    fun `getExtensionIfDefined returns frames relative to base`() {
        val root = AttributedScopeStack.createRoot("source.kotlin", 0)
        val child = root.pushAttributed("meta.class", null)
            .pushAttributed("entity.name", null)

        val ext = child.getExtensionIfDefined(root)
        assertNotNull(ext)
        assertEquals(2, ext!!.size)
        assertEquals(listOf("meta.class"), ext[0].scopeNames)
        assertEquals(listOf("entity.name"), ext[1].scopeNames)
    }

    @Test
    fun `getExtensionIfDefined returns null when base is not ancestor`() {
        val a = AttributedScopeStack.createRoot("source.kotlin", 0)
            .pushAttributed("meta.class", null)
        val b = AttributedScopeStack.createRoot("source.json", 0)
        assertNull(a.getExtensionIfDefined(b))
    }

    @Test
    fun `getExtensionIfDefined with self returns empty list`() {
        val stack = AttributedScopeStack.createRoot("source.kotlin", 0)
        val ext = stack.getExtensionIfDefined(stack)
        assertNotNull(ext)
        assertEquals(emptyList<AttributedScopeStackFrame>(), ext)
    }

    // --- hashCode ---

    @Test
    fun `equal objects have equal hashCodes`() {
        val a = AttributedScopeStack.createRoot("source.kotlin", 0)
            .pushAttributed("meta.class", null)
        val b = AttributedScopeStack.createRoot("source.kotlin", 0)
            .pushAttributed("meta.class", null)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `different scope names produce different hashCodes`() {
        val a = AttributedScopeStack.createRoot("source.kotlin", 0)
        val b = AttributedScopeStack.createRoot("source.json", 0)
        assertNotEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `different tokenAttributes produce different hashCodes`() {
        val a = AttributedScopeStack.createRoot("source.kotlin", 1)
        val b = AttributedScopeStack.createRoot("source.kotlin", 2)
        assertNotEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `fromExtension preserves non-zero tokenAttributes`() {
        val root = AttributedScopeStack.createRoot("source.kotlin", 0)
        val frames = listOf(
            AttributedScopeStackFrame(42, listOf("meta.class")),
            AttributedScopeStackFrame(99, listOf("entity.name"))
        )
        val result = AttributedScopeStack.fromExtension(root, frames)
        assertNotNull(result)
        assertEquals(99, result!!.tokenAttributes)
        assertEquals(42, result.parent!!.tokenAttributes)
    }
}
