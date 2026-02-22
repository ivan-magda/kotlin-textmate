package dev.textmate.grammar.rule

import dev.textmate.regex.CaptureIndex

private val CAPTURING_REGEX_SOURCE = Regex("""\$(\d+)|\$\{(\d+):/(downcase|upcase)\}""")

internal fun hasCaptures(regexSource: String?): Boolean =
    if (regexSource == null) {
        false
    } else {
        CAPTURING_REGEX_SOURCE.containsMatchIn(regexSource)
    }

internal fun replaceCaptures(
    regexSource: String,
    captureSource: String,
    captureIndices: List<CaptureIndex>
): String =
    CAPTURING_REGEX_SOURCE.replace(regexSource) { matchResult ->
        val index = (matchResult.groupValues[1].ifEmpty { matchResult.groupValues[2] }).toInt()
        val capture = captureIndices.getOrNull(index)
        if (capture != null) {
            var result = captureSource.substring(capture.start, capture.end)
            // Remove leading dots that would make the selector invalid
            while (result.startsWith('.')) {
                result = result.substring(1)
            }
            when (matchResult.groupValues[3]) {
                "downcase" -> result.lowercase()
                "upcase" -> result.uppercase()
                else -> result
            }
        } else {
            matchResult.value
        }
    }

private val ESCAPE_REGEX = Regex("""[-\\{}*+?|^$.,\[\]()#\s]""")

internal fun escapeRegExpCharacters(value: String): String {
    return value.replace(ESCAPE_REGEX) { "\\${it.value}" }
}
