package dev.textmate.grammar

import dev.textmate.grammar.tokenize.StateStack

public data class Token(
    public val startIndex: Int,
    public val endIndex: Int,
    public val scopes: List<String>
)

public data class TokenizeLineResult(
    public val tokens: List<Token>,
    public val ruleStack: StateStack
)
