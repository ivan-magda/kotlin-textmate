package dev.textmate.grammar

import org.junit.Assert.assertTrue
import org.junit.Test

class TextMateGrammarTest {
    @Test
    fun `version is a non-blank semantic version`() {
        assertTrue(
            "Unexpected version: ${TextMateGrammar.VERSION}",
            TextMateGrammar.VERSION.matches(Regex("""\d+\.\d+\.\d+(-SNAPSHOT)?""")),
        )
    }
}
