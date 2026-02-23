package dev.textmate.grammar

import dev.textmate.grammar.raw.GrammarReader
import dev.textmate.grammar.tokenize.INITIAL
import dev.textmate.regex.JoniOnigLib
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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

    // --- Kotlin grammar tests (capture retokenization) ---

    @Test
    fun `Kotlin fun keyword and function name scoped correctly`() {
        val ktGrammar = loadGrammar("grammars/Kotlin.tmLanguage.json")
        val result = ktGrammar.tokenizeLine("fun main(args: Array<String>)")
        assertTrue(
            "Should have keyword.hard.fun scope",
            result.tokens.any { it.scopes.contains("keyword.hard.fun.kotlin") }
        )
        assertTrue(
            "Should have function name scope",
            result.tokens.any { it.scopes.contains("entity.name.function.declaration.kotlin") }
        )
    }

    @Test
    fun `Kotlin generic function retokenizes type parameters`() {
        val ktGrammar = loadGrammar("grammars/Kotlin.tmLanguage.json")
        val result = ktGrammar.tokenizeLine("fun <T> test()")
        assertTrue(
            "Should have type parameter scope from retokenization",
            result.tokens.any { it.scopes.contains("entity.name.type.kotlin") }
        )
        assertTrue(
            "Should have function name scope",
            result.tokens.any { it.scopes.contains("entity.name.function.declaration.kotlin") }
        )
    }

    @Test
    fun `Kotlin function tokens cover entire line`() {
        val ktGrammar = loadGrammar("grammars/Kotlin.tmLanguage.json")
        val line = "fun main(args: Array<String>)"
        val result = ktGrammar.tokenizeLine(line)
        val tokens = result.tokens

        assertEquals("First token should start at 0", 0, tokens.first().startIndex)
        for (i in 1 until tokens.size) {
            assertEquals(
                "Token $i should start where token ${i - 1} ends",
                tokens[i - 1].endIndex,
                tokens[i].startIndex
            )
        }
        // Last token may extend 1 past line.length due to appended \n (matches vscode-textmate behavior)
        assertTrue(
            "Last token should end at or past line length",
            tokens.last().endIndex >= line.length
        )
    }

    // --- Markdown grammar tests ---

    @Test
    fun `Markdown fenced code block multiline state`() {
        val mdGrammar = loadGrammar("grammars/markdown.tmLanguage.json")

        // ``` triggers fenced_code_block_unknown (a BeginEndRule)
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

    // --- Multiline / BeginWhileRule condition checking tests ---

    @Test
    fun `multiline block comment in JSON`() {
        // JSON block comments use BeginEndRule, verify state carries across lines
        val r1 = grammar.tokenizeLine("/* start")
        assertTrue(
            "Opening line should have block comment scope",
            r1.tokens.any { it.scopes.contains("comment.block.json") }
        )

        val r2 = grammar.tokenizeLine("middle", r1.ruleStack)
        assertTrue(
            "Middle line should still be inside block comment",
            r2.tokens.any { it.scopes.contains("comment.block.json") }
        )

        val r3 = grammar.tokenizeLine("end */", r2.ruleStack)
        assertTrue(
            "Closing line should have block comment scope",
            r3.tokens.any { it.scopes.contains("comment.block.json") }
        )
    }

    @Test
    fun `Markdown fenced code block closes properly`() {
        val mdGrammar = loadGrammar("grammars/markdown.tmLanguage.json")

        val r1 = mdGrammar.tokenizeLine("```")
        assertTrue(
            r1.tokens.any { it.scopes.contains("markup.fenced_code.block.markdown") }
        )

        val r2 = mdGrammar.tokenizeLine("code here", r1.ruleStack)
        assertTrue(
            "Content should have fenced_code scope",
            r2.tokens.any { it.scopes.contains("markup.fenced_code.block.markdown") }
        )

        // Closing ``` pops the BeginEndRule
        val r3 = mdGrammar.tokenizeLine("```", r2.ruleStack)

        // Empty line after close should NOT have fenced_code scope
        // (avoids Joni lookbehind crash from Markdown inline patterns)
        val r4 = mdGrammar.tokenizeLine("", r3.ruleStack)
        assertFalse(
            "Line after closing fence should NOT have fenced_code scope",
            r4.tokens.any { it.scopes.contains("markup.fenced_code.block.markdown") }
        )
    }

    @Test
    fun `Kotlin multiline string carries state across lines`() {
        val ktGrammar = loadGrammar("grammars/Kotlin.tmLanguage.json")

        val r1 = ktGrammar.tokenizeLine("val s = \"\"\"")
        assertTrue(
            "Triple-quote should start a string scope",
            r1.tokens.any { it.scopes.any { s -> s.contains("string") } }
        )

        val r2 = ktGrammar.tokenizeLine("  content", r1.ruleStack)
        assertTrue(
            "Content inside multiline string should have string scope",
            r2.tokens.any { it.scopes.any { s -> s.contains("string") } }
        )
    }

    @Test
    fun `clean state reset after fenced code block closes`() {
        val mdGrammar = loadGrammar("grammars/markdown.tmLanguage.json")

        val r1 = mdGrammar.tokenizeLine("```")
        val r2 = mdGrammar.tokenizeLine("code", r1.ruleStack)
        val r3 = mdGrammar.tokenizeLine("```", r2.ruleStack)

        // Separator after closed block should have separator scope, not fenced_code
        val r4 = mdGrammar.tokenizeLine("---", r3.ruleStack)
        assertTrue(
            "Separator after closed block should have separator scope",
            r4.tokens.any { it.scopes.contains("meta.separator.markdown") }
        )
        assertFalse(
            "Separator after closed block should NOT have fenced_code scope",
            r4.tokens.any { it.scopes.contains("markup.fenced_code.block.markdown") }
        )
    }

    // --- BeginWhileRule condition checking (raw_block) ---

    @Test
    fun `Markdown raw block BeginWhileRule stays open while indented`() {
        val mdGrammar = loadGrammar("grammars/markdown.tmLanguage.json")

        // 4-space indentation triggers raw_block (a BeginWhileRule)
        val r1 = mdGrammar.tokenizeLine("    code")
        assertTrue(
            "Indented line should have raw block scope",
            r1.tokens.any { it.scopes.contains("markup.raw.block.markdown") }
        )

        // While condition matches: still indented
        val r2 = mdGrammar.tokenizeLine("    more code", r1.ruleStack)
        assertTrue(
            "Continued indented line should still have raw block scope",
            r2.tokens.any { it.scopes.contains("markup.raw.block.markdown") }
        )
    }

    @Test
    fun `Markdown raw block BeginWhileRule exits on unindented line`() {
        val mdGrammar = loadGrammar("grammars/markdown.tmLanguage.json")

        // Open raw_block
        val r1 = mdGrammar.tokenizeLine("    code")
        assertTrue(
            r1.tokens.any { it.scopes.contains("markup.raw.block.markdown") }
        )

        val r2 = mdGrammar.tokenizeLine("    more", r1.ruleStack)
        assertTrue(
            r2.tokens.any { it.scopes.contains("markup.raw.block.markdown") }
        )

        // Empty line: while condition fails, raw_block pops
        val r3 = mdGrammar.tokenizeLine("", r2.ruleStack)
        assertFalse(
            "Unindented line should NOT have raw block scope",
            r3.tokens.any { it.scopes.contains("markup.raw.block.markdown") }
        )
    }
}
