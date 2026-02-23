package dev.textmate.regex

public class OnigString(public val content: String) {

    public val length: Int
        get() = content.length

    public val utf8Bytes: ByteArray =
        content.toByteArray(Charsets.UTF_8)

    private val isMultiByte: Boolean =
        utf8Bytes.size != content.length

    private val byteToCharOffsets: IntArray by lazy {
        buildByteToCharMap()
    }

    private companion object {
        const val MAX_1_BYTE = 0x7F
        const val MAX_2_BYTES = 0x7FF
        const val MAX_3_BYTES = 0xFFFF
    }

    public fun charToByteOffset(charOffset: Int): Int {
        if (charOffset <= 0) return 0
        if (charOffset >= content.length) return utf8Bytes.size
        if (!isMultiByte) return charOffset
        return content.substring(0, charOffset).toByteArray(Charsets.UTF_8).size
    }

    public fun byteToCharOffset(byteOffset: Int): Int {
        if (byteOffset <= 0) return 0
        if (byteOffset >= utf8Bytes.size) return content.length
        if (!isMultiByte) return byteOffset
        return byteToCharOffsets[byteOffset]
    }

    @Suppress("MagicNumber") // UTF-8 byte lengths: 3 and 4 are self-evident from context
    private fun buildByteToCharMap(): IntArray {
        val map = IntArray(utf8Bytes.size + 1)
        var byteIdx = 0
        var charIdx = 0

        while (charIdx < content.length) {
            val codePoint = Character.codePointAt(content, charIdx)
            val charCount = Character.charCount(codePoint)

            val codePointByteLen = when {
                codePoint <= MAX_1_BYTE -> 1
                codePoint <= MAX_2_BYTES -> 2
                codePoint <= MAX_3_BYTES -> 3
                else -> 4
            }

            for (b in 0 until codePointByteLen) {
                if (byteIdx + b < map.size) {
                    map[byteIdx + b] = charIdx
                }
            }

            byteIdx += codePointByteLen
            charIdx += charCount
        }

        if (byteIdx < map.size) {
            map[byteIdx] = content.length
        }

        return map
    }
}
