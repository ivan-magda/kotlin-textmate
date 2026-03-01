# Make RawRule.id Immutable

## Overview
- Remove the mutable `RawRule.id` field and replace it with a per-`Grammar` `IdentityHashMap<RawRule, RuleId>` compilation cache
- Fixes cross-Grammar rule ID pollution when multiple Grammar instances share the same RawGrammar object graph ([#29](https://github.com/ivan-magda/kotlin-textmate/issues/29))
- Also removes the now-unnecessary `deepClone()` machinery since `RawRule` becomes fully immutable

## Context (from discovery)
- Files/components involved:
  - `core/src/main/kotlin/dev/textmate/grammar/raw/RawGrammar.kt` — `RawRule.id` field + `deepClone()` helpers
  - `core/src/main/kotlin/dev/textmate/grammar/rule/RuleFactory.kt` — `desc.id` cache reads/writes
  - `core/src/main/kotlin/dev/textmate/grammar/rule/IRuleRegistry.kt` — `IRuleFactoryHelper` interface
  - `core/src/main/kotlin/dev/textmate/grammar/Grammar.kt` — implements `IRuleFactoryHelper`, calls `deepClone()`
  - `core/src/test/kotlin/dev/textmate/registry/RegistryTest.kt` — existing cross-grammar test
  - `core/src/test/kotlin/dev/textmate/grammar/rule/RuleFactoryTest.kt` — `TestRuleFactoryHelper`
- Related patterns: `IdentityHashMap` (new to project), `IRuleFactoryHelper` as abstraction point
- Dependencies: None — self-contained refactor within `core` module

## Development Approach
- **Testing approach**: TDD (tests first)
- Complete each task fully before moving to the next
- Make small, focused changes
- **CRITICAL: every task MUST include new/updated tests** for code changes in that task
- **CRITICAL: all tests must pass before starting next task** — no exceptions
- **CRITICAL: update this plan file when scope changes during implementation**
- Run tests after each change
- Maintain backward compatibility

## Testing Strategy
- **Unit tests**: required for every task (see Development Approach above)

## Progress Tracking
- Mark completed items with `[x]` immediately when done
- Add newly discovered tasks with ➕ prefix
- Document issues/blockers with ⚠️ prefix
- Update plan if implementation deviates from original scope
- Keep plan in sync with actual work done

## Implementation Steps

### Task 1: Write failing TDD test — cross-grammar sharing without deepClone
- [x] Add test in `RegistryTest.kt`: `two grammars sharing RawGrammar without deepClone both tokenize correctly` — construct two `Grammar` instances that share the same `RawGrammar` via `grammarLookup` (bypassing `getExternalGrammar`/`deepClone`), tokenize with both, assert Grammar-B produces correct scopes
- [x] Verify new test fails (RuleFactory mutates shared `RawRule.id`, Grammar-B gets stale rule IDs from Grammar-A)
- [x] Run `./gradlew :core:test` — only the new test should fail

### Task 2: Add cache methods to IRuleFactoryHelper
- [x] Add `fun getCachedRuleId(desc: RawRule): RuleId?` to `IRuleFactoryHelper` in `IRuleRegistry.kt`
- [x] Add `fun cacheRuleId(desc: RawRule, id: RuleId)` to `IRuleFactoryHelper` in `IRuleRegistry.kt`
- [x] Update `TestRuleFactoryHelper` in `RuleFactoryTest.kt` — add `IdentityHashMap<RawRule, RuleId>` and implement both methods
- [x] Implement cache methods in `Grammar.kt` — add `private val _rawRuleIdCache = IdentityHashMap<RawRule, RuleId>()`, implement `getCachedRuleId` and `cacheRuleId`
- [x] Run `./gradlew :core:test` — existing tests pass (new methods not yet called)

### Task 3: Replace RawRule.id mutation in RuleFactory and remove the field
- [x] In `RuleFactory.getCompiledRuleId()`: replace `desc.id?.let { return RuleId(it) }` with `helper.getCachedRuleId(desc)?.let { return it }`
- [x] In `RuleFactory.getCompiledRuleId()`: replace `desc.id = id.id` with `helper.cacheRuleId(desc, id)`
- [x] In `RuleFactory.resolveRuleIdIfNotInProgress()`: replace `desc.id?.let(::RuleId)` with `helper.getCachedRuleId(desc)` (cycle-detection for cross-grammar includes)
- [x] Remove `public var id: Int? = null` from `RawRule`
- [x] Remove KDoc warning about hash collections from `RawRule`
- [x] Verify TDD test from Task 1 now passes
- [x] Run `./gradlew :core:test` — all tests pass

### Task 4: Remove deepClone machinery
- [x] In `RawGrammar.kt`: remove `RawGrammar.deepClone()`, private `RawRule.deepClone()`, `List.deepCloneRules()`, `Map.deepCloneRuleValues()` helpers, and the KDoc comment
- [x] In `Grammar.kt`: remove `import dev.textmate.grammar.raw.deepClone`
- [x] In `Grammar.kt`: replace `raw.deepClone()` in `getExternalGrammar()` with just `raw`
- [x] In `Grammar.kt`: replace `injectorRaw.deepClone()` in injection handling with just `injectorRaw`
- [x] Write test: `shared RawGrammar is not cloned when loaded by multiple grammars` — verify same object identity (`assertSame`) across Grammar instances
- [x] Run `./gradlew :core:test` — all tests pass

### Task 5: Verify acceptance criteria
- [ ] Verify `RawRule.id` field is removed
- [ ] Verify `IRuleFactoryHelper` has `getCachedRuleId` / `cacheRuleId`
- [ ] Verify `Grammar` stores `IdentityHashMap<RawRule, RuleId>`
- [ ] Verify reproducer test in `RegistryTest` passes
- [ ] Verify `RawRule` KDoc warning about hash collections is removed
- [ ] Run `./gradlew :core:test` — full test suite passes
- [ ] Run `./gradlew build` — full project builds

### Task 6: [Final] Update documentation
- [ ] Update CLAUDE.md if architectural notes about deepClone or RawRule.id need changing
- [ ] Update memory notes if patterns changed

## Technical Details

### Cache replacement

**Before** (shared mutable state on RawRule):
```kotlin
// RuleFactory.getCompiledRuleId()
desc.id?.let { return RuleId(it) }  // reads from RawRule
desc.id = id.id                     // mutates RawRule
```

**After** (per-Grammar external cache):
```kotlin
// RuleFactory.getCompiledRuleId()
helper.getCachedRuleId(desc)?.let { return it }  // reads from Grammar's cache
helper.cacheRuleId(desc, id)                      // writes to Grammar's cache
```

### Why IdentityHashMap
- `RawRule` is a `data class` — `equals()`/`hashCode()` compare all fields
- Two structurally-equal rules at different tree positions must compile independently
- `IdentityHashMap` uses reference equality (`===`), which is correct: each RawRule object in the parsed tree is a distinct compilation target

### Why deepClone removal is safe
- `RawRule.id` was the only mutable field (`var`); all 15 other fields are `val`
- `deepClone()` existed solely to reset `id` to null for cross-grammar isolation
- With `id` removed, `RawRule` is fully immutable — safe to share across Grammar instances
