package dev.textmate.grammar

/** Matcher function: takes a scope stack (outermost-to-innermost) and returns true on match. */
internal typealias ScopeMatcher = (List<String>) -> Boolean

/**
 * Ephemeral holder produced by [InjectionSelectorParser.createMatchers].
 * Not stored after [InjectionRule] is built — plain class, not data class.
 */
internal class MatcherWithPriority(
    val matcher: ScopeMatcher,
    val priority: InjectionPriority
)

/**
 * Port of `matcher.ts` from vscode-textmate.
 * Parses TextMate injection selectors into [MatcherWithPriority] instances.
 *
 * Selector syntax examples:
 *   "comment"                  — matches any scope starting with "comment"
 *   "text, string, comment"    — OR: any of the three
 *   "source.js comment"        — AND: both must appear in order
 *   "source.js -comment"       — AND NOT
 *   "L:comment"                — high priority
 *   "R:comment"                — low priority
 */
internal object InjectionSelectorParser {

    fun createMatchers(selector: String): List<MatcherWithPriority> {
        return try {
            Parser(selector).parse()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private class Parser(selector: String) {
        private val tokens = TOKEN_RE.findAll(selector).map { it.value }.toList()
        private var pos = 0
        private var token: String? = tokens.getOrNull(0)

        private fun advance() {
            pos++
            token = tokens.getOrNull(pos)
        }

        fun parse(): List<MatcherWithPriority> {
            val results = mutableListOf<MatcherWithPriority>()
            while (token != null) {
                var priority = InjectionPriority.DEFAULT
                val t = token
                if (t != null && t.length == 2 && t[1] == ':') {
                    priority = when (t[0]) {
                        'L' -> InjectionPriority.HIGH
                        'R' -> InjectionPriority.LOW
                        else -> InjectionPriority.DEFAULT
                    }
                    advance()
                }
                val matcher = parseConjunction() ?: break
                results.add(MatcherWithPriority(matcher, priority))
                if (token != ",") break
                advance()
            }
            return results
        }

        private fun parseOperand(): ScopeMatcher? {
            val t = token ?: return null
            return when {
                t == "-" -> {
                    advance()
                    val inner = parseOperand() ?: return null
                    { scopes -> !inner(scopes) }
                }
                t == "(" -> {
                    advance()
                    val inner = parseInnerExpression()
                    if (token == ")") advance()
                    inner
                }
                isIdentifier(t) -> {
                    val identifiers = mutableListOf<String>()
                    var current = token
                    while (current != null && isIdentifier(current)) {
                        identifiers.add(current)
                        advance()
                        current = token
                    }
                    { scopes -> nameMatcher(identifiers, scopes) }
                }
                else -> null
            }
        }

        private fun parseConjunction(): ScopeMatcher? {
            val matchers = mutableListOf<ScopeMatcher>()
            var m = parseOperand()
            while (m != null) {
                matchers.add(m)
                m = parseOperand()
            }
            return if (matchers.isEmpty()) null
            else { scopes -> matchers.all { it(scopes) } }
        }

        private fun parseInnerExpression(): ScopeMatcher? {
            val matchers = mutableListOf<ScopeMatcher>()
            var m = parseConjunction()
            while (m != null) {
                matchers.add(m)
                if (token == "|" || token == ",") {
                    do { advance() } while (token == "|" || token == ",")
                } else break
                m = parseConjunction()
            }
            return if (matchers.isEmpty()) null
            else { scopes -> matchers.any { it(scopes) } }
        }

        companion object {
            private val TOKEN_RE = Regex("""([LR]:|[\w\.:][\w\.:\-]*|[,|\-())])""")
            private val IDENTIFIER_RE = Regex("""[\w\.:][\w\.:\-]*""")

            private fun isIdentifier(token: String): Boolean =
                IDENTIFIER_RE.matches(token)

            private fun nameMatcher(identifiers: List<String>, scopes: List<String>): Boolean {
                if (scopes.size < identifiers.size) return false
                var lastIndex = 0
                for (identifier in identifiers) {
                    var found = false
                    for (i in lastIndex until scopes.size) {
                        if (scopesAreMatching(scopes[i], identifier)) {
                            lastIndex = i + 1
                            found = true
                            break
                        }
                    }
                    if (!found) return false
                }
                return true
            }

            private fun scopesAreMatching(scope: String, identifier: String): Boolean {
                if (scope == identifier) return true
                return scope.length > identifier.length
                    && scope.startsWith(identifier)
                    && scope[identifier.length] == '.'
            }
        }
    }
}
