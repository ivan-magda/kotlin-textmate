package dev.textmate.grammar.tokenize

/**
 * Immutable linked list of scope name strings.
 * Port of `ScopeStack` from vscode-textmate `theme.ts`.
 */
class ScopeStack(
    val parent: ScopeStack?,
    val scopeName: String
) {
    companion object {
        fun from(vararg segments: String): ScopeStack? {
            var result: ScopeStack? = null
            for (segment in segments) {
                result = ScopeStack(result, segment)
            }
            return result
        }

        fun push(path: ScopeStack?, scopeNames: List<String>): ScopeStack? {
            var current = path
            for (name in scopeNames) {
                current = ScopeStack(current, name)
            }
            return current
        }
    }

    fun push(scopeName: String): ScopeStack {
        return ScopeStack(this, scopeName)
    }

    fun getSegments(): List<String> {
        val result = mutableListOf<String>()
        var item: ScopeStack? = this

        while (item != null) {
            result.add(item.scopeName)
            item = item.parent
        }
        result.reverse()

        return result
    }

    fun extends(other: ScopeStack): Boolean {
        if (this === other) return true
        val p = parent ?: return false
        return p.extends(other)
    }

    fun getExtensionIfDefined(base: ScopeStack?): List<String>? {
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
