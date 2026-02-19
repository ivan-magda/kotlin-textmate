---
name: adding-grammar
description: Use when adding a new TextMate grammar to the KotlinTextMate project, including corpus files, golden snapshot generation, and conformance test registration
---

# Adding a Grammar to KotlinTextMate

## Overview

Adding a new grammar requires: grammar file, corpus files, golden snapshot generation, and test registration. Missing any step causes silent gaps in conformance coverage.

Placeholders used below: `<lang>` = corpus directory name (always lowercase, e.g., `javascript`), `<Grammar>` = grammar filename without `.tmLanguage.json` (preserve original casing, e.g., `JavaScript`), `<Label>` = human-readable name for test display (e.g., `JavaScript`).

## Prerequisites

- Node.js >= 21.2 (for the snapshot generator)

## Steps

### 0. Obtain the grammar file

Source the `.tmLanguage.json` from VS Code's built-in extensions: `microsoft/vscode` repo under `extensions/<lang>-basics/syntaxes/`. Copy to `shared-assets/grammars/`. Use the exact original filename — casing varies per grammar (e.g., `JavaScript.tmLanguage.json` uppercase, `kotlin.tmLanguage.json` lowercase). If the file is JSONC (has trailing commas or comments), strip them to make valid JSON.

Also check if `vscode-textmate/test-cases/themes/syntaxes/` or `vscode-textmate/benchmark/` already has the grammar.

### 1. Create corpus files

Create `tools/generate-golden/corpus/<lang>/` with hand-crafted source files exercising the language's syntax.

**Check `vscode-textmate/test-cases/themes/tests/` first** — it has small, focused test files (e.g., `test.js`, `test.jsx`) that make excellent corpus files. Also check `vscode-textmate/benchmark/` for larger corpus files.

Prefer 1-3 small focused files over one large dump. Existing corpora use 1-2 files each (JSON: 1 file, Kotlin: 2, Markdown: 2, JavaScript: 1).

### 2. Register in generator

Add entry to `GRAMMARS` in `tools/generate-golden/generate.mjs`:

```javascript
<lang>: { file: "<Grammar>.tmLanguage.json", scope: "<scope.name>" },
```

Find the scope name in the grammar file's top-level `scopeName` field.

### 3. Run generator

```bash
cd tools/generate-golden && npm install && npm run generate
```

Produces `core/src/test/resources/conformance/golden/<lang>.snapshot.json`.

### 4. Add to GoldenSnapshotTest

Add parameter row in `core/src/test/kotlin/dev/textmate/conformance/GoldenSnapshotTest.kt`:

```kotlin
arrayOf("<Label>", "grammars/<Grammar>.tmLanguage.json", "conformance/golden/<lang>.snapshot.json"),
```

### 5. Add sentinel test

Add test in `core/src/test/kotlin/dev/textmate/conformance/SentinelPatternTest.kt`:

```kotlin
@Test
fun `<Label> grammar has 0 sentinel patterns`() {
    val onigLib = JoniOnigLib()
    loadAndCompileGrammar("grammars/<Grammar>.tmLanguage.json", onigLib)
    assertEquals("<Label> should have no sentinel patterns", 0, onigLib.sentinelPatternCount)
}
```

If the grammar uses backreferences inside lookbehinds (like Markdown's strikethrough), expect >0 sentinels. Run the test first with 0 — if it fails, the actual count tells you the correct value.

**Important:** `loadAndCompileGrammar()` tokenizes fixed representative lines. Lazy compilation means only exercised rules get compiled. You MUST add at least one representative line for the new language to the `lines` list inside `loadAndCompileGrammar()` in `SentinelPatternTest.kt`, otherwise 0 sentinels passes vacuously.

### 6. Run tests

```bash
./gradlew :core:test --tests "dev.textmate.conformance.*"
```

### 7. Commit

Commit all new/changed files: grammar file, corpus, snapshot, generator config, test code.

## Key File Locations

| Purpose              | Path                                          |
| -------------------- | --------------------------------------------- |
| Grammar files        | `shared-assets/grammars/`                     |
| Corpus files         | `tools/generate-golden/corpus/<lang>/`        |
| Generator script     | `tools/generate-golden/generate.mjs`          |
| Generated snapshots  | `core/src/test/resources/conformance/golden/` |
| Golden snapshot test | `core/.../conformance/GoldenSnapshotTest.kt`  |
| Sentinel test        | `core/.../conformance/SentinelPatternTest.kt` |
| Reusable test files  | `vscode-textmate/test-cases/themes/tests/`    |

## Common Mistakes

- **Forgetting sentinel test** — sentinels silently degrade tokenization; always verify count
- **Not checking vscode-textmate clone** — reusable corpus files often exist in `test-cases/themes/tests/`
- **Wrong grammar file casing** — use exact filename (e.g., `JavaScript.tmLanguage.json` not `javascript.tmLanguage.json`)
- **Generator without `npm install`** — `node_modules` not committed; always install first
- **Cross-grammar includes won't resolve** — generator uses `loadGrammar: () => null`; sub-grammars (like `source.js.regexp`) won't load, and that's expected — the snapshot captures this behavior
