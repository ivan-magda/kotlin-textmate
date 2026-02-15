package dev.textmate.grammar

/**
 * Accumulates tokens during tokenization (non-binary path).
 * Port of the non-binary path of `LineTokens` from vscode-textmate `grammar.ts`.
 */
internal class LineTokens {

    private val _tokens = mutableListOf<Token>()
    private var _lastTokenEndIndex = 0

    fun produce(stack: StateStackImpl, endIndex: Int) {
        produceFromScopes(stack.contentNameScopesList, endIndex)
    }

    fun produceFromScopes(scopesList: AttributedScopeStack?, endIndex: Int) {
        if (_lastTokenEndIndex >= endIndex) {
            return
        }

        val scopes = scopesList?.getScopeNames() ?: emptyList()

        _tokens.add(Token(
            startIndex = _lastTokenEndIndex,
            endIndex = endIndex,
            scopes = scopes
        ))

        _lastTokenEndIndex = endIndex
    }

    fun getResult(stack: StateStackImpl, lineLength: Int): List<Token> {
        // Pop produced token for newline
        if (_tokens.isNotEmpty() && _tokens.last().startIndex == lineLength - 1) {
            _tokens.removeAt(_tokens.lastIndex)
        }

        if (_tokens.isEmpty()) {
            _lastTokenEndIndex = -1
            produce(stack, lineLength)
            // Fix start index to 0
            val last = _tokens.last()
            _tokens[_tokens.lastIndex] = last.copy(startIndex = 0)
        }

        return _tokens.toList()
    }
}
