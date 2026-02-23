package dev.textmate.theme

enum class FontStyle { ITALIC, BOLD, UNDERLINE, STRIKETHROUGH }

data class ResolvedStyle(
    val foreground: Long,
    val background: Long,
    val fontStyle: Set<FontStyle>
)

internal data class ParsedThemeRule(
    val scope: String,
    val parentScopes: List<String>?,
    val index: Int,
    val fontStyle: Set<FontStyle>?,
    val foreground: Long?,
    val background: Long?
)

/**
 * A compiled theme that resolves TextMate scope stacks to visual styles.
 */
class Theme internal constructor(
    val name: String,
    val defaultStyle: ResolvedStyle,
    private val rules: List<ParsedThemeRule>
) {
    /**
     * Resolves the style for a scope stack (outermost to innermost).
     *
     * Checks each scope in the stack against theme rules, not just the leaf.
     * Iterates outermost to innermost so inner scope matches override outer
     * ones via last-writer-wins — equivalent to vscode-textmate's incremental
     * per-scope-push resolution.
     *
     * Returns [defaultStyle] if no rules match or [scopes] is empty.
     */
    fun match(scopes: List<String>): ResolvedStyle {
        if (scopes.isEmpty()) return defaultStyle

        var foreground: Long? = null
        var background: Long? = null
        var fontStyle: Set<FontStyle>? = null

        for (i in scopes.indices) {
            val scope = scopes[i]
            val parents = scopes.subList(0, i)

            for (rule in rules) {
                if (!matchesScope(scope, rule.scope)) continue
                if (rule.parentScopes != null && !matchesParentScopes(parents, rule.parentScopes)) continue

                if (rule.foreground != null) foreground = rule.foreground
                if (rule.background != null) background = rule.background
                if (rule.fontStyle != null) fontStyle = rule.fontStyle
            }
        }

        return ResolvedStyle(
            foreground = foreground ?: defaultStyle.foreground,
            background = background ?: defaultStyle.background,
            fontStyle = fontStyle ?: defaultStyle.fontStyle
        )
    }
}

/**
 * Returns true if [scopeName] matches [pattern]: either exact match
 * or [scopeName] starts with [pattern] followed by a dot.
 */
internal fun matchesScope(scopeName: String, pattern: String): Boolean {
    if (scopeName == pattern) return true
    return scopeName.startsWith(pattern) && scopeName.length > pattern.length && scopeName[pattern.length] == '.'
}

/**
 * Returns true if all [parentPatterns] (innermost first) can be found
 * in [parentStack] scanning from innermost outward.
 */
internal fun matchesParentScopes(parentStack: List<String>, parentPatterns: List<String>): Boolean {
    var stackIndex = parentStack.lastIndex
    for (pattern in parentPatterns) {
        var found = false
        while (stackIndex >= 0) {
            if (matchesScope(parentStack[stackIndex], pattern)) {
                found = true
                stackIndex--
                break
            }
            stackIndex--
        }
        if (!found) return false
    }
    return true
}

internal fun scopeDepth(scope: String): Int {
    if (scope.isEmpty()) return 0
    var count = 1
    for (ch in scope) {
        if (ch == '.') count++
    }
    return count
}

internal fun compareRules(a: ParsedThemeRule, b: ParsedThemeRule): Int =
    compareValuesBy(a, b, { scopeDepth(it.scope) }, { it.parentScopes?.size ?: 0 }, { it.index })
