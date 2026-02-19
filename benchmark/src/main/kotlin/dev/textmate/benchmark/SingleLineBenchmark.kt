package dev.textmate.benchmark

import dev.textmate.grammar.Grammar
import dev.textmate.grammar.raw.GrammarReader
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

/**
 * Benchmarks tokenization of a single very long line (~421KB minified JSON).
 * Inspired by RedCMD's comment on issue #19.
 *
 * Derive chars/sec from JMH's ms/op: lineLength / (ms_per_op / 1000).
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
open class SingleLineBenchmark {

    @Param("json", "json-textmate")
    lateinit var grammar: String

    private lateinit var parsedGrammar: Grammar
    private lateinit var singleLine: String

    private val grammarPaths = mapOf(
        "json" to "grammars/JSON.tmLanguage.json",
        "json-textmate" to "grammars/json-textmate.tmLanguage.json",
    )

    @Setup(Level.Trial)
    fun setUp() {
        val grammarPath = grammarPaths.getValue(grammar)
        val corpusPath = "benchmark/c.tmLanguage.json.txt"

        val rawGrammar = javaClass.classLoader.getResourceAsStream(grammarPath)
            ?.use { GrammarReader.readGrammar(it) }
            ?: throw IllegalStateException("Grammar not found: $grammarPath")

        parsedGrammar = Grammar(rawGrammar.scopeName, rawGrammar, JoniOnigLib())

        singleLine = javaClass.classLoader.getResourceAsStream(corpusPath)
            ?.bufferedReader()
            ?.readText()
            ?.trimEnd('\n', '\r')
            ?: throw IllegalStateException("Corpus not found: $corpusPath")

        // Warm up pattern compilation
        parsedGrammar.tokenizeLine("")
    }

    @Benchmark
    fun tokenizeSingleLine(): Int {
        val result = parsedGrammar.tokenizeLine(singleLine, null)
        return result.tokens.size
    }
}
