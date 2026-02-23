window.BENCHMARK_DATA = {
  "lastUpdate": 1771878639094,
  "repoUrl": "https://github.com/ivan-magda/kotlin-textmate",
  "entries": {
    "KotlinTextMate Benchmark": [
      {
        "commit": {
          "author": {
            "email": "imagda15@gmail.com",
            "name": "Ivan Magda",
            "username": "ivan-magda"
          },
          "committer": {
            "email": "imagda15@gmail.com",
            "name": "Ivan Magda",
            "username": "ivan-magda"
          },
          "distinct": true,
          "id": "5c63ed834255e2c31691d8d6a3e33a29488b2114",
          "message": "Skip gh-pages fetch on main when branch does not exist yet\n\nThe github-action-benchmark action crashes fetching a non-existent\ngh-pages branch. Use skip-fetch-gh-pages dynamically based on the\nbaseline existence check so the first run creates the branch from\nscratch.",
          "timestamp": "2026-02-23T23:24:07+03:00",
          "tree_id": "4c8647799b907830631ac6a1b8c210416eb9a405",
          "url": "https://github.com/ivan-magda/kotlin-textmate/commit/5c63ed834255e2c31691d8d6a3e33a29488b2114"
        },
        "date": 1771878638487,
        "tool": "jmh",
        "benches": [
          {
            "name": "dev.textmate.benchmark.TokenizerBenchmark.tokenizeFile ( {\"grammar\":\"kotlin\"} )",
            "value": 31.960252811767152,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.textmate.benchmark.TokenizerBenchmark.tokenizeFile ( {\"grammar\":\"json\"} )",
            "value": 12.549007713419275,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.textmate.benchmark.TokenizerBenchmark.tokenizeFile ( {\"grammar\":\"markdown\"} )",
            "value": 384.7434989333333,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.textmate.benchmark.TokenizerBenchmark.tokenizeFile ( {\"grammar\":\"javascript\"} )",
            "value": 1432.2225104,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          }
        ]
      }
    ]
  }
}