package dev.textmate.regex

interface IOnigLib {
    fun createOnigScanner(patterns: List<String>): OnigScanner
    fun createOnigString(str: String): OnigString
}

interface OnigScanner {
    fun findNextMatchSync(string: OnigString, startPosition: Int): MatchResult?
}
