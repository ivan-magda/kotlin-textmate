# Architecture

KotlinTextMate is a Kotlin/JVM port of [vscode-textmate](https://github.com/microsoft/vscode-textmate), the TextMate grammar tokenizer that powers syntax highlighting in VS Code. This document maps the architecture, explains key design decisions, and offers an honest retrospective.

## Data flow

```
.tmLanguage.json ──► GrammarReader ──► RawGrammar
                                          │
                                          ▼
                                      RuleFactory ──► Rule tree (sealed hierarchy)
                                          │
                                          ▼
Grammar.tokenizeLine(line, prevState) ──► Tokenizer
    │                                       │
    │   ┌───────────────────────────────────┘
    │   │
    ▼   ▼
List<Token(startIndex, endIndex, scopes)>, StateStack
    │
    ▼
Theme.match(scopes) ──► ResolvedStyle(foreground, background, fontStyle)
    │
    ▼
CodeHighlighter ──► AnnotatedString (Compose SpanStyle per token)
    │
    ▼
CodeBlock composable ──► rendered text on screen
```

**Step by step:**

1. **Parse** — `GrammarReader` deserializes `.tmLanguage.json` into `RawGrammar`/`RawRule` data classes via Gson.
2. **Compile** — On first `tokenizeLine()`, `RuleFactory` recursively compiles `RawRule` into a `Rule` tree (`MatchRule`, `BeginEndRule`, `BeginWhileRule`, `IncludeOnlyRule`, `CaptureRule`), resolving `#include` references.
3. **Tokenize** — The tokenizer walks each line character-by-character, matching compiled rules via Joni regex scanners. It maintains a `StateStack` (immutable linked list) that carries parser context between lines.
4. **Resolve theme** — `Theme.match(scopes)` maps the flat scope list to foreground color, background color, and font style. This happens _outside_ the tokenizer (see [External theme resolution](#external-theme-resolution)).
5. **Render** — `CodeHighlighter` converts tokens + resolved styles into a Compose `AnnotatedString`. `CodeBlock` wraps this in a scrollable, selectable `Text` composable.

## Module map

```
KotlinTextMate/
├── core/           Pure JVM library (Kotlin 2.0, JDK 17)
│   └── dev.textmate
│       ├── regex/          Joni wrapper
│       ├── grammar/
│       │   ├── raw/        JSON parsing
│       │   ├── rule/       Rule compilation
│       │   └── tokenize/   Tokenizer + state
│       ├── theme/          Theme engine
│       └── registry/       Grammar registry (Registry, GrammarSource)
├── compose-ui/     Android library (Compose)
│   └── dev.textmate.compose
├── benchmark/      JMH benchmarks
└── sample-app/     Android demo
```

### File mapping: vscode-textmate → KotlinTextMate

| vscode-textmate (TypeScript)     | KotlinTextMate (Kotlin)                                                                               | Notes                                                               |
| -------------------------------- | ----------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------- |
| `src/onigLib.ts`                 | `regex/OnigInterfaces.kt`, `OnigString.kt`, `OnigTypes.kt`                                            | `IOnigLib`/`OnigScanner` interfaces                                 |
| (vscode-oniguruma WASM)          | `regex/JoniOnigLib.kt`                                                                                | Joni (Java Oniguruma) with sentinel fallback                        |
| `src/rawGrammar.ts`              | `grammar/raw/RawGrammar.kt`                                                                           | `RawGrammar`, `RawRule`. No separate `RawCapture` — uses `RawRule`  |
| `src/grammarReader.ts`           | `grammar/raw/GrammarReader.kt`                                                                        | JSON only, no PList. Gson with `@SerializedName("while")`           |
| `src/rule.ts`                    | `grammar/rule/Rule.kt`, `RuleFactory.kt`, `RegExpSource.kt`, `RegExpSourceList.kt`, `CaptureUtils.kt` | `Rule` sealed hierarchy. `RuleFactory` is `internal object`         |
| `src/rule.ts` (include parsing)  | `grammar/rule/IncludeReference.kt`                                                                    | Sealed class for `$self`, `$base`, `#name`, `scope`, `scope#rule`   |
| `src/grammar/grammar.ts`         | `grammar/Grammar.kt`, `grammar/tokenize/LineTokens.kt`                                                | `Grammar` is public entry point; `LineTokens` is non-binary only    |
| `src/grammar/grammar.ts` (state) | `grammar/tokenize/StateStack.kt`, `AttributedScopeStack.kt`                                           | `StateStack` interface (public) + `StateStackImpl` (implementation) |
| `src/grammar/tokenizeString.ts`  | `grammar/tokenize/Tokenizer.kt`                                                                       | Core `tokenizeString` loop, `matchRule`, `handleCaptures`           |
| `src/theme.ts`                   | `theme/Theme.kt`, `ThemeReader.kt`, `RawTheme.kt`, `grammar/tokenize/ScopeStack.kt`                   | External resolution, not incremental. `ScopeStack` originates here  |
| `src/matcher.ts`                 | Not ported                                                                                            | Scope selector matching (needed for injection grammars)             |
| `src/registry.ts`                | `registry/Registry.kt`, `registry/GrammarSource.kt`                                                   | Simplified: no theme provider, grammar lookup via `GrammarSource`   |
| `src/main.ts`                    | `grammar/TextMateGrammar.kt`                                                                          | Version constant only; public API lives on `Grammar`                |
| `src/encodedTokenAttributes.ts`  | Not ported                                                                                            | Binary token encoding not needed                                    |

## Key design decisions

### External theme resolution

**vscode-textmate** resolves theme styles incrementally inside the tokenizer: as each scope is pushed onto the stack, `IThemeProvider.themeMatch()` is called and the result is encoded into `tokenAttributes` on the `AttributedScopeStack`. Consumers get pre-resolved style bits from the token output.

**KotlinTextMate** does not do this. The tokenizer produces tokens with raw scope names (`List<String>`). Theme resolution happens externally — `Theme.match(scopes)` is called by `CodeHighlighter` in the compose-ui module.

**Why:** This cleanly separates tokenization from theming. The tokenizer is theme-agnostic. Switching themes does not require retokenization — just re-running `Theme.match()` on the existing tokens. The `AttributedScopeStack.tokenAttributes` field is carried forward at `0` and is unused. The `grammar: Any?` parameter in `pushAttributed()` is permanently unused.

**Correctness:** vscode-textmate resolves styles as innermost-scope-last (incremental push). Our `match()` iterates scopes outermost-to-innermost with rules sorted ascending (least-specific first), achieving identical last-writer-wins semantics. Tested against Dark+/Light+ themes with matching results.

### Graceful regex degradation

Joni (Java Oniguruma) cannot compile patterns with backreferences inside lookbehind assertions (e.g., `(?<=_\1)`). Instead of crashing, `JoniOnigScanner.compilePattern()` catches `JOniException` and returns a never-matching sentinel regex `(?!)`. Pattern indices stay stable (critical for rule ID mapping). Unsupported patterns silently never match; text falls through to the parent scope.

**Why:** A single unsupported pattern in a 3000-rule grammar should not crash the entire tokenizer. In practice, only 1 pattern out of the 4 shipped grammars is affected (Markdown strikethrough). The sentinel count is tracked internally for regression testing.

### No separate RawCapture type

In TextMate grammars, captures are `Map<String, Rule>` — the values have the same structure as rules (can have `name`, `patterns`, etc.). Rather than creating a redundant `RawCapture` data class, we reuse `RawRule` directly for captures. `RuleFactory.compileCaptures()` parses the string keys as capture group indices.

### `applyEndPatternLast` as `Int?` with `BooleanOrIntAdapter`

Production VS Code grammars use `1`/`0`. First-mate test fixture grammars use `true`/`false`. JavaScript is lenient about truthiness; Kotlin/Gson is not. A `@JsonAdapter(BooleanOrIntAdapter::class)` on the field coerces both to `Int?`, then `RuleFactory` converts to `Boolean` at the compilation boundary.

### Per-Grammar rule ID caching

`RawRule` is fully immutable (all `val` fields). Rule IDs are cached externally in each `Grammar` instance via an `IdentityHashMap<RawRule, RuleId>`, exposed through `IRuleFactoryHelper.getCachedRuleId()`/`cacheRuleId()`. This prevents infinite recursion when grammars have circular `$self` references — the same `RawRule` instance hit a second time returns its pre-assigned ID immediately.

This departs from vscode-textmate, which stores the ID on the rule object itself (`rule.id`). The per-Grammar cache avoids cross-grammar pollution when multiple `Grammar` instances share the same `RawGrammar` object graph, and preserves `data class` semantics (stable `equals`/`hashCode`). `IdentityHashMap` (not `HashMap`) is required because structurally-equal `RawRule` objects at different tree positions must compile to independent rules. No `deepClone()` is needed.

### Anchor caching in RegExpSource

Patterns containing `\A` (start-of-string) or `\G` (previous match position) need 4 regex variants (each anchor allowed or disallowed). `RegExpSource` precomputes all 4 string variants at construction time and stores them in an `AnchorCache`. `RegExpSourceList` maintains 5 compiled scanner cache slots (1 no-anchor + 4 anchor combinations). This avoids regex recompilation during the hot tokenization loop.

### `\n` appending and stripping

The tokenizer appends `\n` to every line before tokenization (matching vscode-textmate). `lineLength` includes the `\n`. `beginRuleCapturedEOL` compares against this length. `LineTokens.getResult()` strips the trailing newline token if its `startIndex == lineLength - 1`. The last real token's `endIndex` may exceed the original line length when the last character and `\n` share the same scope — this matches vscode-textmate behavior; consumers using `substring` get silent clamping.

### Theme `match()` — all scopes, ascending sort, last-writer-wins

Rules are sorted least-specific first (by scope depth, parent scope count, then file order). `match()` iterates all scopes from outermost to innermost, checking every rule. Last write to each attribute (foreground, background, fontStyle) wins. This naturally handles independent attributes from different rules (e.g., bold from a parent scope, color from a child scope).

**Loop nesting order matters:** scope-outer/rule-inner is correct. rule-outer/scope-inner with break gives wrong results when rules match at different stack positions.

## Thread safety

No part of the codebase uses synchronization. This is by design — vscode-textmate is single-threaded (JavaScript), and the port preserves that assumption.

**Safe to share across threads:**

- `Theme` — fully immutable after construction
- `ScopeStack`, `AttributedScopeStack` — immutable linked lists
- `OnigString` — effectively immutable (`byteToCharOffsets` is `lazy` with synchronized default)
- `JoniOnigScanner` instances — no mutable state after construction

**Not safe to share:**

- `Grammar` — mutable rule registry (`_ruleId2desc`), lazy compilation state. Do not call `tokenizeLine()` concurrently on the same `Grammar` instance.
- `StateStackImpl` — `reset()` mutates `_enterPos`/`_anchorPos` on shared ancestor frames.
- Rule objects — lazy-cached `_cachedCompiledPatterns` with unsynchronized check-then-act. `BeginEndRule` mutates cached `RegExpSourceList` via `setSource()` when resolving end-pattern backreferences.
- `JoniOnigLib._sentinelPatterns` — unsynchronized `MutableSet`.

**For Android:** Create one `Grammar` per thread/coroutine, or serialize access to a shared instance. `Theme` can be shared freely.

## Retrospective

### What works well

**Faithful port with clean Kotlin idioms.** The codebase maps closely enough to vscode-textmate that cross-referencing is straightforward, but uses Kotlin idioms where they improve readability: sealed class hierarchy for rules, data classes for tokens, `internal` visibility for implementation details, `@SerializedName`/`@JsonAdapter` for Gson quirks.

**Layered caching.** The three-level cache (per-rule `RegExpSourceList` → per-anchor-combination `CompiledRule` → Joni `Regex`) means regex compilation happens once during warmup and never again. This is the main reason tokenization is fast (79k–458k lines/sec).

**Graceful degradation.** The sentinel pattern mechanism is well-designed: unsupported regex patterns silently become no-ops, indices stay stable, and the count is tracked for testing. Only 1 out of ~4500 patterns across 4 grammars degrades.

**External theme resolution.** Separating tokenization from theming was the right call. The tokenizer is simpler, themes can be switched without retokenizing, and the compose-ui module stays thin (~60 lines in `CodeHighlighter`).

**Test infrastructure.** Dual conformance testing (33 first-mate tests + 4 golden snapshot tests) against two independent reference implementations provides strong correctness guarantees. The sentinel regression test catches regex engine issues early.

**Immutable `RawRule` with per-Grammar ID caching.** Rule IDs are cached in an `IdentityHashMap<RawRule, RuleId>` per `Grammar`, keeping `RawRule` fully immutable. Multiple `Grammar` instances safely share the same `RawGrammar` without cloning.

### What I would change in a v2

**Tighten the public API surface.** The `grammar.rule` package leaks ~10 types that should be `internal`: `Rule` hierarchy, `RuleId`, `CompiledRule`, `IRuleRegistry`, `IRuleRegistryOnigLib`. These are visible because `Grammar` directly implements `IRuleFactoryHelper` + `IRuleRegistryOnigLib`. A v2 should delegate to an internal helper object instead of implementing these interfaces on `Grammar` itself. Similarly, `StateStackImpl` should be `internal` — consumers only need the `StateStack` interface.

**Add a null guard to `GrammarReader`.** `Gson.fromJson()` returns `null` for empty input even with a non-null Kotlin return type. `ThemeReader` guards against this; `GrammarReader` does not. A one-line `?: throw` would prevent deferred NPEs.

**Reduce per-token allocations in `getScopeNames()`.** Every call to `ScopeStack.getSegments()` allocates a list and reverses it. Since `ScopeStack` is immutable, the result could be cached as a lazy field. At ~5 tokens per line and ~100k lines/sec, this adds up.

**Pool `OnigString` or avoid the `+ "\n"` allocation.** Every line creates a new `String` via concatenation and a new `ByteArray` via `toByteArray()`. A reusable byte buffer with the `\n` appended directly would eliminate two allocations per line.

**`CaptureRule` should not extend `Rule`.** It overrides all three abstract methods (`collectPatterns`, `compile`, `compileAG`) with `throw UnsupportedOperationException`. This is a Liskov Substitution violation. A v2 could extract `CompilableRule` as the base and keep `CaptureRule` separate.

**Observability for degraded patterns.** The sentinel count is `internal` — consumers have no way to know why certain syntax isn't highlighted. An optional diagnostic callback or a public `diagnostics()` method would help integrators debug grammar issues.

**compose-ui tests.** The compose-ui module has zero tests. `CodeHighlighter` (token→AnnotatedString conversion) is testable without an Android emulator and should have unit tests covering index clamping, font style mapping, and empty-line handling.
