package dev.textmate.registry

import dev.textmate.grammar.raw.RawGrammar

/**
 * Loads raw TextMate grammars on demand by scope name.
 */
fun interface GrammarSource {
    fun loadRawGrammar(scopeName: String): RawGrammar?
}
