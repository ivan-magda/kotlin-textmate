package dev.textmate.theme

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ThemeTest {

    private lateinit var darkPlus: Theme

    @Before
    fun setUp() {
        val darkVsStream = javaClass.classLoader.getResourceAsStream("themes/dark_vs.json")
            ?: throw IllegalArgumentException("dark_vs.json not found")
        val darkPlusStream = javaClass.classLoader.getResourceAsStream("themes/dark_plus.json")
            ?: throw IllegalArgumentException("dark_plus.json not found")
        darkPlus = darkVsStream.use { vs ->
            darkPlusStream.use { plus ->
                ThemeReader.readTheme(vs, plus)
            }
        }
    }

    // --- matchesScope unit tests ---

    @Test
    fun `matchesScope exact match`() {
        assertTrue(matchesScope("keyword", "keyword"))
    }

    @Test
    fun `matchesScope prefix with dot`() {
        assertTrue(matchesScope("keyword.control", "keyword"))
        assertTrue(matchesScope("keyword.control.kotlin", "keyword"))
        assertTrue(matchesScope("keyword.control.kotlin", "keyword.control"))
    }

    @Test
    fun `matchesScope no false prefix without dot`() {
        assertFalse(matchesScope("keywordx", "keyword"))
        assertFalse(matchesScope("keywordcontrol", "keyword"))
    }

    @Test
    fun `matchesScope pattern longer than scope`() {
        assertFalse(matchesScope("keyword", "keyword.control"))
    }

    // --- matchesParentScopes unit tests ---

    @Test
    fun `matchesParentScopes single parent matches`() {
        val stack = listOf("source.kotlin", "meta.class")
        assertTrue(matchesParentScopes(stack, listOf("source.kotlin")))
        assertTrue(matchesParentScopes(stack, listOf("meta.class")))
    }

    @Test
    fun `matchesParentScopes prefix match with dot`() {
        val stack = listOf("source.kotlin", "meta.class.body")
        assertTrue(matchesParentScopes(stack, listOf("meta.class")))
        assertTrue(matchesParentScopes(stack, listOf("meta")))
    }

    @Test
    fun `matchesParentScopes multiple parents innermost first`() {
        val stack = listOf("source.kotlin", "meta.class", "meta.function")
        // patterns are innermost-first: meta.function must be found first, then source
        assertTrue(matchesParentScopes(stack, listOf("meta.function", "source.kotlin")))
    }

    @Test
    fun `matchesParentScopes order matters`() {
        val stack = listOf("source.kotlin", "meta.class")
        // pattern wants meta.class innermost, then source — meta.class is at index 1 (innermost),
        // source.kotlin at index 0 — should match
        assertTrue(matchesParentScopes(stack, listOf("meta.class", "source.kotlin")))
        // reversed: source.kotlin innermost — source is at index 0, meta.class at index 1,
        // but scanning starts from lastIndex (1), so it finds source.kotlin? No — source is at 0.
        // scan from index 1: stack[1]="meta.class" != "source.kotlin", stack[0]="source.kotlin" matches,
        // then next pattern "meta.class" needs stackIndex < 0 → not found
        assertFalse(matchesParentScopes(stack, listOf("source.kotlin", "meta.class")))
    }

    @Test
    fun `matchesParentScopes not found`() {
        val stack = listOf("source.kotlin", "meta.class")
        assertFalse(matchesParentScopes(stack, listOf("meta.function")))
    }

    @Test
    fun `matchesParentScopes partial match fails`() {
        val stack = listOf("source.kotlin")
        // two patterns but only one scope in stack
        assertFalse(matchesParentScopes(stack, listOf("source.kotlin", "meta.class")))
    }

    @Test
    fun `matchesParentScopes empty stack`() {
        assertFalse(matchesParentScopes(emptyList(), listOf("source")))
    }

    @Test
    fun `matchesParentScopes empty patterns`() {
        assertTrue(matchesParentScopes(listOf("source.kotlin"), emptyList()))
    }

    // --- compareRules unit tests ---

    @Test
    fun `compareRules sorts by scope depth ascending`() {
        val shallow = ParsedThemeRule("keyword", null, 0, null, null, null)
        val deep = ParsedThemeRule("keyword.control", null, 1, null, null, null)
        assertTrue(compareRules(shallow, deep) < 0)
        assertTrue(compareRules(deep, shallow) > 0)
    }

    @Test
    fun `compareRules same depth sorts by parent count ascending`() {
        val noParent = ParsedThemeRule("keyword", null, 0, null, null, null)
        val oneParent = ParsedThemeRule("keyword", listOf("source"), 1, null, null, null)
        val twoParents = ParsedThemeRule("keyword", listOf("meta", "source"), 2, null, null, null)
        assertTrue(compareRules(noParent, oneParent) < 0)
        assertTrue(compareRules(oneParent, twoParents) < 0)
    }

    @Test
    fun `compareRules same depth same parents sorts by index ascending`() {
        val first = ParsedThemeRule("keyword", null, 0, null, null, null)
        val second = ParsedThemeRule("keyword", null, 5, null, null, null)
        assertTrue(compareRules(first, second) < 0)
        assertTrue(compareRules(second, first) > 0)
    }

    @Test
    fun `compareRules equal rules return zero`() {
        val rule = ParsedThemeRule("keyword", null, 3, null, null, null)
        assertEquals(0, compareRules(rule, rule))
    }

    @Test
    fun `compareRules depth takes priority over parent count`() {
        // deeper scope with no parents should sort after shallow scope with parents
        val shallowWithParents = ParsedThemeRule("keyword", listOf("source", "meta"), 0, null, null, null)
        val deepNoParents = ParsedThemeRule("keyword.control", null, 1, null, null, null)
        assertTrue(compareRules(shallowWithParents, deepNoParents) < 0)
    }

    // --- Theme.match tests with Dark+ ---

    @Test
    fun `keyword control matches dark_plus rule`() {
        val style = darkPlus.match(listOf("source.kotlin", "keyword.control.kotlin"))
        assertEquals(0xFFC586C0, style.foreground)
    }

    @Test
    fun `string matches dark_vs rule`() {
        val style = darkPlus.match(listOf("source.kotlin", "string.quoted.double.kotlin"))
        assertEquals(0xFFCE9178, style.foreground)
    }

    @Test
    fun `comment matches dark_vs rule`() {
        val style = darkPlus.match(listOf("source.kotlin", "comment.line.double-slash.kotlin"))
        assertEquals(0xFF6A9955, style.foreground)
    }

    @Test
    fun `keyword matches dark_vs rule`() {
        val style = darkPlus.match(listOf("source.kotlin", "keyword.other.kotlin"))
        assertEquals(0xFF569CD6, style.foreground)
    }

    @Test
    fun `keyword operator more specific in dark_vs`() {
        val style = darkPlus.match(listOf("source.kotlin", "keyword.operator.kotlin"))
        assertEquals(0xFFD4D4D4, style.foreground)
    }

    @Test
    fun `support type property-name matches dark_vs`() {
        val style = darkPlus.match(listOf("source.json", "support.type.property-name.json"))
        assertEquals(0xFF9CDCFE, style.foreground)
    }

    @Test
    fun `entity name function in object literal uses function color`() {
        // Production dark_plus: meta.object-literal.key → #9CDCFE, entity.name.function → #DCDCAA
        // Leaf scope (entity.name.function) wins
        val style = darkPlus.match(listOf("source.js", "meta.object-literal.key", "entity.name.function"))
        assertEquals(0xFFDCDCAA, style.foreground)
    }

    @Test
    fun `entity name function without matching parent uses base rule`() {
        // "entity.name.function" without parent → #DCDCAA
        val style = darkPlus.match(listOf("source.js", "entity.name.function"))
        assertEquals(0xFFDCDCAA, style.foreground)
    }

    @Test
    fun `markup heading matches middle scope in stack`() {
        val style = darkPlus.match(
            listOf("text.html.markdown", "markup.heading.1.markdown", "entity.name.section.markdown")
        )
        assertEquals(0xFF569CD6, style.foreground)
        assertTrue(style.fontStyle.contains(FontStyle.BOLD))
    }

    @Test
    fun `unknown scope returns default`() {
        val style = darkPlus.match(listOf("source.kotlin", "some.unknown.scope"))
        assertEquals(darkPlus.defaultStyle.foreground, style.foreground)
    }

    @Test
    fun `empty scopes returns default style`() {
        val style = darkPlus.match(emptyList())
        assertEquals(darkPlus.defaultStyle, style)
    }

    // --- FontStyle tests ---

    @Test
    fun `emphasis resolves to italic`() {
        val style = darkPlus.match(listOf("text.html", "emphasis"))
        assertTrue(style.fontStyle.contains(FontStyle.ITALIC))
    }

    @Test
    fun `strong resolves to bold`() {
        val style = darkPlus.match(listOf("text.html", "strong"))
        assertTrue(style.fontStyle.contains(FontStyle.BOLD))
    }

    @Test
    fun `markup bold has bold fontStyle and foreground`() {
        val style = darkPlus.match(listOf("text.html.markdown", "markup.bold"))
        assertTrue(style.fontStyle.contains(FontStyle.BOLD))
        assertEquals(0xFF569CD6, style.foreground)
    }

    @Test
    fun `parent scope rule matches keyword operator new in source cpp`() {
        // dark_plus has "source.cpp keyword.operator.new" → #C586C0
        // plain "keyword.operator" in dark_vs → #D4D4D4
        // With source.cpp parent, the parent scope rule should win
        val style = darkPlus.match(listOf("source.cpp", "meta.block", "keyword.operator.new"))
        assertEquals(0xFFC586C0, style.foreground)
    }

    @Test
    fun `parent scope rule does not match without required parent`() {
        // dark_vs has standalone "keyword.operator.new" → #569CD6
        // dark_plus has "source.cpp keyword.operator.new" → #C586C0 (requires parent)
        // Without source.cpp parent, the standalone dark_vs rule wins
        val style = darkPlus.match(listOf("source.other", "keyword.operator.new"))
        assertEquals(0xFF569CD6, style.foreground)
    }

    // --- Synthetic Theme.match tests ---

    private val defaultStyle = ResolvedStyle(0xFF000000, 0xFFFFFFFF, emptySet())

    private fun syntheticTheme(vararg rules: ParsedThemeRule): Theme {
        return Theme("test", defaultStyle, rules.toList().sortedWith(::compareRules))
    }

    @Test
    fun `independent attributes compose from different stack depths`() {
        val theme = syntheticTheme(
            ParsedThemeRule("outer", null, 0, setOf(FontStyle.BOLD), null, null),
            ParsedThemeRule("inner", null, 1, null, 0xFF00FF00, null)
        )
        val style = theme.match(listOf("outer", "inner"))
        assertEquals(0xFF00FF00, style.foreground)
        assertTrue(style.fontStyle.contains(FontStyle.BOLD))
        assertEquals(defaultStyle.background, style.background)
    }

    @Test
    fun `single scope list works correctly`() {
        val theme = syntheticTheme(
            ParsedThemeRule("keyword", null, 0, null, 0xFFFF0000, null)
        )
        val style = theme.match(listOf("keyword"))
        assertEquals(0xFFFF0000, style.foreground)
    }

    @Test
    fun `more specific rule overrides less specific at same depth`() {
        val theme = syntheticTheme(
            ParsedThemeRule("keyword", null, 0, null, 0xFFFF0000, null),
            ParsedThemeRule("keyword", listOf("source"), 1, null, 0xFF00FF00, null)
        )
        // With matching parent: parent-scoped rule overrides
        val withParent = theme.match(listOf("source", "keyword"))
        assertEquals(0xFF00FF00, withParent.foreground)
        // Without matching parent: base rule applies
        val noParent = theme.match(listOf("other", "keyword"))
        assertEquals(0xFFFF0000, noParent.foreground)
    }
}
