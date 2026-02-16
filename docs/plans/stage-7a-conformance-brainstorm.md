# Stage 7a: Conformance Testing — Brainstorm

## Goal

Validate that KotlinTextMate's tokenizer produces the same output as the reference vscode-textmate TypeScript implementation. Target: 90%+ conformance.

## Current Test Coverage (What We Have)

15 test files covering all layers:

| Layer | Test files | What they test |
|-------|-----------|----------------|
| Regex | `JoniOnigScannerTest`, `JoniLookbehindTest` | Byte↔char offset conversion, lookbehind support |
| Grammar parsing | `GrammarReaderTest`, `TextMateGrammarTest` | JSON deserialization, raw grammar structure |
| Rule compilation | `RuleFactoryTest`, `RegExpSourceTest`, `IncludeReferenceTest`, `CaptureUtilsTest` | Rule hierarchy, regex anchoring, captures |
| Tokenization | `GrammarTest` (12 tests), `GrammarIntegrationTest` (9 tests) | Scope presence checks, multi-line state |
| State management | `StateStackTest`, `ScopeStackTest`, `AttributedScopeStackTest` | Parser state across lines |
| Theme | `ThemeTest`, `ThemeReaderTest` | Scope matching, theme parsing |

### Gap: Assertion granularity

Current tests assert **scope presence** ("does scope X appear somewhere in the line") but never assert the **exact token sequence** (value, startIndex, endIndex, full scope stack). This means:
- Token boundary bugs (wrong startIndex/endIndex) go undetected
- Scope stacking order errors go undetected
- Extra/missing tokens go undetected
- Subtle regressions in capture retokenization, contentName, applyEndPatternLast go undetected

## vscode-textmate Test Fixtures (What's Available)

The reference repo at `vscode-textmate/` contains three test suites plus a theme test suite.

### 1. `test-cases/first-mate/tests.json` (5522 lines, 64 test cases)

Originally from GitHub's Atom first-mate project. Each test case specifies:
```json
{
  "desc": "TEST #3",
  "grammars": ["fixtures/hello.json", "fixtures/text.json", ...],
  "grammarPath": "fixtures/hello.json",       // optional: resolve by path
  "grammarScopeName": "source.coffee",        // optional: resolve by scope name
  "grammarInjections": ["source.x"],          // optional: injection grammars
  "lines": [
    {
      "line": "hello world!",
      "tokens": [
        {"value": "hello", "scopes": ["source.hello", "prefix.hello"]},
        {"value": " ", "scopes": ["source.hello"]},
        ...
      ]
    }
  ]
}
```

The TypeScript test runner (`src/tests/tokenization.test.ts`) is ~100 lines: load grammars, tokenize each line, compare `{value, scopes}` with expected output using `deepStrictEqual`.

**Breakdown by runnability:**

| Category | Count | Notes |
|----------|-------|-------|
| Truly self-contained (JSON, no external includes, no injections) | **33** | Can run with current `Grammar` API directly |
| Needs external grammar includes (primary grammar has cross-grammar `include`) | 29 | Need a mini-Registry to load multiple grammars |
| Needs injection support | 2 | TEST #47, #49 — skip |
| **Total** | **64** | |

**The 33 self-contained tests** use these fixture grammars (no external `include` references):

`hello.json`, `coffee-script.json`, `text.json`, `content-name.json`, `apply-end-pattern-last.json`, `imaginary.json`, `multiline.json`, `c.json`, `infinite-loop.json`, `scss.json`, `nested-captures.json`, `hyperlink.json`, `forever.json`, `json.json`, `thrift.json`, `loops.json`

These exercise: BeginEnd rules, captures, nested captures, contentName, applyEndPatternLast, infinite loop protection, multiline rules, basic BeginWhile — the core engine features.

**The 29 "needs includes" tests** use grammars like `ruby.json`, `html.json`, `javascript.json`, `python.json`, etc. that have `include: "source.other"` references. These require loading multiple grammars and resolving cross-grammar references — essentially a `Registry`. The test inputs may or may not actually trigger the external include paths, but the grammar compilation will attempt to resolve them.

### 2. `test-cases/suite1/tests.json` (1832 lines, 20 test cases)

| Category | Count | Details |
|----------|-------|---------|
| PList-only | 13 | Markdown, ASP, Perl, Ruby, Makefile, YAML, etc. |
| JSON but needs external includes | 5 | Groovy→javadoc, Jade→js/css/etc., html2→js/css, #105→embedded |
| JSON self-contained | **2** | `infinite-loop.json` (5 lines), `147.grammar.json` (1 line) |

Only 2 are directly usable. `infinite-loop.json` is already covered by first-mate. Low value.

### 3. `test-cases/suite1/whileTests.json` (513 lines, 9 test cases)

All 9 use `whileLang.plist` — PList format. Cannot use without a PList parser. Skip.

### 4. `test-cases/themes/tests/` (theme conformance)

Source files (`.java`, `.html`, `.less`, `.yaml`, etc.) with `.result` files containing `{content, color}` per theme. Uses ~15 grammars and ~12 themes we don't have. Out of scope.

## Constraints

- **No PList parser** — eliminates suite1 (13/20), whileTests (all 9), first-mate (0 — all JSON or skipped)
- **No injection grammars** — eliminates 2 first-mate tests
- **No Registry** (currently) — Grammar API takes a single `RawGrammar`, no cross-grammar resolution. Eliminates 29 first-mate tests + 5 suite1 tests unless we build one
- **Joni limitation** — backrefs inside lookbehinds don't work (affects 1 Markdown pattern: strikethrough). Gracefully degrades to never-matching sentinel
- **Grammar files** — we have JSON, Kotlin, Markdown grammars in `shared-assets/grammars/` (not tested by any upstream fixture)

## Brainstormed Approaches

### Approach A: Minimal — Self-contained first-mate fixtures only

**What:** Run the 33 self-contained first-mate tests. One parameterized JUnit test class.

**Pros:**
- Zero infrastructure needed — no Registry, no PList, no Node.js scripts
- Uses Microsoft's own expected output — no reference data generation
- Tests core engine features (BeginEnd, captures, nested captures, contentName, applyEndPatternLast, infinite loops, multiline)
- ~150 lines of Kotlin

**Cons:**
- Doesn't test our 3 production grammars (JSON, Kotlin, Markdown) at all
- 33 tests is decent but misses 29 tests that exercise more complex grammar interactions
- Doesn't validate theme integration

**Effort:** Small (half a day)

### Approach B: First-mate fixtures + golden snapshots for our grammars

**What:** Approach A + generate reference tokenization data for our 3 grammars using Node.js/vscode-textmate, then assert exact match in Kotlin.

**Pros:**
- Covers both engine correctness (first-mate) and grammar-specific correctness (our 3 grammars)
- Golden snapshots catch regressions in production-relevant scenarios
- Curated test corpus can target features we care about (string interpolation, fenced code blocks, etc.)

**Cons:**
- Requires a one-time Node.js script (~50 lines) to generate reference data
- Need to maintain golden files when grammars are updated
- Markdown will have 1 known divergence (strikethrough) due to Joni limitation

**Effort:** Medium (1-2 days)

### Approach C: Build a test-only mini-Registry to unlock 29 more tests

**What:** Approach B + implement a lightweight `TestRegistry` (loads multiple JSON grammars, resolves `scopeName` → `RawGrammar`) to run the 29 first-mate tests that need external grammar includes.

**Pros:**
- 62/64 first-mate tests runnable (vs 33/64)
- Tests real cross-grammar include resolution
- Registry is needed eventually anyway

**Cons:**
- Registry is non-trivial — needs to hook into `Grammar` construction to provide external grammar lookup
- Current `Grammar` constructor takes a single `RawGrammar`; would need an `externalGrammarLookup: (String) -> RawGrammar?` parameter or similar
- Some of the 29 tests may fail because the included grammars also have `include` chains (transitive resolution)
- More scope than a PoC validation step

**Effort:** Medium-large (2-3 days)

### Approach D: Approach B + themed output validation

**What:** Approach B + for each golden snapshot, also apply our 4 themes and assert the resolved colors match vscode-textmate's themed output.

**Pros:**
- End-to-end validation: grammar → tokenizer → theme → colors
- Catches bugs in theme `match()` (scope iteration order, fontStyle resolution, etc.)

**Cons:**
- Need to generate themed reference data too (Node.js script becomes more complex)
- Theme differences may be noisy (our `match()` architecture differs slightly from vscode-textmate's incremental approach)
- Themed output format needs design (per-token: `{value, scopes, fg, bg, fontStyle}`)

**Effort:** Medium (adds ~1 day to Approach B)

## Recommendation

**Approach B** (first-mate fixtures + golden snapshots) is the sweet spot.

- The 33 self-contained first-mate tests validate engine correctness against Microsoft's own test suite
- The golden snapshots validate our 3 production grammars against the canonical TypeScript implementation
- No need for a Registry (that's a feature, not a validation concern)
- Theme conformance can be a follow-up (Approach D) after tokenizer conformance is confirmed

### Implementation outline

1. **Copy first-mate JSON fixture grammars** into `core/src/test/resources/conformance/first-mate/` (only the ~16 self-contained ones)
2. **`FirstMateConformanceTest.kt`** — JUnit 4 `@Parameterized`. Parse `tests.json`, filter to 33 self-contained tests. For each: load grammar by scopeName from loaded fixtures, tokenize lines, assert exact `{value, scopes}` match
3. **`generate-snapshots.mjs`** — Node.js ESM script. Loads vscode-textmate, tokenizes curated corpus files against our 3 grammars, dumps `{line, tokens: [{value, scopes}]}` as JSON
4. **Curated corpus files** — 3-5 representative source files per grammar (30-80 lines each):
   - JSON: nested objects, arrays, strings with escapes, numbers, booleans, null
   - Kotlin: fun, val/var, string interpolation, multiline strings, generics, lambdas, when, annotations
   - Markdown: headings, bold/italic, fenced code, indented code (BeginWhile), blockquotes, lists, links, inline code
5. **`GoldenSnapshotTest.kt`** — loads snapshots, tokenizes same input, asserts exact match. Known divergences (strikethrough) documented with `@Ignore` + comment
6. **Bonus: sentinel pattern inventory test** — asserts which regex patterns fell back to the never-matching sentinel, so we know the blast radius of Joni limitations

### Assertion strategy

- **Exact match**, not threshold-based. A test either passes or is explicitly documented as a known divergence
- The TypeScript runner itself filters empty tokens (`TODO@Alex: fix tests instead of working around`) — we should do the same
- Use `assertEquals` with clear diff output (value + full scope list), not `assertTrue` with string messages

### What to skip

- suite1 tests (2 usable, both redundant with first-mate)
- whileTests (all PList)
- Theme test fixtures (wrong grammars, wrong themes)
- Building a Registry (not needed for PoC validation)
- PList parser (out of scope)

## Open Questions

1. **Test runner: JUnit 4 `@Parameterized` vs JUnit 5 `@ParameterizedTest`?** Project currently uses JUnit 4. Switching to JUnit 5 just for conformance tests adds a dependency. Recommendation: stay with JUnit 4.

2. **Where to put fixture files?** Options:
   - `core/src/test/resources/conformance/first-mate/` (copy fixture grammars + tests.json)
   - Symlink to `vscode-textmate/test-cases/` (avoids duplication but fragile)
   - Recommendation: copy the needed files. ~16 small JSON grammars + 1 test manifest. Avoids path issues.

3. **Golden snapshot format?** Options:
   - One JSON file per grammar (e.g., `kotlin-snapshot.json`) with all lines
   - One JSON file per corpus file (e.g., `kotlin-function.json`, `kotlin-strings.json`)
   - Recommendation: one file per grammar, simpler to manage.

4. **Should the golden snapshot test be parameterized per-line or per-file?** Per-line gives better failure isolation but 100+ test methods. Per-file is simpler. Recommendation: per-file, with clear diff output showing the first divergent line.

5. **Mini-Registry for 29 more tests — worth it?** Could be a separate PR after the core 33 pass. The infrastructure (scopeName → Grammar lookup) would be useful beyond testing. But not needed for PoC validation. Recommendation: defer.

## Data Summary

| Suite | Total | Runnable now | Needs Registry | Needs PList | Needs Injections |
|-------|-------|-------------|----------------|-------------|------------------|
| first-mate | 64 | **33** | 29 | 0 | 2 |
| suite1 | 20 | **2** | 5 | 13 | 0 |
| whileTests | 9 | **0** | 0 | 9 | 0 |
| **Total** | **93** | **35** | **34** | **22** | **2** |

The 33 self-contained first-mate tests cover 16 distinct grammars and ~50 lines of tokenization with exact token matching. Combined with golden snapshots for our 3 production grammars, this provides strong conformance coverage for a PoC.
