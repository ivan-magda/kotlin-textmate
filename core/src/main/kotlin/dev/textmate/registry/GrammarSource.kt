package dev.textmate.registry

import dev.textmate.grammar.raw.RawGrammar

/**
 * Loads raw TextMate grammars on demand by scope name.
 */
public fun interface GrammarSource {
    public fun loadRawGrammar(scopeName: String): RawGrammar?
}
