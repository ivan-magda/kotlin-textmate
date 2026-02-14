package dev.textmate.regex

data class CaptureIndex(
    val start: Int,
    val end: Int,
    val length: Int
)

data class MatchResult(
    val index: Int,
    val captureIndices: List<CaptureIndex>
)
