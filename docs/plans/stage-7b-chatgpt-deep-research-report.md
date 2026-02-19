# Stage 7b Performance Benchmarking for kotlin-textmate

I’ll answer as a world-famous JVM performance engineer (PhD, microbenchmarking & language tooling) with the ACM SIGPLAN PLDI Most Influential Paper Award.

**TL;DR**: Your internal PoC plan already defines a practical baseline target—~1000 lines tokenized in under 100 ms (≈10k lines/sec) on a “mid Android device.” fileciteturn7file0L536-L538 A solid Stage 7b benchmark should: (a) separate “one-time” costs (grammar/theme load + compilation) from “steady-state” tokenization throughput, (b) measure line-by-line tokenization with state carried across lines (the editor-realistic mode), and (c) report wall-clock plus allocations/GC cost (because token objects and scope stacks can dominate in a JVM port). The best-supported JVM tooling path today is either the Gradle **me.champeau.jmh** plugin for raw JMH on JVM citeturn41search0 or **kotlinx-benchmark** (JMH-backed) for Kotlin projects, where the “Kotlin classes are final” pitfall is explicitly addressed via `allopen`. citeturn41search4turn41search5

## Scope and objectives

Stage 7 in your PoC plan explicitly treats performance as a validation deliverable: measure tokenization time for roughly 1000 lines of Kotlin and aim for <100 ms on a typical Android device. fileciteturn7file0L529-L538 That same threshold also appears as an explicit PoC success criterion (“Время токенизации 1000 строк < 100ms”), reinforcing that **~10k lines/sec** is the project’s minimum “good enough” reference point for Stage 7b. fileciteturn7file0L569-L576

Stage 7b, as framed in the repo issue you referenced, is about going beyond that single baseline into a benchmark suite that is:
- **Comparable** to the upstream reference (vscode-textmate) for apples-to-apples interpretation.
- **Comparable across competitors** (tm4e, syntect, and tree-sitter context).
- **Structured for JVM reality** (JIT warmup, allocations, GC).
- **Small and stable enough** to avoid over-engineering while still catching regressions.

## vscode-textmate benchmarking reference

### What we can confirm from primary artifacts
Even without relying on editor UI behavior, vscode-textmate’s public API confirms the core unit of work that a benchmark must exercise: tokenization is fundamentally *line-based*, and correct results require passing a state stack (“ruleStack”) from one line to the next. citeturn23search5turn16search0

A key performance-relevant detail is that vscode-textmate exposes two tokenization APIs:
- `tokenizeLine(...)` returning object-style tokens (`IToken[]`).
- `tokenizeLine2(...)` returning a compact binary representation (`Uint32Array`). citeturn23search5turn28search2

This is an important “apples-to-apples” design constraint for kotlin-textmate benchmarking: if your Kotlin port’s public/high-level API resembles `tokenizeLine` (object tokens), you may still want a second internal/low-allocation mode analogous to `tokenizeLine2`, *because upstream explicitly offers a more allocation-efficient format.* citeturn23search5turn28search2

### What the upstream repository itself says about running benchmarks
The upstream project’s README explicitly documents that benchmarks are part of development workflow (“Run benchmark with `npm run benchmark`”). citeturn16search0turn32search0 This gives a firm anchor that “their benchmark” is intended as an operational reference, even if the exact corpus/grammars used by `benchmark/benchmark.js` must be verified by inspecting the script itself.

### Limitation in this research
I was not able to retrieve the literal contents of `benchmark/benchmark.js` (and therefore could not directly enumerate **the exact corpus, files, number of lines, or grammar set** used there). This is a hard limitation of the environment’s access mechanism: attempts to open the specific upstream benchmark file and menu-linked pages repeatedly failed due to “cache miss” fetch errors, preventing direct inspection.

Because of that, the only claims made here about “what to measure” are grounded in:
- The upstream *public API contract* (`tokenizeLine`, `tokenizeLine2`, state stack carry) citeturn23search5turn28search2  
- The upstream project’s stated ability to run a benchmark via `npm run benchmark` citeturn16search0turn32search0  
- Externally published timing context for syntax highlighting/tokenization workloads (notably a widely cited benchmark slide comparing related engines). citeturn48view0

If Stage 7b must remain a strict “benchmark.js parity check,” the missing step is: **inspect the benchmark script and replicate its dataset + grammar(s) + measurement harness exactly**.

## Competitive throughput context

### syntect (Rust TextMate-style highlighter)
A frequently referenced public datapoint comes from Tristan Hume’s 2017 talk on syntax highlighting internals. In a “jQuery benchmark: 9200 lines,” he reports:
- “Syntect takes 680ms, or 13,000 lines per second.”
- A Sublime Text 3 dev build taking 90 ms (same slide).
He also notes broader editor comparisons that include rendering a screen (e.g., Visual Studio Code ~2 seconds, Atom 6 seconds), and explicitly warns those comparisons “aren’t totally fair” except relative to Sublime where theme/syntax are matched. citeturn48view0

This gives you a **sanity check range**: for a complex real-world JS corpus, ~13k lines/sec for a non-JVM engine is presented as achievable for “just syntax highlighting” (tokenization), while full editor latency can be far worse once rendering, incremental edits, and UI threading are involved. citeturn48view0

### tm4e (Eclipse Java TextMate engine)
The most directly relevant “competitor on JVM” is tm4e, described as an Eclipse ecosystem TextMate engine and broadly characterized as a Java port of the vscode-textmate approach. Public sources found in this research describe tm4e’s purpose and integration (TextMate-compatible tokenization for Eclipse-based tooling), but I did **not** find an authoritative, published tm4e throughput benchmark (e.g., lines/sec or ms/1000 lines) in the sources available here. citeturn0search9turn0search10

Given your goal (“what’s a good number for TextMate tokenization on JVM?”), tm4e’s lack of easily discoverable published benchmarks matters: it suggests you should **not assume** a canonical JVM baseline exists publicly; your own JMH numbers will likely become the more actionable reference for your project.

### tree-sitter (context, not TextMate)
Tree-sitter is often compared in syntax highlighting discussions, but it is architecturally different: it’s an incremental parsing library building concrete syntax trees, and the official docs position it as “fast enough to parse on every keystroke.” citeturn47search2

In the sources surfaced here, I did not find a primary/official “lines/sec” figure on the tree-sitter site itself; it emphasizes *keystroke-level* incremental performance rather than a single throughput benchmark number. citeturn47search2

### Framing “good numbers” for kotlin-textmate Stage 7b
You have two grounded numeric anchors:

- **Project-defined baseline**: <100 ms per ~1000 lines on mid Android hardware (≈10k lines/sec). fileciteturn7file0L536-L538  
- **Public non-JVM context**: ~13k lines/sec in a “9200-line jQuery” tokenization-focused benchmark for syntect. citeturn48view0  

A pragmatic interpretation is that your PoC success criterion is already in the ballpark of a public “fast highlighter” datapoint, despite Kotlin-on-Android constraints. That’s a reasonable indication that the target is *not* absurdly conservative.

## Benchmark harness design for Kotlin tokenization

### Corpus selection for “realistic, comparable, not over-engineered”
Your PoC plan already recommends a canonical fixture source for correctness validation: use `.txt` fixtures from `vscode-textmate/test-cases/`. fileciteturn7file0L531-L534 Reusing that same fixture set for performance benchmarking has two advantages:
- You already depend on it conceptually for conformance, so it’s in-scope and familiar. fileciteturn7file0L531-L534  
- It increases the chance that your “time per 1000 lines” is comparable across implementations and regression-resistant.

A minimal but robust corpus strategy for Stage 7b would be:
- One Kotlin-like file with multi-line constructs (strings/comments) to stress state carry.
- One “regex heavy” grammar file (often JS/TS) if available in fixtures, to stress pattern matching.
- One structured language (JSON) to represent “easy path.”
This aligns with your earlier PoC focus on Kotlin/JSON/Markdown coverage and stateful multi-line behavior. fileciteturn7file0L531-L538

### What to measure in the tokenizer, specifically
Given vscode-textmate’s API and editor usage patterns, the benchmark should isolate at least two modes:

**Steady-state tokenization throughput**
- Pre-load and pre-compile grammar(s) once per trial.
- Run a full-file tokenization loop, line-by-line, passing the returned state stack into the next call. This mirrors the “tokenize multiple lines” contract and matches how editors do incremental highlighting. citeturn23search5turn28search2

**Token format variants**
- Measure your “object token” output path (analogous to `tokenizeLine`). citeturn23search5turn28search2  
- If kotlin-textmate has or can provide a compact token encoding, measure that too (analogous to `tokenizeLine2`), because upstream explicitly offers a low-allocation format and many real-world throughput differences are allocation-driven on JVM. citeturn23search5turn28search2

**One-time costs vs steady-state**
Even if Stage 7b focuses on throughput, it’s worth separating:
- Grammar parsing + compilation (one-time) vs.
- Tokenization (repeated).

This separation matters because editors pay those costs at different times: load/compile at startup or language activation, tokenization continuously.

### Why caching and incremental behavior matter (and how it relates to performance design)
Your PoC “next steps” explicitly call out performance levers like “OnigScanner caching” and “incremental tokenization.” fileciteturn7file0L583-L588 This is consistent with known practical profiles of regex-based tokenizers: time is often dominated by regex execution and by cost of repeatedly constructing scanners/pattern lists (depending on engine design). citeturn48view0

For Stage 7b, that suggests two benchmark cases are enough to avoid over-engineering while still measuring the work that future optimizations will target:
- A “no caching” baseline (worst-case, but establishes headroom).
- A “recommended caching enabled” case (best-case realistic configuration).

## JMH in a Kotlin/Gradle project

### Two viable integration paths
**Raw JMH via Gradle (JVM-first)**
The Gradle Plugin Portal describes **`me.champeau.jmh`** as a plugin that integrates JMH with Gradle and provides conventions for setting up and executing microbenchmarks. citeturn41search0 This is the most direct path if:
- You are JVM-only (or JVM + Android with a JVM benchmark module),
- You want full, explicit control over JMH options, forks, profilers, and result formats.

**kotlinx-benchmark (Kotlin-centric, multi-platform capable)**
The Kotlin team’s **kotlinx-benchmark** is explicitly a toolkit for running benchmarks across Kotlin targets and states it uses JMH “under the hood” on JVM. citeturn41search4turn41search5 It also documents a key Kotlin pitfall: because Kotlin classes are `final` by default, JMH’s subclass generation requires benchmark classes/methods to be `open`, and it recommends solving that via `allopen` on the `@State` annotation. citeturn41search4turn41search5

### Kotlin-specific pitfalls that matter for tokenizer benchmarks
The “final by default” constraint is not academic—tokenizer benchmarks typically carry state (compiled grammar, arrays of lines, reusable buffers). That means you will almost certainly use `@State`, so you must ensure your benchmark state class is compatible with JMH’s codegen. citeturn41search4turn41search5

### Recommended JMH structure for a tokenizer-style benchmark
The most “load-bearing” JMH design points for tokenizer benchmarks are well captured by Oracle’s guidance on benchmarking pitfalls and JMH anatomy:
- Use `@State` (often `Scope.Thread` or `Scope.Benchmark`) to hold stable benchmark state.
- Use `@BenchmarkMode(Mode.Throughput)` (or AverageTime/SampleTime depending on reporting goals).
- Use `@Fork` to reduce JVM warmup/JIT variance.
- Use JMH idioms (like Blackholes) to avoid dead-code elimination where relevant. citeturn44search3turn44search4

For tokenizers specifically, it is usually best to:
- Put “compile grammar” in `@Setup(Level.Trial)` (or outside the benchmark) and benchmark only tokenization.
- Keep the input corpus pre-split into lines in state, so you benchmark tokenization instead of string splitting.
- In the benchmark method, iterate through all lines, maintaining a local `stateStack` variable that gets updated each line—this mirrors the real algorithmic dependency structure. citeturn23search5turn28search2

### Which to choose: raw JMH vs kotlinx-benchmark
A practical decision rule:

- Choose **raw JMH (+ me.champeau.jmh)** if Stage 7b is strictly JVM benchmarking with a need to match upstream methodology closely and to use more advanced profilers/flags, because this is the most direct JMH integration path in Gradle. citeturn41search0turn44search4  
- Choose **kotlinx-benchmark** if you want a Kotlin-first workflow (and possibly future KMP reuse), and you value its explicit Kotlin ergonomics (notably the documented `allopen` solution for JMH compatibility). citeturn41search4turn41search5  

## Metrics beyond wall-clock time

### Allocation and GC pressure are usually worth measuring for tokenizers
For a tokenizer, allocations matter because:
- Token objects (and scope lists) can create high allocation rates.
- State stacks and intermediate strings can amplify GC cost, especially on Android-like heaps.

JMH supports measuring this without external profilers using the built-in **GC profiler** (`-prof gc`), which reports metrics such as:
- `gc.alloc.rate` (MB/sec),
- `gc.alloc.rate.norm` (bytes/op),
- plus GC count/time and memory churn metrics. citeturn41search11turn44search2

This is particularly relevant given upstream’s dual output mode (`tokenizeLine` vs `tokenizeLine2`), since offering a low-allocation token encoding is a known strategy for improving throughput predictably on the JVM. citeturn23search5turn28search2

### Other metrics syntax-highlighting benchmarks commonly care about
Beyond raw throughput, “real editor” performance tends to be gated by tail latencies and incremental behavior:

- **Latency per “typical edit”**: Editors want “fast enough to parse on every keystroke,” especially in incremental systems like tree-sitter. citeturn47search2 While TextMate tokenizers are not parse-tree-based, you still care about “how much work must happen after an edit,” which is why state carries across lines and why incremental re-tokenization windows (e.g., visible lines) matter in editor practice. citeturn23search5turn48view0  
- **Timeout / worst-case protection**: vscode-textmate surfaces a `stoppedEarly` flag in both tokenization result types, reflecting that pathological grammars or regex backtracking can require cutoff behavior. citeturn23search5turn28search2 If kotlin-textmate implements similar safeguards, benchmarking should include a “pathological grammar/case” scenario or at least track whether early-stop triggers occur under benchmark workloads.

### Putting it together without over-engineering
A Stage 7b benchmark suite that is “just enough” (and still useful) typically reports:

- One **throughput** metric: e.g., ms per 1000 lines (matching your PoC success criterion) fileciteturn7file0L536-L538  
- One **allocation** metric: bytes/op and/or MB/sec via `-prof gc` citeturn41search11turn44search2  
- Optional but valuable: a second throughput line for a “binary/compact token” path if you have one (mirroring upstream `tokenizeLine2`). citeturn23search5turn28search2  

Within those constraints, you get a benchmark that is (1) aligned with your existing PoC target, (2) compatible with how the upstream API is designed to be used, and (3) capable of catching the two most common JVM regressions in tokenizers: slower regex execution and higher allocation/GC pressure. fileciteturn7file0L536-L538