package dev.textmate.regex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class JoniOnigScannerTest {

    private val lib = JoniOnigLib()

    // Test 1: Simple keyword match
    @Test
    fun `simple keyword match`() {
        val scanner = lib.createOnigScanner(listOf("\\bfun\\b"))
        val str = lib.createOnigString("fun main()")

        val result = scanner.findNextMatchSync(str, 0)

        assertNotNull(result)
        assertEquals(0, result!!.index)
        assertEquals(0, result.captureIndices[0].start)
        assertEquals(3, result.captureIndices[0].end)
        assertEquals(3, result.captureIndices[0].length)
    }

    // Test 2: Capture groups
    @Test
    fun `capture groups`() {
        val scanner = lib.createOnigScanner(listOf("(\\w+)\\s*=\\s*(\\w+)"))
        val str = lib.createOnigString("val x = 42")

        val result = scanner.findNextMatchSync(str, 0)

        assertNotNull(result)
        assertEquals(3, result!!.captureIndices.size)
        // Group 0: full match "x = 42"
        assertEquals(4, result.captureIndices[0].start)
        assertEquals(10, result.captureIndices[0].end)
        // Group 1: "x"
        assertEquals(4, result.captureIndices[1].start)
        assertEquals(5, result.captureIndices[1].end)
        assertEquals(1, result.captureIndices[1].length)
        // Group 2: "42"
        assertEquals(8, result.captureIndices[2].start)
        assertEquals(10, result.captureIndices[2].end)
    }

    // Test 3: Multiple patterns — earliest match wins
    @Test
    fun `multiple patterns - earliest match returned`() {
        val scanner = lib.createOnigScanner(listOf(
            "\\bclass\\b",
            "\\bfun\\b",
            "\\bval\\b"
        ))
        val str = lib.createOnigString("fun main() { val x = 1 }")

        val result = scanner.findNextMatchSync(str, 0)

        assertNotNull(result)
        assertEquals(1, result!!.index)
        assertEquals(0, result.captureIndices[0].start)
        assertEquals(3, result.captureIndices[0].end)
    }

    // Test 4: Same position tie-break — first pattern in list wins
    @Test
    fun `same position tie-break - first pattern wins`() {
        val scanner = lib.createOnigScanner(listOf("f", "fun"))
        val str = lib.createOnigString("fun main()")

        val result = scanner.findNextMatchSync(str, 0)

        assertNotNull(result)
        assertEquals(0, result!!.index)
    }

    // Test 5: Start position parameter
    @Test
    fun `start position - search from middle of string`() {
        val scanner = lib.createOnigScanner(listOf("\\bfun\\b"))
        val str = lib.createOnigString("val x = fun()")

        val result1 = scanner.findNextMatchSync(str, 0)
        assertNotNull(result1)
        assertEquals(8, result1!!.captureIndices[0].start)

        val result2 = scanner.findNextMatchSync(str, 9)
        assertNull(result2)
    }

    // Test 6: Unicode — Cyrillic (2-byte UTF-8)
    @Test
    fun `unicode - cyrillic byte offset conversion`() {
        // Use [^\s]+ instead of \w+ because Joni's \w is ASCII-only
        val scanner = lib.createOnigScanner(listOf("[^\\s]+"))
        val str = lib.createOnigString("\u041f\u0440\u0438\u0432\u0435\u0442 \u043c\u0438\u0440")
        // "Привет мир" — 6 chars + space + 3 chars = 10 chars, 19 bytes

        val result = scanner.findNextMatchSync(str, 0)

        assertNotNull(result)
        assertEquals(0, result!!.captureIndices[0].start)
        assertEquals(6, result.captureIndices[0].end)
        assertEquals(6, result.captureIndices[0].length)

        // Search from char offset 7 (past the space)
        val result2 = scanner.findNextMatchSync(str, 7)
        assertNotNull(result2)
        assertEquals(7, result2!!.captureIndices[0].start)
        assertEquals(10, result2.captureIndices[0].end)
    }

    // Test 7: Unicode — mixed ASCII and multi-byte
    @Test
    fun `unicode - mixed ascii and multibyte`() {
        val scanner = lib.createOnigScanner(listOf("world"))
        val str = lib.createOnigString("Hello\u3001world")
        // H=1 e=1 l=1 l=1 o=1 \u3001=3bytes w=1 o=1 r=1 l=1 d=1 = 13 bytes, 11 chars

        val result = scanner.findNextMatchSync(str, 0)

        assertNotNull(result)
        assertEquals(6, result!!.captureIndices[0].start)
        assertEquals(11, result.captureIndices[0].end)
    }

    // Test 8: Unicode — emoji (4-byte UTF-8 / surrogate pairs)
    @Test
    fun `unicode - emoji surrogate pairs`() {
        val scanner = lib.createOnigScanner(listOf("world"))
        // U+1F600 = surrogate pair \uD83D\uDE00, 4 UTF-8 bytes, 2 Kotlin Chars
        val str = lib.createOnigString("\uD83D\uDE00 world")
        // emoji=4bytes space=1 world=5 = 10 bytes; Kotlin length = 2+1+5 = 8 chars
        // But wait: "world" starts at char offset 3 (surrogate pair = 2 chars, space = 1)

        val result = scanner.findNextMatchSync(str, 0)

        assertNotNull(result)
        assertEquals(3, result!!.captureIndices[0].start)
        assertEquals(8, result.captureIndices[0].end)
    }

    // Test 9: No match returns null
    @Test
    fun `no match returns null`() {
        val scanner = lib.createOnigScanner(listOf("\\bclass\\b"))
        val str = lib.createOnigString("fun main()")

        val result = scanner.findNextMatchSync(str, 0)

        assertNull(result)
    }

    // Test 10: Empty string
    @Test
    fun `empty string - no match`() {
        val scanner = lib.createOnigScanner(listOf("\\w+"))
        val str = lib.createOnigString("")

        val result = scanner.findNextMatchSync(str, 0)

        assertNull(result)
    }

    // Test 11: Match at end of string
    @Test
    fun `match at end of string`() {
        val scanner = lib.createOnigScanner(listOf("\\)$"))
        val str = lib.createOnigString("fun main()")

        val result = scanner.findNextMatchSync(str, 0)

        assertNotNull(result)
        assertEquals(9, result!!.captureIndices[0].start)
        assertEquals(10, result.captureIndices[0].end)
    }

    // Test 12: Optional capture group (unmatched)
    @Test
    fun `optional capture group that does not match`() {
        val scanner = lib.createOnigScanner(listOf("(\\w+)(\\s*=\\s*(\\w+))?"))
        val str = lib.createOnigString("hello")

        val result = scanner.findNextMatchSync(str, 0)

        assertNotNull(result)
        // Group 0: "hello"
        assertEquals(0, result!!.captureIndices[0].start)
        assertEquals(5, result.captureIndices[0].end)
        // Group 1: "hello"
        assertEquals(0, result.captureIndices[1].start)
        assertEquals(5, result.captureIndices[1].end)
        // Group 2: unmatched
        assertEquals(0, result.captureIndices[2].start)
        assertEquals(0, result.captureIndices[2].end)
        assertEquals(0, result.captureIndices[2].length)
        // Group 3: unmatched
        assertEquals(0, result.captureIndices[3].start)
        assertEquals(0, result.captureIndices[3].end)
    }

    // Test 13: OnigString reuse across multiple scanners
    @Test
    fun `onigString reuse - multiple scanners on same string`() {
        val scanner1 = lib.createOnigScanner(listOf("\\bfun\\b"))
        val scanner2 = lib.createOnigScanner(listOf("\\bmain\\b"))
        val str = lib.createOnigString("fun main()")

        val r1 = scanner1.findNextMatchSync(str, 0)
        val r2 = scanner2.findNextMatchSync(str, 0)

        assertNotNull(r1)
        assertEquals(0, r1!!.captureIndices[0].start)
        assertNotNull(r2)
        assertEquals(4, r2!!.captureIndices[0].start)
    }

    // Test 14: Invalid regex degrades gracefully (never matches)
    @Test
    fun `invalid regex pattern degrades to never-matching`() {
        val scanner = lib.createOnigScanner(listOf("[invalid"))
        val str = lib.createOnigString("test input")
        assertNull(scanner.findNextMatchSync(str, 0))
    }

    // Test 15: Invalid pattern among valid patterns preserves index mapping
    @Test
    fun `invalid pattern among valid patterns preserves index mapping`() {
        val scanner = lib.createOnigScanner(listOf("[invalid", "\\bfun\\b"))
        val str = lib.createOnigString("fun main()")
        val result = scanner.findNextMatchSync(str, 0)
        assertNotNull(result)
        assertEquals(1, result!!.index)
        assertEquals(0, result.captureIndices[0].start)
        assertEquals(3, result.captureIndices[0].end)
    }

    // Test 16: Empty patterns list
    @Test
    fun `empty patterns list returns no match`() {
        val scanner = lib.createOnigScanner(emptyList())
        val str = lib.createOnigString("fun main()")
        assertNull(scanner.findNextMatchSync(str, 0))
    }

    // Test 16: Negative start position treated as zero
    @Test
    fun `negative start position is treated as zero`() {
        val scanner = lib.createOnigScanner(listOf("\\bfun\\b"))
        val str = lib.createOnigString("fun main()")
        val result = scanner.findNextMatchSync(str, -5)
        assertNotNull(result)
        assertEquals(0, result!!.captureIndices[0].start)
    }

    // Test 17: Start position beyond string length
    @Test
    fun `start position beyond string length returns null`() {
        val scanner = lib.createOnigScanner(listOf("\\bfun\\b"))
        val str = lib.createOnigString("fun main()")
        assertNull(scanner.findNextMatchSync(str, 100))
    }

    // Test 18: Start position at exact string length
    @Test
    fun `start position at string length returns null`() {
        val text = "fun main()"
        val scanner = lib.createOnigScanner(listOf("\\bfun\\b"))
        val str = lib.createOnigString(text)
        assertNull(scanner.findNextMatchSync(str, text.length))
    }
}
