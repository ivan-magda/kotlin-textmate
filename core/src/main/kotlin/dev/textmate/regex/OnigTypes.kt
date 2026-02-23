package dev.textmate.regex

public data class CaptureIndex(
    public val start: Int,
    public val end: Int,
    public val length: Int
)

public data class MatchResult(
    public val index: Int,
    public val captureIndices: List<CaptureIndex>
)
