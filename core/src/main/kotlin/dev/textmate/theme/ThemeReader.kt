package dev.textmate.theme

import com.google.gson.Gson
import java.io.InputStream
import java.io.InputStreamReader

/**
 * Reads VS Code color themes from JSON files and constructs [Theme] instances.
 */
object ThemeReader {

    private val gson: Gson = Gson()

    /**
     * Reads a single theme from an [InputStream].
     * Supports both modern (`tokenColors`) and legacy (`settings`) formats.
     *
     * The caller is responsible for closing the [inputStream] after this method returns.
     */
    fun readTheme(inputStream: InputStream): Theme {
        return readTheme(listOf(inputStream))
    }

    /**
     * Reads and merges multiple themes (base + overlays) in order.
     * Earlier streams have lower priority; later streams override.
     *
     * The caller is responsible for closing the streams after this method returns.
     */
    fun readTheme(vararg inputStreams: InputStream): Theme {
        require(inputStreams.isNotEmpty()) { "At least one theme InputStream is required" }
        return readTheme(inputStreams.toList())
    }

    private fun readTheme(inputStreams: List<InputStream>): Theme {
        var globalIndex = 0
        val allRules = mutableListOf<ParsedThemeRule>()
        var themeName = ""
        var editorForeground: Long? = null
        var editorBackground: Long? = null

        for (stream in inputStreams) {
            val raw = parseRawTheme(stream)
            if (raw.name != null) themeName = raw.name

            raw.colors?.get("editor.foreground")?.let { parseHexColor(it) }?.let { editorForeground = it }
            raw.colors?.get("editor.background")?.let { parseHexColor(it) }?.let { editorBackground = it }

            val settings = raw.tokenColors ?: raw.settings ?: continue

            for (setting in settings) {
                val style = setting.settings ?: continue
                val foreground = style.foreground?.let { parseHexColor(it) }
                val background = style.background?.let { parseHexColor(it) }
                val fontStyle = parseFontStyle(style.fontStyle)

                val scopes = parseScopeField(setting.scope)
                for (scopeStr in scopes) {
                    val parts = scopeStr.trim().split(" ")
                    val leaf = parts.last()
                    val parents = if (parts.size > 1) parts.dropLast(1).reversed() else null

                    allRules.add(
                        ParsedThemeRule(
                            scope = leaf,
                            parentScopes = parents,
                            index = globalIndex++,
                            fontStyle = fontStyle,
                            foreground = foreground,
                            background = background
                        )
                    )
                }
            }
        }

        // Extract default style: prefer empty-scope tokenColors entry, then colors.editor.*
        var defaultFg = editorForeground ?: 0xFF000000L
        var defaultBg = editorBackground ?: 0xFFFFFFFFL
        var defaultFontStyle: Set<FontStyle> = emptySet()

        val contentRules = mutableListOf<ParsedThemeRule>()
        for (rule in allRules) {
            if (rule.scope.isEmpty()) {
                if (rule.foreground != null) defaultFg = rule.foreground
                if (rule.background != null) defaultBg = rule.background
                if (rule.fontStyle != null) defaultFontStyle = rule.fontStyle
            } else {
                contentRules.add(rule)
            }
        }

        val sorted = contentRules.sortedWith(::compareRules)

        return Theme(
            name = themeName,
            defaultStyle = ResolvedStyle(defaultFg, defaultBg, defaultFontStyle),
            rules = sorted
        )
    }

    private fun parseRawTheme(inputStream: InputStream): RawTheme {
        return gson.fromJson(InputStreamReader(inputStream, Charsets.UTF_8), RawTheme::class.java)
            ?: throw IllegalArgumentException("Failed to parse theme: empty or invalid JSON")
    }
}

internal fun parseScopeField(scope: Any?): List<String> {
    return when (scope) {
        null -> listOf("")
        is String -> scope.split(",").map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { listOf("") }
        is List<*> -> scope.filterIsInstance<String>()
        else -> listOf("")
    }
}

internal fun parseHexColor(hex: String): Long? {
    if (!hex.startsWith("#")) return null
    val digits = hex.substring(1)
    return when (digits.length) {
        6 -> {
            val rgb = digits.toLongOrNull(16) ?: return null
            0xFF000000L or rgb
        }

        8 -> {
            val rrggbbaa = digits.toLongOrNull(16) ?: return null
            val rr = (rrggbbaa shr 24) and 0xFF
            val gg = (rrggbbaa shr 16) and 0xFF
            val bb = (rrggbbaa shr 8) and 0xFF
            val aa = rrggbbaa and 0xFF
            (aa shl 24) or (rr shl 16) or (gg shl 8) or bb
        }

        else -> null
    }
}

internal fun parseFontStyle(fontStyle: String?): Set<FontStyle>? {
    if (fontStyle == null) return null
    if (fontStyle.isBlank()) return emptySet()
    val result = mutableSetOf<FontStyle>()
    for (token in fontStyle.split(" ")) {
        when (token.lowercase()) {
            "italic" -> result.add(FontStyle.ITALIC)
            "bold" -> result.add(FontStyle.BOLD)
            "underline" -> result.add(FontStyle.UNDERLINE)
            "strikethrough" -> result.add(FontStyle.STRIKETHROUGH)
        }
    }
    return result
}
