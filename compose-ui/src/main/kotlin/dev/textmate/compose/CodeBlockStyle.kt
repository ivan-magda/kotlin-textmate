package dev.textmate.compose

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Immutable
public data class CodeBlockStyle(
    public val textStyle: TextStyle,
    public val softWrap: Boolean,
    public val contentPadding: PaddingValues,
)

public object CodeBlockDefaults {
    public fun style(
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
