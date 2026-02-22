package dev.textmate.registry

import dev.textmate.grammar.Grammar
import dev.textmate.grammar.raw.GrammarReader
import dev.textmate.grammar.raw.RawGrammar
import dev.textmate.grammar.raw.RawRule
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
        val grammar = requireNotNull(registry.loadGrammar("source.json"))
        val result = grammar.tokenizeLine("true")
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
        val sourceCalledFor = mutableListOf<String>()
        val registry = Registry(
            grammarSource = { scope ->
                sourceCalledFor.add(scope)
                scopeToResource[scope]?.let { loadRaw(it) }
            },
            onigLib = JoniOnigLib()
        )
        val rawJson = loadRaw("grammars/JSON.tmLanguage.json")
        registry.addGrammar(rawJson)
        assertNotNull(registry.loadGrammar("source.json"))
        assertFalse("GrammarSource should not be called for pre-loaded grammar",
            sourceCalledFor.contains("source.json"))
    }

    @Test
    fun `cross-grammar include resolves JSON inside Markdown fenced code block`() {
        val registry = createRegistry()
        val grammar = requireNotNull(registry.loadGrammar("text.html.markdown"))

        // Tokenize a Markdown fenced JSON block
        val state = grammar.tokenizeLine("```json").ruleStack
        val result = grammar.tokenizeLine("{\"key\": true}", state)

        val allScopes = result.tokens.flatMap { it.scopes }
        // meta.embedded.block.json comes from Markdown's BeginWhileRule contentName
        assertTrue(
            "Expected meta.embedded.block.json, got: $allScopes",
            allScopes.contains("meta.embedded.block.json")
        )
        // IncludeOnlyRule flattens sub-patterns, so source.json is NOT pushed as a scope.
        // Verify JSON grammar patterns matched by checking for specific JSON grammar scopes.
        assertTrue(
            "Expected constant.language.json (JSON grammar), got: $allScopes",
            allScopes.contains("constant.language.json")
        )
    }

    @Test
    fun `cross-grammar tokens have proper JSON structure scopes`() {
        val registry = createRegistry()
        val grammar = requireNotNull(registry.loadGrammar("text.html.markdown"))

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
        requireNotNull(keyToken) { "Should find a token containing 'key'" }
        // JSON object keys use the "objectkey" rule with scope "string.json support.type.property-name.json"
        assertTrue(
            "Expected support.type.property-name.json (JSON grammar), got: ${keyToken.scopes}",
            keyToken.scopes.any { "support.type.property-name.json" in it }
        )
    }

    @Test
    fun `two grammars embedding the same external grammar both tokenize correctly`() {
        // Reproducer for RawRule.id sharing bug:
        // Two Grammar instances that both embed source.json via the same grammarLookup.
        // If RawRule objects are shared, Grammar-A's compilation mutates RawRule.id,
        // and Grammar-B sees stale IDs pointing into Grammar-A's rule table.
        val rawJson = loadRaw("grammars/JSON.tmLanguage.json")

        // Two synthetic wrapper grammars that both include source.json
        fun makeWrapper(scope: String) = RawGrammar(
            scopeName = scope,
            patterns = listOf(RawRule(include = "source.json"))
        )

        val onigLib = JoniOnigLib()
        val lookup: (String) -> RawGrammar? = { if (it == "source.json") rawJson else null }

        val grammarA = Grammar("wrapper.a", makeWrapper("wrapper.a"), onigLib, lookup)
        val grammarB = Grammar("wrapper.b", makeWrapper("wrapper.b"), onigLib, lookup)

        // Grammar-A compiles first — this mutates RawRule.id fields on the shared rawJson
        val resultA = grammarA.tokenizeLine("true")
        assertTrue(
            "Grammar-A should tokenize JSON correctly",
            resultA.tokens.any { it.scopes.any { s -> s.contains("constant.language.json") } }
        )

        // Grammar-B compiles second — if RawRule.id is polluted, this breaks
        val resultB = grammarB.tokenizeLine("true")
        assertTrue(
            "Grammar-B should also tokenize JSON correctly (not get stale rule IDs from A), " +
                "got: ${resultB.tokens.map { it.scopes }}",
            resultB.tokens.any { it.scopes.any { s -> s.contains("constant.language.json") } }
        )
    }

    @Test
    fun `tokenizing cyclic external includes does not overflow`() {
        val grammarA = RawGrammar(
            scopeName = "source.cycle.a",
            patterns = listOf(RawRule(include = "source.cycle.b"))
        )
        val grammarB = RawGrammar(
            scopeName = "source.cycle.b",
            patterns = listOf(RawRule(include = "source.cycle.a"))
        )
        val registry = Registry(
            grammarSource = { scope ->
                when (scope) {
                    "source.cycle.a" -> grammarA
                    "source.cycle.b" -> grammarB
                    else -> null
                }
            },
            onigLib = JoniOnigLib()
        )

        val grammar = requireNotNull(registry.loadGrammar("source.cycle.a"))
        try {
            grammar.tokenizeLine("test")
        } catch (e: StackOverflowError) {
            fail("Cyclic external includes should not cause StackOverflowError during tokenization")
        }
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

    @Test
    fun `injection grammar registered before loadGrammar is applied`() {
        val hostRaw = RawGrammar(
            scopeName = "source.host",
            patterns = listOf(RawRule(begin = "//", end = "$", name = "comment.line.host"))
        )
        val injectorRaw = RawGrammar(
            scopeName = "text.injector",
            injectionSelector = "comment",
            patterns = listOf(RawRule(match = "TODO", name = "keyword.todo.injected"))
        )
        val registry = Registry(grammarSource = { null })
        registry.addGrammar(hostRaw)
        registry.addGrammar(injectorRaw)

        val grammar = registry.loadGrammar("source.host")
            ?: error("Grammar 'source.host' not found")
        assertEquals(1, grammar.getInjections().size)
    }
}
