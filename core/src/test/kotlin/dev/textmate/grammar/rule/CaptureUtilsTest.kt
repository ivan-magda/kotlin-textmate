package dev.textmate.grammar.rule

import dev.textmate.regex.CaptureIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureUtilsTest {

    @Test
    fun `hasCaptures returns false for null`() {
        assertFalse(hasCaptures(null))
    }

    @Test
    fun `hasCaptures returns false for plain string`() {
        assertFalse(hasCaptures("keyword.control"))
    }

    @Test
    fun `hasCaptures returns true for dollar-number`() {
        assertTrue(hasCaptures("meta.\$1.block"))
    }

    @Test
    fun `hasCaptures returns true for dollar-brace downcase`() {
        assertTrue(hasCaptures("entity.name.\${1:/downcase}"))
    }

    @Test
    fun `replaceCaptures substitutes capture reference`() {
        val result = replaceCaptures(
            "meta.\$1.block",
            "class Foo {",
            listOf(
                CaptureIndex(start = 0, end = 11, length = 11),
                CaptureIndex(start = 6, end = 9, length = 3)
            )
        )
        assertEquals("meta.Foo.block", result)
    }

    @Test
    fun `replaceCaptures handles downcase`() {
        val result = replaceCaptures(
            "entity.name.\${1:/downcase}",
            "class FOO {",
            listOf(
                CaptureIndex(start = 0, end = 11, length = 11),
                CaptureIndex(start = 6, end = 9, length = 3)
            )
        )
        assertEquals("entity.name.foo", result)
    }

    @Test
    fun `replaceCaptures handles upcase`() {
        val result = replaceCaptures(
            "entity.name.\${1:/upcase}",
            "class foo {",
            listOf(
                CaptureIndex(start = 0, end = 11, length = 11),
                CaptureIndex(start = 6, end = 9, length = 3)
            )
        )
        assertEquals("entity.name.FOO", result)
    }

    @Test
    fun `replaceCaptures strips leading dots`() {
        val result = replaceCaptures(
            "meta.\$1.block",
            "...test...",
            listOf(
                CaptureIndex(start = 0, end = 10, length = 10),
                CaptureIndex(start = 0, end = 7, length = 7)
            )
        )
        assertEquals("meta.test.block", result)
    }

    @Test
    fun `escapeRegExpCharacters escapes metacharacters`() {
        assertEquals("hello\\.world", escapeRegExpCharacters("hello.world"))
        assertEquals("a\\+b\\*c", escapeRegExpCharacters("a+b*c"))
        assertEquals("\\[foo\\]", escapeRegExpCharacters("[foo]"))
    }
}
