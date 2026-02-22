# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## General Rules

Do NOT expand scope beyond what is explicitly requested. If the user asks to download one file, download that one file. Ask before adding extra corpus files, grammars, or other 'nice to have' additions.

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

The architecture mirrors vscode-textmate's layered design, ported from TypeScript to Kotlin:

```
Compose UI (AnnotatedString) → Theme Engine → Tokenizer → Grammar → Regex Layer (Joni)
```

### Core module package layout (`dev.textmate.*`)

- **regex/** — Joni-based Oniguruma wrapper. `IOnigLib`/`OnigScanner` interfaces abstract the regex engine. `OnigString` handles UTF-8 byte↔char offset conversion (critical because Joni operates on byte offsets while the API uses char offsets).
- **grammar/** — Public API entry point: `Grammar` class (compiles raw grammars and exposes `tokenizeLine()`), `Token`/`TokenizeLineResult`, `TextMateGrammar` (version constant).
- **grammar/raw/** — Data classes (`RawGrammar`, `RawRule`) and `GrammarReader` for parsing `.tmLanguage.json` files. Captures are `Map<String, RawRule>` (no separate `RawCapture` type). Rule IDs are assigned during compilation by `RuleFactory`, not during parsing.
- **grammar/rule/** — Rule hierarchy and compilation: `sealed class Rule` (`CaptureRule`, `MatchRule`, `IncludeOnlyRule`, `BeginEndRule`, `BeginWhileRule`), `RuleFactory` (compiles `RawRule` → `Rule`), `RegExpSource`/`RegExpSourceList` (regex pattern management with anchor caching), `CompiledRule` (OnigScanner wrapper), `IRuleRegistry`/`IRuleFactoryHelper` interfaces. Implementation details are `internal`; Rule constructors are `internal` (only `RuleFactory` creates them). `IRuleRegistry.getRule()` returns nullable `Rule?` to handle circular references during compilation.
- **grammar/tokenize/** — Tokenization engine and state: `Tokenizer.kt` (core `tokenizeString` loop), `LineTokens` (token accumulator), `StateStack`/`StateStackImpl` (parser state across lines), `ScopeStack`/`AttributedScopeStack` (scope name tracking).
- **theme/** — Theme engine: `Theme` (scope-to-style resolution via `match()`), `ThemeReader` (JSON parsing, theme merging), `FontStyle`/`ResolvedStyle` (public API). Supports legacy (`settings`) and modern (`tokenColors`) VS Code theme formats. Theme files are production VS Code themes (stripped of JSONC trailing commas). Unlike vscode-textmate (which resolves styles incrementally per scope push), our `match()` receives the full scope stack and iterates all scopes outermost-to-innermost — this is why middle scopes like `markup.heading` get colored.
- **registry/** — Grammar registry: `Registry` (public API for multi-grammar loading and caching), `GrammarSource` (functional interface for on-demand grammar loading). Cross-grammar `include` resolution is wired through `Grammar.grammarLookup`.

## Key Technical Details

- **Kotlin 2.0.21**, JVM target 17, Android minSdk 24
- **Joni** (Java Oniguruma) for regex — works with byte offsets, requiring conversion to/from char offsets. Graceful degradation: unsupported patterns (backreferences inside lookbehinds) compile to a never-matching sentinel instead of crashing
- **Gson** for JSON deserialization of grammar files
- The `while` keyword in `RawRule` is mapped via `@SerializedName("while")` to `whilePattern`
- Grammar and theme files live in `shared-assets/` at the project root (single source of truth). Both `core` (test resources via `srcDir`) and `sample-app` (Android assets via `assets.srcDir`) point there. No duplication.
- Reference source for porting: `https://github.com/microsoft/vscode-textmate` `src/` directory
