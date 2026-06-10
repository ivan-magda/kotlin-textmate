# Third-Party Notices

KotlinTextMate includes and is derived from third-party material. The original
copyright notices and permission notices are retained below, as required by the
respective licenses. KotlinTextMate itself is licensed under the [MIT License](LICENSE).

## Ported source code

### vscode-textmate

KotlinTextMate is a Kotlin port of [vscode-textmate](https://github.com/microsoft/vscode-textmate)
(baseline version 9.3.2 — see [docs/UPSTREAM.md](docs/UPSTREAM.md)). Substantial portions of
this project's source are direct translations of the original TypeScript.

> Copyright (c) Microsoft Corporation
>
> Licensed under the MIT License.

## Bundled grammars (`shared-assets/grammars/`)

These TextMate grammar files are redistributed unmodified except for JSONC-to-JSON
normalization. All are MIT-licensed.

| File | Source | Copyright |
|---|---|---|
| `JSON.tmLanguage.json` | [microsoft/vscode-JSON.tmLanguage](https://github.com/microsoft/vscode-JSON.tmLanguage) | © Microsoft Corporation |
| `JavaScript.tmLanguage.json` | [microsoft/vscode](https://github.com/microsoft/vscode) (TypeScript-TmLanguage) | © Microsoft Corporation |
| `markdown.tmLanguage.json` | [microsoft/vscode-markdown-tm-grammar](https://github.com/microsoft/vscode-markdown-tm-grammar) | © Microsoft Corporation |
| `kotlin.tmLanguage.json` | [mathiasfrohlich/vscode-kotlin](https://github.com/mathiasfrohlich/vscode-kotlin) | © Mathias Fröhlich |

## Bundled themes (`shared-assets/themes/`)

VS Code default color themes, redistributed with JSONC trailing commas stripped.
Sourced from [microsoft/vscode](https://github.com/microsoft/vscode) (`extensions/theme-defaults/themes/`),
MIT-licensed, © Microsoft Corporation.

- `dark_vs.json`, `dark_plus.json`, `light_vs.json`, `light_plus.json`

## Benchmark corpus (`shared-assets/benchmark/`)

- `jquery.js.txt` — [jQuery](https://jquery.com/), MIT License, © OpenJS Foundation and jQuery contributors.

---

The MIT License text for all of the above is identical to the one in [LICENSE](LICENSE);
only the copyright holders differ.
