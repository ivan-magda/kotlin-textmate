package dev.textmate.compose

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import dev.textmate.grammar.Grammar
import dev.textmate.grammar.tokenize.StateStack
import dev.textmate.theme.FontStyle
import dev.textmate.theme.ResolvedStyle
import dev.textmate.theme.Theme
import androidx.compose.ui.text.font.FontStyle as ComposeFontStyle

class CodeHighlighter(private val grammar: Grammar, private val theme: Theme) {

    fun highlight(code: String): AnnotatedString {
        val builder = AnnotatedString.Builder()
        val lines = code.lines()
        var prevState: StateStack? = null

        for ((index, line) in lines.withIndex()) {
            val result = grammar.tokenizeLine(line, prevState)
            prevState = result.ruleStack

            for (token in result.tokens) {
                val start = token.startIndex.coerceIn(0, line.length)
                val end = token.endIndex.coerceIn(0, line.length)
                if (start >= end) continue

                val style = theme.match(token.scopes)
                builder.pushStyle(toSpanStyle(style))
                builder.append(line.substring(start, end))
                builder.pop()
            }

            if (index < lines.lastIndex) {
                builder.append("\n")
            }
        }

        return builder.toAnnotatedString()
    }

    private fun toSpanStyle(style: ResolvedStyle): SpanStyle {
        val hasUnderline = FontStyle.UNDERLINE in style.fontStyle
        val hasStrikethrough = FontStyle.STRIKETHROUGH in style.fontStyle
        val textDecoration = when {
            hasUnderline && hasStrikethrough -> TextDecoration.Underline + TextDecoration.LineThrough
            hasUnderline -> TextDecoration.Underline
            hasStrikethrough -> TextDecoration.LineThrough
            else -> null
        }

        return SpanStyle(
            color = style.foreground.toComposeColor(),
            fontWeight = if (FontStyle.BOLD in style.fontStyle) FontWeight.Bold else null,
            fontStyle = if (FontStyle.ITALIC in style.fontStyle) ComposeFontStyle.Italic else null,
            textDecoration = textDecoration
        )
    }
}
