package dev.textmate.grammar.rule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IncludeReferenceTest {

    @Test
    fun `parse dollar base`() {
        val ref = parseInclude("\$base")
        assertTrue(ref is IncludeReference.BaseReference)
    }

    @Test
    fun `parse dollar self`() {
        val ref = parseInclude("\$self")
        assertTrue(ref is IncludeReference.SelfReference)
    }

    @Test
    fun `parse relative reference`() {
        val ref = parseInclude("#string") as IncludeReference.RelativeReference
        assertEquals("string", ref.ruleName)
    }

    @Test
    fun `parse top-level reference`() {
        val ref = parseInclude("source.json") as IncludeReference.TopLevelReference
        assertEquals("source.json", ref.scopeName)
    }

    @Test
    fun `parse top-level repository reference`() {
        val ref = parseInclude("source.json#string") as IncludeReference.TopLevelRepositoryReference
        assertEquals("source.json", ref.scopeName)
        assertEquals("string", ref.ruleName)
    }
}
