package dev.textmate.grammar.tokenize

/**
 * Linked list of scope names with token attributes.
 * Port of `AttributedScopeStack` from vscode-textmate `grammar.ts`.
 *
 * Tracks scope names only — theme resolution is done externally via [dev.textmate.theme.Theme.match].
 */
class AttributedScopeStack private constructor(
    val parent: AttributedScopeStack?,
    val scopePath: ScopeStack,
    val tokenAttributes: Int
) {
    val scopeName: String get() = scopePath.scopeName

    companion object {
        fun createRoot(scopeName: String, tokenAttributes: Int): AttributedScopeStack {
            return AttributedScopeStack(null, ScopeStack(null, scopeName), tokenAttributes)
        }

        fun equals(a: AttributedScopeStack?, b: AttributedScopeStack?): Boolean {
            var x = a
            var y = b
            while (true) {
                if (x === y) return true
                if (x == null && y == null) return true
                if (x == null || y == null) return false
                if (x.scopeName != y.scopeName || x.tokenAttributes != y.tokenAttributes) return false
                x = x.parent
                y = y.parent
            }
        }

        internal fun fromExtension(
            namesScopeList: AttributedScopeStack?,
            contentNameScopesList: List<AttributedScopeStackFrame>
        ): AttributedScopeStack? {
            var current = namesScopeList
            var scopeNames = namesScopeList?.scopePath

            for (frame in contentNameScopesList) {
                val pushed = ScopeStack.push(scopeNames, frame.scopeNames)
                requireNotNull(pushed) { "fromExtension: frame scopeNames must not be empty" }
                scopeNames = pushed
                current = AttributedScopeStack(current, pushed, frame.encodedTokenAttributes)
            }

            return current
        }
    }

    /**
     * Push a scope path onto this stack.
     * [scopePath] may be null (returns this), a single scope, or space-separated scopes.
     * [grammar] is unused — kept for API compatibility with vscode-textmate.
     */
    fun pushAttributed(scopePath: String?, grammar: Any?): AttributedScopeStack {
        if (scopePath == null) return this

        if (!scopePath.contains(' ')) {
            return pushSingle(scopePath)
        }

        val scopes = scopePath.split(' ')
        var result = this

        for (scope in scopes) {
            result = result.pushSingle(scope)
        }

        return result
    }

    private fun pushSingle(scopeName: String): AttributedScopeStack {
        val newPath = this.scopePath.push(scopeName)
        // No per-scope theme resolution; carry forward parent attributes
        return AttributedScopeStack(this, newPath, tokenAttributes)
    }

    fun getScopeNames(): List<String> {
        return scopePath.getSegments()
    }

    internal fun getExtensionIfDefined(base: AttributedScopeStack?): List<AttributedScopeStackFrame>? {
        val result = mutableListOf<AttributedScopeStackFrame>()
        var self: AttributedScopeStack? = this

        while (self != null && self !== base) {
            val scopeNames = self.scopePath.getExtensionIfDefined(self.parent?.scopePath)
                ?: return null
            result.add(AttributedScopeStackFrame(self.tokenAttributes, scopeNames))
            self = self.parent
        }

        return if (self === base) result.reversed() else null
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AttributedScopeStack) return false
        return equals(this, other)
    }

    override fun hashCode(): Int {
        var hash = 0
        var item: AttributedScopeStack? = this
        while (item != null) {
            hash = 31 * hash + item.scopeName.hashCode()
            hash = 31 * hash + item.tokenAttributes
            item = item.parent
        }
        return hash
    }

    override fun toString(): String {
        return getScopeNames().joinToString(" ")
    }
}

internal data class AttributedScopeStackFrame(
    val encodedTokenAttributes: Int,
    val scopeNames: List<String>
)
