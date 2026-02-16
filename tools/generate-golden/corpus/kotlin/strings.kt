package example

fun strings() {
    val simple = "hello"
    val escaped = "line1\nline2\ttab"
    val interpolated = "Hello, $simple!"
    val expression = "result: ${1 + 2}"
    val multiline = """
        first line
        second line
    """.trimIndent()
    val empty = ""
    val unicode = "\u0048\u0065\u006C\u006C\u006F"
}
