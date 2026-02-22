package dev.textmate.grammar

import dev.textmate.grammar.raw.GrammarReader
import dev.textmate.grammar.raw.RawGrammar
import dev.textmate.grammar.raw.RawRule
import dev.textmate.registry.Registry
import org.junit.Assert.*
import org.junit.Test

class InjectionGrammarTest {

    private fun loadFixture(name: String): RawGrammar {
        val stream = javaClass.classLoader
            .getResourceAsStream("conformance/first-mate/fixtures/$name")
            ?: error("Fixture not found: $name")
        return GrammarReader.readGrammar(stream)
    }

    // --- External injection grammar via injectionSelector ---

    @Test
    fun `getInjections returns one rule for external injector`() {
        val hostRaw = RawGrammar(
            scopeName = "source.test",
            patterns = listOf(RawRule(match = "//.*", name = "comment.line.test"))
        )
        val injectorRaw = RawGrammar(
            scopeName = "text.injector",
            injectionSelector = "comment",
            patterns = listOf(RawRule(match = "TODO", name = "keyword.todo.test"))
        )
        val registry = Registry(grammarSource = { null })
        registry.addGrammar(hostRaw)
        registry.addGrammar(injectorRaw)

        val grammar = registry.loadGrammar("source.test")!!
        val injections = grammar.getInjections()

        assertEquals(1, injections.size)
        assertEquals("comment", injections[0].debugSelector)
        assertEquals(0, injections[0].priority)
    }

    @Test
    fun `getInjections result is cached — same list on repeated calls`() {
        val hostRaw = RawGrammar(scopeName = "source.test", patterns = emptyList())
        val injectorRaw = RawGrammar(
            scopeName = "text.injector",
            injectionSelector = "comment",
            patterns = listOf(RawRule(match = "x", name = "test.x"))
        )
        val registry = Registry(grammarSource = { null })
        registry.addGrammar(hostRaw)
        registry.addGrammar(injectorRaw)

        val grammar = registry.loadGrammar("source.test")!!
        val first = grammar.getInjections()
        val second = grammar.getInjections()
        assertSame("Same list instance expected (cache must return same reference)", first, second)
    }

    @Test
    fun `getInjections returns empty when no injectors registered`() {
        val hostRaw = RawGrammar(scopeName = "source.test", patterns = emptyList())
        val registry = Registry(grammarSource = { null })
        registry.addGrammar(hostRaw)

        val grammar = registry.loadGrammar("source.test")!!
        assertTrue(grammar.getInjections().isEmpty())
    }

    @Test
    fun `getInjections L-priority injector has priority minus one`() {
        val hostRaw = RawGrammar(scopeName = "source.test", patterns = emptyList())
        val injectorRaw = RawGrammar(
            scopeName = "text.injector",
            injectionSelector = "L:comment",
            patterns = listOf(RawRule(match = "x", name = "test.x"))
        )
        val registry = Registry(grammarSource = { null })
        registry.addGrammar(hostRaw)
        registry.addGrammar(injectorRaw)

        val grammar = registry.loadGrammar("source.test")!!
        assertEquals(-1, grammar.getInjections()[0].priority)
    }

    @Test
    fun `grammar with injectionSelector does not inject into itself`() {
        val selfInjectingGrammar = RawGrammar(
            scopeName = "source.test",
            injectionSelector = "source.test",
            patterns = listOf(RawRule(match = "x", name = "test.x"))
        )
        val registry = Registry(grammarSource = { null })
        registry.addGrammar(selfInjectingGrammar)

        val grammar = registry.loadGrammar("source.test")!!
        assertTrue(
            "Grammar must not inject into itself",
            grammar.getInjections().isEmpty()
        )
    }

    // --- Inline grammar.injections map ---

    @Test
    fun `getInjections compiles inline injections map`() {
        val hostRaw = RawGrammar(
            scopeName = "source.test",
            patterns = listOf(RawRule(match = "//.*", name = "comment.line.test")),
            injections = mapOf(
                "comment" to RawRule(
                    patterns = listOf(RawRule(match = "TODO", name = "keyword.todo.inline"))
                )
            )
        )
        val registry = Registry(grammarSource = { null })
        registry.addGrammar(hostRaw)

        val grammar = registry.loadGrammar("source.test")!!
        val injections = grammar.getInjections()

        assertEquals(1, injections.size)
        assertEquals("comment", injections[0].debugSelector)
    }

    // --- Tokenizer integration: end-to-end injection matching ---

    @Test
    fun `hyperlink injected into C comment produces link scope`() {
        val cGrammar = loadFixture("c.json")
        val hyperlinkGrammar = loadFixture("hyperlink.json")

        val registry = Registry(grammarSource = { null })
        registry.addGrammar(cGrammar)
        registry.addGrammar(hyperlinkGrammar)

        val grammar = registry.loadGrammar("source.c")!!
        val line = "// http://example.com"
        val result = grammar.tokenizeLine(line)

        val urlToken = requireNotNull(result.tokens.firstOrNull { token ->
            val end = token.endIndex.coerceAtMost(line.length)
            line.substring(token.startIndex, end) == "http://example.com"
        }) {
            "Expected a token for 'http://example.com'. " +
            "Tokens: ${result.tokens.map { line.substring(it.startIndex, it.endIndex.coerceAtMost(line.length)) to it.scopes }}"
        }

        assertTrue(
            "Expected markup.underline.link.http.hyperlink scope. Got: ${urlToken.scopes}",
            urlToken.scopes.contains("markup.underline.link.http.hyperlink")
        )
    }

    @Test
    fun `injected scope only fires inside matching scope — not outside comment`() {
        val cGrammar = loadFixture("c.json")
        val hyperlinkGrammar = loadFixture("hyperlink.json")

        val registry = Registry(grammarSource = { null })
        registry.addGrammar(cGrammar)
        registry.addGrammar(hyperlinkGrammar)

        val grammar = registry.loadGrammar("source.c")!!
        val line = "int x; // http://example.com"
        val result = grammar.tokenizeLine(line)

        // "int" token (outside comment) must not have hyperlink scope
        val intToken = result.tokens.first()
        val intText = line.substring(intToken.startIndex, intToken.endIndex.coerceAtMost(line.length))
        assertFalse(
            "'$intText' should not have hyperlink scope. Got: ${intToken.scopes}",
            intToken.scopes.contains("markup.underline.link.http.hyperlink")
        )

        // URL inside comment must have it
        val urlToken = requireNotNull(result.tokens.firstOrNull { token ->
            val end = token.endIndex.coerceAtMost(line.length)
            line.substring(token.startIndex, end).startsWith("http://")
        }) { "Expected a token for the URL inside the comment" }

        assertTrue(
            "Expected hyperlink scope on URL inside comment. Got: ${urlToken.scopes}",
            urlToken.scopes.contains("markup.underline.link.http.hyperlink")
        )
    }

    @Test
    fun `L-priority injection beats normal rule at same position`() {
        val hostRaw = RawGrammar(
            scopeName = "source.test",
            patterns = listOf(RawRule(match = "\\w+", name = "word.test"))
        )
        val injectorRaw = RawGrammar(
            scopeName = "text.injector",
            injectionSelector = "L:source.test",
            patterns = listOf(RawRule(match = "hello", name = "greeting.injected"))
        )
        val registry = Registry(grammarSource = { null })
        registry.addGrammar(hostRaw)
        registry.addGrammar(injectorRaw)

        val grammar = registry.loadGrammar("source.test")!!
        val line = "hello world"
        val result = grammar.tokenizeLine(line)

        val firstToken = result.tokens.first()
        val text = line.substring(firstToken.startIndex, firstToken.endIndex.coerceAtMost(line.length))
        assertEquals("hello", text)
        assertTrue(
            "L: injection should win at same position. Scopes: ${firstToken.scopes}",
            firstToken.scopes.contains("greeting.injected")
        )
    }

    @Test
    fun `inline injection map produces correct scope at tokenizer level`() {
        val hostRaw = RawGrammar(
            scopeName = "source.test",
            // begin/end rule keeps comment scope on the stack for injection matching
            patterns = listOf(RawRule(begin = "//", end = "$", name = "comment.line.test")),
            injections = mapOf(
                "comment" to RawRule(
                    patterns = listOf(RawRule(match = "TODO", name = "keyword.todo.inline"))
                )
            )
        )
        val registry = Registry(grammarSource = { null })
        registry.addGrammar(hostRaw)

        val grammar = registry.loadGrammar("source.test")!!
        val line = "// TODO fix this"
        val result = grammar.tokenizeLine(line)

        val todoToken = requireNotNull(result.tokens.firstOrNull { token ->
            val end = token.endIndex.coerceAtMost(line.length)
            line.substring(token.startIndex, end) == "TODO"
        }) { "Expected a token covering 'TODO'" }

        assertTrue(
            "Expected keyword.todo.inline scope on TODO. Got: ${todoToken.scopes}",
            todoToken.scopes.contains("keyword.todo.inline")
        )
    }

    @Test
    fun `no injectors — fast path returns matchRule result directly`() {
        val hostRaw = RawGrammar(
            scopeName = "source.test",
            patterns = listOf(RawRule(match = "\\w+", name = "word.test"))
        )
        val registry = Registry(grammarSource = { null })
        registry.addGrammar(hostRaw)

        val grammar = registry.loadGrammar("source.test")!!
        assertTrue("No injectors", grammar.getInjections().isEmpty())

        val result = grammar.tokenizeLine("hello")
        assertTrue(result.tokens.first().scopes.contains("word.test"))
    }
}
