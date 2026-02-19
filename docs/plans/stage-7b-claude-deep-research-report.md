# Benchmarking a Kotlin TextMate tokenizer: the complete playbook

**The canonical reference point for TextMate tokenization performance is roughly 5,000–18,000 lines/second** on the vscode-textmate reference implementation, with regex matching consuming the vast majority of CPU time. No JVM TextMate implementation has published formal benchmarks — making any systematic measurement for kotlin-textmate genuinely novel. The recommended setup is **kotlinx-benchmark 0.4.16** for day-to-day KMP benchmarks backed by JMH on JVM, supplemented by raw JMH with `-prof gc` and `-prof async` for deep optimization passes.

This report synthesizes benchmark data from vscode-textmate, syntect, and tree-sitter; compares JMH integration approaches for Kotlin; and provides a concrete, phased benchmark design that avoids over-engineering while capturing the metrics that actually matter.

---

## What the vscode-textmate benchmark actually measures

The benchmark lives at `benchmark/benchmark.js` and runs via `npm run benchmark`. It tokenizes real-world source files line-by-line using the vscode-textmate engine with `vscode-oniguruma` (a WASM-packaged Oniguruma regex library). Grammar loading and WASM initialization happen before timing begins — only the `tokenizeLine()` / `tokenizeLine2()` calls are measured.

The corpus in the `benchmark/` directory includes **large.js.txt** (jQuery source, ~9,000+ lines), **minified.js.txt** (few very long lines), **bootstrap.css** (~6,000+ lines), and **bootstrap.min.css** (~118 KB across just 12 lines). Grammars are `JavaScript.tmLanguage.json` and likely a CSS grammar. The benchmark passes `ruleStack` state between lines starting from `vsctm.INITIAL`, matching exactly how an editor would tokenize a buffer.

The most authoritative absolute numbers come from **Alexandru Dima's February 2017 VS Code blog post** on syntax highlighting optimizations, which reported median-of-10-runs timings for the full VS Code tokenization pipeline (including theme matching):

| File | Size | Lines | Time (VS Code 1.9) | Derived throughput |
|------|------|-------|--------------------|--------------------|
| `checker.ts` (TypeScript) | 1.18 MB | 22,253 | **3,939 ms** | ~5,650 lines/s, ~300 KB/s |
| `bootstrap.min.css` | 118 KB | 12 | **416 ms** | ~284 KB/s (lines/s misleading here) |
| `sqlite3.c` (C) | 6.73 MB | 200,904 | **10,964 ms** | ~18,300 lines/s, ~614 KB/s |

These numbers include theme matching in the tokenization pass (VS Code 1.9's single-pass design), so raw `tokenizeLine2()` without theme resolution would be somewhat faster. The key optimization that drove the 1.8→1.9 improvement was switching from object-based token representations to binary-encoded **Uint32Array** — cutting per-line memory from **648 bytes to 96 bytes** for a representative line in Chrome, directly addressing GC pressure.

The benchmark structure is simple: no formal warm-up passes or iteration framework. It reads files, starts a `performance.now()` timer, loops over all lines calling `tokenizeLine()` with state chaining, and reports elapsed time. It is a regression-tracking tool, not a statistical benchmarking harness.

---

## How competing implementations perform

**No JVM TextMate implementation has published benchmarks.** This is the single most important context for the kotlin-textmate project: any systematic measurement establishes the JVM baseline.

**syntect** (Rust, Oniguruma-based) is the best-documented alternative. Tristan Hume's talk "How Your Text Editor Does Syntax Highlighting" provides the most useful cross-implementation comparison using a large ES6 JavaScript file:

| Implementation | Time for large ES6 file | Estimated throughput |
|---------------|------------------------|---------------------|
| Sublime Text 3 (custom DFA engine) | **~90 ms** | ~100,000+ lines/s |
| syntect (Rust + Oniguruma) | **~680 ms** | ~13,000 lines/s |
| VS Code / TextMate 2 / Spacemacs | **~2,000 ms** | ~4,500 lines/s |
| Atom (first-mate) | **~6,000 ms** | ~1,500 lines/s |

Syntect's own Criterion benchmarks show **~1.9 ms** to parse a 30-line, 791-byte ERB file, and it documents several key optimizations: pre-linked cross-language references, compact binary scope representation, bit manipulation for scope prefix matching, regex match caching, and lazy regex compilation. The project explicitly notes that **most time is spent inside the Oniguruma regex engine** — Sublime Text's ~7.5× advantage comes entirely from its custom multi-pattern DFA regex engine.

**tm4e** (Eclipse's Java port) has **no benchmarks, no JMH setup, and no published numbers.** Recent PRs by @sebthom show active performance work (avoiding redundant `getLineLength()` calls, minor TMModel optimizations), but nothing measured systematically.

**tree-sitter** operates in a fundamentally different performance regime. Initial parsing of a ~5,500-line Scala file takes ~65 ms, and incremental re-parsing after edits typically completes in **<1 ms**. A Symflower report cited a **36× speedup** migrating from JavaParser to tree-sitter for Java. These numbers aren't directly comparable since tree-sitter builds a full concrete syntax tree using generated LR parsers rather than regex-matching line by line, but they provide useful context for editor responsiveness expectations.

**For a JVM TextMate tokenizer, a reasonable performance target is 5,000–15,000 lines/second** for complex grammars (TypeScript, C), with the lower end reflecting JNI/JVM overhead and the upper end achievable with careful optimization. Beating syntect's native-code performance would be exceptional; matching vscode-textmate's JavaScript performance is a strong baseline.

---

## kotlinx-benchmark vs raw JMH for a KMP project

For a Kotlin Multiplatform project where JVM performance is the primary concern, the pragmatic answer is **use both** — kotlinx-benchmark for everyday regression tracking, raw JMH for deep profiling.

**kotlinx-benchmark 0.4.16** (released February 2025) uses JMH under the hood on JVM, generates JMH-compatible benchmark code, and integrates cleanly with KMP's multi-target build. It supports `@Benchmark`, `@State`, `@Setup`, `@TearDown`, `@Param`, warmup/iteration configuration, and fork control via `advanced("jvmForks", "2")`. On non-JVM targets it provides its own runtime, enabling you to benchmark the same tokenizer code on JS and Native if needed. The default JMH version is **1.37**.

The critical limitation: **kotlinx-benchmark cannot invoke JMH profilers** (`-prof gc`, `-prof async`, `-prof perfasm`) through its Gradle plugin. To use profilers, you must either build the JMH JAR and run it manually with profiler flags, or maintain a separate JVM-only benchmark module using the **me.champeau.jmh plugin v0.7.3** (released January 2025).

| Capability | kotlinx-benchmark 0.4.16 | me.champeau.jmh 0.7.3 |
|------------|--------------------------|----------------------|
| KMP support | ✅ JVM, JS, Native, WasmJs | ❌ JVM only |
| JMH profilers (`-prof gc`, `-prof async`) | ❌ Requires manual JAR run | ✅ Native support |
| Warmup/iteration/fork control | ✅ Full on JVM | ✅ Full |
| `@State` scopes | `Scope.Benchmark` cross-platform; full JMH scopes on JVM | ✅ All scopes |
| Setup levels | JVM: Trial/Iteration/Invocation; others: Trial only | ✅ All levels |
| Blackhole | Implicit (return values) | ✅ Explicit parameter |

**Recommended approach:** Place benchmark sources in a dedicated KMP source set using kotlinx-benchmark for CI regression detection (`./gradlew jvmBenchmark`). When you need allocation profiling or flame graphs, run the generated JMH JAR directly: `java -jar build/libs/benchmarks-jmh.jar -prof gc -prof async:output=flamegraph`.

---

## Essential Kotlin-specific pitfalls

**The allopen plugin is mandatory.** Kotlin classes are `final` by default, but JMH generates subclasses of `@State`-annotated classes. Without the allopen compiler plugin targeting `@State`, JMH fails at runtime:

```kotlin
plugins {
    kotlin("plugin.allopen") version "2.1.10"
}
allOpen {
    annotation("org.openjdk.jmh.annotations.State")
}
```

**Use `@JvmField` on state properties.** Without it, JMH accesses state through Kotlin-generated getter methods, introducing overhead that can skew tight-loop measurements. For `lateinit var` properties, `@JvmField` is implied automatically.

**Avoid value classes in benchmark state.** `@JvmInline value class` instances are unboxed by the compiler, and JMH cannot handle them as `@State` fields. `@JvmField` cannot be applied to inline class type properties. If your tokenizer API uses value classes, unwrap them in the benchmark's `@Setup` method.

**JMH does not understand `suspend` functions.** You cannot annotate a `suspend fun` with `@Benchmark`. If any tokenization API is suspending, bridge with `runBlocking {}` inside the benchmark method — but be aware this adds coroutine dispatch overhead to every measured invocation.

**Kotlin null checks add measurable cost in tight loops.** Kotlin inserts `Intrinsics.checkNotNullParameter()` calls for non-nullable parameters. For micro-benchmarks, this is usually fine (it's what production code will execute), but be aware it exists if comparing against Java implementations.

---

## Concrete benchmark structure for a tokenizer

The following structure separates expensive grammar loading (`Level.Trial`) from measured tokenization, uses `Scope.Thread` for thread-safe mutable tokenizer state, and parameterizes across languages:

```kotlin
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Fork(3)
@Warmup(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
open class TokenizerBenchmark {

    @JvmField var grammar: IGrammar = TODO()
    @JvmField var lines: List<String> = emptyList()

    @Param("kotlin", "typescript", "css")
    lateinit var language: String

    @Setup(Level.Trial)
    fun loadGrammar() {
        grammar = registry.loadGrammarSync(language)
        lines = loadCorpusFile(language)
    }

    @Benchmark
    @OperationsPerInvocation(1000) // adjust to actual line count
    fun tokenizeFile(bh: Blackhole) {
        var state = INITIAL_STATE
        for (line in lines) {
            val result = grammar.tokenizeLine(line, state)
            state = result.ruleStack
            bh.consume(result)
        }
    }
}
```

**Key design decisions:** `@Setup(Level.Trial)` runs grammar loading once per fork — grammar parsing is expensive and the result is immutable, so it belongs outside measurement. Use `@Setup(Level.Iteration)` only if you need to reset mutable tokenizer state between iterations. `Mode.AverageTime` is the most intuitive ("this file takes X µs to tokenize"); add `Mode.SampleTime` later if you need latency percentiles. **Three forks minimum** — this is critical for both timing accuracy and GC measurement, as each fork starts a fresh JVM isolating JIT compilation and heap layout effects.

For corpus files, mirror what vscode-textmate uses: a large real-world file per grammar (jQuery for JavaScript, Bootstrap for CSS) plus a minified variant to test long-line performance. Report both **lines/second** and **KB/second** — lines/sec is more intuitive but misleading for minified files with few very long lines.

---

## Memory and GC pressure deserve first-class measurement

**Allocation pressure is the #1 non-obvious performance concern for TextMate tokenizers.** Each `tokenizeLine()` call creates token arrays, state objects (ruleStack), regex match results, scope strings, and intermediate collections. VS Code's own optimization history proves this: switching from object-based tokens to binary `Uint32Array` encoding cut per-line memory from **648 bytes to 96 bytes**, delivering a **14–46% speedup** depending on the file.

JMH's built-in `-prof gc` profiler reports the metric you care about most: **`gc.alloc.rate.norm`** — bytes allocated per benchmark operation. It uses `ThreadMXBean.getThreadAllocatedBytes()`, which is accurate and low-overhead. A typical output looks like:

```
TokenizerBench.tokenizeFile           avgt   5   1553.201 ±   6.199  ns/op
TokenizerBench.tokenizeFile:·gc.alloc.rate.norm  avgt   5   2048.001 ±   0.001   B/op
TokenizerBench.tokenizeFile:·gc.count            avgt   5     18.000             counts
TokenizerBench.tokenizeFile:·gc.time             avgt   5     14.000                ms
```

**Never use `@Fork(0)` for GC profiling** — the harness's own allocations pollute results. Three forks is the minimum for meaningful GC data.

For deeper analysis, **async-profiler** (via `-prof async`) is the most valuable tool for a regex-heavy tokenizer. It avoids JVM safepoint bias, profiles native code (critical if your regex engine uses JNI), and generates allocation flame graphs that show exactly which methods create the most objects:

```bash
java -jar benchmarks.jar \
  -prof gc \
  -prof "async:libPath=/path/to/libasyncProfiler.so;output=flamegraph;dir=flames" \
  TokenizerBench
```

An allocation flame graph for a TextMate tokenizer typically reveals time concentrated in regex matching (Oniguruma scanner), scope resolution, and token array construction — the same hotspots VS Code optimized.

---

## A phased approach that avoids over-engineering

**Phase 1 (day one):** Set up kotlinx-benchmark with `-prof gc`. Measure throughput (lines/sec) and `gc.alloc.rate.norm` (bytes/op) across 2–3 grammars. This captures 80% of actionable data. Run time: ~30 seconds per benchmark method with 3 forks × 10 iterations × 2 seconds.

**Phase 2 (when optimizing):** Add async-profiler flame graphs to identify hot methods and allocation sites. CPU flame graphs show where time goes; allocation flame graphs (`event=alloc`) show where objects are created. This is the investigation tool for specific optimization passes.

**Phase 3 (if editor latency matters):** Add `@BenchmarkMode(Mode.SampleTime)` to capture p99 latency distribution. A single-line tokenization that spikes to >16 ms (one frame at 60fps) causes visible editor stutter. GC pauses are the usual culprit.

**What's overkill:** Hardware counter profiling (`-prof perfnorm`) before you've optimized algorithmic issues. Running across multiple GC algorithms. Assembly-level profiling (`-prof perfasm`). Tracking more than 3 metrics in CI — throughput plus allocation rate is sufficient for regression detection; everything else belongs in investigation sessions.

## Conclusion

The TextMate tokenization landscape has a conspicuous measurement gap: no JVM implementation has published formal benchmarks. The vscode-textmate reference at **~5,600–18,300 lines/sec** (depending on grammar complexity and file characteristics) and syntect at **~13,000 lines/sec** establish the performance envelope. For kotlin-textmate, the most impactful first step is establishing a JMH-backed baseline with `gc.alloc.rate.norm` as the secondary metric — because VS Code's own history demonstrates that allocation pressure, not raw CPU speed, is where TextMate tokenizers leave the most performance on the table. The tooling is mature: kotlinx-benchmark 0.4.16 for KMP-integrated regression tracking, me.champeau.jmh 0.7.3 with async-profiler for deep dives, and the allopen plugin to make it all work with Kotlin's sealed-by-default classes.