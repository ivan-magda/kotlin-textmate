# KotlinTextMate

A Kotlin/JVM port of [vscode-textmate](https://github.com/microsoft/vscode-textmate) — TextMate grammar tokenizer for syntax highlighting on Android and JVM.

<!-- TODO: CI, license, Maven Central badges once published -->

<!--![Sample app screenshot](docs/images/sample-app.png) <!-- TODO: capture from emulator -->-->

## Why

There was no standalone TextMate grammar engine for Kotlin or Android.
The closest JVM option, [tm4e](https://github.com/eclipse-tm4e/tm4e), targets Eclipse and requires Java 21.
The only active KMP syntax highlighter, [Highlights](https://github.com/SnipMeDev/Highlights), uses hand-written regex and supports 17 languages.

TextMate grammars — the same format VS Code uses — cover 600+ languages and are actively maintained. KotlinTextMate brings them to the JVM.

## Features

- Loads standard `.tmLanguage.json` grammars — the same files VS Code uses
- VS Code JSON theme support (Dark+, Light+, or any `tokenColors`-based theme)
- Jetpack Compose `CodeBlock` composable with `AnnotatedString` output
- [Joni](https://github.com/jruby/joni) (Java Oniguruma) regex engine with graceful fallback for unsupported patterns
- Line-by-line tokenization with persistent state across lines

## Quick start

```kotlin
// Load a grammar
val rawGrammar = assets.open("grammars/kotlin.tmLanguage.json")
    .use { GrammarReader.readGrammar(it) }
val grammar = Grammar(rawGrammar.scopeName, rawGrammar, JoniOnigLib())

// Load a theme (base + overlay, same as VS Code)
val theme = assets.open("themes/dark_vs.json").use { base ->
    assets.open("themes/dark_plus.json").use { overlay ->
        ThemeReader.readTheme(base, overlay)
    }
}

// Render in Compose
CodeBlock(
    code = sourceCode,
    grammar = grammar,
    theme = theme,
)
```

For custom rendering without `CodeBlock`:

```kotlin
val highlighted = rememberHighlightedCode(code, grammar, theme)
Text(text = highlighted)
```

Or tokenize directly:

```kotlin
var state: StateStack? = null
for (line in code.lines()) {
    val result = grammar.tokenizeLine(line, state)
    state = result.ruleStack
    for (token in result.tokens) {
        val style = theme.match(token.scopes)
        // style.foreground (ARGB), style.fontStyle
    }
}
```

## Project structure

| Module         | Description                                                           |
| -------------- | --------------------------------------------------------------------- |
| **core**       | Grammar parsing, rule compilation, tokenizer, theme engine (pure JVM) |
| **compose-ui** | `CodeBlock` composable, `CodeHighlighter`, `AnnotatedString` bridge   |
| **sample-app** | Android demo app — 3 languages, 2 themes, soft wrap toggle            |
| **benchmark**  | JMH performance benchmarks via kotlinx-benchmark                      |

## Benchmarks

| Grammar    | Lines/sec | ms per 1k lines |
| ---------- | --------- | --------------- |
| Kotlin     | 79,300    | 12.6            |
| JSON       | 457,600   | 2.2             |
| Markdown   | 95,700    | 10.4            |
| JavaScript | 10,300    | 97.1            |

Competitive with [vscode-textmate](https://github.com/microsoft/vscode-textmate) (~5.6–18.3k lines/sec on jQuery) and [syntect](https://github.com/trishume/syntect) (~13k). Details and methodology in [BENCHMARK.md](docs/BENCHMARK.md).

## Acknowledgments

- [vscode-textmate](https://github.com/microsoft/vscode-textmate) — the TypeScript implementation this project ports
- [Joni](https://github.com/jruby/joni) — Java port of the Oniguruma regex engine
- [TextMate](https://macromates.com/) — the original grammar format

## License

[MIT](LICENSE)
