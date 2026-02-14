package dev.textmate.grammar.raw

import com.google.gson.Gson
import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader

/**
 * Reads TextMate grammars from `.tmLanguage.json` files.
 *
 * After JSON deserialization, every [RawRule] in the grammar tree is
 * assigned a unique positive integer [RawRule.id].
 */
object GrammarReader {

    private val gson: Gson = Gson()

    fun readGrammar(json: String): RawGrammar {
        val grammar = gson.fromJson(json, RawGrammar::class.java)
        assignIds(grammar)
        return grammar
    }

    fun readGrammar(inputStream: InputStream): RawGrammar {
        return readGrammar(InputStreamReader(inputStream, Charsets.UTF_8))
    }

    fun readGrammar(reader: Reader): RawGrammar {
        val grammar = gson.fromJson(reader, RawGrammar::class.java)
        assignIds(grammar)
        return grammar
    }

    private fun assignIds(grammar: RawGrammar) {
        val counter = Counter()
        grammar.patterns?.forEach { assignRuleIds(it, counter) }
        grammar.repository?.values?.forEach { assignRuleIds(it, counter) }
        grammar.injections?.values?.forEach { assignRuleIds(it, counter) }
    }

    private fun assignRuleIds(rule: RawRule, counter: Counter) {
        rule.id = counter.next()
        rule.patterns?.forEach { assignRuleIds(it, counter) }
        rule.repository?.values?.forEach { assignRuleIds(it, counter) }
        assignCaptureIds(rule.captures, counter)
        assignCaptureIds(rule.beginCaptures, counter)
        assignCaptureIds(rule.endCaptures, counter)
        assignCaptureIds(rule.whileCaptures, counter)
    }

    private fun assignCaptureIds(captures: Map<String, RawCapture>?, counter: Counter) {
        captures?.values?.forEach { capture ->
            capture.patterns?.forEach { assignRuleIds(it, counter) }
        }
    }

    private class Counter(private var value: Int = 0) {
        fun next(): Int = ++value
    }
}
