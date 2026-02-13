# KotlinTextMate — Proof of Concept Plan

## Цель

Портировать core **vscode-textmate** (TypeScript) на Kotlin, используя **Joni** для Oniguruma regex. Результат — работающий tokenizer, который загружает TextMate грамматики (`.tmLanguage.json`) и выдаёт подсвеченный код через Compose `AnnotatedString` на Android.

## Архитектура PoC

```
┌─────────────────────────────────────────────────┐
│              Compose UI (Android)                │
│         CodeBlock / AnnotatedString              │
├─────────────────────────────────────────────────┤
│              Theme Engine                        │
│     .tmTheme / VS Code JSON → ScopeStyle         │
├─────────────────────────────────────────────────┤
│              Tokenizer (core)                    │
│    Grammar loading → line-by-line tokenization   │
├─────────────────────────────────────────────────┤
│              Regex Layer (Joni)                  │
│     OnigRegExp wrapper → Joni Scanner            │
└─────────────────────────────────────────────────┘
```

## Референсный исходник

Репозиторий: https://github.com/microsoft/vscode-textmate

Ключевые файлы для портирования (в `src/`):

| Файл TypeScript | Описание | Приоритет |
|-----------------|----------|-----------|
| `grammar.ts` | Основной Grammar class, правила, скоупы | P0 |
| `rule.ts` | Rule types (MatchRule, BeginEndRule, BeginWhileRule) | P0 |
| `matcher.ts` | Scope matcher (selector → rule matching) | P0 |
| `registry.ts` | Реестр грамматик, загрузка и кеширование | P0 |
| `theme.ts` | Theme parsing, scope → style resolution | P0 |
| `onigLib.ts` | Интерфейс для Oniguruma (заменяем на Joni) | P0 |
| `encodedTokenAttributes.ts` | Битовая упаковка атрибутов токенов | P1 |
| `grammarReader.ts` | Парсинг JSON/PList грамматик | P1 |
| `rawGrammar.ts` | Типы для raw grammar data | P1 |
| `utils.ts` | Утилиты | P2 |
| `debug.ts` | Debug logging | P2 |

---

## Этапы реализации

### Этап 0: Настройка проекта (1 день)

**Задача:** Создать Kotlin-проект с Gradle, подключить зависимости.

1. Создать новый проект:
   ```
   kotlin-textmate/
   ├── build.gradle.kts          (root)
   ├── settings.gradle.kts
   ├── core/                     (Kotlin library — tokenizer)
   │   ├── build.gradle.kts
   │   └── src/main/kotlin/
   │       └── dev/textmate/
   │           ├── grammar/
   │           ├── theme/
   │           ├── regex/
   │           └── registry/
   ├── compose-ui/               (Compose rendering)
   │   ├── build.gradle.kts
   │   └── src/main/kotlin/
   │       └── dev/textmate/compose/
   └── sample-app/               (Android demo app)
       ├── build.gradle.kts
       └── src/main/kotlin/
   ```

2. Настроить Gradle:
   - `core/` — чистый Kotlin/JVM модуль
   - Зависимости core:
     ```kotlin
     dependencies {
         implementation("org.jruby.joni:joni:2.2.1")
         implementation("org.jruby.jcodings:jcodings:1.0.58")
         implementation("com.google.code.gson:gson:2.11.0") // для парсинга JSON грамматик
     }
     ```
   - `compose-ui/` — Android library с Compose
   - `sample-app/` — Android application

3. Минимальные версии:
   - Kotlin: 2.0+
   - JDK: 17
   - Android minSdk: 24
   - Compose BOM: 2024.12+

4. Добавить тестовые ресурсы:
   - Скачать 3 грамматики из https://github.com/microsoft/vscode/tree/main/extensions :
     - `kotlin.tmLanguage.json`
     - `json.tmLanguage.json`
     - `markdown.tmLanguage.json`
   - Скачать 2 темы:
     - Dark+ (VS Code default dark)
     - Light+ (VS Code default light)
   - Положить в `core/src/test/resources/grammars/` и `core/src/test/resources/themes/`

---

### Этап 1: Regex Layer — Joni wrapper (2-3 дня)

**Задача:** Создать обёртку над Joni, совместимую с интерфейсом `IOnigLib` из vscode-textmate.

**Референс:** `src/onigLib.ts` — интерфейсы `IOnigLib`, `IOnigCaptureIndex`, `OnigScanner`, `OnigString`.

1. Создать интерфейсы в `core/src/main/kotlin/dev/textmate/regex/`:

   ```kotlin
   // OnigRegExp.kt
   data class CaptureIndex(
       val start: Int,
       val end: Int,
       val length: Int
   )

   data class MatchResult(
       val index: Int,           // индекс паттерна, который сматчился
       val captureIndices: List<CaptureIndex>
   )

   interface IOnigLib {
       fun createOnigScanner(patterns: List<String>): OnigScanner
       fun createOnigString(str: String): OnigString
   }

   interface OnigScanner {
       fun findNextMatchSync(string: OnigString, startPosition: Int): MatchResult?
   }

   class OnigString(val content: String) {
       val length: Int get() = content.length
   }
   ```

2. Реализовать `JoniOnigLib`:

   ```kotlin
   // JoniOnigLib.kt
   class JoniOnigLib : IOnigLib {
       override fun createOnigScanner(patterns: List<String>): OnigScanner {
           return JoniOnigScanner(patterns)
       }
       override fun createOnigString(str: String): OnigString {
           return OnigString(str)
       }
   }
   ```

3. Реализовать `JoniOnigScanner`:
   - Для каждого паттерна компилировать `org.joni.Regex` с `org.jcodings.specific.UTF8Encoding`
   - `findNextMatchSync` — перебрать все скомпилированные regex, найти первый match с наименьшей start position (или наибольшей длиной при равных позициях)
   - Использовать `Matcher.search()` с указанием `startPosition` (в байтах! Joni работает с byte offsets, нужна конвертация char offset → byte offset для UTF-8)

   **ВАЖНО:** Joni работает с byte offsets, а vscode-textmate оперирует char offsets. Нужна конвертация:
   ```kotlin
   // char offset → byte offset (для передачи в Joni)
   fun charToByteOffset(str: String, charOffset: Int): Int {
       return str.substring(0, charOffset).toByteArray(Charsets.UTF_8).size
   }

   // byte offset → char offset (для результата из Joni)
   fun byteToCharOffset(bytes: ByteArray, byteOffset: Int): Int {
       return String(bytes, 0, byteOffset, Charsets.UTF_8).length
   }
   ```

4. Написать тесты:
   - Простой match: `\\bfun\\b` матчит "fun" в строке "fun main()"
   - Capture groups: `(\\w+)\\s*=\\s*(\\w+)` из "val x = 42"
   - Scanner с несколькими паттернами — проверить что возвращается first match
   - Unicode строки — проверить корректность byte/char конвертации
   - Edge cases: пустая строка, no match, паттерн в конце строки

---

### Этап 2: Grammar Data Types и Parser (2-3 дня)

**Задача:** Описать Kotlin data classes для TextMate грамматик, реализовать загрузку из JSON.

**Референс:** `src/rawGrammar.ts` (типы), `src/grammarReader.ts` (парсинг).

1. Создать типы в `core/src/main/kotlin/dev/textmate/grammar/raw/`:

   ```kotlin
   // RawGrammar.kt — основные типы
   data class RawGrammar(
       val scopeName: String,                    // e.g. "source.kotlin"
       val patterns: List<RawRule>,
       val repository: Map<String, RawRule>?,
       val injections: Map<String, RawRule>?,
       val injectionSelector: String?,
       val fileTypes: List<String>?,
       val name: String?,
       val firstLineMatch: String?
   )

   data class RawRule(
       val id: Int? = null,                     // internal, не из JSON
       val include: String? = null,             // "#repo-name", "$base", "$self", "source.other"
       val name: String? = null,                // scope name
       val contentName: String? = null,
       val match: String? = null,               // regex
       val captures: Map<String, RawCapture>? = null,
       val begin: String? = null,
       val beginCaptures: Map<String, RawCapture>? = null,
       val end: String? = null,
       val endCaptures: Map<String, RawCapture>? = null,
       val whilePattern: String? = null,        // "while" в JSON (while — reserved в Kotlin!)
       val whileCaptures: Map<String, RawCapture>? = null,
       val patterns: List<RawRule>? = null,
       val repository: Map<String, RawRule>? = null,
       val applyEndPatternLast: Boolean? = null
   )

   data class RawCapture(
       val name: String? = null,
       val patterns: List<RawRule>? = null
   )
   ```

   **ВАЖНО:** В JSON грамматиках ключ `"while"` — reserved keyword в Kotlin. Использовать `@SerializedName("while")` с Gson или кастомный десериализатор.

2. Реализовать `GrammarReader`:
   - Парсинг `.tmLanguage.json` (JSON формат — основной) через Gson
   - Опционально: парсинг PList XML (`.tmLanguage`) — можно пропустить для PoC
   - Нумерация правил: каждому RawRule назначить уникальный `id`

3. Тесты:
   - Загрузить `json.tmLanguage.json` — проверить что scopeName = "source.json"
   - Загрузить `kotlin.tmLanguage.json` — проверить что repository содержит ожидаемые ключи
   - Проверить корректность десериализации `"while"` поля

---

### Этап 3: Rule Compilation (3-4 дня)

**Задача:** Портировать компиляцию raw rules в исполняемые Rule объекты.

**Референс:** `src/rule.ts` — это самый сложный файл (~800 строк).

1. Создать Rule hierarchy в `core/src/main/kotlin/dev/textmate/grammar/`:

   ```kotlin
   sealed class Rule(val id: RuleId, val name: String?, val contentName: String?)

   class MatchRule(
       id: RuleId,
       name: String?,
       val match: String,        // regex
       val captures: List<CaptureRule>
   ) : Rule(id, name, null)

   class BeginEndRule(
       id: RuleId,
       name: String?,
       contentName: String?,
       val begin: String,
       val beginCaptures: List<CaptureRule>,
       val end: String,
       val endCaptures: List<CaptureRule>,
       val applyEndPatternLast: Boolean,
       val patterns: CompilePatternsResult
   ) : Rule(id, name, contentName)

   class BeginWhileRule(
       id: RuleId,
       name: String?,
       contentName: String?,
       val begin: String,
       val beginCaptures: List<CaptureRule>,
       val whilePattern: String,
       val whileCaptures: List<CaptureRule>,
       val patterns: CompilePatternsResult
   ) : Rule(id, name, contentName)

   class IncludeOnlyRule(
       id: RuleId,
       name: String?,
       contentName: String?,
       val patterns: CompilePatternsResult
   ) : Rule(id, name, contentName)

   class CaptureRule(
       id: RuleId,
       name: String?,
       contentName: String?,
       val retokenizeCaptureWithRuleId: RuleId?
   ) : Rule(id, name, contentName)

   @JvmInline
   value class RuleId(val value: Int)

   data class CompilePatternsResult(
       val patterns: List<RuleId>,
       val hasMissingPatterns: Boolean
   )
   ```

2. Реализовать `RuleFactory`:
   - `compileRule(rawRule)` → компиляция одного правила
   - `compilePatterns(patterns, repository)` → резолвинг `#includes`
   - **Include resolution**: `$self`, `$base`, `#name`, `source.other`
   - `RuleRegistry` — хранилище скомпилированных правил по id

3. Реализовать `RegExpSource` и `RegExpSourceList`:
   - Класс для работы с regex паттернами, которые могут содержать back-references (`\1`, `\2`) и `\G` anchor
   - `RegExpSourceList` — компилирует набор паттернов в один `OnigScanner`
   - Кэширование скомпилированных сканнеров

4. Тесты:
   - Компиляция JSON грамматики — проверить что все правила создались без ошибок
   - Проверить include resolution: `#string` → правило из repository
   - Проверить корректность back-reference подстановки

---

### Этап 4: Tokenizer (4-5 дней)

**Задача:** Портировать основной цикл токенизации — построчное разбиение кода на токены с scope stacks.

**Референс:** `src/grammar.ts` — функции `_tokenize`, `_tokenizeString` (~600 строк логики).

1. Создать `Grammar` class:

   ```kotlin
   class Grammar(
       private val rootScopeName: String,
       private val onigLib: IOnigLib,
       private val rawGrammar: RawGrammar,
       private val registry: GrammarRegistry  // для embedded languages
   ) {
       private val ruleRegistry = RuleRegistry()
       private val rootRule: Rule = // компиляция корневого правила

       fun tokenizeLine(
           lineText: String,
           prevState: StateStack?
       ): TokenizeLineResult {
           // ...
       }
   }

   data class Token(
       val startIndex: Int,
       val endIndex: Int,
       val scopes: List<String>       // e.g. ["source.kotlin", "keyword.control.kotlin"]
   )

   data class TokenizeLineResult(
       val tokens: List<Token>,
       val endState: StateStack        // передаётся в следующую строку
   )
   ```

2. Реализовать `StateStack`:
   - Immutable linked list стек (как в vscode-textmate)
   - Каждый frame содержит: ruleId, scopeName, contentName, endRule pattern
   - `push()`, `pop()`, `equals()`, `reset()`
   - **ВАЖНО:** StateStack используется между строками — каждая строка получает prevState и возвращает endState

3. Реализовать `_tokenizeString()` — основной алгоритм:
   ```
   Для каждой позиции в строке:
     1. Собрать все injection rules + текущие patterns
     2. Создать OnigScanner из паттернов
     3. Найти первый match начиная с текущей позиции
     4. Если match — endRule текущего BeginEnd:
        → вытолкнуть стек, применить endCaptures
     5. Если match — BeginEnd rule:
        → протолкнуть стек, применить beginCaptures
     6. Если match — Match rule:
        → применить captures, не менять стек
     7. Если match — BeginWhile rule:
        → протолкнуть стек (аналогично BeginEnd)
     8. Если нет match:
        → продвинуть позицию на 1
     9. Записать токен(ы) с текущим scope stack
   ```

4. **BeginWhile обработка**: перед токенизацией каждой строки проверить `while` паттерн текущего BeginWhile rule. Если не матчится — pop стек.

5. **Captures обработка**: когда rule с captures матчится, каждая capture group порождает вложенный scope. Если capture имеет свои patterns — рекурсивно токенизировать captured text.

6. Тесты (критически важные):
   - JSON: `{"key": "value", "num": 42}` → проверить scopes для строк, ключей, чисел, пунктуации
   - Kotlin: `fun main() { println("hello") }` → keyword, function name, string literal
   - Multiline: блочный комментарий `/* ... \n ... */` через 2 строки — проверить что state передаётся
   - Nested scopes: строка внутри интерполяции `"text ${expr} more"` в Kotlin
   - **Regression tests**: скопировать тесты из vscode-textmate (`src/tests/`) и адаптировать

---

### Этап 5: Theme Engine (2 дня)

**Задача:** Загрузка тем и маппинг scopes → стили (цвет, bold, italic).

**Референс:** `src/theme.ts`.

1. Создать типы:

   ```kotlin
   data class ThemeSetting(
       val scope: String?,                // e.g. "keyword.control"
       val fontStyle: FontStyle?,         // ITALIC, BOLD, UNDERLINE, STRIKETHROUGH
       val foreground: String?,           // hex color "#FF0000"
       val background: String?
   )

   enum class FontStyle { ITALIC, BOLD, UNDERLINE, STRIKETHROUGH }

   data class ResolvedStyle(
       val foreground: Long,              // ARGB color
       val background: Long,
       val fontStyle: Set<FontStyle>
   )

   class Theme(
       val name: String,
       val settings: List<ThemeSetting>,
       val defaultForeground: Long,
       val defaultBackground: Long
   ) {
       fun match(scopes: List<String>): ResolvedStyle {
           // Самый специфичный scope wins
           // "keyword.control.kotlin" > "keyword.control" > "keyword"
       }
   }
   ```

2. Реализовать `ThemeReader`:
   - Парсинг VS Code JSON theme (`tokenColors` массив)
   - Парсинг `.tmTheme` (PList XML) — опционально для PoC
   - Конвертация hex цветов в ARGB Long

3. Реализовать scope matching:
   - Каскадное разрешение: более специфичный scope selector побеждает
   - Parent scope matching: `meta.function keyword` матчит keyword внутри function scope

4. Тесты:
   - Загрузить Dark+ тему
   - `["source.kotlin", "keyword.control.kotlin"]` → ожидаемый цвет (purple в Dark+)
   - `["source.kotlin", "string.quoted.double.kotlin"]` → ожидаемый цвет (orange в Dark+)

---

### Этап 6: Compose Bridge и Demo App (2-3 дня)

**Задача:** Превратить токены в `AnnotatedString` и показать в Android-приложении.

1. Создать `CodeHighlighter` в `compose-ui/`:

   ```kotlin
   class CodeHighlighter(
       private val grammar: Grammar,
       private val theme: Theme
   ) {
       fun highlight(code: String): AnnotatedString {
           val builder = AnnotatedString.Builder()
           var state: StateStack? = null

           for (line in code.lines()) {
               val result = grammar.tokenizeLine(line, state)
               state = result.endState

               for (token in result.tokens) {
                   val text = line.substring(token.startIndex, token.endIndex)
                   val style = theme.match(token.scopes)
                   builder.withStyle(SpanStyle(
                       color = Color(style.foreground),
                       fontWeight = if (FontStyle.BOLD in style.fontStyle) FontWeight.Bold else null,
                       fontStyle = if (FontStyle.ITALIC in style.fontStyle) FontStyleCompat.Italic else null
                   )) {
                       append(text)
                   }
               }
               builder.append("\n")
           }
           return builder.toAnnotatedString()
       }
   }
   ```

2. Создать `CodeBlock` composable:

   ```kotlin
   @Composable
   fun CodeBlock(
       code: String,
       grammar: Grammar,
       theme: Theme,
       modifier: Modifier = Modifier
   ) {
       val highlighter = remember(grammar, theme) { CodeHighlighter(grammar, theme) }
       val annotatedString = remember(code) { highlighter.highlight(code) }

       SelectionContainer {
           Text(
               text = annotatedString,
               modifier = modifier
                   .background(Color(theme.defaultBackground))
                   .padding(16.dp)
                   .horizontalScroll(rememberScrollState()),
               fontFamily = FontFamily.Monospace,
               fontSize = 14.sp,
               lineHeight = 20.sp
           )
       }
   }
   ```

3. Sample App:
   - Один экран с тремя вкладками: Kotlin, JSON, Markdown
   - Каждая вкладка показывает hardcoded code snippet с подсветкой
   - Переключатель Dark / Light тема
   - Грамматики и темы загружать из assets

4. Тесты / ручная проверка:
   - Визуально сравнить вывод с VS Code для того же кода и темы
   - Скриншот-тесты (опционально)

---

### Этап 7: Валидация и документация (1-2 дня)

1. **Conformance тесты:**
   - Взять набор `.txt` fixtures из `vscode-textmate/test-cases/`
   - Для каждого: прогнать через Kotlin tokenizer, сравнить output с эталоном из vscode-textmate
   - Цель: **90%+ совпадение** scope names для JSON и Kotlin грамматик

2. **Performance baseline:**
   - Замерить время токенизации ~1000 строк Kotlin-кода
   - Цель: < 100ms на среднем Android-устройстве (для PoC достаточно)

3. **Документация:**
   - README.md с описанием проекта и мотивацией
   - ARCHITECTURE.md — маппинг файлов vscode-textmate → Kotlin классы
   - Known limitations (что не поддерживается в PoC)

4. **Known limitations PoC** (задокументировать, не фиксить):
   - Только JVM/Android (нет iOS)
   - Нет PList парсинга (только JSON грамматики)
   - Нет injection grammars (embedded languages)
   - Нет incremental tokenization
   - Нет кэширования OnigScanner
   - BeginWhile rules могут работать некорректно в edge cases

---

## Timeline

| Этап | Описание | Дни |
|------|----------|-----|
| 0 | Настройка проекта | 1 |
| 1 | Joni regex wrapper | 2-3 |
| 2 | Grammar types + JSON parser | 2-3 |
| 3 | Rule compilation | 3-4 |
| 4 | Tokenizer (core) | 4-5 |
| 5 | Theme engine | 2 |
| 6 | Compose UI + demo | 2-3 |
| 7 | Валидация + docs | 1-2 |
| **Итого** | | **17-22 дня** |

## Критерии успеха PoC

1. ✅ Загружает стандартные VS Code JSON грамматики без модификаций
2. ✅ Корректно токенизирует Kotlin, JSON, Markdown код
3. ✅ Подсветка визуально совпадает с VS Code (Dark+ тема)
4. ✅ Работает на Android (Compose UI)
5. ✅ 90%+ conformance с output vscode-textmate на тестовых fixtures
6. ✅ Время токенизации 1000 строк < 100ms

## Следующие шаги после PoC

1. **KMP**: Вынести regex в `expect`/`actual` — Joni для JVM/Android, Oniguruma C interop для iOS
2. **Compose Multiplatform UI**: Перевести compose-ui модуль на CMP
3. **Performance**: OnigScanner caching, incremental tokenization
4. **Grammar coverage**: injection grammars, embedded languages
5. **Distribution**: Publish на Maven Central как KMP библиотеку

## Полезные ссылки

- vscode-textmate source: https://github.com/microsoft/vscode-textmate
- TextMate grammar spec: https://macromates.com/manual/en/language_grammars
- Joni (Java Oniguruma): https://github.com/jruby/joni
- VS Code grammar extensions: https://github.com/microsoft/vscode/tree/main/extensions
- VS Code themes: https://github.com/microsoft/vscode/tree/main/extensions/theme-defaults
- Scope naming conventions: https://www.sublimetext.com/docs/scope_naming.html
