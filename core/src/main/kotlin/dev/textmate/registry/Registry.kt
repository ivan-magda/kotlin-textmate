package dev.textmate.registry

import dev.textmate.grammar.Grammar
import dev.textmate.grammar.raw.RawGrammar
import dev.textmate.regex.IOnigLib
import dev.textmate.regex.JoniOnigLib

/**
 * Registry for loading and caching TextMate grammars with cross-grammar include resolution.
 * Not thread-safe.
 */
class Registry(
    private val grammarSource: GrammarSource,
    private val onigLib: IOnigLib = JoniOnigLib()
) {
    private val rawGrammars = mutableMapOf<String, RawGrammar>()
    private val grammars = mutableMapOf<String, Grammar>()

    fun addGrammar(rawGrammar: RawGrammar) {
        rawGrammars[rawGrammar.scopeName] = rawGrammar
    }

    fun loadGrammar(scopeName: String): Grammar? {
        grammars[scopeName]?.let { return it }
        val raw = resolveRawGrammar(scopeName) ?: return null
        val grammar = Grammar(raw.scopeName, raw, onigLib) { lookupScope ->
            resolveRawGrammar(lookupScope)
        }
        grammars[scopeName] = grammar
        return grammar
    }

    private fun resolveRawGrammar(scopeName: String): RawGrammar? {
        rawGrammars[scopeName]?.let { return it }
        val loaded = grammarSource.loadRawGrammar(scopeName) ?: return null
        rawGrammars[loaded.scopeName] = loaded
        return loaded
    }
}
