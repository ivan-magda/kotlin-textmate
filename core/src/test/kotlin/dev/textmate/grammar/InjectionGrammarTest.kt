package dev.textmate.grammar

import dev.textmate.grammar.raw.RawGrammar
import dev.textmate.grammar.raw.RawRule
import dev.textmate.registry.Registry
import org.junit.Assert.*
import org.junit.Test

class InjectionGrammarTest {

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
}
