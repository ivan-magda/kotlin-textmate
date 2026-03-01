window.BENCHMARK_DATA = {
  "lastUpdate": 1772386802880,
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
      },
      {
        "commit": {
          "author": {
            "email": "imagda15@gmail.com",
            "name": "Ivan Magda",
            "username": "ivan-magda"
          },
          "committer": {
            "email": "noreply@github.com",
            "name": "GitHub",
            "username": "web-flow"
          },
          "distinct": true,
          "id": "71e59046099b94edff6cdc5ba4de4dcdb01a91d1",
          "message": "Make RawRule fully immutable with per-Grammar rule ID caching (#33)\n\n* Update .gitignore\n\n* feat: add failing TDD test for RawRule.id cross-grammar pollution\n\nAdds test proving that two Grammar instances sharing the same RawGrammar\nobject (without deepClone) fail because RuleFactory mutates RawRule.id\non shared objects, causing Grammar-B to get stale rule IDs from Grammar-A.\n\n* feat: add getCachedRuleId/cacheRuleId to IRuleFactoryHelper\n\nAdd per-Grammar RawRule-to-RuleId cache methods to IRuleFactoryHelper\ninterface, with IdentityHashMap-based implementations in Grammar and\nTestRuleFactoryHelper. Methods are not yet called — wiring happens in\nthe next task.\n\n* feat: replace RawRule.id mutation with per-Grammar cache in RuleFactory\n\nRuleFactory now uses helper.getCachedRuleId/cacheRuleId instead of\nreading/writing desc.id directly. The mutable RawRule.id field is\nremoved, making RawRule fully immutable and safe to share across\nGrammar instances without deepClone.\n\n* feat: remove deepClone machinery now that RawRule is immutable\n\nWith RawRule.id removed and rule ID caching moved to per-Grammar\nIdentityHashMap, RawRule is fully immutable and safe to share across\nGrammar instances without cloning.\n\n* feat: verify acceptance criteria and fix detekt issues\n\n* feat: update documentation for RawRule immutability refactoring\n\nUpdate CLAUDE.md grammar/raw and grammar/rule descriptions to reflect\nthat RawRule is fully immutable and rule IDs are cached per-Grammar\nvia IRuleFactoryHelper.\n\n* fix: address code review findings\n\n- Update stale test comments in RegistryTest that referenced removed\n  RawRule.id mutation; now describe per-Grammar IdentityHashMap caching\n- Rename misleading test 'shared RawGrammar is not cloned' to accurately\n  reflect what it verifies (lookup invocation behavior)\n- Add comment on _rawRuleIdCache explaining why IdentityHashMap is required\n- Update ARCHITECTURE.md: replace stale 'Mutable id on RawRule' section\n  with 'Per-Grammar rule ID caching'; move completed retrospective item\n  to 'What works well'\n- Fix detekt SpacingBetweenDeclarationsWithComments violation\n\n* move completed plan: 2026-03-01-rawrule-id-immutability.md\n\n* Use IdentityHashMap for raw rule cache\n\n* Rename cacheRuleId to setCachedRuleId\n\nRename parameter 'desc' to 'rawRule' in IRuleFactoryHelper and\nimplementations for clarity. Update all call sites (Grammar,\nRuleFactory)\nand tests.\n\n* Delete 2026-03-01-rawrule-id-immutability.md",
          "timestamp": "2026-03-01T20:15:44+03:00",
          "tree_id": "89c6e11d8cf017b7c5a0f28f205b888b1ba00595",
          "url": "https://github.com/ivan-magda/kotlin-textmate/commit/71e59046099b94edff6cdc5ba4de4dcdb01a91d1"
        },
        "date": 1772385490832,
        "tool": "jmh",
        "benches": [
          {
            "name": "dev.textmate.benchmark.TokenizerBenchmark.tokenizeFile ( {\"grammar\":\"kotlin\"} )",
            "value": 31.78615200048963,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.textmate.benchmark.TokenizerBenchmark.tokenizeFile ( {\"grammar\":\"json\"} )",
            "value": 12.541541678128883,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.textmate.benchmark.TokenizerBenchmark.tokenizeFile ( {\"grammar\":\"markdown\"} )",
            "value": 410.9780928800001,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.textmate.benchmark.TokenizerBenchmark.tokenizeFile ( {\"grammar\":\"javascript\"} )",
            "value": 1453.4293224,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          }
        ]
      },
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
          "id": "daafccd09a6ef22495e82eff9a1f445ecf65ab59",
          "message": "Merge branch 'main' of https://github.com/ivan-magda/kotlin-textmate",
          "timestamp": "2026-03-01T20:37:24+03:00",
          "tree_id": "89c6e11d8cf017b7c5a0f28f205b888b1ba00595",
          "url": "https://github.com/ivan-magda/kotlin-textmate/commit/daafccd09a6ef22495e82eff9a1f445ecf65ab59"
        },
        "date": 1772386802414,
        "tool": "jmh",
        "benches": [
          {
            "name": "dev.textmate.benchmark.TokenizerBenchmark.tokenizeFile ( {\"grammar\":\"kotlin\"} )",
            "value": 32.500244101480696,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.textmate.benchmark.TokenizerBenchmark.tokenizeFile ( {\"grammar\":\"json\"} )",
            "value": 12.795695046823452,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.textmate.benchmark.TokenizerBenchmark.tokenizeFile ( {\"grammar\":\"markdown\"} )",
            "value": 392.73723266666667,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.textmate.benchmark.TokenizerBenchmark.tokenizeFile ( {\"grammar\":\"javascript\"} )",
            "value": 1436.2108266999999,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          }
        ]
      }
    ]
  }
}