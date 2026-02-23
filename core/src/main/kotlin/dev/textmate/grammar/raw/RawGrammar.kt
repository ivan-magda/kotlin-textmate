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
public data class RawGrammar(
    public val scopeName: String,
    public val name: String? = null,
    public val patterns: List<RawRule>? = null,
    public val repository: Map<String, RawRule>? = null,
    public val injections: Map<String, RawRule>? = null,
    public val injectionSelector: String? = null,
    public val fileTypes: List<String>? = null,
    public val firstLineMatch: String? = null
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
public data class RawRule(
    public var id: Int? = null,
    public val include: String? = null,
    public val name: String? = null,
    public val contentName: String? = null,
    public val match: String? = null,
    public val captures: Map<String, RawRule>? = null,
    public val begin: String? = null,
    public val beginCaptures: Map<String, RawRule>? = null,
    public val end: String? = null,
    public val endCaptures: Map<String, RawRule>? = null,
    @SerializedName("while")
    public val whilePattern: String? = null,
    public val whileCaptures: Map<String, RawRule>? = null,
    public val patterns: List<RawRule>? = null,
    public val repository: Map<String, RawRule>? = null,
    @JsonAdapter(BooleanOrIntAdapter::class)
    public val applyEndPatternLast: Int? = null,
    public val comment: String? = null
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
        if (value == null) {
            out.nullValue()
        } else {
            out.value(value)
        }
    }

    override fun read(input: JsonReader): Int? =
        when (input.peek()) {
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
