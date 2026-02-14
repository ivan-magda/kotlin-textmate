package dev.textmate.grammar.raw

import com.google.gson.annotations.SerializedName

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
 * [id] is assigned post-parse by [GrammarReader] — it does not come from JSON.
 * Because [id] is mutable, instances should not be placed in hash-based
 * collections (e.g. [HashSet], [HashMap] keys) before ID assignment is complete.
 */
data class RawRule(
    var id: Int? = null,
    val include: String? = null,
    val name: String? = null,
    val contentName: String? = null,
    val match: String? = null,
    val captures: Map<String, RawCapture>? = null,
    val begin: String? = null,
    val beginCaptures: Map<String, RawCapture>? = null,
    val end: String? = null,
    val endCaptures: Map<String, RawCapture>? = null,
    @SerializedName("while")
    val whilePattern: String? = null,
    val whileCaptures: Map<String, RawCapture>? = null,
    val patterns: List<RawRule>? = null,
    val repository: Map<String, RawRule>? = null,
    val applyEndPatternLast: Int? = null,
    val comment: String? = null
)

/**
 * A capture group definition, keyed by capture index string ("0", "1", …).
 */
data class RawCapture(
    val name: String? = null,
    val patterns: List<RawRule>? = null
)
