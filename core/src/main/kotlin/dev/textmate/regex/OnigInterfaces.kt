package dev.textmate.regex

public interface IOnigLib {
    public fun createOnigScanner(patterns: List<String>): OnigScanner
    public fun createOnigString(str: String): OnigString
}

public interface OnigScanner {
    public fun findNextMatchSync(string: OnigString, startPosition: Int): MatchResult?
}
