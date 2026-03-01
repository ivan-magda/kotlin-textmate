package dev.textmate.grammar.rule

import dev.textmate.grammar.raw.GrammarReader
import dev.textmate.grammar.raw.RawGrammar
import dev.textmate.grammar.raw.RawRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class RuleFactoryTest {

    private fun loadGrammar(resourcePath: String): RawGrammar {
        return javaClass.classLoader.getResourceAsStream(resourcePath)
            ?.use { stream -> GrammarReader.readGrammar(stream) }
            ?: throw IllegalArgumentException("Resource not found: $resourcePath")
    }

    private fun createHelper(): TestRuleFactoryHelper {
        return TestRuleFactoryHelper()
    }

    // ── Integration tests ───────────────────────────────────────────

    @Test
    fun `compile JSON grammar without errors`() {
        val grammar = loadGrammar("grammars/JSON.tmLanguage.json")
        val helper = createHelper()
        val repository = RuleFactory.initGrammarRepository(grammar)

        val rootRuleId = RuleFactory.getCompiledRuleId(repository["\$self"]!!, helper, repository)
        val rootRule = helper.getRule(rootRuleId) as IncludeOnlyRule
        assertNotNull(rootRule)
    }

    @Test
    fun `compile Kotlin grammar without errors`() {
        val grammar = loadGrammar("grammars/kotlin.tmLanguage.json")
        val helper = createHelper()
        val repository = RuleFactory.initGrammarRepository(grammar)

        val rootRuleId = RuleFactory.getCompiledRuleId(repository["\$self"]!!, helper, repository)
        val rootRule = helper.getRule(rootRuleId) as IncludeOnlyRule
        assertNotNull(rootRule)
    }

    @Test
    fun `MatchRule is created from rule with match`() {
        val helper = createHelper()
        val repository = mutableMapOf<String, RawRule>()
        val desc = RawRule(
            match = "\\bclass\\b",
            name = "keyword.other.class"
        )

        val ruleId = RuleFactory.getCompiledRuleId(desc, helper, repository)
        val rule = helper.getRule(ruleId) as MatchRule
        assertNotNull(rule)
    }

    @Test
    fun `BeginEndRule is created from rule with begin and end`() {
        val helper = createHelper()
        val repository = mutableMapOf<String, RawRule>()
        val desc = RawRule(
            begin = "\"",
            end = "\"",
            name = "string.quoted.double"
        )

        val ruleId = RuleFactory.getCompiledRuleId(desc, helper, repository)
        val rule = helper.getRule(ruleId) as BeginEndRule
        assertNotNull(rule)
    }

    @Test
    fun `BeginWhileRule is created from rule with begin and while`() {
        val helper = createHelper()
        val repository = mutableMapOf<String, RawRule>()
        val desc = RawRule(
            begin = "(^|\\G)(>) ?",
            whilePattern = "(^|\\G)\\s*(>) ?",
            name = "markup.quote.markdown"
        )

        val ruleId = RuleFactory.getCompiledRuleId(desc, helper, repository)
        val rule = helper.getRule(ruleId) as BeginWhileRule
        assertNotNull(rule)
    }

    @Test
    fun `BeginWhileRule from markdown blockquote`() {
        val grammar = loadGrammar("grammars/markdown.tmLanguage.json")
        val helper = createHelper()
        val repository = RuleFactory.initGrammarRepository(grammar)

        val blockquoteRule = repository["blockquote"]!!
        val ruleId = RuleFactory.getCompiledRuleId(blockquoteRule, helper, repository)
        val rule = helper.getRule(ruleId) as BeginWhileRule
        assertNotNull(rule)
    }

    @Test
    fun `include resolution resolves hash reference`() {
        val helper = createHelper()
        val stringRule = RawRule(
            match = "\"[^\"]*\"",
            name = "string.quoted"
        )
        val repository = mutableMapOf(
            "string" to stringRule
        )
        val parentRule = RawRule(
            patterns = listOf(RawRule(include = "#string"))
        )

        val ruleId = RuleFactory.getCompiledRuleId(parentRule, helper, repository)
        val rule = helper.getRule(ruleId) as IncludeOnlyRule
        assertTrue(rule.patterns.isNotEmpty())

        val resolvedRule = helper.getRule(rule.patterns[0]) as MatchRule
        assertNotNull(resolvedRule)
    }

    @Test
    fun `compileCaptures creates sparse array`() {
        val helper = createHelper()
        val repository = mutableMapOf<String, RawRule>()
        val captures = mapOf(
            "0" to RawRule(name = "punctuation.definition.begin"),
            "2" to RawRule(name = "entity.name.tag")
        )

        val result = RuleFactory.compileCaptures(captures, helper, repository)
        assertEquals(3, result.size) // indices 0, 1, 2
        assertNotNull(result[0])
        assertNull(result[1])
        assertNotNull(result[2])
        assertEquals("punctuation.definition.begin", result[0]!!.getName(null, null))
    }

    @Test
    fun `compileCaptures returns empty for null input`() {
        val helper = createHelper()
        val repository = mutableMapOf<String, RawRule>()
        val result = RuleFactory.compileCaptures(null, helper, repository)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `back-references are detected in end patterns`() {
        val helper = createHelper()
        val repository = mutableMapOf<String, RawRule>()
        val desc = RawRule(
            begin = "(<)(\\w+)",
            end = "(</)(\\1)(>)",
            name = "tag"
        )

        val ruleId = RuleFactory.getCompiledRuleId(desc, helper, repository)
        val rule = helper.getRule(ruleId) as BeginEndRule
        assertTrue("end pattern should have back-references", rule.endHasBackReferences)
    }

    @Test
    fun `IncludeOnlyRule is created from rule with only patterns`() {
        val helper = createHelper()
        val repository = mutableMapOf<String, RawRule>()
        val desc = RawRule(
            patterns = listOf(
                RawRule(match = "a", name = "a"),
                RawRule(match = "b", name = "b")
            )
        )

        val ruleId = RuleFactory.getCompiledRuleId(desc, helper, repository)
        val rule = helper.getRule(ruleId) as IncludeOnlyRule
        assertEquals(2, rule.patterns.size)
    }

    @Test
    fun `IncludeOnlyRule is created from rule with only include`() {
        val helper = createHelper()
        val stringRule = RawRule(match = "\"[^\"]*\"", name = "string.quoted")
        val repository = mutableMapOf("string" to stringRule)
        val desc = RawRule(include = "#string")

        val ruleId = RuleFactory.getCompiledRuleId(desc, helper, repository)
        val rule = helper.getRule(ruleId) as IncludeOnlyRule
        assertNotNull(rule)
    }

    @Test
    fun `initGrammarRepository creates self and base entries`() {
        val grammar = loadGrammar("grammars/JSON.tmLanguage.json")
        val repo = RuleFactory.initGrammarRepository(grammar)

        assertNotNull(repo["\$self"])
        assertNotNull(repo["\$base"])
        assertSame(repo["\$self"], repo["\$base"])
        // Repository keys from grammar should also be present
        assertNotNull(repo["string"])
        assertNotNull(repo["array"])
    }

    @Test
    fun `repeated compilation returns same rule ID`() {
        val helper = createHelper()
        val repository = mutableMapOf<String, RawRule>()
        val desc = RawRule(match = "test", name = "test")

        val id1 = RuleFactory.getCompiledRuleId(desc, helper, repository)
        val id2 = RuleFactory.getCompiledRuleId(desc, helper, repository)
        assertEquals("same desc should return same ID", id1, id2)
    }

    @Test
    fun `mutually including external grammars compile without stack overflow`() {
        val grammarA = RawGrammar(
            scopeName = "source.a",
            patterns = listOf(RawRule(include = "source.b"))
        )
        val grammarB = RawGrammar(
            scopeName = "source.b",
            patterns = listOf(RawRule(include = "source.a"))
        )
        val helper = TestRuleFactoryHelper(
            externalGrammars = mapOf(
                "source.a" to grammarA,
                "source.b" to grammarB
            )
        )
        val repository = RuleFactory.initGrammarRepository(grammarA)
        val rootRule = repository["\$self"] ?: error("Expected \$self rule in repository")

        try {
            val rootRuleId = RuleFactory.getCompiledRuleId(rootRule, helper, repository)
            assertNotNull(helper.getRule(rootRuleId))
        } catch (error: StackOverflowError) {
            fail("Mutual external includes should compile without StackOverflowError: ${error.message}")
        }
    }
}

/**
 * Test helper implementing [IRuleFactoryHelper] with an internal rule registry.
 */
class TestRuleFactoryHelper(
    private val externalGrammars: Map<String, RawGrammar> = emptyMap()
) : IRuleFactoryHelper {

    private var _lastRuleId = 0
    private val _ruleId2desc = mutableListOf<Rule?>()
    private val _externalRepositories = mutableMapOf<String, MutableMap<String, RawRule>>()
    private val _rawRuleIdCache = java.util.IdentityHashMap<RawRule, RuleId>()

    override fun getRule(ruleId: RuleId): Rule? {
        return _ruleId2desc.getOrNull(ruleId.id)
    }

    override fun <T : Rule> registerRule(factory: (RuleId) -> T): T {
        val id = RuleId(++_lastRuleId)
        // Reserve the slot
        while (_ruleId2desc.size <= id.id) {
            _ruleId2desc.add(null)
        }
        val rule = factory(id)
        _ruleId2desc[id.id] = rule
        return rule
    }

    override fun getExternalGrammar(
        scopeName: String,
        repository: MutableMap<String, RawRule>
    ): RawGrammar? {
        return externalGrammars[scopeName]
    }

    override fun getExternalGrammarRepository(
        scopeName: String,
        repository: MutableMap<String, RawRule>
    ): MutableMap<String, RawRule>? {
        _externalRepositories[scopeName]?.let { return it }
        val externalGrammar = getExternalGrammar(scopeName, repository) ?: return null
        val initialized = RuleFactory.initGrammarRepository(externalGrammar, base = repository["\$base"])
        _externalRepositories[scopeName] = initialized
        return initialized
    }

    override fun getCachedRuleId(desc: RawRule): RuleId? = _rawRuleIdCache[desc]

    override fun cacheRuleId(desc: RawRule, id: RuleId) {
        _rawRuleIdCache[desc] = id
    }
}
