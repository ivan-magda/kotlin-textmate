package dev.textmate.conformance

import dev.textmate.regex.JoniOnigLib
import dev.textmate.registry.Registry
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class FirstMateConformanceTest(
    private val desc: String,
    private val testCase: FirstMateTestCase
) {

    companion object {
        private const val FIXTURES_BASE = "conformance/first-mate/"
        private val KNOWN_DIVERGENCES = emptySet<String>()

        private val allTests by lazy {
            ConformanceTestSupport.loadFirstMateTests("${FIXTURES_BASE}tests.json")
        }

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun loadTestCases(): List<Array<Any>> {
            return allTests
                .filter { canRun(it) }
                .map { arrayOf(it.desc, it) }
        }

        private val scopeToResource: Map<String, String> by lazy {
            val cl = javaClass.classLoader
            allTests
                .flatMap { it.grammars }
                .distinct()
                .mapNotNull { path ->
                    val resource = "$FIXTURES_BASE$path"
                    if (cl.getResource(resource) != null) {
                        val raw = ConformanceTestSupport.loadRawGrammar(resource)
                        raw.scopeName to resource
                    } else null
                }.toMap()
        }

        private fun canRun(test: FirstMateTestCase): Boolean {
            if (test.grammarPath != null) {
                return javaClass.classLoader.getResource("$FIXTURES_BASE${test.grammarPath}") != null
            }
            val scope = test.grammarScopeName ?: return false
            return scope in scopeToResource
        }
    }

    @Test
    fun `tokens match reference`() {
        val grammar = loadGrammarForTest()

        if (desc in KNOWN_DIVERGENCES) {
            try {
                ConformanceTestSupport.assertGrammarTokenization(
                    grammar, testCase.lines, desc
                )
            } catch (_: AssertionError) {
                return // Expected divergence
            }
            fail("$desc passed but is in KNOWN_DIVERGENCES — remove it from the set")
        } else {
            ConformanceTestSupport.assertGrammarTokenization(
                grammar, testCase.lines, desc
            )
        }
    }

    private fun loadGrammarForTest(): dev.textmate.grammar.Grammar {
        val registry = Registry(
            grammarSource = { null },
            onigLib = JoniOnigLib()
        )

        // Pre-load all grammars via addGrammar so they're in the registry's
        // internal map — required for injectionLookup to discover injectors
        val loadedGrammars = testCase.grammars.mapNotNull { path ->
            val resource = "${FIXTURES_BASE}$path"
            if (javaClass.classLoader.getResource(resource) == null) return@mapNotNull null
            val raw = ConformanceTestSupport.loadRawGrammar(resource)
            registry.addGrammar(raw)
            path to raw.scopeName
        }.toMap()

        val targetScope = testCase.grammarScopeName
            ?: loadedGrammars[testCase.grammarPath]
            ?: error("Test '${testCase.desc}': target grammar not found")

        return registry.loadGrammar(targetScope)
            ?: error("Grammar for scope '$targetScope' could not be loaded")
    }
}
