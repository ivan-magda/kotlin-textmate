#!/usr/bin/env python3
"""
IQR-based benchmark regression detector for KotlinTextMate.

Reads the data.js file produced by github-action-benchmark, analyzes the most
recent data point against a rolling window of historical results, and exits
with code 1 if a statistically significant regression is detected.

Algorithm (for avgt mode — lower is better, regression = value increased):
  1. Extract the last WINDOW_SIZE measurements per benchmark.
  2. Current value = last entry; baseline = the rest.
  3. Remove outliers from baseline outside [Q1 - 1.5*IQR, Q3 + 1.5*IQR].
  4. Compute baseline median.
  5. If current > baseline_median * (1 + THRESHOLD), flag as regression.
  6. Skip benchmarks with fewer than MIN_HISTORY data points.

Usage:
  python check_regression.py <path-to-data.js> <suite-name>

Exit codes:
  0 — all benchmarks within acceptable range (or insufficient history)
  1 — regression detected in one or more benchmarks
  2 — usage error
"""

import json
import os
import statistics
import sys

WINDOW_SIZE = 20
THRESHOLD = 0.20
MIN_HISTORY = 10

DATA_JS_PREFIX = "window.BENCHMARK_DATA = "


def load_data_js(path: str) -> dict:
    with open(path, encoding="utf-8") as f:
        content = f.read()
    if content.startswith(DATA_JS_PREFIX):
        content = content[len(DATA_JS_PREFIX) :]
    return json.loads(content)


def extract_history(entries: list[dict]) -> dict[str, list[float]]:
    history: dict[str, list[float]] = {}
    for entry in entries:
        for bench in entry.get("benches", []):
            history.setdefault(bench["name"], []).append(bench["value"])
    return history


def iqr_filter(values: list[float]) -> list[float]:
    if len(values) < 4:
        return list(values)
    s = sorted(values)
    n = len(s)
    q1 = statistics.median(s[: n // 2])
    q3 = statistics.median(s[(n + 1) // 2 :])
    iqr = q3 - q1
    lo = q1 - 1.5 * iqr
    hi = q3 + 1.5 * iqr
    return [v for v in values if lo <= v <= hi]


def check_regressions(
    history: dict[str, list[float]],
) -> tuple[list[dict], list[dict]]:
    results = []
    regressions = []

    for name, all_values in sorted(history.items()):
        window = all_values[-WINDOW_SIZE:]
        current = window[-1]

        if len(window) < MIN_HISTORY:
            results.append(
                {
                    "name": name,
                    "current": current,
                    "baseline": None,
                    "ratio": None,
                    "status": "SKIP",
                    "reason": f"only {len(window)} data points (need {MIN_HISTORY})",
                }
            )
            continue

        baseline_window = window[:-1]
        cleaned = iqr_filter(baseline_window)
        if not cleaned:
            cleaned = list(baseline_window)

        baseline = statistics.median(cleaned)
        if baseline <= 0:
            results.append(
                {
                    "name": name,
                    "current": current,
                    "baseline": baseline,
                    "ratio": None,
                    "status": "SKIP",
                    "reason": "baseline median is zero or negative",
                }
            )
            continue

        ratio = current / baseline
        is_regression = ratio > 1.0 + THRESHOLD

        entry = {
            "name": name,
            "current": current,
            "baseline": baseline,
            "ratio": ratio,
            "status": "REGRESSION" if is_regression else "OK",
        }
        results.append(entry)
        if is_regression:
            regressions.append(entry)

    return results, regressions


def write_summary(results: list[dict], regressions: list[dict]) -> None:
    summary_path = os.environ.get("GITHUB_STEP_SUMMARY")
    if not summary_path:
        return

    lines = ["## Benchmark Regression Check", ""]

    if regressions:
        lines.append(f"**{len(regressions)} regression(s) detected.**")
    else:
        lines.append("All benchmarks within acceptable range.")

    lines.extend(
        [
            "",
            "| Benchmark | Current (ms/op) | Baseline (ms/op) | Ratio | Status |",
            "|-----------|----------------:|------------------:|------:|--------|",
        ]
    )

    for r in results:
        name = r["name"].split(".")[-1]
        current_str = f"{r['current']:.2f}"
        if r["baseline"] is not None:
            baseline_str = f"{r['baseline']:.2f}"
        else:
            baseline_str = "—"
        if r["ratio"] is not None:
            ratio_str = f"{r['ratio']:.2f}x"
        else:
            ratio_str = "—"
        status = r["status"]
        if "reason" in r:
            status = f"{status} ({r['reason']})"
        lines.append(
            f"| {name} | {current_str} | {baseline_str} | {ratio_str} | {status} |"
        )

    lines.extend(
        [
            "",
            f"Window: {WINDOW_SIZE} runs, threshold: {THRESHOLD * 100:.0f}%, "
            f"min history: {MIN_HISTORY}",
        ]
    )

    with open(summary_path, "a", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")


def main() -> None:
    if len(sys.argv) != 3:
        print(f"Usage: {sys.argv[0]} <data.js path> <suite name>", file=sys.stderr)
        sys.exit(2)

    data_js_path = sys.argv[1]
    suite_name = sys.argv[2]

    print(f"Loading benchmark data from {data_js_path}")
    data = load_data_js(data_js_path)

    entries_map = data.get("entries", {})
    if suite_name not in entries_map:
        available = list(entries_map.keys())
        print(f"Suite '{suite_name}' not found. Available: {available}")
        print("No history yet — skipping regression check.")
        sys.exit(0)

    entries = entries_map[suite_name]
    print(f"Found {len(entries)} historical entries for '{suite_name}'")

    history = extract_history(entries)
    print(
        f"Checking {len(history)} benchmarks "
        f"(window={WINDOW_SIZE}, threshold={THRESHOLD * 100:.0f}%, "
        f"min_history={MIN_HISTORY}):\n"
    )

    results, regressions = check_regressions(history)

    for r in results:
        if r["status"] == "SKIP":
            print(f"  SKIP  {r['name']}: {r.get('reason', '')}")
        else:
            print(
                f"  {r['status']:4s}  {r['name']}: "
                f"current={r['current']:.4f} baseline={r['baseline']:.4f} "
                f"ratio={r['ratio']:.2f}x"
            )

    write_summary(results, regressions)

    if regressions:
        print(f"\nREGRESSION DETECTED in {len(regressions)} benchmark(s):")
        for r in regressions:
            print(
                f"  {r['name']}: {r['current']:.4f}ms vs "
                f"baseline {r['baseline']:.4f}ms "
                f"({r['ratio']:.2f}x, >{1.0 + THRESHOLD:.2f}x threshold)"
            )
        sys.exit(1)
    else:
        print("\nAll benchmarks within acceptable range.")
        sys.exit(0)


if __name__ == "__main__":
    main()
