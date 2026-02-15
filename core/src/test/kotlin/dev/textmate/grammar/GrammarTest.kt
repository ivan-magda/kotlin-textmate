package dev.textmate.grammar

import dev.textmate.grammar.raw.GrammarReader
import dev.textmate.regex.JoniOnigLib
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class GrammarTest {

    private lateinit var grammar: Grammar

    private fun loadGrammar(resourcePath: String): Grammar {
        val rawGrammar = javaClass.classLoader.getResourceAsStream(resourcePath)
            ?.use { stream -> GrammarReader.readGrammar(stream) }
            ?: throw IllegalArgumentException("Resource not found: $resourcePath")
        return Grammar(rawGrammar.scopeName, rawGrammar, JoniOnigLib())
    }

    @Before
    fun setUp() {
        grammar = loadGrammar("grammars/JSON.tmLanguage.json")
    }

    @Test
    fun `tokenize empty string`() {
        val result = grammar.tokenizeLine("")
        assertEquals("Should have exactly one token", 1, result.tokens.size)
        val token = result.tokens[0]
        assertEquals(0, token.startIndex)
        assertTrue("Token should include source.json", token.scopes.contains("source.json"))
    }

    @Test
    fun `tokenize JSON boolean`() {
        val result = grammar.tokenizeLine("true")
        assertEquals("Should have exactly one token", 1, result.tokens.size)
        val token = result.tokens[0]
        assertEquals(0, token.startIndex)
        assertEquals(4, token.endIndex)
        assertTrue(token.scopes.contains("source.json"))
        assertTrue(token.scopes.contains("constant.language.json"))
    }

    @Test
    fun `tokenize JSON number`() {
        val result = grammar.tokenizeLine("42")
        assertEquals("Should have exactly one token", 1, result.tokens.size)
        val token = result.tokens[0]
        assertEquals(0, token.startIndex)
        assertEquals(2, token.endIndex)
        assertTrue(token.scopes.contains("source.json"))
        assertTrue(token.scopes.contains("constant.numeric.json"))
    }

    @Test
    fun `tokenize JSON object`() {
        val result = grammar.tokenizeLine("""{"key": "value"}""")
        val hasStringScope = result.tokens.any { token ->
            token.scopes.contains("string.quoted.double.json")
        }
        assertTrue("JSON object should have string scopes", hasStringScope)
    }

    @Test
    fun `tokens cover entire line`() {
        val line = """{"key": "value"}"""
        val result = grammar.tokenizeLine(line)
        val tokens = result.tokens

        assertEquals("First token should start at 0", 0, tokens.first().startIndex)

        // No gaps between tokens
        for (i in 1 until tokens.size) {
            assertEquals(
                "Token $i should start where token ${i - 1} ends",
                tokens[i - 1].endIndex,
                tokens[i].startIndex
            )
        }

        assertEquals("Last token should end at line length", line.length, tokens.last().endIndex)

        // No token should extend beyond the original line
        assertTrue(
            "No token should have endIndex > line.length",
            tokens.all { it.endIndex <= line.length }
        )
    }

    @Test
    fun `multiline state passing`() {
        val result1 = grammar.tokenizeLine("{")
        val result2 = grammar.tokenizeLine(""""key": "value"""", result1.ruleStack)

        val hasStringScope = result2.tokens.any { token ->
            token.scopes.contains("string.quoted.double.json")
        }
        assertTrue("Line 2 should have string scopes using prevState", hasStringScope)
    }

    @Test
    fun `multiline array tokenization`() {
        val result1 = grammar.tokenizeLine("[")
        val result2 = grammar.tokenizeLine("1,", result1.ruleStack)

        val numericToken = result2.tokens.find { token ->
            token.scopes.contains("constant.numeric.json")
        }
        assertNotNull("Number inside array on line 2 should have numeric scope", numericToken)
    }

    @Test
    fun `INITIAL state works like null`() {
        val resultNull = grammar.tokenizeLine("true", null)
        val resultInitial = grammar.tokenizeLine("true", INITIAL)

        assertEquals(
            "INITIAL should produce same tokens as null",
            resultNull.tokens,
            resultInitial.tokens
        )
    }

    @Test
    fun `detailed scope check for curly brace`() {
        val result = grammar.tokenizeLine("{")
        assertEquals("Should have exactly one token", 1, result.tokens.size)
        val token = result.tokens[0]
        assertEquals(0, token.startIndex)
        assertEquals(1, token.endIndex)
        assertTrue(token.scopes.contains("punctuation.definition.dictionary.begin.json"))
        assertTrue(token.scopes.contains("meta.structure.dictionary.json"))
        assertTrue(token.scopes.contains("source.json"))
    }

    @Test
    fun `string literal produces begin content and end tokens`() {
        val result = grammar.tokenizeLine("\"hello\"")
        val tokens = result.tokens
        assertEquals("String should produce 3 tokens", 3, tokens.size)

        // Opening quote
        val begin = tokens[0]
        assertEquals(0, begin.startIndex)
        assertEquals(1, begin.endIndex)
        assertTrue(begin.scopes.contains("punctuation.definition.string.begin.json"))

        // String content
        val content = tokens[1]
        assertEquals(1, content.startIndex)
        assertEquals(6, content.endIndex)
        assertTrue(content.scopes.contains("string.quoted.double.json"))

        // Closing quote
        val end = tokens[2]
        assertEquals(6, end.startIndex)
        assertEquals(7, end.endIndex)
        assertTrue(end.scopes.contains("punctuation.definition.string.end.json"))
    }

    @Test
    fun `three-line tokenization with state passing`() {
        val r1 = grammar.tokenizeLine("{")
        val r2 = grammar.tokenizeLine("  \"key\": true", r1.ruleStack)
        val r3 = grammar.tokenizeLine("}", r2.ruleStack)

        // Line 2 should have property-name and boolean scopes
        assertTrue(r2.tokens.any { it.scopes.contains("support.type.property-name.json") })
        assertTrue(r2.tokens.any { it.scopes.contains("constant.language.json") })

        // Line 3 tokens should cover the full line
        assertEquals(0, r3.tokens.first().startIndex)
        assertEquals(1, r3.tokens.last().endIndex)
    }

    // --- Markdown grammar tests (BeginWhileRule coverage) ---

    @Test
    fun `Markdown fenced code block exercises BeginWhileRule`() {
        val mdGrammar = loadGrammar("grammars/markdown.tmLanguage.json")

        // ``` triggers a BeginWhileRule (fenced_code.block)
        val r1 = mdGrammar.tokenizeLine("```")
        assertTrue(
            "Fenced code opener should have fenced_code scope",
            r1.tokens.any { it.scopes.contains("markup.fenced_code.block.markdown") }
        )
        assertTrue(
            "Fenced code opener should have punctuation scope",
            r1.tokens.any { it.scopes.contains("punctuation.definition.markdown") }
        )

        // Content inside the fenced code block should carry state
        val r2 = mdGrammar.tokenizeLine("code here", r1.ruleStack)
        assertTrue(
            "Content inside fenced code should still have fenced_code scope",
            r2.tokens.any { it.scopes.contains("markup.fenced_code.block.markdown") }
        )
    }
}
