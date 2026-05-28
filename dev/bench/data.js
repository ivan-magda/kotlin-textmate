window.BENCHMARK_DATA = {
  "lastUpdate": 1779998576411,
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
          "id": "05bada79a584938e528f6ad854d712eb0db973a6",
          "message": "Move package-layout detail from CLAUDE.md to ARCHITECTURE.md\n\nShrinks the always-loaded instruction surface: CLAUDE.md drops from\n56 to 42 lines (5.5KB → 2.8KB) by replacing the inline architecture\nsection with a pitch-style pointer. The per-package detail is merged\ninto ARCHITECTURE.md's Module map as \"Core package layout\", avoiding\nduplication with existing key-design-decision sections.",
          "timestamp": "2026-04-16T19:51:24+03:00",
          "tree_id": "3a4ac8a29a6735cb239965564de0c6e2d5d2f56e",
          "url": "https://github.com/ivan-magda/kotlin-textmate/commit/05bada79a584938e528f6ad854d712eb0db973a6"
        },
        "date": 1776358487532,
        "tool": "jmh",
        "benches": [
          {
            "name": "dev.textmate.benchmark.TokenizerBenchmark.tokenizeFile ( {\"grammar\":\"kotlin\"} )",
            "value": 32.36528707028783,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.textmate.benchmark.TokenizerBenchmark.tokenizeFile ( {\"grammar\":\"json\"} )",
            "value": 12.52838108375,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.textmate.benchmark.TokenizerBenchmark.tokenizeFile ( {\"grammar\":\"markdown\"} )",
            "value": 379.64952946666665,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.textmate.benchmark.TokenizerBenchmark.tokenizeFile ( {\"grammar\":\"javascript\"} )",
            "value": 1444.3143193,
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
          "id": "4608d67e652439bd64dcbdc1814b71fb4249667f",
          "message": "Bump version to 0.2.0-SNAPSHOT",
          "timestamp": "2026-05-14T19:22:57+03:00",
          "tree_id": "5ada5ca81f7377d726b68aafbe949b3447ca2883",
          "url": "https://github.com/ivan-magda/kotlin-textmate/commit/4608d67e652439bd64dcbdc1814b71fb4249667f"
        },
        "date": 1778776041716,
        "tool": "jmh",
        "benches": [
          {
            "name": "dev.textmate.benchmark.TokenizerBenchmark.tokenizeFile ( {\"grammar\":\"kotlin\"} )",
            "value": 32.74216741221576,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.textmate.benchmark.TokenizerBenchmark.tokenizeFile ( {\"grammar\":\"json\"} )",
            "value": 12.854794315384614,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.textmate.benchmark.TokenizerBenchmark.tokenizeFile ( {\"grammar\":\"markdown\"} )",
            "value": 398.30426142,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.textmate.benchmark.TokenizerBenchmark.tokenizeFile ( {\"grammar\":\"javascript\"} )",
            "value": 1463.3062834,
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
          "id": "c729827d57d823af7997c3a7e7c337478c668092",
          "message": "Force patched versions of build-time AGP buildscript transitive deps (#34)\n\nResolves 27 Dependabot alerts. All flagged packages (netty, bouncycastle, protobuf, commons-io, commons-compress, jose4j, jdom2) are transitive dependencies of the Android Gradle Plugin's buildscript classpath (bundletool/apksig/sdklib/analytics-grpc) — build tooling only, not shipped in the published core/compose-ui artifacts or the app runtime.\n\nPin them to patched versions via buildscript classpath resolutionStrategy.force:\n- io.netty:* (11 modules) 4.1.93.Final -> 4.1.132.Final\n- org.bouncycastle:bc{prov,pkix,util}-jdk18on 1.77 -> 1.84\n- com.google.protobuf:protobuf-java{,-util} 3.22.3 -> 3.25.5\n- commons-io:commons-io -> 2.15.1 (>=2.14.0 patch floor; highest already on classpath)\n- org.apache.commons:commons-compress 1.21 -> 1.26.0\n- org.bitbucket.b_c:jose4j 0.9.5 -> 0.9.6\n- org.jdom:jdom2 2.0.6 -> 2.0.6.1\n\n./gradlew build (unit tests + Android assemble) passes; buildEnvironment confirms every flagged module resolves to its patched version.",
          "timestamp": "2026-05-28T22:59:19+03:00",
          "tree_id": "dcc969e80f0ed96ea8a2aac60565e5e92f239f46",
          "url": "https://github.com/ivan-magda/kotlin-textmate/commit/c729827d57d823af7997c3a7e7c337478c668092"
        },
        "date": 1779998575764,
        "tool": "jmh",
        "benches": [
          {
            "name": "dev.textmate.benchmark.TokenizerBenchmark.tokenizeFile ( {\"grammar\":\"kotlin\"} )",
            "value": 32.253007061187915,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.textmate.benchmark.TokenizerBenchmark.tokenizeFile ( {\"grammar\":\"json\"} )",
            "value": 12.145240534774931,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.textmate.benchmark.TokenizerBenchmark.tokenizeFile ( {\"grammar\":\"markdown\"} )",
            "value": 439.63217492,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "dev.textmate.benchmark.TokenizerBenchmark.tokenizeFile ( {\"grammar\":\"javascript\"} )",
            "value": 1582.4230711999999,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          }
        ]
      }
    ]
  }
}