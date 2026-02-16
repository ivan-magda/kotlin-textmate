# Stage 7a: Conformance Testing — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Validate KotlinTextMate's tokenizer against the reference vscode-textmate implementation using exact token matching on 33 first-mate fixtures + golden snapshots for 3 production grammars.

**Architecture:** Two parameterized JUnit 4 test classes (`FirstMateConformanceTest`, `GoldenSnapshotTest`) share a utility object (`ConformanceTestSupport`) for grammar loading, token comparison, and diff formatting. A Node.js script generates golden reference data from canonical vscode-textmate. A sentinel inventory test asserts exact counts of Joni-degraded patterns per grammar.

**Tech Stack:** JUnit 4 `@Parameterized`, Gson (existing dep), Node.js + vscode-textmate + vscode-oniguruma (golden generation only)

**Design doc:** `docs/plans/2026-02-17-conformance-testing-design.md`

---

### Task 1: Copy first-mate fixture files

**Files:**
- Create: `core/src/test/resources/conformance/first-mate/tests.json`
- Create: `core/src/test/resources/conformance/first-mate/fixtures/` (16 JSON grammar files)

**Step 1: Create the conformance resource directory and copy tests.json**

```bash
mkdir -p core/src/test/resources/conformance/first-mate/fixtures
cp vscode-textmate/test-cases/first-mate/tests.json core/src/test/resources/conformance/first-mate/
```

**Step 2: Copy only the self-contained fixture grammars**

These 16 grammars have no external `include` references and are needed by the 33 self-contained tests:

```bash
cd vscode-textmate/test-cases/first-mate/fixtures
for f in hello.json text.json coffee-script.json content-name.json \
         apply-end-pattern-last.json imaginary.json multiline.json \
         c.json infinite-loop.json scss.json nested-captures.json \
         hyperlink.json forever.json json.json thrift.json loops.json; do
    cp "$f" ../../../../core/src/test/resources/conformance/first-mate/fixtures/
done
cd ../../../..
```

**Step 3: Verify files are on the classpath**

```bash
ls core/src/test/resources/conformance/first-mate/fixtures/ | wc -l
# Expected: 16
```

**Step 4: Commit**

```bash
git add core/src/test/resources/conformance/
git commit -m "Add first-mate conformance test fixtures (16 self-contained grammars)"
```

---

### Task 2: ConformanceTestSupport — data models and deserialization

**Files:**
- Create: `core/src/test/kotlin/dev/textmate/conformance/ConformanceTestSupport.kt`

**Step 1: Write a smoke test for tests.json deserialization**

Create `core/src/test/kotlin/dev/textmate/conformance/ConformanceTestSupportTest.kt`:

```kotlin
package dev.textmate.conformance

import org.junit.Assert.*
import org.junit.Test

class ConformanceTestSupportTest {

    @Test
    fun `loads first-mate tests json`() {
        val tests = ConformanceTestSupport.loadFirstMateTests(
            "conformance/first-mate/tests.json"
        )
        assertTrue("Should load at least 60 test cases", tests.size >= 60)

        val test3 = tests.find { it.desc == "TEST #3" }
        assertNotNull("Should find TEST #3", test3)
        assertEquals("fixtures/hello.json", test3!!.grammarPath)
        assertEquals(1, test3.lines.size)
        assertEquals("hello world!", test3.lines[0].line)
        assertTrue(test3.lines[0].tokens.isNotEmpty())

        val firstToken = test3.lines[0].tokens[0]
        assertEquals("hello", firstToken.value)
        assertTrue(firstToken.scopes.contains("source.hello"))
    }
}
```

**Step 2: Run test to verify it fails**

```bash
./gradlew :core:test --tests "dev.textmate.conformance.ConformanceTestSupportTest"
```

Expected: FAIL — class `ConformanceTestSupport` does not exist.

**Step 3: Implement ConformanceTestSupport with models and deserialization**

Create `core/src/test/kotlin/dev/textmate/conformance/ConformanceTestSupport.kt`:

```kotlin
package dev.textmate.conformance

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dev.textmate.grammar.Grammar
import dev.textmate.grammar.Token
import dev.textmate.grammar.raw.GrammarReader
import dev.textmate.grammar.raw.RawGrammar
import dev.textmate.regex.JoniOnigLib
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import java.io.InputStreamReader

// --- Data models ---

data class ExpectedToken(val value: String, val scopes: List<String>)

data class ExpectedLine(val line: String, val tokens: List<ExpectedToken>)

data class FirstMateTestCase(
    val desc: String,
    val grammarPath: String?,
    val grammarScopeName: String?,
    val grammars: List<String>,
    val grammarInjections: List<String>?,
    val lines: List<ExpectedLine>
)

data class GoldenSnapshot(
    val grammar: String,
    val generatedWith: String?,
    val files: List<GoldenFile>
)

data class GoldenFile(
    val source: String,
    val lines: List<ExpectedLine>
)

// --- Support object ---

object ConformanceTestSupport {

    private val gson = Gson()

    // --- Loading ---

    fun loadFirstMateTests(resourcePath: String): List<FirstMateTestCase> {
        val stream = javaClass.classLoader.getResourceAsStream(resourcePath)
            ?: throw IllegalStateException("Resource not found: $resourcePath")
        return stream.use { s ->
            InputStreamReader(s, Charsets.UTF_8).use { reader ->
                gson.fromJson<List<FirstMateTestCase>>(
                    reader,
                    object : TypeToken<List<FirstMateTestCase>>() {}.type
                ) ?: throw IllegalStateException("Failed to parse: $resourcePath")
            }
        }
    }

    fun loadGoldenSnapshot(resourcePath: String): GoldenSnapshot {
        val stream = javaClass.classLoader.getResourceAsStream(resourcePath)
            ?: throw IllegalStateException("Resource not found: $resourcePath")
        return stream.use { s ->
            InputStreamReader(s, Charsets.UTF_8).use { reader ->
                gson.fromJson(reader, GoldenSnapshot::class.java)
                    ?: throw IllegalStateException("Failed to parse: $resourcePath")
            }
        }
    }

    fun loadRawGrammar(resourcePath: String): RawGrammar {
        val stream = javaClass.classLoader.getResourceAsStream(resourcePath)
            ?: throw IllegalStateException("Resource not found: $resourcePath")
        return stream.use { GrammarReader.readGrammar(it) }
    }

    fun createGrammar(rawGrammar: RawGrammar): Grammar {
        return Grammar(rawGrammar.scopeName, rawGrammar, JoniOnigLib())
    }

    // --- Token conversion ---

    fun actualToExpected(line: String, tokens: List<Token>): List<ExpectedToken> {
        return tokens.mapNotNull { token ->
            val start = token.startIndex.coerceAtMost(line.length)
            val end = token.endIndex.coerceAtMost(line.length)
            val value = line.substring(start, end)
            // Filter empty tokens (matches vscode-textmate test runner behavior)
            if (value.isEmpty() && line.isNotEmpty()) null
            else ExpectedToken(value, token.scopes)
        }
    }

    // --- Assertions ---

    fun assertTokensMatch(
        lineText: String,
        lineIndex: Int,
        expected: List<ExpectedToken>,
        actual: List<ExpectedToken>,
        testDesc: String
    ) {
        // Filter empty expected tokens for non-empty lines
        // (matches vscode-textmate's TODO@Alex workaround)
        val filteredExpected = if (lineText.isNotEmpty()) {
            expected.filter { it.value.isNotEmpty() }
        } else {
            expected
        }

        if (filteredExpected == actual) return

        val sb = StringBuilder()
        sb.appendLine("Token mismatch in '$testDesc', line $lineIndex: \"$lineText\"")
        sb.appendLine("Expected ${filteredExpected.size} tokens, got ${actual.size}")
        sb.appendLine()

        val maxTokens = maxOf(filteredExpected.size, actual.size)
        for (i in 0 until maxTokens) {
            val exp = filteredExpected.getOrNull(i)
            val act = actual.getOrNull(i)

            if (exp == act) {
                sb.appendLine("  token[$i] OK: \"${exp?.value}\" ${exp?.scopes}")
                continue
            }

            sb.appendLine("  token[$i] MISMATCH:")
            if (exp != null) {
                sb.appendLine("    expected: \"${exp.value}\" ${exp.scopes}")
            } else {
                sb.appendLine("    expected: (no more tokens)")
            }
            if (act != null) {
                sb.appendLine("    actual:   \"${act.value}\" ${act.scopes}")
            } else {
                sb.appendLine("    actual:   (no more tokens)")
            }

            if (exp != null && act != null && exp.value == act.value) {
                val missing = exp.scopes - act.scopes.toSet()
                val extra = act.scopes - exp.scopes.toSet()
                if (missing.isNotEmpty()) sb.appendLine("    missing scopes: $missing")
                if (extra.isNotEmpty()) sb.appendLine("    extra scopes:   $extra")
            }
        }

        fail(sb.toString())
    }
}
```

**Step 4: Run test to verify it passes**

```bash
./gradlew :core:test --tests "dev.textmate.conformance.ConformanceTestSupportTest"
```

Expected: PASS

**Step 5: Commit**

```bash
git add core/src/test/kotlin/dev/textmate/conformance/
git commit -m "Add ConformanceTestSupport: models, deserialization, assertion helpers"
```

---

### Task 3: FirstMateConformanceTest

**Files:**
- Create: `core/src/test/kotlin/dev/textmate/conformance/FirstMateConformanceTest.kt`

**Step 1: Write the parameterized test class**

```kotlin
package dev.textmate.conformance

import dev.textmate.grammar.tokenize.StateStack
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class FirstMateConformanceTest(
    private val desc: String,
    private val testCase: FirstMateTestCase
) {

    companion object {
        private val SKIP_INJECTIONS = setOf("TEST #47", "TEST #49")
        private const val FIXTURES_BASE = "conformance/first-mate/"

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun loadTestCases(): List<Array<Any>> {
            val allTests = ConformanceTestSupport.loadFirstMateTests(
                "${FIXTURES_BASE}tests.json"
            )
            return allTests
                .filter { it.desc !in SKIP_INJECTIONS }
                .filter { it.grammarInjections.isNullOrEmpty() }
                .filter { canRun(it) }
                .map { arrayOf(it.desc as Any, it as Any) }
        }

        private fun canRun(test: FirstMateTestCase): Boolean {
            // Check if all referenced grammar fixtures are available
            return test.grammars.all { path ->
                javaClass.classLoader.getResource("$FIXTURES_BASE$path") != null
            }
        }
    }

    @Test
    fun `tokens match reference`() {
        val grammar = loadGrammarForTest()
        var state: StateStack? = null

        for ((lineIndex, expectedLine) in testCase.lines.withIndex()) {
            val result = grammar.tokenizeLine(expectedLine.line, state)
            val actual = ConformanceTestSupport.actualToExpected(
                expectedLine.line, result.tokens
            )

            ConformanceTestSupport.assertTokensMatch(
                lineText = expectedLine.line,
                lineIndex = lineIndex,
                expected = expectedLine.tokens,
                actual = actual,
                testDesc = desc
            )

            state = result.ruleStack
        }
    }

    private fun loadGrammarForTest(): dev.textmate.grammar.Grammar {
        // Load all referenced grammars, index by scopeName
        val rawGrammars = testCase.grammars
            .filter { path ->
                javaClass.classLoader.getResource("${FIXTURES_BASE}$path") != null
            }
            .associate { path ->
                val raw = ConformanceTestSupport.loadRawGrammar("$FIXTURES_BASE$path")
                raw.scopeName to raw
            }

        // Resolve target grammar
        val targetScope = when {
            testCase.grammarScopeName != null -> testCase.grammarScopeName
            testCase.grammarPath != null -> {
                val raw = ConformanceTestSupport.loadRawGrammar(
                    "$FIXTURES_BASE${testCase.grammarPath}"
                )
                raw.scopeName
            }
            else -> error("Test '${testCase.desc}' has neither grammarPath nor grammarScopeName")
        }

        val rawGrammar = rawGrammars[targetScope]
            ?: error("Grammar for scope '$targetScope' not found in: ${rawGrammars.keys}")

        return ConformanceTestSupport.createGrammar(rawGrammar)
    }
}
```

**Step 2: Run it**

```bash
./gradlew :core:test --tests "dev.textmate.conformance.FirstMateConformanceTest"
```

Expected: 33 tests discovered. Most should pass. Failures reveal real tokenizer bugs.

**Step 3: Investigate and fix any failures**

For each failing test:
1. Read the failure diff to understand what's wrong
2. Check if it's a tokenizer bug or a test infrastructure issue
3. Fix the tokenizer code if it's a bug; fix the test infrastructure if it's a setup issue
4. Re-run until all 33 pass

Common issues to watch for:
- Empty token filtering edge cases
- Last token endIndex exceeding line length (handled by `coerceAtMost` in `actualToExpected`)
- `grammarPath` vs `grammarScopeName` resolution
- Fixture grammars that look self-contained but have a subtle external include

**Step 4: Commit**

```bash
git add core/src/test/kotlin/dev/textmate/conformance/FirstMateConformanceTest.kt
git commit -m "Add FirstMateConformanceTest: 33 exact-match conformance tests"
```

If tokenizer fixes were needed, commit them separately first:

```bash
git add core/src/main/kotlin/...
git commit -m "Fix <specific tokenizer bug> found by conformance tests"
```

---

### Task 4: Sentinel pattern inventory test

**Files:**
- Modify: `core/src/main/kotlin/dev/textmate/regex/JoniOnigLib.kt`
- Create: `core/src/test/kotlin/dev/textmate/conformance/SentinelPatternTest.kt`

**Step 1: Write the failing test**

Create `core/src/test/kotlin/dev/textmate/conformance/SentinelPatternTest.kt`:

```kotlin
package dev.textmate.conformance

import dev.textmate.regex.JoniOnigLib
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Asserts the exact number of regex patterns that fell back to the
 * never-matching sentinel due to Joni compilation failures.
 * Catches silent degradation from Joni or grammar updates.
 */
class SentinelPatternTest {

    @Test
    fun `JSON grammar has 0 sentinel patterns`() {
        val onigLib = JoniOnigLib()
        loadAndCompileGrammar("grammars/JSON.tmLanguage.json", onigLib)
        assertEquals("JSON should have no sentinel patterns", 0, onigLib.sentinelPatternCount)
    }

    @Test
    fun `Kotlin grammar has 0 sentinel patterns`() {
        val onigLib = JoniOnigLib()
        loadAndCompileGrammar("grammars/kotlin.tmLanguage.json", onigLib)
        assertEquals("Kotlin should have no sentinel patterns", 0, onigLib.sentinelPatternCount)
    }

    @Test
    fun `Markdown grammar has exactly 1 sentinel pattern`() {
        val onigLib = JoniOnigLib()
        loadAndCompileGrammar("grammars/markdown.tmLanguage.json", onigLib)
        assertEquals(
            "Markdown should have exactly 1 sentinel (strikethrough)",
            1, onigLib.sentinelPatternCount
        )
    }

    private fun loadAndCompileGrammar(resourcePath: String, onigLib: JoniOnigLib) {
        val rawGrammar = ConformanceTestSupport.loadRawGrammar(resourcePath)
        val grammar = dev.textmate.grammar.Grammar(
            rawGrammar.scopeName, rawGrammar, onigLib
        )
        // Force compilation by tokenizing an empty line
        grammar.tokenizeLine("")
    }
}
```

**Step 2: Run test to verify it fails**

```bash
./gradlew :core:test --tests "dev.textmate.conformance.SentinelPatternTest"
```

Expected: FAIL — `JoniOnigLib` has no `sentinelPatternCount` property.

**Step 3: Add sentinel counting to JoniOnigLib**

Modify `core/src/main/kotlin/dev/textmate/regex/JoniOnigLib.kt`:

Add a `sentinelPatternCount` property to `JoniOnigLib` and pass a callback to `JoniOnigScanner`:

```kotlin
package dev.textmate.regex

import org.jcodings.specific.UTF8Encoding
import org.joni.Matcher
import org.joni.Option
import org.joni.Regex
import org.joni.Region
import org.joni.Syntax
import org.joni.WarnCallback
import org.joni.exception.JOniException

class JoniOnigLib : IOnigLib {

    var sentinelPatternCount: Int = 0
        private set

    override fun createOnigScanner(patterns: List<String>): OnigScanner {
        return JoniOnigScanner(patterns) { sentinelPatternCount++ }
    }

    override fun createOnigString(str: String): OnigString {
        return OnigString(str)
    }
}

internal class JoniOnigScanner(
    patterns: List<String>,
    private val onSentinel: (() -> Unit)? = null
) : OnigScanner {

    private val regexes: List<Regex> = patterns.map { compilePattern(it) }

    override fun findNextMatchSync(string: OnigString, startPosition: Int): MatchResult? {
        // ... (unchanged)
    }

    private fun buildMatchResult(
        patternIndex: Int,
        region: Region,
        string: OnigString
    ): MatchResult {
        // ... (unchanged)
    }

    private fun compilePattern(pattern: String): Regex {
        val patternBytes = pattern.toByteArray(Charsets.UTF_8)
        return try {
            Regex(
                patternBytes,
                0,
                patternBytes.size,
                Option.CAPTURE_GROUP,
                UTF8Encoding.INSTANCE,
                Syntax.DEFAULT,
                WarnCallback.NONE
            )
        } catch (_: JOniException) {
            onSentinel?.invoke()
            NEVER_MATCH_REGEX
        }
    }

    companion object {
        private val NEVER_MATCH_REGEX: Regex by lazy {
            val bytes = "(?!x)x".toByteArray(Charsets.UTF_8)
            Regex(bytes, 0, bytes.size, Option.CAPTURE_GROUP, UTF8Encoding.INSTANCE, Syntax.DEFAULT, WarnCallback.NONE)
        }
    }
}
```

Key changes:
- `JoniOnigLib` gains `var sentinelPatternCount: Int` with `private set`
- `JoniOnigScanner` takes an optional `onSentinel` callback
- `compilePattern` moves from `companion object` to instance method (needs `onSentinel` access)
- `compilePattern` calls `onSentinel?.invoke()` in the catch block
- `NEVER_MATCH_REGEX` stays in companion (no instance state needed)

**Step 4: Run all tests to verify nothing broke**

```bash
./gradlew :core:test
```

Expected: All existing tests pass + sentinel tests pass.

**Step 5: Commit**

```bash
git add core/src/main/kotlin/dev/textmate/regex/JoniOnigLib.kt
git add core/src/test/kotlin/dev/textmate/conformance/SentinelPatternTest.kt
git commit -m "Add sentinel pattern inventory test with Joni degradation counting"
```

---

### Task 5: Golden snapshot tooling (Node.js)

**Files:**
- Create: `tools/generate-golden/package.json`
- Create: `tools/generate-golden/generate.mjs`

**Step 1: Create the Node.js project**

Create `tools/generate-golden/package.json`:

```json
{
  "name": "generate-golden",
  "version": "1.0.0",
  "private": true,
  "type": "module",
  "scripts": {
    "generate": "node generate.mjs"
  },
  "dependencies": {
    "vscode-textmate": "^9.0.0",
    "vscode-oniguruma": "^2.0.1"
  }
}
```

**Step 2: Create the generator script**

Create `tools/generate-golden/generate.mjs`:

```javascript
// Generates golden tokenization snapshots from the canonical vscode-textmate.
// Usage: cd tools/generate-golden && npm install && npm run generate

import { readFileSync, writeFileSync, mkdirSync, readdirSync } from 'fs';
import { createRequire } from 'module';
import { join, basename, relative } from 'path';
import * as vsctm from 'vscode-textmate';
import * as oniguruma from 'vscode-oniguruma';

const require = createRequire(import.meta.url);
const wasmBin = readFileSync(require.resolve('vscode-oniguruma/release/onig.wasm'));
await oniguruma.loadWASM(wasmBin.buffer);

const onigLib = Promise.resolve({
    createOnigScanner: (patterns) => new oniguruma.OnigScanner(patterns),
    createOnigString: (s) => new oniguruma.OnigString(s),
});

const ROOT = join(import.meta.dirname, '..', '..');
const GRAMMARS_DIR = join(ROOT, 'shared-assets', 'grammars');
const CORPUS_DIR = join(import.meta.dirname, 'corpus');
const OUTPUT_DIR = join(ROOT, 'core', 'src', 'test', 'resources', 'conformance', 'golden');

// Grammar config: directory name in corpus/ -> grammar file + scope name
const GRAMMARS = {
    json:     { file: 'JSON.tmLanguage.json',      scope: 'source.json' },
    kotlin:   { file: 'kotlin.tmLanguage.json',     scope: 'source.kotlin' },
    markdown: { file: 'markdown.tmLanguage.json',   scope: 'text.html.markdown' },
};

async function tokenizeFile(grammar, sourceText) {
    const lines = sourceText.split('\n');
    let ruleStack = vsctm.INITIAL;
    const result = [];

    for (const line of lines) {
        const r = grammar.tokenizeLine(line, ruleStack);
        const tokens = r.tokens.map(t => ({
            value: line.substring(t.startIndex, t.endIndex),
            scopes: t.scopes,
        }));
        result.push({ line, tokens });
        ruleStack = r.ruleStack;
    }
    return result;
}

mkdirSync(OUTPUT_DIR, { recursive: true });

for (const [lang, config] of Object.entries(GRAMMARS)) {
    const grammarPath = join(GRAMMARS_DIR, config.file);
    const grammarContent = readFileSync(grammarPath, 'utf8');
    const rawGrammar = vsctm.parseRawGrammar(grammarContent, grammarPath);

    const registry = new vsctm.Registry({
        onigLib,
        loadGrammar: () => null,
    });

    const grammar = await registry.addGrammar(rawGrammar);
    const corpusDir = join(CORPUS_DIR, lang);
    const corpusFiles = readdirSync(corpusDir).filter(f => !f.startsWith('.'));

    const files = [];
    let totalLines = 0;
    let totalTokens = 0;

    for (const corpusFile of corpusFiles) {
        const sourceText = readFileSync(join(corpusDir, corpusFile), 'utf8');
        const lines = await tokenizeFile(grammar, sourceText);
        files.push({ source: `${lang}/${corpusFile}`, lines });
        totalLines += lines.length;
        totalTokens += lines.reduce((sum, l) => sum + l.tokens.length, 0);
    }

    const version = JSON.parse(readFileSync(join(import.meta.dirname, 'node_modules', 'vscode-textmate', 'package.json'), 'utf8')).version;
    const snapshot = {
        grammar: `grammars/${config.file}`,
        generatedWith: `vscode-textmate@${version}`,
        files,
    };

    const outputPath = join(OUTPUT_DIR, `${lang}.snapshot.json`);
    writeFileSync(outputPath, JSON.stringify(snapshot, null, 2) + '\n');
    console.log(`${lang}: ${corpusFiles.length} files, ${totalLines} lines, ${totalTokens} tokens -> ${relative(ROOT, outputPath)}`);
}

console.log('\nDone. Review with: git diff core/src/test/resources/conformance/golden/');
```

**Step 3: Install dependencies**

```bash
cd tools/generate-golden && npm install && cd ../..
```

**Step 4: Commit tooling (without generated output yet)**

```bash
git add tools/generate-golden/package.json tools/generate-golden/generate.mjs
echo "node_modules/" > tools/generate-golden/.gitignore
git add tools/generate-golden/.gitignore
git commit -m "Add golden snapshot generation tooling (Node.js)"
```

---

### Task 6: Create corpus files

**Files:**
- Create: `tools/generate-golden/corpus/json/mixed-values.json`
- Create: `tools/generate-golden/corpus/kotlin/functions.kt`
- Create: `tools/generate-golden/corpus/kotlin/strings.kt`
- Create: `tools/generate-golden/corpus/markdown/blocks.md`
- Create: `tools/generate-golden/corpus/markdown/inline.md`

**Step 1: Create JSON corpus**

Create `tools/generate-golden/corpus/json/mixed-values.json`:

```json
{
  "name": "test",
  "version": 42,
  "active": true,
  "tags": ["alpha", "beta"],
  "metadata": null,
  "nested": {
    "key": "value with \"escapes\" and \\backslash",
    "numbers": [1, -2.5, 3e10],
    "empty": {}
  }
}
```

**Step 2: Create Kotlin corpus**

Create `tools/generate-golden/corpus/kotlin/functions.kt`:

```kotlin
package example

import kotlin.collections.List

fun main(args: Array<String>) {
    val message = "Hello, World!"
    println(message)
}

fun <T : Comparable<T>> sort(list: List<T>): List<T> {
    return list.sorted()
}

class Calculator {
    fun add(a: Int, b: Int): Int = a + b

    companion object {
        const val PI = 3.14159
    }
}

annotation class MyAnnotation

@MyAnnotation
fun annotated() {
    val x = when (true) {
        true -> 1
        false -> 0
    }
}
```

Create `tools/generate-golden/corpus/kotlin/strings.kt`:

```kotlin
package example

fun strings() {
    val simple = "hello"
    val escaped = "line1\nline2\ttab"
    val interpolated = "Hello, $simple!"
    val expression = "result: ${1 + 2}"
    val multiline = """
        first line
        second line
    """.trimIndent()
    val empty = ""
    val unicode = "\u0048\u0065\u006C\u006C\u006F"
}
```

**Step 3: Create Markdown corpus**

Create `tools/generate-golden/corpus/markdown/blocks.md`:

```markdown
# Heading 1

## Heading 2

---

```
code block
more code
```

    indented code
    more indented

> Blockquote text
> second line

- Item 1
- Item 2
  - Nested item

1. First
2. Second
```

Create `tools/generate-golden/corpus/markdown/inline.md`:

```markdown
# Inline formatting

Some **bold** and *italic* text.

A `code span` in a sentence.

A [link](http://example.com) and an ![image](img.png).

Mixed **bold and *italic*** together.
```

Note: No `~~strikethrough~~` in the corpus (Joni backreference-in-lookbehind limitation).

**Step 4: Commit corpus files**

```bash
git add tools/generate-golden/corpus/
git commit -m "Add corpus files for golden snapshot generation"
```

---

### Task 7: Generate golden snapshots

**Step 1: Run the generator**

```bash
cd tools/generate-golden && npm run generate && cd ../..
```

Expected output:

```
json: 1 files, ~12 lines, ~60 tokens -> core/src/test/resources/conformance/golden/json.snapshot.json
kotlin: 2 files, ~40 lines, ~200 tokens -> core/src/test/resources/conformance/golden/kotlin.snapshot.json
markdown: 2 files, ~30 lines, ~100 tokens -> core/src/test/resources/conformance/golden/markdown.snapshot.json
```

**Step 2: Inspect the generated files**

```bash
ls -la core/src/test/resources/conformance/golden/
# Should have 3 .snapshot.json files

# Spot-check: verify the JSON snapshot has reasonable tokens
head -30 core/src/test/resources/conformance/golden/json.snapshot.json
```

**Step 3: Commit golden files**

```bash
git add core/src/test/resources/conformance/golden/
git commit -m "Add golden tokenization snapshots generated from vscode-textmate"
```

---

### Task 8: GoldenSnapshotTest

**Files:**
- Create: `core/src/test/kotlin/dev/textmate/conformance/GoldenSnapshotTest.kt`

**Step 1: Write the parameterized test class**

```kotlin
package dev.textmate.conformance

import dev.textmate.grammar.tokenize.StateStack
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class GoldenSnapshotTest(
    private val label: String,
    private val grammarResource: String,
    private val snapshotResource: String
) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun grammars(): List<Array<Any>> = listOf(
            arrayOf("JSON", "grammars/JSON.tmLanguage.json", "conformance/golden/json.snapshot.json"),
            arrayOf("Kotlin", "grammars/kotlin.tmLanguage.json", "conformance/golden/kotlin.snapshot.json"),
            arrayOf("Markdown", "grammars/markdown.tmLanguage.json", "conformance/golden/markdown.snapshot.json"),
        )
    }

    @Test
    fun `tokens match golden snapshot`() {
        val rawGrammar = ConformanceTestSupport.loadRawGrammar(grammarResource)
        val grammar = ConformanceTestSupport.createGrammar(rawGrammar)
        val snapshot = ConformanceTestSupport.loadGoldenSnapshot(snapshotResource)

        for (file in snapshot.files) {
            var state: StateStack? = null

            for ((lineIndex, expectedLine) in file.lines.withIndex()) {
                val result = grammar.tokenizeLine(expectedLine.line, state)
                val actual = ConformanceTestSupport.actualToExpected(
                    expectedLine.line, result.tokens
                )

                ConformanceTestSupport.assertTokensMatch(
                    lineText = expectedLine.line,
                    lineIndex = lineIndex,
                    expected = expectedLine.tokens,
                    actual = actual,
                    testDesc = "$label/${file.source}"
                )

                state = result.ruleStack
            }
        }
    }
}
```

**Step 2: Run it**

```bash
./gradlew :core:test --tests "dev.textmate.conformance.GoldenSnapshotTest"
```

Expected: 3 tests (JSON, Kotlin, Markdown). Should pass if the tokenizer matches vscode-textmate.

**Step 3: Investigate and fix any failures**

Golden snapshot failures indicate divergences between our tokenizer and vscode-textmate on production grammars. For each failure:
1. Read the diff — which token(s) diverge?
2. Determine root cause: tokenizer bug, scope stacking order, capture issue, or Joni regex difference?
3. Fix if it's a tokenizer bug; document if it's an accepted Joni limitation

**Step 4: Commit**

```bash
git add core/src/test/kotlin/dev/textmate/conformance/GoldenSnapshotTest.kt
git commit -m "Add GoldenSnapshotTest: exact-match conformance for 3 production grammars"
```

---

### Task 9: Run full test suite and final commit

**Step 1: Run all conformance tests**

```bash
./gradlew :core:test --tests "dev.textmate.conformance.*" --info
```

Expected:
- `FirstMateConformanceTest`: 33 tests PASS
- `GoldenSnapshotTest`: 3 tests PASS
- `SentinelPatternTest`: 3 tests PASS
- `ConformanceTestSupportTest`: 1 test PASS

**Step 2: Run the full test suite (including existing tests)**

```bash
./gradlew :core:test
```

Expected: All existing tests still pass + all conformance tests pass.

**Step 3: Verify test counts**

```bash
./gradlew :core:test 2>&1 | grep -E "tests? (completed|passed|failed)"
```

**Step 4: Final commit if any remaining changes**

```bash
git status
# If clean, nothing to commit. If any cleanup needed:
git add -A && git commit -m "Stage 7a: conformance testing complete"
```
