package dev.textmate.benchmark

import dev.textmate.grammar.Grammar
import dev.textmate.grammar.raw.GrammarReader
import dev.textmate.grammar.tokenize.StateStack
import dev.textmate.regex.JoniOnigLib
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Level
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Param
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import java.util.concurrent.TimeUnit

@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
open class TokenizerBenchmark {

    @Param("kotlin", "json", "markdown", "javascript")
    lateinit var grammar: String

    private lateinit var parsedGrammar: Grammar
    private lateinit var lines: List<String>

    private val grammarPaths = mapOf(
        "kotlin" to "grammars/kotlin.tmLanguage.json",
        "json" to "grammars/JSON.tmLanguage.json",
        "markdown" to "grammars/markdown.tmLanguage.json",
        "javascript" to "grammars/JavaScript.tmLanguage.json",
    )

    private val corpusPaths = mapOf(
        "kotlin" to "benchmark/large.kt.txt",
        "json" to "benchmark/large.json.txt",
        "markdown" to "benchmark/large.md.txt",
        "javascript" to "benchmark/jquery.js.txt",
    )

    @Setup(Level.Trial)
    fun setUp() {
        val grammarPath = grammarPaths.getValue(grammar)
        val corpusPath = corpusPaths.getValue(grammar)

        val rawGrammar = javaClass.classLoader.getResourceAsStream(grammarPath)
            ?.use { GrammarReader.readGrammar(it) }
            ?: throw IllegalStateException("Grammar not found: $grammarPath")

        // No registry — injected grammars (e.g. source.js.regexp) are unresolved,
        // which inflates JavaScript times due to extra fallback matching attempts.
        parsedGrammar = Grammar(rawGrammar.scopeName, rawGrammar, JoniOnigLib())

        lines = javaClass.classLoader.getResourceAsStream(corpusPath)
            ?.bufferedReader()
            ?.readLines()
            ?: throw IllegalStateException("Corpus not found: $corpusPath")

        // Warm up pattern compilation so it's excluded from the measurement
        parsedGrammar.tokenizeLine("")
    }

    @Benchmark
    fun tokenizeFile(): Int {
        var state: StateStack? = null
        var tokenCount = 0
        for (line in lines) {
            val result = parsedGrammar.tokenizeLine(line, state)
            state = result.ruleStack
            tokenCount += result.tokens.size
        }
        return tokenCount
    }
}
