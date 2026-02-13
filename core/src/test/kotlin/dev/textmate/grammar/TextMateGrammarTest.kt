package dev.textmate.grammar

import org.junit.Assert.assertEquals
import org.junit.Test

class TextMateGrammarTest {
    @Test
    fun `version is set`() {
        assertEquals("0.1.0-SNAPSHOT", TextMateGrammar.VERSION)
    }
}
