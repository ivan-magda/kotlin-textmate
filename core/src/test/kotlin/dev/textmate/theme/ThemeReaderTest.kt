package dev.textmate.theme

import org.junit.Assert.*
import org.junit.Test

class ThemeReaderTest {

    private fun loadTheme(vararg resourcePaths: String): Theme {
        val streams = resourcePaths.map { path ->
            javaClass.classLoader.getResourceAsStream(path)
                ?: throw IllegalArgumentException("Resource not found: $path")
        }
        return streams.use { ThemeReader.readTheme(*it.toTypedArray()) }
    }

    @Test
    fun `load Dark VS legacy settings format`() {
        val theme = loadTheme("themes/dark_vs.json")
        assertEquals("Dark (Visual Studio)", theme.name)
        // Verify rules loaded by matching a known scope
        assertNotEquals(theme.defaultStyle, theme.match(listOf("source", "comment")))
    }

    @Test
    fun `load Dark+ merged theme`() {
        val theme = loadTheme("themes/dark_vs.json", "themes/dark_plus.json")
        assertEquals("Dark+", theme.name)
    }

    @Test
    fun `load Light VS`() {
        val theme = loadTheme("themes/light_vs.json")
        assertEquals("Light (Visual Studio)", theme.name)
    }

    @Test
    fun `load Light+ merged theme`() {
        val theme = loadTheme("themes/light_vs.json", "themes/light_plus.json")
        assertEquals("Light+", theme.name)
    }

    @Test
    fun `Dark VS default foreground and background`() {
        val theme = loadTheme("themes/dark_vs.json")
        assertEquals(0xFFD4D4D4, theme.defaultStyle.foreground)
        assertEquals(0xFF1E1E1E, theme.defaultStyle.background)
        assertTrue(theme.defaultStyle.fontStyle.isEmpty())
    }

    @Test
    fun `Light VS default foreground and background`() {
        val theme = loadTheme("themes/light_vs.json")
        assertEquals(0xFF000000, theme.defaultStyle.foreground)
        assertEquals(0xFFFFFFFFL, theme.defaultStyle.background)
    }

    @Test
    fun `parseHexColor RRGGBB`() {
        assertEquals(0xFFD4D4D4, parseHexColor("#D4D4D4"))
        assertEquals(0xFF000000, parseHexColor("#000000"))
        assertEquals(0xFFFFFFFF, parseHexColor("#FFFFFF"))
        assertEquals(0xFF569CD6, parseHexColor("#569cd6"))
    }

    @Test
    fun `parseHexColor RRGGBBAA`() {
        // #FF000080 → RR=FF GG=00 BB=00 AA=80 → ARGB=0x80FF0000
        assertEquals(0x80FF0000L, parseHexColor("#FF000080"))
    }

    @Test
    fun `parseHexColor invalid`() {
        assertNull(parseHexColor("D4D4D4"))
        assertNull(parseHexColor("#GGG"))
        assertNull(parseHexColor("#12345"))
        assertNull(parseHexColor(""))
    }

    @Test
    fun `parseFontStyle null returns null`() {
        assertNull(parseFontStyle(null))
    }

    @Test
    fun `parseFontStyle empty resets`() {
        assertEquals(emptySet<FontStyle>(), parseFontStyle(""))
    }

    @Test
    fun `parseFontStyle single values`() {
        assertEquals(setOf(FontStyle.BOLD), parseFontStyle("bold"))
        assertEquals(setOf(FontStyle.ITALIC), parseFontStyle("italic"))
        assertEquals(setOf(FontStyle.UNDERLINE), parseFontStyle("underline"))
        assertEquals(setOf(FontStyle.STRIKETHROUGH), parseFontStyle("strikethrough"))
    }

    @Test
    fun `parseFontStyle combined`() {
        assertEquals(setOf(FontStyle.BOLD, FontStyle.ITALIC), parseFontStyle("bold italic"))
    }
}

/**
 * Helper to use multiple InputStreams and close them all after the block.
 */
private fun <T> List<java.io.InputStream>.use(block: (List<java.io.InputStream>) -> T): T {
    try {
        return block(this)
    } finally {
        for (stream in this) {
            try { stream.close() } catch (_: Exception) {}
        }
    }
}
