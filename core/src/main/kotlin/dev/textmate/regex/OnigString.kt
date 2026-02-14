package dev.textmate.regex

class OnigString(val content: String) {

    val length: Int get() = content.length

    val utf8Bytes: ByteArray = content.toByteArray(Charsets.UTF_8)

    private val isMultiByte: Boolean = utf8Bytes.size != content.length

    private val byteToCharOffsets: IntArray by lazy { buildByteToCharMap() }

    fun charToByteOffset(charOffset: Int): Int {
        if (charOffset <= 0) return 0
        if (charOffset >= content.length) return utf8Bytes.size
        if (!isMultiByte) return charOffset
        return content.substring(0, charOffset).toByteArray(Charsets.UTF_8).size
    }

    fun byteToCharOffset(byteOffset: Int): Int {
        if (byteOffset <= 0) return 0
        if (byteOffset >= utf8Bytes.size) return content.length
        if (!isMultiByte) return byteOffset
        return byteToCharOffsets[byteOffset]
    }

    private fun buildByteToCharMap(): IntArray {
        val map = IntArray(utf8Bytes.size + 1)
        var byteIdx = 0
        var charIdx = 0
        while (charIdx < content.length) {
            val codePoint = Character.codePointAt(content, charIdx)
            val charCount = Character.charCount(codePoint)
            val codePointByteLen = when {
                codePoint <= 0x7F -> 1
                codePoint <= 0x7FF -> 2
                codePoint <= 0xFFFF -> 3
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
