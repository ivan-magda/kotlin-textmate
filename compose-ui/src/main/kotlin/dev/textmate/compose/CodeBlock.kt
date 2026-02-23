package dev.textmate.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import dev.textmate.grammar.Grammar
import dev.textmate.theme.Theme

/**
 * Converts an ARGB [Long] (as produced by [dev.textmate.theme.ResolvedStyle]) to a Compose [Color].
 */
internal fun Long.toComposeColor(): Color = Color(this.toInt())

@Composable
public fun rememberHighlightedCode(
    code: String,
    grammar: Grammar,
    theme: Theme,
): AnnotatedString = remember(code, grammar, theme) {
    CodeHighlighter(grammar, theme).highlight(code)
}

@Composable
public fun CodeBlock(
    code: String,
    grammar: Grammar,
    theme: Theme,
    modifier: Modifier = Modifier,
    style: CodeBlockStyle = CodeBlockDefaults.style(),
) {
    val annotatedString = rememberHighlightedCode(code, grammar, theme)
    val backgroundColor = remember(theme) {
        theme.defaultStyle.background.toComposeColor()
    }

    SelectionContainer {
        Text(
            text = annotatedString,
            style = style.textStyle,
            softWrap = style.softWrap,
            modifier = modifier
                .fillMaxWidth()
                .background(backgroundColor)
                .then(
                    if (!style.softWrap) {
                        Modifier.horizontalScroll(rememberScrollState())
                    } else {
                        Modifier
                    }
                )
                .padding(style.contentPadding)
        )
    }
}
