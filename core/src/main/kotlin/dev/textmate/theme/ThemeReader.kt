package dev.textmate.theme

import com.google.gson.Gson
import java.io.InputStream
import java.io.InputStreamReader

/**
 * Reads VS Code color themes from JSON files and constructs [Theme] instances.
 */
public object ThemeReader {

    private val gson: Gson = Gson()

    /**
     * Reads a single theme from an [InputStream].
     * Supports both modern (`tokenColors`) and legacy (`settings`) formats.
     *
     * The caller is responsible for closing the [inputStream] after this method returns.
     */
    public fun readTheme(inputStream: InputStream): Theme {
        return readTheme(listOf(inputStream))
    }

    /**
     * Reads and merges multiple themes (base + overlays) in order.
     * Earlier streams have lower priority; later streams override.
     *
     * The caller is responsible for closing the streams after this method returns.
     */
    public fun readTheme(vararg inputStreams: InputStream): Theme {
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
        var defaultFg = editorForeground ?: DEFAULT_BLACK
        var defaultBg = editorBackground ?: DEFAULT_WHITE
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

private const val HEX_RGB_LEN = 6
private const val HEX_RGBA_LEN = 8
private const val BYTE_MASK = 0xFFL
private const val SHIFT_RED = 24
private const val SHIFT_GREEN = 16
private const val SHIFT_BLUE = 8
private const val OPAQUE_ALPHA = 0xFF000000L
private const val DEFAULT_BLACK = 0xFF000000L
private const val DEFAULT_WHITE = 0xFFFFFFFFL

@Suppress("MagicNumber") // 16 = hex radix
internal fun parseHexColor(hex: String): Long? {
    if (!hex.startsWith("#")) return null
    val digits = hex.substring(1)
    return when (digits.length) {
        HEX_RGB_LEN -> digits.toLongOrNull(16)?.let { rgb -> OPAQUE_ALPHA or rgb }

        HEX_RGBA_LEN -> digits.toLongOrNull(16)?.let { rrggbbaa ->
            val rr = (rrggbbaa shr SHIFT_RED) and BYTE_MASK
            val gg = (rrggbbaa shr SHIFT_GREEN) and BYTE_MASK
            val bb = (rrggbbaa shr SHIFT_BLUE) and BYTE_MASK
            val aa = rrggbbaa and BYTE_MASK
            (aa shl SHIFT_RED) or (rr shl SHIFT_GREEN) or (gg shl SHIFT_BLUE) or bb
        }

        else -> null
    }
}

internal fun parseFontStyle(fontStyle: String?): Set<FontStyle>? =
    when {
        fontStyle == null -> null
        fontStyle.isBlank() -> emptySet()
        else -> buildSet {
            for (token in fontStyle.split(" ")) {
                when (token.lowercase()) {
                    "italic" -> add(FontStyle.ITALIC)
                    "bold" -> add(FontStyle.BOLD)
                    "underline" -> add(FontStyle.UNDERLINE)
                    "strikethrough" -> add(FontStyle.STRIKETHROUGH)
                }
            }
        }
    }
