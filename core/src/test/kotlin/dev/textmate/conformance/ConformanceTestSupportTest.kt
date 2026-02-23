package dev.textmate.conformance

import dev.textmate.grammar.Token
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConformanceTestSupportTest {

    @Test
    fun `loads first-mate tests json`() {
        val tests = ConformanceTestSupport.loadFirstMateTests(
            "conformance/first-mate/tests.json"
        )
        assertTrue("Should load at least 60 test cases", tests.size >= 60)

        val test3 = requireNotNull(tests.find { it.desc == "TEST #3" }) { "Should find TEST #3" }
        assertEquals("fixtures/hello.json", test3.grammarPath)
        assertEquals(1, test3.lines.size)
        assertEquals("hello world!", test3.lines[0].line)
        assertTrue(test3.lines[0].tokens.isNotEmpty())

        val firstToken = test3.lines[0].tokens[0]
        assertEquals("hello", firstToken.value)
        assertTrue(firstToken.scopes.contains("source.hello"))
    }

    @Test
    fun `actualToExpected preserves empty token on empty line`() {
        val tokens = listOf(Token(0, 0, listOf("source.test")))
        val result = ConformanceTestSupport.actualToExpected("", tokens)
        assertEquals(1, result.size)
        assertEquals("", result[0].value)
        assertEquals(listOf("source.test"), result[0].scopes)
    }

    @Test
    fun `actualToExpected clamps endIndex exceeding line length`() {
        val tokens = listOf(Token(0, 6, listOf("source.test")))
        val result = ConformanceTestSupport.actualToExpected("hello", tokens)
        assertEquals(1, result.size)
        assertEquals("hello", result[0].value)
    }

    @Test
    fun `actualToExpected does not filter empty tokens on non-empty line`() {
        val tokens = listOf(
            Token(0, 0, listOf("source.test")),
            Token(0, 5, listOf("source.test", "keyword"))
        )
        val result = ConformanceTestSupport.actualToExpected("hello", tokens)
        assertEquals(2, result.size)
        assertEquals("", result[0].value)
        assertEquals("hello", result[1].value)
    }
}
