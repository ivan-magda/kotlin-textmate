package dev.textmate.grammar.raw

import com.google.gson.TypeAdapter
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter

/**
 * Top-level TextMate grammar, as loaded from a .tmLanguage.json file.
 */
data class RawGrammar(
    val scopeName: String,
    val name: String? = null,
    val patterns: List<RawRule>? = null,
    val repository: Map<String, RawRule>? = null,
    val injections: Map<String, RawRule>? = null,
    val injectionSelector: String? = null,
    val fileTypes: List<String>? = null,
    val firstLineMatch: String? = null
)

/**
 * A single grammar rule. All fields are optional because a rule can be
 * a match rule, a begin/end rule, a begin/while rule, an include-only rule,
 * or just a patterns container.
 *
 * [id] is assigned during rule compilation by [dev.textmate.grammar.rule.RuleFactory],
 * not during parsing. Because [id] is mutable, instances should not be placed
 * in hash-based collections (e.g. [HashSet], [HashMap] keys).
 */
data class RawRule(
    var id: Int? = null,
    val include: String? = null,
    val name: String? = null,
    val contentName: String? = null,
    val match: String? = null,
    val captures: Map<String, RawRule>? = null,
    val begin: String? = null,
    val beginCaptures: Map<String, RawRule>? = null,
    val end: String? = null,
    val endCaptures: Map<String, RawRule>? = null,
    @SerializedName("while")
    val whilePattern: String? = null,
    val whileCaptures: Map<String, RawRule>? = null,
    val patterns: List<RawRule>? = null,
    val repository: Map<String, RawRule>? = null,
    @JsonAdapter(BooleanOrIntAdapter::class)
    val applyEndPatternLast: Int? = null,
    val comment: String? = null
)

/**
 * Deep-clones this grammar, producing independent [RawRule] objects with [RawRule.id] reset
 * to null. Required when the same [RawGrammar] is embedded by multiple [Grammar][dev.textmate.grammar.Grammar]
 * instances: each Grammar mutates [RawRule.id] as a compilation cache, so they must not share
 * rule objects. Matches vscode-textmate's `clone(grammar)` in `initGrammar()`.
 */
internal fun RawGrammar.deepClone(): RawGrammar = copy(
    patterns = patterns.deepCloneRules(),
    repository = repository.deepCloneRuleValues(),
    injections = injections.deepCloneRuleValues()
)

private fun RawRule.deepClone(): RawRule = copy(
    id = null,
    captures = captures.deepCloneRuleValues(),
    beginCaptures = beginCaptures.deepCloneRuleValues(),
    endCaptures = endCaptures.deepCloneRuleValues(),
    whileCaptures = whileCaptures.deepCloneRuleValues(),
    patterns = patterns.deepCloneRules(),
    repository = repository.deepCloneRuleValues()
)

private fun List<RawRule>?.deepCloneRules(): List<RawRule>? =
    this?.map(RawRule::deepClone)

private fun Map<String, RawRule>?.deepCloneRuleValues(): Map<String, RawRule>? =
    this?.mapValues { it.value.deepClone() }

/** Deserializes both JSON booleans (`true`/`false`) and integers (`1`/`0`) to `Int?`. */
internal class BooleanOrIntAdapter : TypeAdapter<Int?>() {
    override fun write(out: JsonWriter, value: Int?) {
        if (value == null) out.nullValue() else out.value(value)
    }

    override fun read(input: JsonReader): Int? {
        return when (input.peek()) {
            JsonToken.NULL -> {
                input.nextNull()
                null
            }

            JsonToken.BOOLEAN -> if (input.nextBoolean()) 1 else 0
            JsonToken.NUMBER -> input.nextInt()
            else -> {
                input.skipValue()
                null
            }
        }
    }
}
