package dev.textmate.regex

import org.jcodings.specific.UTF8Encoding
import org.joni.Matcher
import org.joni.Option
import org.joni.Regex
import org.joni.Syntax
import org.joni.WarnCallback
import org.joni.exception.SyntaxException
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Empirical test to determine exactly which lookbehind patterns crash Joni.
 *
 * Goal: determine whether simple lookbehinds like (?<=\S) crash,
 * or only backreferences inside lookbehinds like (?<=_\1) crash.
 */
class JoniLookbehindTest {

    private fun compilePattern(pattern: String): Regex {
        val bytes = pattern.toByteArray(Charsets.UTF_8)
        return Regex(
            bytes, 0, bytes.size,
            Option.CAPTURE_GROUP,
            UTF8Encoding.INSTANCE,
            Syntax.DEFAULT,
            WarnCallback.NONE
        )
    }

    private fun compileAndSearch(pattern: String, input: String): Int {
        val regex = compilePattern(pattern)
        val inputBytes = input.toByteArray(Charsets.UTF_8)
        val matcher = regex.matcher(inputBytes)
        return matcher.search(0, inputBytes.size, Option.DEFAULT)
    }

    // ============================================================
    // Group 1: Simple lookbehinds (no backreferences)
    // ============================================================

    @Test
    fun `simple positive lookbehind for non-whitespace`() {
        // (?<=\S) — matches position preceded by non-whitespace
        compilePattern("""(?<=\S)""")
        compileAndSearch("""(?<=\S)x""", "ax")
        println("PASS: (?<=\\S) compiles and searches OK")
    }

    @Test
    fun `simple negative lookbehind for word char`() {
        // (?<!\w) — matches position NOT preceded by a word char
        compilePattern("""(?<!\w)""")
        compileAndSearch("""(?<!\w)x""", " x")
        println("PASS: (?<!\\w) compiles and searches OK")
    }

    @Test
    fun `negative lookbehind for backslash`() {
        compilePattern("""(?<!\\)""")
        println("PASS: (?<!\\\\) compiles OK")
    }

    @Test
    fun `negative lookbehind for backtick`() {
        compilePattern("""(?<!`)""")
        println("PASS: (?<!`) compiles OK")
    }

    @Test
    fun `positive lookbehind for specific chars`() {
        // (?<=\w~~) — from strikethrough, no backreference
        compilePattern("""(?<=\w~~)""")
        println("PASS: (?<=\\w~~) compiles OK")
    }

    @Test
    fun `combined lookbehind and lookahead without backrefs`() {
        // Bold end-like: (?<=\S)(\*\*|__)
        compilePattern("""(?<=\S)(\*\*|__)""")
        compileAndSearch("""(?<=\S)(\*\*|__)""", "hello**")
        println("PASS: (?<=\\S)(\\*\\*|__) compiles and searches OK")
    }

    @Test
    fun `negative lookbehind with character class`() {
        compilePattern("""(?<![~\\])""")
        println("PASS: (?<![~\\\\]) compiles OK")
    }

    // ============================================================
    // Group 2: Backreferences outside lookbehinds
    // ============================================================

    @Test
    fun `backreference outside lookbehind`() {
        compilePattern("""(\w+)\1""")
        compileAndSearch("""(\w+)\1""", "abcabc")
        println("PASS: (\\w+)\\1 compiles and searches OK")
    }

    @Test
    fun `named backreference outside lookbehind`() {
        compilePattern("""(?<open>\*\*)\k<open>""")
        println("PASS: named backref outside lookbehind compiles OK")
    }

    // ============================================================
    // Group 3: Backreferences INSIDE lookbehinds (suspected crash)
    // ============================================================

    @Test
    fun `backreference inside positive lookbehind - simple`() {
        // (\w)(?<=\1) — backreference inside lookbehind crashes Joni
        assertThrows(SyntaxException::class.java) {
            compilePattern("""(\w)(?<=\1)""")
        }
    }

    @Test
    fun `backreference inside negative lookbehind - simple`() {
        assertThrows(SyntaxException::class.java) {
            compilePattern("""(\w)(?<!\1)""")
        }
    }

    @Test
    fun `named backreference inside lookbehind`() {
        assertThrows(SyntaxException::class.java) {
            compilePattern("""(?<open>\w)(?<=\k<open>)""")
        }
    }

    // ============================================================
    // Group 4: Actual patterns from the markdown grammar
    // ============================================================

    @Test
    fun `markdown bold END pattern`() {
        // From grammar: "end": "(?<=\\S)(\\1)"
        // The \1 backreference is inside (...) but after (?<=\S)
        // Note: \1 itself is NOT inside the lookbehind here
        val pattern = """(?<=\S)(\1)"""
        try {
            compilePattern(pattern)
            println("COMPILED OK: bold end")
        } catch (e: Exception) {
            println("CRASH on bold end: ${e::class.simpleName}: ${e.message}")
            throw e
        }
    }

    @Test
    fun `markdown italic END pattern`() {
        // From grammar: "end": "(?<=\\S)(\\1)((?!\\1)|(?=\\1\\1))"
        val pattern = """(?<=\S)(\1)((?!\1)|(?=\1\1))"""
        try {
            compilePattern(pattern)
            println("COMPILED OK: italic end")
        } catch (e: Exception) {
            println("CRASH on italic end: ${e::class.simpleName}: ${e.message}")
            throw e
        }
    }

    @Test
    fun `markdown bold BEGIN core pattern`() {
        // Core of bold begin: (?<!\w)\*\* and (?<!\w)\b__
        // These have lookbehinds but NO backreferences inside them
        val pattern = """(?<open>(\*\*(?=\w)|(?<!\w)\*\*|(?<!\w)\b__))(?=\S)"""
        try {
            compilePattern(pattern)
            println("COMPILED OK: bold begin core")
            compileAndSearch(pattern, "**bold")
            println("SEARCH OK: bold begin core")
        } catch (e: Exception) {
            println("CRASH on bold begin core: ${e::class.simpleName}: ${e.message}")
            throw e
        }
    }

    @Test
    fun `markdown italic BEGIN core pattern`() {
        val pattern = """(?<open>(\*(?=\w)|(?<!\w)\*|(?<!\w)\b_))(?=\S)"""
        try {
            compilePattern(pattern)
            println("COMPILED OK: italic begin core")
            compileAndSearch(pattern, "*italic")
            println("SEARCH OK: italic begin core")
        } catch (e: Exception) {
            println("CRASH on italic begin core: ${e::class.simpleName}: ${e.message}")
            throw e
        }
    }

    @Test
    fun `markdown bold BEGIN full pattern with named backrefs in lookahead`() {
        // The full bold begin pattern uses \k<open> in the LOOKAHEAD body
        // Also uses \g<square> recursion and \k<raw> and \k<title>
        @Suppress("MaxLineLength")
        val pattern = """(?x) (?<open>(\*\*(?=\w)|(?<!\w)\*\*|(?<!\w)\b__))(?=\S)(?=(""" +
            """<[^>]*+>""" +
            """|(?<raw>`+)([^`]|(?!(?<!`)\k<raw>(?!`))`)*+\k<raw>""" +
            """|\\[\\`*_{}\[\]()#.!+\->]?+""" +
            """|\[((?<square>[^\[\]\\]|\\.|\[\g<square>*+\])*+\](([ ]?\[[^\]]*+\])""" +
            """|(\\([ \t]*+<?(.*?)>?[ \t]*+((?<title>['"])(.*?)\k<title>)?\\))))""" +
            """|(?!(?<=\S)\k<open>).""" +
            """)*+(?<=\S)(?=__\b|\*\*)\k<open>)"""
        try {
            compilePattern(pattern)
            println("COMPILED OK: bold full begin")
            compileAndSearch(pattern, "**hello**")
            println("SEARCH OK: bold full begin")
        } catch (e: Exception) {
            println("CRASH on bold full begin: ${e::class.simpleName}: ${e.message}")
            throw e
        }
    }

    @Test
    fun `markdown inline raw pattern`() {
        // (`+)((?:[^`]|(?!(?<!`)\1(?!`))`)*+)(\1)
        // (?<!`) is simple lookbehind; \1 is OUTSIDE the lookbehind (in a lookahead)
        val pattern = """(`+)((?:[^`]|(?!(?<!`)\1(?!`))`)*+)(\1)"""
        try {
            compilePattern(pattern)
            println("COMPILED OK: inline raw")
            compileAndSearch(pattern, "`code`")
            println("SEARCH OK: inline raw")
        } catch (e: Exception) {
            println("CRASH on inline raw: ${e::class.simpleName}: ${e.message}")
            throw e
        }
    }

    @Test
    fun `markdown strikethrough full pattern`() {
        // Contains (?<=_\1) — backreference INSIDE lookbehind crashes Joni
        assertThrows(SyntaxException::class.java) {
            compilePattern("""(?<!\\)(~{2,})(?!(?<=\w~~)_)((?:[^~]|(?!(?<![~\\])\1(?!~))~)*+)(\1)(?!(?<=_\1)\w)""")
        }
    }

    @Test
    fun `markdown strikethrough - isolated backref in lookbehind`() {
        assertThrows(SyntaxException::class.java) {
            compilePattern("""(~{2,})(?<=_\1)""")
        }
    }

    // ============================================================
    // Group 5: Possessive quantifiers and group recursion
    // ============================================================

    @Test
    fun `possessive quantifier basic`() {
        compilePattern("""[^>]*+""")
        println("PASS: possessive quantifier [^>]*+ compiles OK")
    }

    @Test
    fun `possessive quantifier with group`() {
        compilePattern("""(\w)*+""")
        println("PASS: possessive quantifier (\\w)*+ compiles OK")
    }

    @Test
    fun `named group recursion`() {
        val pattern = """(?<square>[^\[\]\\]|\\.|\[\g<square>*+\])*+"""
        try {
            compilePattern(pattern)
            println("COMPILED OK: named group recursion")
        } catch (e: Exception) {
            println("CRASH on named group recursion: ${e::class.simpleName}: ${e.message}")
            throw e
        }
    }

    // ============================================================
    // Group 6: Lookaheads containing backrefs (for comparison)
    // ============================================================

    @Test
    fun `backreference inside lookahead`() {
        // (?!\1) — backreference inside negative lookahead (NOT lookbehind)
        val pattern = """(\w)(?!\1)"""
        try {
            compilePattern(pattern)
            println("COMPILED OK: backref in lookahead")
            compileAndSearch(pattern, "ab")
            println("SEARCH OK: backref in lookahead")
        } catch (e: Exception) {
            println("CRASH on backref in lookahead: ${e::class.simpleName}: ${e.message}")
            throw e
        }
    }

    @Test
    fun `named backref inside lookahead - not lookbehind`() {
        // (?!\k<open>) vs (?<=\k<open>)
        val pattern = """(?<open>\w)(?!\k<open>)"""
        try {
            compilePattern(pattern)
            println("COMPILED OK: named backref in lookahead")
            compileAndSearch(pattern, "ab")
            println("SEARCH OK: named backref in lookahead")
        } catch (e: Exception) {
            println("CRASH on named backref in lookahead: ${e::class.simpleName}: ${e.message}")
            throw e
        }
    }
}
