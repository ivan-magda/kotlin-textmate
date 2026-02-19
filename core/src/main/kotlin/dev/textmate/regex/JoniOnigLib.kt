package dev.textmate.regex

import org.jcodings.specific.UTF8Encoding
import org.joni.Matcher
import org.joni.Option
import org.joni.Regex
import org.joni.Region
import org.joni.Syntax
import org.joni.WarnCallback
import org.joni.exception.JOniException

class JoniOnigLib : IOnigLib {

    private val _sentinelPatterns = mutableSetOf<String>()

    /** Number of unique regex patterns that fell back to the never-matching sentinel. */
    internal val sentinelPatternCount: Int get() = _sentinelPatterns.size

    override fun createOnigScanner(patterns: List<String>): OnigScanner {
        return JoniOnigScanner(patterns) { pattern -> _sentinelPatterns.add(pattern) }
    }

    override fun createOnigString(str: String): OnigString {
        return OnigString(str)
    }
}

internal class JoniOnigScanner(
    patterns: List<String>,
    private val onSentinel: ((String) -> Unit)? = null
) : OnigScanner {

    private val regexes: List<Regex> = patterns.map { compilePattern(it) }

    override fun findNextMatchSync(string: OnigString, startPosition: Int): MatchResult? {
        val bytes = string.utf8Bytes
        val byteStart = string.charToByteOffset(startPosition)
        val byteLength = bytes.size

        var bestResult: MatchResult? = null
        var bestStartByte = Int.MAX_VALUE

        for ((patternIndex, regex) in regexes.withIndex()) {
            val matcher = regex.matcher(bytes)
            val status = matcher.search(byteStart, byteLength, Option.DEFAULT)

            if (status != Matcher.FAILED) {
                val region = matcher.eagerRegion
                val matchStartByte = region.getBeg(0)

                if (matchStartByte < bestStartByte) {
                    bestStartByte = matchStartByte
                    bestResult = buildMatchResult(patternIndex, region, string)
                }

                if (matchStartByte == byteStart) {
                    break
                }
            }
        }

        return bestResult
    }

    private fun buildMatchResult(
        patternIndex: Int,
        region: Region,
        string: OnigString
    ): MatchResult {
        val captureIndices = (0 until region.numRegs).map { i ->
            val begByte = region.getBeg(i)
            val endByte = region.getEnd(i)
            if (begByte < 0) {
                CaptureIndex(start = 0, end = 0, length = 0)
            } else {
                val startChar = string.byteToCharOffset(begByte)
                val endChar = string.byteToCharOffset(endByte)
                CaptureIndex(start = startChar, end = endChar, length = endChar - startChar)
            }
        }
        return MatchResult(index = patternIndex, captureIndices = captureIndices)
    }

    private fun compilePattern(pattern: String): Regex {
        val patternBytes = pattern.toByteArray(Charsets.UTF_8)
        return try {
            Regex(
                patternBytes,
                0,
                patternBytes.size,
                Option.CAPTURE_GROUP,
                UTF8Encoding.INSTANCE,
                Syntax.DEFAULT,
                WarnCallback.NONE
            )
        } catch (_: JOniException) {
            onSentinel?.invoke(pattern)
            NEVER_MATCH_REGEX
        }
    }

    companion object {
        private val NEVER_MATCH_REGEX: Regex by lazy {
            val bytes = "(?!)".toByteArray(Charsets.UTF_8)
            Regex(bytes, 0, bytes.size, Option.CAPTURE_GROUP, UTF8Encoding.INSTANCE, Syntax.DEFAULT, WarnCallback.NONE)
        }
    }
}
