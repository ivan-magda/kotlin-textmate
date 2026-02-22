package dev.textmate.grammar.raw

import com.google.gson.Gson
import com.google.gson.JsonIOException
import com.google.gson.JsonSyntaxException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader

/**
 * Reads TextMate grammars from `.tmLanguage.json` files.
 *
 * Rule IDs are not assigned during parsing — they are assigned later
 * during rule compilation by [dev.textmate.grammar.rule.RuleFactory].
 */
object GrammarReader {

    private val gson: Gson = Gson()

    /**
     * Parses a TextMate grammar from a JSON string.
     *
     * @throws JsonSyntaxException if the JSON is malformed or does not match the expected structure
     */
    fun readGrammar(json: String): RawGrammar =
        gson.fromJson(json, RawGrammar::class.java)

    /**
     * Parses a TextMate grammar from an [InputStream].
     *
     * The caller is responsible for closing the [inputStream] after this method returns.
     *
     * @throws JsonSyntaxException if the JSON is malformed or does not match the expected structure
     * @throws JsonIOException if reading from the stream fails
     */
    fun readGrammar(inputStream: InputStream): RawGrammar =
        readGrammar(InputStreamReader(inputStream, Charsets.UTF_8))

    /**
     * Parses a TextMate grammar from a [Reader].
     *
     * The caller is responsible for closing the [reader] after this method returns.
     *
     * @throws JsonSyntaxException if the JSON is malformed or does not match the expected structure
     * @throws JsonIOException if reading from the reader fails
     */
    fun readGrammar(reader: Reader): RawGrammar =
        gson.fromJson(reader, RawGrammar::class.java)
}
