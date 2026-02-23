package dev.textmate.grammar.tokenize

/**
 * Immutable linked list of scope name strings.
 * Port of `ScopeStack` from vscode-textmate `theme.ts`.
 */
public class ScopeStack(
    public val parent: ScopeStack?,
    public val scopeName: String
) {
    public companion object {
        public fun from(vararg segments: String): ScopeStack? {
            var result: ScopeStack? = null
            for (segment in segments) {
                result = ScopeStack(result, segment)
            }
            return result
        }

        public fun push(path: ScopeStack?, scopeNames: List<String>): ScopeStack? {
            var current = path
            for (name in scopeNames) {
                current = ScopeStack(current, name)
            }
            return current
        }
    }

    public fun push(scopeName: String): ScopeStack {
        return ScopeStack(this, scopeName)
    }

    public fun getSegments(): List<String> {
        val result = mutableListOf<String>()
        var item: ScopeStack? = this

        while (item != null) {
            result.add(item.scopeName)
            item = item.parent
        }
        result.reverse()

        return result
    }

    public fun extends(other: ScopeStack): Boolean {
        if (this === other) return true
        val p = parent ?: return false
        return p.extends(other)
    }

    public fun getExtensionIfDefined(base: ScopeStack?): List<String>? {
        val result = mutableListOf<String>()
        var item: ScopeStack? = this

        while (item != null && item !== base) {
            result.add(item.scopeName)
            item = item.parent
        }

        return if (item === base) {
            result.reversed()
        } else {
            null
        }
    }

    override fun toString(): String {
        return getSegments().joinToString(" ")
    }
}
