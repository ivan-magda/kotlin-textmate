package dev.textmate.grammar.raw

import org.junit.Assert.*
import org.junit.Test

class GrammarReaderTest {

    private fun loadGrammar(resourcePath: String): RawGrammar {
        return javaClass.classLoader.getResourceAsStream(resourcePath)
            ?.use { stream -> GrammarReader.readGrammar(stream) }
            ?: throw IllegalArgumentException("Resource not found: $resourcePath")
    }

    private fun loadGrammarAsString(resourcePath: String): RawGrammar {
        val json = javaClass.classLoader.getResourceAsStream(resourcePath)!!
            .use { it.bufferedReader(Charsets.UTF_8).readText() }
        return GrammarReader.readGrammar(json)
    }

    // ── JSON grammar ────────────────────────────────────────────────

    @Test
    fun `JSON grammar has correct scopeName`() {
        val grammar = loadGrammar("grammars/JSON.tmLanguage.json")
        assertEquals("source.json", grammar.scopeName)
    }

    @Test
    fun `JSON grammar has correct name`() {
        val grammar = loadGrammar("grammars/JSON.tmLanguage.json")
        assertEquals("JSON (Javascript Next)", grammar.name)
    }

    @Test
    fun `JSON grammar has patterns`() {
        val grammar = loadGrammar("grammars/JSON.tmLanguage.json")
        assertNotNull(grammar.patterns)
        assertTrue(grammar.patterns!!.isNotEmpty())
        assertEquals("#value", grammar.patterns!![0].include)
    }

    @Test
    fun `JSON grammar has expected repository keys`() {
        val grammar = loadGrammar("grammars/JSON.tmLanguage.json")
        val keys = grammar.repository!!.keys
        val expected = setOf(
            "array", "comments", "constant", "number",
            "object", "string", "objectkey", "stringcontent", "value"
        )
        assertEquals(expected, keys)
    }

    // ── Kotlin grammar ──────────────────────────────────────────────

    @Test
    fun `Kotlin grammar has correct scopeName`() {
        val grammar = loadGrammar("grammars/kotlin.tmLanguage.json")
        assertEquals("source.kotlin", grammar.scopeName)
    }

    @Test
    fun `Kotlin grammar has fileTypes`() {
        val grammar = loadGrammar("grammars/kotlin.tmLanguage.json")
        assertEquals(listOf("kt", "kts"), grammar.fileTypes)
    }

    @Test
    fun `Kotlin grammar has repository with expected keys`() {
        val grammar = loadGrammar("grammars/kotlin.tmLanguage.json")
        val keys = grammar.repository!!.keys
        assertTrue("import" in keys)
        assertTrue("package" in keys)
        assertTrue("code" in keys)
        assertTrue("comments" in keys)
        assertTrue("keywords" in keys)
    }

    // ── Markdown grammar ────────────────────────────────────────────

    @Test
    fun `markdown grammar has correct scopeName`() {
        val grammar = loadGrammar("grammars/markdown.tmLanguage.json")
        assertEquals("text.html.markdown", grammar.scopeName)
    }

    @Test
    fun `markdown blockquote rule has while field deserialized`() {
        val grammar = loadGrammar("grammars/markdown.tmLanguage.json")
        val blockquote = grammar.repository!!["blockquote"]
        assertNotNull("blockquote rule should exist", blockquote)
        assertNotNull("whilePattern should be deserialized", blockquote!!.whilePattern)
        assertTrue(
            blockquote.whilePattern!!.contains("(>) ?")
        )
    }

    @Test
    fun `markdown frontMatter has applyEndPatternLast as integer`() {
        val grammar = loadGrammar("grammars/markdown.tmLanguage.json")
        val frontMatter = grammar.repository!!["frontMatter"]
        assertNotNull("frontMatter rule should exist", frontMatter)
        assertEquals(1, frontMatter!!.applyEndPatternLast)
    }

    // ── Structural tests ────────────────────────────────────────────

    @Test
    fun `captures are deserialized with string keys`() {
        val grammar = loadGrammar("grammars/JSON.tmLanguage.json")
        val array = grammar.repository!!["array"]!!
        val cap0 = array.beginCaptures!!["0"]
        assertNotNull(cap0)
        assertEquals("punctuation.definition.array.begin.json", cap0!!.name)
    }

    @Test
    fun `nested patterns and repository structures are preserved`() {
        val grammar = loadGrammar("grammars/JSON.tmLanguage.json")
        val obj = grammar.repository!!["object"]!!
        assertNotNull(obj.begin)
        assertNotNull(obj.end)
        assertNotNull(obj.patterns)
        assertTrue(obj.patterns!!.size >= 2)
    }

    @Test
    fun `include references are preserved`() {
        val grammar = loadGrammar("grammars/JSON.tmLanguage.json")
        assertEquals("#value", grammar.patterns!![0].include)
    }

    @Test
    fun `begin end captures are deserialized correctly`() {
        val grammar = loadGrammar("grammars/JSON.tmLanguage.json")
        val string = grammar.repository!!["string"]!!
        assertEquals(
            "punctuation.definition.string.begin.json",
            string.beginCaptures!!["0"]!!.name
        )
        assertEquals(
            "punctuation.definition.string.end.json",
            string.endCaptures!!["0"]!!.name
        )
    }

    @Test
    fun `contentName is deserialized`() {
        val grammar = loadGrammar("grammars/kotlin.tmLanguage.json")
        val import = grammar.repository!!["import"]!!
        assertEquals("entity.name.package.kotlin", import.contentName)
    }

    @Test
    fun `readGrammar from string produces same result`() {
        val fromStream = loadGrammar("grammars/JSON.tmLanguage.json")
        val fromString = loadGrammarAsString("grammars/JSON.tmLanguage.json")
        assertEquals(fromStream.scopeName, fromString.scopeName)
        assertEquals(fromStream.patterns!!.size, fromString.patterns!!.size)
        assertEquals(fromStream.repository!!.keys, fromString.repository!!.keys)
    }
}
