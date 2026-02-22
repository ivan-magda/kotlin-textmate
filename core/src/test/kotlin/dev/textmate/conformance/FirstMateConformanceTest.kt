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
                .filter { it.grammarInjections.isNullOrEmpty() }
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
        var targetScopeFromPath: String? = null
        val rawGrammars = testCase.grammars
            .filter { path ->
                javaClass.classLoader.getResource("${FIXTURES_BASE}$path") != null
            }
            .associate { path ->
                val raw = ConformanceTestSupport.loadRawGrammar("$FIXTURES_BASE$path")
                if (path == testCase.grammarPath) {
                    targetScopeFromPath = raw.scopeName
                }
                raw.scopeName to raw
            }

        val targetScope = when {
            testCase.grammarScopeName != null -> testCase.grammarScopeName
            testCase.grammarPath != null -> targetScopeFromPath
                ?: error("Grammar for path '${testCase.grammarPath}' not found")
            else -> error("Test '${testCase.desc}' has neither grammarPath nor grammarScopeName")
        }

        val registry = Registry(
            grammarSource = { scope -> rawGrammars[scope] },
            onigLib = JoniOnigLib()
        )
        rawGrammars.values.forEach { registry.addGrammar(it) }

        return registry.loadGrammar(targetScope)
            ?: error("Grammar for scope '$targetScope' could not be loaded")
    }
}
