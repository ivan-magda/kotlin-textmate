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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.textmate.grammar.Grammar
import dev.textmate.theme.Theme

/**
 * Converts an ARGB [Long] (as produced by [dev.textmate.theme.ResolvedStyle]) to a Compose [Color].
 */
fun Long.toComposeColor(): Color = Color(this.toInt())

@Composable
fun CodeBlock(
    code: String,
    grammar: Grammar,
    theme: Theme,
    modifier: Modifier = Modifier
) {
    val annotatedString = remember(code, grammar, theme) {
        CodeHighlighter(grammar, theme).highlight(code)
    }
    val backgroundColor = remember(theme) {
        theme.defaultStyle.background.toComposeColor()
    }

    SelectionContainer {
        Text(
            text = annotatedString,
            fontFamily = FontFamily.Monospace,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            softWrap = false,
            modifier = modifier
                .fillMaxWidth()
                .background(backgroundColor)
                .horizontalScroll(rememberScrollState())
                .padding(16.dp)
        )
    }
}
