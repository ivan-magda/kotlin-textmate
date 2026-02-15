package dev.textmate.grammar

data class Token(val startIndex: Int, val endIndex: Int, val scopes: List<String>)

data class TokenizeLineResult(val tokens: List<Token>, val ruleStack: StateStack)
