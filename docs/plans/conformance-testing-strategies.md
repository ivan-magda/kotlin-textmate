# Conformance testing strategies for a vscode-textmate Kotlin port

**A Kotlin Multiplatform port of vscode-textmate can achieve high-fidelity conformance by directly reusing the original TypeScript project's JSON test fixtures, adopting the golden-file pattern used by both vscode-textmate's theme tests and bat's regression suite, and building a differential testing harness that generates reference output from the canonical implementation.** No universal cross-implementation conformance suite exists today — tm4e (Java) comes closest by synchronizing code line-by-line with vscode-textmate releases, while syntect (Rust) targets Sublime Text rather than vscode-textmate. The strategies below combine the best patterns from all known ports into a concrete, actionable plan.

---

## How vscode-textmate tests itself

The original TypeScript implementation uses **Mocha** with Node.js `assert.deepStrictEqual` and organizes tests across three files in `src/tests/` backed by fixtures in `test-cases/`.

**Tokenization tests** (`tokenization.test.ts`) load JSON test suites from three fixture files: `test-cases/first-mate/tests.json` (~5,500 lines, originally ported from Atom's First Mate library), `test-cases/suite1/tests.json`, and `test-cases/suite1/whileTests.json` (dedicated to `begin/while` rule coverage). The core assertion function, `assertLineTokenization`, calls `grammar.tokenizeLine(line, prevState)`, maps results to `{value, scopes}` pairs, and deep-compares them against expected tokens. State flows line-to-line — the returned `ruleStack` feeds into the next call, validating that incremental tokenization state serialization works correctly. Additionally, the function tests **state stack diff/apply round-tripping** via `diffStateStacksRefEq` and `applyStateStackDiff`.

The JSON fixture format is straightforward and directly portable to Kotlin:

```json
{
  "desc": "TEST #4",
  "grammarScopeName": "source.coffee",
  "grammars": ["fixtures/text.json", "fixtures/javascript.json", "fixtures/coffee-script.json"],
  "lines": [
    {
      "line": "return",
      "tokens": [
        { "value": "return", "scopes": ["source.coffee", "keyword.control.coffee"] }
      ]
    }
  ]
}
```

Each entry specifies either `grammarPath` or `grammarScopeName`, a `grammars` array of files to pre-register, and ordered `lines` where each token carries a `value` (matched text) and `scopes` (full scope stack, outermost first). The grammar fixtures in `test-cases/first-mate/fixtures/` include purpose-built grammars like `apply-end-pattern-last.json` and `nested-captures.json` alongside real-language grammars for JavaScript, Python, C, Java, and others.

**Grammar metadata tests** (`grammar.test.ts`) validate the binary token encoding layer — `EncodedTokenAttributes.set()` and getter methods — and also test inline tokenization with direct scope validation. **Theme tests** (`themes.test.ts`) use a **golden-file pattern**: source files in `test-cases/themes/tests/` (covering ~20 languages) are tokenized against multiple themes, and the output is compared against `.result` files. When a test fails, `tst.writeExpected()` regenerates the golden file for manual review.

## What other ports teach about conformance

**tm4e (Java/Eclipse)** is the most relevant precedent for a JVM port. Described as "a Java port of vscode-textmate," it achieves conformance through **code-level synchronization** — PRs like "sync tm4e core with vscode-textmate 9.2.1" translate TypeScript changes directly into Java. Its `org.eclipse.tm4e.core.tests` module uses JUnit and reuses the same JSON fixture format from vscode-textmate. This proves that vscode-textmate's test fixtures port cleanly to JVM languages. tm4e does not track a formal conformance percentage but instead validates equivalence through code identity and passing the ported test suite.

**syntect (Rust)** takes a fundamentally different approach because it targets **Sublime Text's `.sublime-syntax` format** rather than TextMate grammars. It uses Sublime Text's inline assertion format (`// SYNTAX TEST` with `^^^` markers), running the `syntest` example against test files bundled from `sublimehq/Packages`. Bug reports compare syntect output against Sublime Text 3 rendering. This is not directly applicable to vscode-textmate conformance but demonstrates the power of inline assertion testing.

**bat (Rust)** provides the clearest model for **snapshot-based regression testing**. Source files live in `tests/syntax-tests/source/<Language>/test.<ext>`, and pre-generated highlighted output is stored in `tests/syntax-tests/highlighted/`. An `update.sh` script regenerates all golden files; developers manually review diffs before committing. As bat's creator @sharkdp noted: "This is intended to be a very high-level integration test that makes sure that the output of bat still looks the same."

**TextMateSharp (C#)** is a port of tm4e (not directly of vscode-textmate), creating a **transitive conformance chain**: vscode-textmate → tm4e → TextMateSharp. It inherits tm4e's test structure. No mature Go or Swift port with conformance testing exists.

**The critical finding is that no universal cross-implementation test corpus exists.** Each port either (a) carries over vscode-textmate's own fixtures (tm4e), (b) targets a different reference implementation (syntect → Sublime Text), or (c) tests only self-consistency (bat).

## Testing tools and the inline assertion format

Three standalone tools offer directly applicable testing patterns:

**vscode-tmgrammar-test** is the most widely adopted tool. It provides two complementary modes. The **unit test mode** uses inline assertions embedded in source code comments, inspired by Sublime Text's syntax test format:

```scala
// SYNTAX TEST "source.scala" "sample testcase"
class Stack[A] {
// <----- keyword.declaration.scala
//    ^^^^^ entity.name.class.declaration
//          ^ source.scala meta.bracket.scala
```

The `^^^` markers assert scopes at columns directly above; `<---` tests from line start; `- scope` provides negative assertions. This format is human-readable and excellent for grammar authors to validate specific constructs. The **snapshot mode** (`vscode-tmgrammar-snap`) generates `.snap` files capturing every token's full scope stack, updated via `--updateSnapshot`. Both modes run against the actual vscode-textmate engine.

**VS Code's own colorize testing pattern** (used by markdown, TypeScript, and LaTeX grammar extensions) places source files in `test/colorize-fixtures/` and golden output in `test/colorize-results/`. Running `npm test` tokenizes fixtures and diffs against results; `npm run accept` updates baselines. This is the pattern VS Code grammar extension authors use daily.

**textmate-tester** generates `.spec.yaml` files for each example source file and uses `git diff` to detect tokenization changes across grammar modifications — a lightweight alternative to structured golden files.

## The binary token format and which path to test

vscode-textmate exposes two tokenization APIs with fundamentally different outputs. `tokenizeLine()` returns `IToken[]` with human-readable `scopes: string[]` arrays. `tokenizeLine2()` returns a `Uint32Array` encoding **2 uint32 values per token**: the start index and a packed metadata integer.

The **32-bit metadata encoding** packs five fields using bitmasks:

| Field | Bits | Width | Range |
|-------|------|-------|-------|
| LanguageId | 0–7 | 8 bits | 0–255 |
| TokenType | 8–10 | 3 bits | Other/Comment/String/RegEx |
| FontStyle | 11–13 | 3 bits | Bold/Italic/Underline/Strikethrough |
| Foreground | 14–22 | 9 bits | Color map index 0–511 |
| Background | 23–31 | 9 bits | Color map index 0–511 |

In Kotlin, the unsigned right shift operator `ushr` replaces TypeScript's `>>>`, and `Int` provides the 32-bit representation. The `EncodedTokenAttributes` namespace translates naturally to a Kotlin `object` with static-like methods.

**VS Code uses exclusively the binary path** (`tokenizeLine2`) in its editor for performance — it merges tokenization and theme resolution into a single pass, stores tokens in an `ArrayBuffer`-backed `Uint32Array` with minimal GC pressure, and collapses consecutive tokens with identical metadata. The `tokenizeLine()` path exists for external tools needing full scope hierarchies.

**For a Kotlin port, test both paths.** Use `tokenizeLine()` output for scope-level correctness (the fundamental correctness signal), and `tokenizeLine2()` for binary format compatibility if you intend to interoperate with VS Code themes. Most ports (tm4e, TextMateSharp) primarily test the scope-level path because it's more debuggable and the binary encoding is a thin, separately testable layer.

## Recommended conformance test architecture

Based on the patterns observed across all implementations, a Kotlin Multiplatform conformance suite should combine four testing layers:

**Layer 1 — Ported vscode-textmate fixtures (immediate, high value).** Deserialize `tests.json`, `whileTests.json`, and the first-mate fixtures into Kotlin data classes and run them through your engine. This is what tm4e does and provides baseline conformance with ~100+ test cases covering core tokenization semantics. The Kotlin test loop mirrors the TypeScript original:

```kotlin
data class RawToken(val value: String, val scopes: List<String>)
data class TestLine(val line: String, val tokens: List<RawToken>)

fun assertLineTokenization(grammar: IGrammar, testCase: TestLine, prevState: StateStack?): StateStack {
    val result = grammar.tokenizeLine(testCase.line, prevState)
    val actual = result.tokens.map { token ->
        RawToken(
            value = testCase.line.substring(token.startIndex, token.endIndex),
            scopes = token.scopes.toList()
        )
    }
    assertEquals(testCase.tokens, actual, "Tokenizing: ${testCase.line}")
    return result.ruleStack
}
```

**Layer 2 — Generated golden files from reference implementation (differential testing).** Write a Node.js script that tokenizes a corpus of real-world source files through canonical vscode-textmate and writes structured JSON golden files. Run the same corpus through the Kotlin port and diff. This is the bat/colorize pattern adapted for cross-implementation comparison:

```
conformance/
├── generate-golden.ts          # Node script using vscode-textmate
├── run-conformance.kt          # Kotlin test comparing port output to golden
├── corpus/
│   ├── basic/hello.js
│   ├── edge-cases/heredoc.rb
│   └── real-world/lodash-excerpt.js
├── grammars/
│   └── source.js.tmLanguage.json
├── golden/
│   ├── basic/hello.js.tokens.json
│   └── edge-cases/heredoc.rb.tokens.json
└── known-divergences.json
```

The golden file generation script is straightforward:

```typescript
// generate-golden.ts
import * as vsctm from 'vscode-textmate';
import * as oniguruma from 'vscode-oniguruma';

const grammar = await registry.loadGrammar('source.js');
let ruleStack = vsctm.INITIAL;
const output = { lines: [] };

for (const line of sourceLines) {
    const r = grammar.tokenizeLine(line, ruleStack);
    output.lines.push({
        line,
        tokens: r.tokens.map(t => ({
            startIndex: t.startIndex,
            endIndex: t.endIndex,
            scopes: t.scopes
        }))
    });
    ruleStack = r.ruleStack;
}
fs.writeFileSync('golden/test.js.tokens.json', JSON.stringify(output, null, 2));
```

**Layer 3 — Binary encoding unit tests.** Test `EncodedTokenAttributes` (or your Kotlin equivalent) in isolation with known bit patterns from `grammar.test.ts`:

```kotlin
@Test fun `binary token encoding round-trips correctly`() {
    var value = EncodedTokenAttributes.set(0, languageId = 1,
        tokenType = StandardTokenType.RegEx,
        containsBalancedBrackets = false,
        fontStyle = FontStyle.Underline or FontStyle.Bold,
        foreground = 101, background = 102)
    assertEquals(1, EncodedTokenAttributes.getLanguageId(value))
    assertEquals(StandardTokenType.RegEx, EncodedTokenAttributes.getTokenType(value))
    assertEquals(FontStyle.Underline or FontStyle.Bold, EncodedTokenAttributes.getFontStyle(value))
    assertEquals(101, EncodedTokenAttributes.getForeground(value))
    assertEquals(102, EncodedTokenAttributes.getBackground(value))
}
```

**Layer 4 — Edge-case-specific regression tests.** Build targeted tests for each known tricky pattern, using purpose-built minimal grammars.

## Edge cases that demand dedicated test coverage

The most failure-prone areas in TextMate grammar implementations, drawn from vscode-textmate's own issue tracker and the experiences of ports, fall into several categories.

**Back-references in end patterns** are the single most common source of divergence. When a `begin` pattern captures text (e.g., `<<(\w+)` for heredocs), the `end` pattern's `\1` is dynamically resolved at match time. The resolved pattern must be stored in the state stack for incremental re-tokenization. Empty captures resolving `\1` to an empty string can trigger zero-width matches and infinite loops — vscode-textmate has explicit loop detection for this.

**Begin/while rules** differ from begin/end in a subtle but critical way: the `while` pattern is tested against **the start of each subsequent line** before any sub-patterns, and the entire rule is popped when `while` fails. This means `while` can only exclude whole lines, not stop mid-line. vscode-textmate's `whileTests.json` provides dedicated test cases. A key gotcha (Issue #87): while rules are checked even during capture retokenization, causing unexpected failures when `^` doesn't match because matching is happening mid-line inside a capture.

**`applyEndPatternLast`** reverses the default priority between end patterns and sub-patterns at the same match position. With the flag set to `true`, sub-patterns get priority. The test fixture `apply-end-pattern-last.json` in vscode-textmate tests this explicitly — a grammar where `}excentricSyntax` must match before bare `}` closes the scope.

**Anchor semantics** are nuanced across contexts. `\A` matches only at document start (not line start), `\G` matches where the previous match ended (critical for contiguous scope matching), and `^` matches line start. During **capture retokenization** (Issue #74), anchors retain their document/line-wide meaning — `^` does not match the start of a captured substring unless it happens to be at column 0.

**Injection grammars** use `L:` (left/before) and `R:` (right/after) selectors to control priority relative to the host grammar's patterns. Cross-grammar injections have known limitations: injecting into `source.cs` when it's already embedded in a Razor file doesn't work (Issue #122). Unclosed constructs in embedded languages can consume the rest of the document (Issue #207).

**Capture retokenization** — applying sub-patterns to captured groups — has performance implications (Issue #167: even empty `"patterns": []` causes ~100x degradation) and anchor quirks. **Zero-width match loop detection** (improved in PR #146) must handle both begin/end and begin/while rules. **Unicode handling** requires careful UTF-16 to UTF-8 conversion for Oniguruma, with surrogate pairs and non-BMP characters as primary risk areas.

A minimal edge-case test matrix should cover:

- `applyEndPatternLast`: true vs. false/undefined at same-position matches
- Back-references: `\1` in end from begin captures, including empty captures
- Begin/while: line continuation, while-failure mid-document, interaction with captures
- Anchors: `\A` at document start only, `\G` continuations, `^` in captures
- Injections: `L:` priority, `R:` priority, nested embedded languages
- Recursion: `$self`, `$base`, repository self-references, loop detection
- Priority: leftmost-wins, first-defined-wins, end vs. sub-pattern ordering
- Unicode: multi-byte characters, emoji, surrogate pairs in token boundaries

## Measuring and tracking conformance over time

No existing port tracks formal conformance metrics, but establishing quantitative tracking from day one differentiates a serious port. Define three tiers of conformance measurement:

**Token-level match rate** (`matching_tokens / total_tokens`) is the finest-grained metric. A token matches if both its `startIndex` and its complete `scopes` array are identical. **Line-level match rate** measures lines where every token matches — a stricter metric that catches off-by-one errors cascading through a line. **File-level match rate** measures files where every line matches perfectly — the strictest test.

Track **known divergences** in a structured registry rather than ignoring failures. Each divergence entry should document the category (regex engine difference, intentional deviation, unimplemented feature), severity, affected test files, and status (accepted, in-progress, won't-fix). This transforms test failures from noise into a managed backlog:

```json
{
  "id": "ONIG_UNICODE_PROPERTY",
  "description": "Kotlin regex engine handles \\p{Lu} differently from Oniguruma",
  "category": "regex-engine",
  "severity": "low",
  "affected_files": ["edge-cases/unicode-properties.py"],
  "status": "accepted"
}
```

Run conformance reporting in CI on every commit. A dashboard showing "**14,891 of 15,234 tokens matching (97.7%)** — 31 new divergences" immediately flags regressions and provides a concrete target for improvement.

## Conclusion

The most effective conformance strategy combines four elements: **directly reusing vscode-textmate's own JSON fixtures** (proven by tm4e on the JVM), **generating golden files from the canonical TypeScript implementation** against a curated corpus of real-world source files, **isolated unit tests for the binary encoding layer**, and **targeted edge-case tests** for the dozen patterns that consistently trip up ports. Start with the ported fixtures for immediate baseline coverage, then incrementally expand the golden-file corpus by tokenizing VS Code's own grammar test files through the reference implementation. The regex engine choice (Oniguruma bindings vs. a JVM regex library) will be the primary source of divergence — track it quantitatively from day one rather than discovering it in production.