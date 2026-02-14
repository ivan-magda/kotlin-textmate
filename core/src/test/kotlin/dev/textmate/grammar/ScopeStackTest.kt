package dev.textmate.grammar

import org.junit.Assert.*
import org.junit.Test

class ScopeStackTest {

    @Test
    fun `from creates linked list from segments`() {
        val stack = ScopeStack.from("source.kotlin", "meta.class", "entity.name")
        assertNotNull(stack)
        assertEquals(listOf("source.kotlin", "meta.class", "entity.name"), stack!!.getSegments())
    }

    @Test
    fun `from with no segments returns null`() {
        val stack = ScopeStack.from()
        assertNull(stack)
    }

    @Test
    fun `from with single segment`() {
        val stack = ScopeStack.from("source.kotlin")
        assertNotNull(stack)
        assertEquals("source.kotlin", stack!!.scopeName)
        assertNull(stack.parent)
    }

    @Test
    fun `push adds scope to stack`() {
        val base = ScopeStack.from("source.kotlin")!!
        val pushed = base.push("string.quoted")
        assertEquals("string.quoted", pushed.scopeName)
        assertEquals(listOf("source.kotlin", "string.quoted"), pushed.getSegments())
    }

    @Test
    fun `companion push adds multiple scopes`() {
        val base = ScopeStack.from("source.kotlin")
        val result = ScopeStack.push(base, listOf("meta.class", "entity.name"))
        assertNotNull(result)
        assertEquals(listOf("source.kotlin", "meta.class", "entity.name"), result!!.getSegments())
    }

    @Test
    fun `companion push on null path`() {
        val result = ScopeStack.push(null, listOf("source.kotlin", "meta.class"))
        assertNotNull(result)
        assertEquals(listOf("source.kotlin", "meta.class"), result!!.getSegments())
    }

    @Test
    fun `companion push with empty list returns same path`() {
        val base = ScopeStack.from("source.kotlin")
        val result = ScopeStack.push(base, emptyList())
        assertSame(base, result)
    }

    @Test
    fun `getSegments returns ordered list`() {
        val stack = ScopeStack(ScopeStack(null, "a"), "b")
        assertEquals(listOf("a", "b"), stack.getSegments())
    }

    @Test
    fun `extends checks ancestry`() {
        val root = ScopeStack.from("source.kotlin")!!
        val child = root.push("meta.class")
        val grandchild = child.push("entity.name")

        assertTrue(grandchild.extends(root))
        assertTrue(grandchild.extends(child))
        assertTrue(child.extends(root))
        assertTrue(root.extends(root))
        assertFalse(root.extends(child))
    }

    @Test
    fun `getExtensionIfDefined returns delta from base`() {
        val root = ScopeStack.from("source.kotlin")!!
        val child = root.push("meta.class").push("entity.name")

        val extension = child.getExtensionIfDefined(root)
        assertNotNull(extension)
        assertEquals(listOf("meta.class", "entity.name"), extension)
    }

    @Test
    fun `getExtensionIfDefined with null base returns all segments`() {
        val stack = ScopeStack.from("source.kotlin", "meta.class")!!
        val extension = stack.getExtensionIfDefined(null)
        assertNotNull(extension)
        assertEquals(listOf("source.kotlin", "meta.class"), extension)
    }

    @Test
    fun `getExtensionIfDefined returns null when base is not ancestor`() {
        val a = ScopeStack.from("source.kotlin")!!
        val b = ScopeStack.from("source.json")!!.push("meta.structure")
        assertNull(b.getExtensionIfDefined(a))
    }

    @Test
    fun `getExtensionIfDefined with self returns empty list`() {
        val stack = ScopeStack.from("source.kotlin")!!
        val extension = stack.getExtensionIfDefined(stack)
        assertNotNull(extension)
        assertEquals(emptyList<String>(), extension)
    }

    @Test
    fun `toString returns space-separated scope names`() {
        val stack = ScopeStack.from("source.kotlin", "meta.class")!!
        assertEquals("source.kotlin meta.class", stack.toString())
    }
}
