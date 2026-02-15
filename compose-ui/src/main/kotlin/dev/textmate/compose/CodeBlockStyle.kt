package dev.textmate.compose

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Immutable
data class CodeBlockStyle(
    val textStyle: TextStyle,
    val softWrap: Boolean,
    val contentPadding: PaddingValues,
)

object CodeBlockDefaults {
    fun style(
        textStyle: TextStyle = DefaultTextStyle,
        softWrap: Boolean = false,
        contentPadding: PaddingValues = PaddingValues(16.dp),
    ): CodeBlockStyle = CodeBlockStyle(textStyle, softWrap, contentPadding)
}

private val DefaultTextStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 14.sp,
    lineHeight = 20.sp,
)
