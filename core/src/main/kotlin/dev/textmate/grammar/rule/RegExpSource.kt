package dev.textmate.grammar.rule

import dev.textmate.regex.CaptureIndex

private val BACK_REFERENCE_REGEX = Regex("""\\(\d+)""")

internal class RegExpSource(regExpSource: String, val ruleId: RuleId) {

    var source: String
        private set

    var hasAnchor: Boolean
        private set

    val hasBackReferences: Boolean
    private var _anchorCache: AnchorCache? = null

    init {
        if (regExpSource.isNotEmpty()) {
            val len = regExpSource.length
            var lastPushedPos = 0
            val output = StringBuilder()
            var anchor = false

            var pos = 0
            while (pos < len) {
                val ch = regExpSource[pos]
                if (ch == '\\' && pos + 1 < len) {
                    val nextCh = regExpSource[pos + 1]
                    if (nextCh == 'z') {
                        output.append(regExpSource, lastPushedPos, pos)
                        output.append("\$(?!\\n)(?<!\\n)")
                        lastPushedPos = pos + 2
                    } else if (nextCh == 'A' || nextCh == 'G') {
                        anchor = true
                    }
                    pos++
                }
                pos++
            }

            this.hasAnchor = anchor
            if (lastPushedPos == 0) {
                this.source = regExpSource
            } else {
                output.append(regExpSource, lastPushedPos, len)
                this.source = output.toString()
            }
        } else {
            this.hasAnchor = false
            this.source = regExpSource
        }

        if (this.hasAnchor) {
            this._anchorCache = buildAnchorCache()
        }

        this.hasBackReferences = BACK_REFERENCE_REGEX.containsMatchIn(this.source)
    }

    fun clone(): RegExpSource {
        return RegExpSource(this.source, this.ruleId)
    }

    fun setSource(newSource: String) {
        if (this.source == newSource) {
            return
        }
        this.source = newSource
        if (this.hasAnchor) {
            this._anchorCache = buildAnchorCache()
        }
    }

    fun resolveBackReferences(lineText: String, captureIndices: List<CaptureIndex>): String {
        val capturedValues = captureIndices.map { capture ->
            lineText.substring(capture.start, capture.end)
        }
        return BACK_REFERENCE_REGEX.replace(this.source) { matchResult ->
            val index = matchResult.groupValues[1].toInt()
            escapeRegExpCharacters(capturedValues.getOrElse(index) { "" })
        }
    }

    fun resolveAnchors(allowA: Boolean, allowG: Boolean): String {
        val cache = this._anchorCache
        if (!this.hasAnchor || cache == null) {
            return this.source
        }

        return when {
            allowA && allowG -> cache.A1_G1
            allowA -> cache.A1_G0
            allowG -> cache.A0_G1
            else -> cache.A0_G0
        }
    }

    private fun buildAnchorCache(): AnchorCache {
        val len = this.source.length
        val a0g0 = CharArray(len)
        val a0g1 = CharArray(len)
        val a1g0 = CharArray(len)
        val a1g1 = CharArray(len)

        var pos = 0
        while (pos < len) {
            val ch = this.source[pos]
            a0g0[pos] = ch
            a0g1[pos] = ch
            a1g0[pos] = ch
            a1g1[pos] = ch

            if (ch == '\\' && pos + 1 < len) {
                when (val nextCh = this.source[pos + 1]) {
                    'A' -> {
                        a0g0[pos + 1] = '\uFFFF'
                        a0g1[pos + 1] = '\uFFFF'
                        a1g0[pos + 1] = 'A'
                        a1g1[pos + 1] = 'A'
                    }

                    'G' -> {
                        a0g0[pos + 1] = '\uFFFF'
                        a0g1[pos + 1] = 'G'
                        a1g0[pos + 1] = '\uFFFF'
                        a1g1[pos + 1] = 'G'
                    }

                    else -> {
                        a0g0[pos + 1] = nextCh
                        a0g1[pos + 1] = nextCh
                        a1g0[pos + 1] = nextCh
                        a1g1[pos + 1] = nextCh
                    }
                }
                pos++
            }
            pos++
        }

        return AnchorCache(
            A0_G0 = String(a0g0),
            A0_G1 = String(a0g1),
            A1_G0 = String(a1g0),
            A1_G1 = String(a1g1)
        )
    }

    private data class AnchorCache(
        val A0_G0: String,
        val A0_G1: String,
        val A1_G0: String,
        val A1_G1: String
    )
}
