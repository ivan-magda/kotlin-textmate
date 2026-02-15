package dev.textmate.grammar

import dev.textmate.grammar.raw.GrammarReader
import dev.textmate.grammar.tokenize.StateStack
import dev.textmate.regex.JoniOnigLib
import org.joni.exception.SyntaxException
import org.junit.Assert.*
import org.junit.Test

/**
 * End-to-end integration tests verifying the tokenizer produces correct output
 * on realistic multi-token, multi-line code samples across all 3 test grammars.
 */
class GrammarIntegrationTest {

    private fun loadGrammar(resourcePath: String): Grammar {
        val rawGrammar = javaClass.classLoader.getResourceAsStream(resourcePath)
            ?.use { stream -> GrammarReader.readGrammar(stream) }
            ?: throw IllegalArgumentException("Resource not found: $resourcePath")
        return Grammar(rawGrammar.scopeName, rawGrammar, JoniOnigLib())
    }

    private fun printTokens(line: String, tokens: List<Token>) {
        println("Line: \"$line\"")
        for (token in tokens) {
            val end = token.endIndex.coerceAtMost(line.length)
            val text = line.substring(token.startIndex.coerceAtMost(line.length), end)
            println("  [${token.startIndex}..${token.endIndex}) \"$text\" → ${token.scopes.joinToString(", ")}")
        }
    }

    private fun assertTokensCoverLine(line: String, tokens: List<Token>) {
        assertTrue("Should have at least one token", tokens.isNotEmpty())
        assertEquals("First token should start at 0", 0, tokens.first().startIndex)
        for (i in 1 until tokens.size) {
            assertEquals(
                "Token $i should start where token ${i - 1} ends",
                tokens[i - 1].endIndex,
                tokens[i].startIndex
            )
        }
        assertTrue(
            "Last token should end at or past line length (got ${tokens.last().endIndex}, line length ${line.length})",
            tokens.last().endIndex >= line.length
        )
    }

    private fun tokenizeLines(grammar: Grammar, vararg lines: String): List<Pair<String, TokenizeLineResult>> {
        val results = mutableListOf<Pair<String, TokenizeLineResult>>()
        var state: StateStack? = null
        for (line in lines) {
            val result = grammar.tokenizeLine(line, state)
            results.add(line to result)
            state = result.ruleStack
        }
        return results
    }

    // --- JSON integration tests ---

    @Test
    fun `JSON single-line object with mixed value types`() {
        val grammar = loadGrammar("grammars/JSON.tmLanguage.json")
        val line = """{"name": "test", "count": 42, "active": true}"""
        val result = grammar.tokenizeLine(line)
        val tokens = result.tokens

        printTokens(line, tokens)
        assertTokensCoverLine(line, tokens)

        assertTrue("Should have property-name scope",
            tokens.any { it.scopes.contains("support.type.property-name.json") })
        assertTrue("Should have string scope",
            tokens.any { it.scopes.contains("string.quoted.double.json") })
        assertTrue("Should have numeric scope",
            tokens.any { it.scopes.contains("constant.numeric.json") })
        assertTrue("Should have boolean scope",
            tokens.any { it.scopes.contains("constant.language.json") })
        assertTrue("Should have dictionary begin",
            tokens.any { it.scopes.contains("punctuation.definition.dictionary.begin.json") })
        assertTrue("Should have dictionary end",
            tokens.any { it.scopes.contains("punctuation.definition.dictionary.end.json") })
        assertTrue("Should have key-value separator",
            tokens.any { it.scopes.contains("punctuation.separator.dictionary.key-value.json") })
        assertTrue("Should have pair separator",
            tokens.any { it.scopes.contains("punctuation.separator.dictionary.pair.json") })
    }

    @Test
    fun `JSON multiline nested object`() {
        val grammar = loadGrammar("grammars/JSON.tmLanguage.json")
        val results = tokenizeLines(
            grammar,
            "{",
            """  "person": {""",
            """    "age": 30""",
            "  }",
            "}"
        )

        for ((line, result) in results) {
            printTokens(line, result.tokens)
            assertTokensCoverLine(line, result.tokens)
        }

        // Line 3: nested value has numeric scope and property-name scope
        val line3Tokens = results[2].second.tokens
        assertTrue("Nested value should have numeric scope",
            line3Tokens.any { it.scopes.contains("constant.numeric.json") })
        assertTrue("Nested key should have property-name scope",
            line3Tokens.any { it.scopes.contains("support.type.property-name.json") })

        // Line 5: final } has dictionary end punctuation
        val line5Tokens = results[4].second.tokens
        assertTrue("Final brace should have dictionary end scope",
            line5Tokens.any { it.scopes.contains("punctuation.definition.dictionary.end.json") })
    }

    // --- Kotlin integration tests ---

    @Test
    fun `Kotlin multiline function with string interpolation`() {
        val grammar = loadGrammar("grammars/kotlin.tmLanguage.json")
        val results = tokenizeLines(
            grammar,
            "fun main() {",
            "    val message = \"Hello, \${name}!\"",
            "    println(message)",
            "}"
        )

        for ((line, result) in results) {
            printTokens(line, result.tokens)
            assertTokensCoverLine(line, result.tokens)
        }

        // L1: fun keyword and function name
        val l1 = results[0].second.tokens
        assertTrue("L1 should have fun keyword",
            l1.any { it.scopes.contains("keyword.hard.fun.kotlin") })
        assertTrue("L1 should have function name",
            l1.any { it.scopes.contains("entity.name.function.declaration.kotlin") })

        // L2: val keyword, string, template expression
        val l2 = results[1].second.tokens
        assertTrue("L2 should have val keyword",
            l2.any { it.scopes.contains("keyword.hard.kotlin") })
        assertTrue("L2 should have string scope",
            l2.any { it.scopes.contains("string.quoted.double.kotlin") })
        assertTrue("L2 should have template expression",
            l2.any { it.scopes.contains("meta.template.expression.kotlin") })

        // L3: function call
        val l3 = results[2].second.tokens
        assertTrue("L3 should have function call scope",
            l3.any { it.scopes.contains("entity.name.function.call.kotlin") })

        // L4: all tokens have source.kotlin
        val l4 = results[3].second.tokens
        assertTrue("L4 all tokens should have source.kotlin",
            l4.all { it.scopes.contains("source.kotlin") })
    }

    @Test
    fun `Kotlin simple dollar interpolation`() {
        val grammar = loadGrammar("grammars/kotlin.tmLanguage.json")
        val line = "val greeting = \"Hello, \$name!\""
        val result = grammar.tokenizeLine(line)

        printTokens(line, result.tokens)
        assertTokensCoverLine(line, result.tokens)

        assertTrue("Should have variable string-escape scope for \$name",
            result.tokens.any { it.scopes.contains("variable.string-escape.kotlin") })
    }

    // --- Markdown integration tests ---

    @Test
    fun `Markdown safe patterns across multiple block types`() {
        val grammar = loadGrammar("grammars/markdown.tmLanguage.json")
        val results = tokenizeLines(
            grammar,
            "---",
            "```",
            "val x = 1",
            "```",
            "",
            "    indented code",
            "    more code"
        )

        for ((line, result) in results) {
            printTokens(line, result.tokens)
            assertTokensCoverLine(line, result.tokens)
        }

        // L1: separator
        assertTrue("L1 should have separator scope",
            results[0].second.tokens.any { it.scopes.contains("meta.separator.markdown") })

        // L2: fenced code begin
        val l2 = results[1].second.tokens
        assertTrue("L2 should have fenced_code scope",
            l2.any { it.scopes.contains("markup.fenced_code.block.markdown") })
        assertTrue("L2 should have punctuation scope",
            l2.any { it.scopes.contains("punctuation.definition.markdown") })

        // L3: inside fenced code
        assertTrue("L3 should still have fenced_code scope",
            results[2].second.tokens.any { it.scopes.contains("markup.fenced_code.block.markdown") })

        // L4: closing fence
        assertTrue("L4 should have fenced_code scope",
            results[3].second.tokens.any { it.scopes.contains("markup.fenced_code.block.markdown") })

        // L5: empty line after close — NOT fenced_code
        assertFalse("L5 should NOT have fenced_code scope",
            results[4].second.tokens.any { it.scopes.contains("markup.fenced_code.block.markdown") })

        // L6-L7: raw block
        assertTrue("L6 should have raw block scope",
            results[5].second.tokens.any { it.scopes.contains("markup.raw.block.markdown") })
        assertTrue("L7 should have raw block scope",
            results[6].second.tokens.any { it.scopes.contains("markup.raw.block.markdown") })
    }

    @Test
    fun `Markdown state isolation between blocks`() {
        val grammar = loadGrammar("grammars/markdown.tmLanguage.json")
        val results = tokenizeLines(
            grammar,
            "```",
            "code inside",
            "```",
            "---",
            "    raw block",
            "    more raw"
        )

        for ((line, result) in results) {
            printTokens(line, result.tokens)
            assertTokensCoverLine(line, result.tokens)
        }

        // Fenced block lines
        assertTrue("L1 should have fenced_code scope",
            results[0].second.tokens.any { it.scopes.contains("markup.fenced_code.block.markdown") })
        assertTrue("L2 should have fenced_code scope",
            results[1].second.tokens.any { it.scopes.contains("markup.fenced_code.block.markdown") })
        assertTrue("L3 should have fenced_code scope",
            results[2].second.tokens.any { it.scopes.contains("markup.fenced_code.block.markdown") })

        // Separator: no fenced scope leak
        val l4 = results[3].second.tokens
        assertTrue("L4 should have separator scope",
            l4.any { it.scopes.contains("meta.separator.markdown") })
        assertFalse("L4 should NOT have fenced_code scope",
            l4.any { it.scopes.contains("markup.fenced_code.block.markdown") })

        // Raw block: no fenced scope leak
        val l5 = results[4].second.tokens
        assertTrue("L5 should have raw block scope",
            l5.any { it.scopes.contains("markup.raw.block.markdown") })
        assertFalse("L5 should NOT have fenced_code scope",
            l5.any { it.scopes.contains("markup.fenced_code.block.markdown") })

        val l6 = results[5].second.tokens
        assertTrue("L6 should have raw block scope",
            l6.any { it.scopes.contains("markup.raw.block.markdown") })
        assertFalse("L6 should NOT have fenced_code scope",
            l6.any { it.scopes.contains("markup.fenced_code.block.markdown") })
    }

    @Test
    fun `Markdown Joni lookbehind crash on inline patterns`() {
        // Joni crashes on complex lookbehinds used in Markdown inline patterns:
        //   (?<=\S), (?<!\w), etc. in bold, italic, strikethrough, links, inline code.
        // Headings include #inline in captures, so "# Title" triggers the crash.
        // If this test fails (no exception thrown), Joni has fixed lookbehind support.
        val grammar = loadGrammar("grammars/markdown.tmLanguage.json")

        assertThrows(SyntaxException::class.java) {
            val r1 = grammar.tokenizeLine("# Title")
            grammar.tokenizeLine("Some **bold** and `inline code`", r1.ruleStack)
        }
    }
}
