package dev.textmate.sample

import android.content.res.AssetManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.textmate.compose.CodeBlock
import dev.textmate.compose.CodeBlockDefaults
import dev.textmate.grammar.Grammar
import dev.textmate.grammar.raw.GrammarReader
import dev.textmate.regex.JoniOnigLib
import dev.textmate.theme.Theme
import dev.textmate.theme.ThemeReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val KOTLIN_SNIPPET = """
data class User(
    val name: String, 
    val age: Int
)

fun greet(user: User): String {
    return "Hello, ${'$'}{'$'}{user.name}!" +
            "You are ${'$'}{'$'}{user.age} years old."
}

fun main() {
    val users = listOf(
        User("Alice", 30),
        User("Bob", 25)
    )
    users
        .filter { it.age > 28 }
        .forEach { println(greet(it)) }
}
""".trimIndent()

private val JSON_SNIPPET = """
{
    "name": "kotlin-textmate",
    "version": "0.1.0",
    "languages": ["kotlin", "json", "markdown"],
    "config": { "theme": "dark+", "tabSize": 4 }
}
""".trimIndent()

// Uses only patterns safe from Joni's lookbehind limitation (no bold, italic, headings, links)
private val MARKDOWN_SNIPPET = """
---

    indented code block
    val x = 42

```kotlin
fun main() {
    println("fenced block")
}
```

---
""".trimIndent()

private enum class Language(
    val title: String,
    val grammarAsset: String,
    val snippet: String
) {
    KOTLIN("Kotlin", "grammars/kotlin.tmLanguage.json", KOTLIN_SNIPPET),
    JSON("JSON", "grammars/JSON.tmLanguage.json", JSON_SNIPPET),
    MARKDOWN("Markdown", "grammars/markdown.tmLanguage.json", MARKDOWN_SNIPPET)
}

private data class LoadedResources(
    val grammars: Map<Language, Grammar>,
    val darkTheme: Theme,
    val lightTheme: Theme
)

private fun loadResources(assets: AssetManager): LoadedResources {
    val onigLib = JoniOnigLib()

    val grammars = Language.entries.associateWith { lang ->
        val rawGrammar = assets.open(lang.grammarAsset).use { GrammarReader.readGrammar(it) }
        Grammar(rawGrammar.scopeName, rawGrammar, onigLib)
    }

    val darkTheme = assets.open("themes/dark_vs.json").use { darkVs ->
        assets.open("themes/dark_plus.json").use { darkPlus ->
            ThemeReader.readTheme(darkVs, darkPlus)
        }
    }

    val lightTheme = assets.open("themes/light_vs.json").use { lightVs ->
        assets.open("themes/light_plus.json").use { lightPlus ->
            ThemeReader.readTheme(lightVs, lightPlus)
        }
    }

    return LoadedResources(grammars, darkTheme, lightTheme)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                SampleApp(assets)
            }
        }
    }
}

@Composable
private fun SampleApp(assets: AssetManager) {
    var resources by remember { mutableStateOf<LoadedResources?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            resources = withContext(Dispatchers.IO) { loadResources(assets) }
        } catch (e: Exception) {
            error = e.message ?: "Unknown error"
        }
    }

    val currentError = error
    val currentResources = resources
    when {
        currentError != null -> ErrorScreen(currentError)
        currentResources != null -> MainScreen(currentResources)
        else -> LoadingScreen()
    }
}

@Composable
private fun LoadingScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Text("Loading grammars and themes…", modifier = Modifier.padding(top = 16.dp))
        }
    }
}

@Composable
private fun ErrorScreen(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Error: $message", color = MaterialTheme.colorScheme.error)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(resources: LoadedResources) {
    var isDark by remember { mutableStateOf(true) }
    var isSoftWrap by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) }
    val language = Language.entries[selectedTab]
    val theme = if (isDark) resources.darkTheme else resources.lightTheme

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("KotlinTextMate") },
                actions = {
                    Text(if (isSoftWrap) "Wrap" else "Scroll")
                    Switch(checked = isSoftWrap, onCheckedChange = { isSoftWrap = it })
                    Text(if (isDark) "Dark+" else "Light+")
                    Switch(checked = isDark, onCheckedChange = { isDark = it })
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                Language.entries.forEachIndexed { index, lang ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(lang.title) }
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                CodeBlock(
                    code = language.snippet,
                    grammar = resources.grammars.getValue(language),
                    theme = theme,
                    style = CodeBlockDefaults.style(softWrap = isSoftWrap),
                )
            }
        }
    }
}
