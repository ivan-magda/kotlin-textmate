package dev.textmate.registry

import dev.textmate.grammar.raw.GrammarReader
import dev.textmate.grammar.raw.RawGrammar
import dev.textmate.regex.JoniOnigLib
import org.junit.Assert.*
import org.junit.Test

class RegistryTest {

    private val scopeToResource = mapOf(
        "source.json" to "grammars/JSON.tmLanguage.json",
        "source.js" to "grammars/JavaScript.tmLanguage.json",
        "source.kotlin" to "grammars/kotlin.tmLanguage.json",
        "text.html.markdown" to "grammars/markdown.tmLanguage.json"
    )

    private fun loadRaw(resourcePath: String): RawGrammar {
        val stream = javaClass.classLoader.getResourceAsStream(resourcePath)
            ?: error("Resource not found: $resourcePath")
        return stream.use { GrammarReader.readGrammar(it) }
    }

    private fun createRegistry(): Registry {
        return Registry(
            grammarSource = { scope -> scopeToResource[scope]?.let { loadRaw(it) } },
            onigLib = JoniOnigLib()
        )
    }

    @Test
    fun `load single grammar and tokenize`() {
        val registry = createRegistry()
        val grammar = registry.loadGrammar("source.json")
        assertNotNull(grammar)
        val result = grammar!!.tokenizeLine("true")
        assertEquals(1, result.tokens.size)
        assertTrue(result.tokens[0].scopes.contains("source.json"))
        assertTrue(result.tokens[0].scopes.contains("constant.language.json"))
    }

    @Test
    fun `loadGrammar returns null for unknown scope`() {
        val registry = createRegistry()
        assertNull(registry.loadGrammar("source.unknown"))
    }

    @Test
    fun `same grammar loaded twice returns cached instance`() {
        val registry = createRegistry()
        val first = registry.loadGrammar("source.json")
        val second = registry.loadGrammar("source.json")
        assertNotNull(first)
        assertSame(first, second)
    }

    @Test
    fun `addGrammar pre-loads grammar`() {
        var sourceCalledFor = mutableListOf<String>()
        val registry = Registry(
            grammarSource = { scope ->
                sourceCalledFor.add(scope)
                scopeToResource[scope]?.let { loadRaw(it) }
            },
            onigLib = JoniOnigLib()
        )
        val rawJson = loadRaw("grammars/JSON.tmLanguage.json")
        registry.addGrammar(rawJson)
        val grammar = registry.loadGrammar("source.json")
        assertNotNull(grammar)
        assertFalse("GrammarSource should not be called for pre-loaded grammar",
            sourceCalledFor.contains("source.json"))
    }

    @Test
    fun `cross-grammar include resolves JSON inside Markdown fenced code block`() {
        val registry = createRegistry()
        val grammar = registry.loadGrammar("text.html.markdown")
        assertNotNull(grammar)

        // Tokenize a Markdown fenced JSON block
        val state = grammar!!.tokenizeLine("```json").ruleStack
        val result = grammar.tokenizeLine("{\"key\": true}", state)

        val allScopes = result.tokens.flatMap { it.scopes }
        assertTrue(
            "JSON tokens inside Markdown should include meta.embedded.block.json",
            allScopes.any { it.contains("meta.embedded.block.json") }
        )
        // IncludeOnlyRule flattens sub-patterns, so source.json scope is NOT pushed.
        // Instead, verify JSON grammar patterns actually matched by checking for
        // JSON-specific scopes (these only exist if cross-grammar resolution worked).
        assertTrue(
            "JSON tokens inside Markdown should have JSON-grammar scopes, got: $allScopes",
            allScopes.any { it.contains(".json") && !it.contains("markdown") && !it.contains("meta.embedded") }
        )
    }

    @Test
    fun `cross-grammar tokens have proper JSON structure scopes`() {
        val registry = createRegistry()
        val grammar = registry.loadGrammar("text.html.markdown")!!

        val line = "{\"key\": 42}"
        val state = grammar.tokenizeLine("```json").ruleStack
        val result = grammar.tokenizeLine(line, state)

        // Find the token containing "key" — it should have string scopes from the JSON grammar
        val keyToken = result.tokens.find { token ->
            val text = line.substring(
                token.startIndex.coerceAtMost(line.length),
                token.endIndex.coerceAtMost(line.length)
            )
            text.contains("key")
        }
        assertNotNull("Should find a token containing 'key'", keyToken)
        assertTrue(
            "Key token should have string scope from JSON grammar, got: ${keyToken!!.scopes}",
            keyToken.scopes.any { it.contains("string") }
        )
    }

    @Test
    fun `Grammar without lookup still works`() {
        // Verify backward compatibility: Grammar without grammarLookup
        val rawJson = loadRaw("grammars/JSON.tmLanguage.json")
        val grammar = dev.textmate.grammar.Grammar(
            rawJson.scopeName, rawJson, JoniOnigLib()
        )
        val result = grammar.tokenizeLine("true")
        assertEquals(1, result.tokens.size)
        assertTrue(result.tokens[0].scopes.contains("constant.language.json"))
    }
}
