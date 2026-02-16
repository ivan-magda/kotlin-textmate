package dev.textmate.theme

/**
 * Top-level VS Code color theme, as loaded from a JSON theme file.
 */
internal data class RawTheme(
    val name: String? = null,
    val include: String? = null,
    val tokenColors: List<RawThemeSetting>? = null,
    val settings: List<RawThemeSetting>? = null,
    val colors: Map<String, String>? = null
)

/**
 * A single token color rule in a theme.
 *
 * [scope] is polymorphic in JSON: it can be a [String], a [List] of strings, or absent (`null`).
 * Gson deserializes a JSON string as [String] and a JSON array as [ArrayList].
 */
internal data class RawThemeSetting(
    val name: String? = null,
    val scope: Any? = null,
    val settings: RawThemeStyle? = null
)

internal data class RawThemeStyle(
    val foreground: String? = null,
    val background: String? = null,
    val fontStyle: String? = null
)
