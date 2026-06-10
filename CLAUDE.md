# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## General Rules

Do NOT expand scope beyond what is explicitly requested. If the user asks to download one file, download that one file. Ask before adding extra corpus files, grammars, or other 'nice to have' additions.

`docs/` review and plan documents (`architecture-review-*.md`, `plans/`) are internal — do not reference them in PR titles/descriptions or commit messages.

## Project Overview

KotlinTextMate is a Kotlin port of [vscode-textmate](https://github.com/microsoft/vscode-textmate) (TypeScript). It provides a TextMate grammar tokenizer for syntax highlighting, targeting JVM/Android with a Compose UI layer. The implementation plan is in `docs/plans/plan-poc.md` (written in Russian).

## Build Commands

```bash
./gradlew build                    # Build everything
./gradlew :core:build              # Build core module only
./gradlew :core:test               # Run core tests
./gradlew :core:test --tests "dev.textmate.regex.JoniOnigScannerTest"   # Single test class
./gradlew :core:test --tests "dev.textmate.regex.JoniOnigScannerTest.testSimpleKeywordMatch"  # Single test method
./gradlew :sample-app:assembleDebug  # Build Android sample app
```

## Module Structure

- **core/** — JVM library: regex layer (Joni wrapper), grammar parsing, tokenizer, theme engine
- **compose-ui/** — Android library: Compose UI bridge (depends on core). Public API: `CodeBlock` composable, `CodeBlockStyle`/`CodeBlockDefaults` (Material3 Defaults pattern), `rememberHighlightedCode` (escape hatch for custom rendering), `CodeHighlighter`
- **benchmark/** — JMH benchmarks via kotlinx-benchmark (depends on core). `./gradlew :benchmark:smokeBenchmark` (~1 min), `./gradlew :benchmark:benchmark` (full, ~8-10 min)
- **sample-app/** — Android app: demo application (depends on core + compose-ui)

## Architecture

Before making architectural changes, porting code from vscode-textmate, or understanding module boundaries → `ARCHITECTURE.md` (data flow, package layout, file mapping, key design decisions, thread-safety rules, retrospective).

## Key Technical Details

- **Kotlin 2.0.21**, JVM target 17, Android minSdk 24
- **Joni** (Java Oniguruma) for regex — works with byte offsets, requiring conversion to/from char offsets. Graceful degradation: unsupported patterns (backreferences inside lookbehinds) compile to a never-matching sentinel instead of crashing
- **Gson** for JSON deserialization of grammar files
- The `while` keyword in `RawRule` is mapped via `@SerializedName("while")` to `whilePattern`
- Grammar and theme files live in `shared-assets/` at the project root (single source of truth). Both `core` (test resources via `srcDir`) and `sample-app` (Android assets via `assets.srcDir`) point there. No duplication.
- Reference source for porting: `https://github.com/microsoft/vscode-textmate` `src/` directory (baseline v9.3.2, recorded in `docs/UPSTREAM.md`)
- New Gson-deserialized models (`Raw*`) need R8 keep rules in `core/src/main/resources/META-INF/proguard/`, mirrored in `compose-ui/consumer-rules.pro`, or minified consumers break
- After changing public API of `core`/`compose-ui`, run `./gradlew apiDump` and commit the updated `*.api` dumps — CI `apiCheck` gates them (binary-compatibility-validator; `apiCheck` is read-only)
- `TextMateGrammar.VERSION` is generated from `VERSION_NAME` by `core/build.gradle.kts` — there is no `TextMateGrammar.kt` under `src/`
