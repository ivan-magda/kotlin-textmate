package dev.textmate.grammar

internal sealed class IncludeReference {
    data object BaseReference : IncludeReference()
    data object SelfReference : IncludeReference()
    class RelativeReference(val ruleName: String) : IncludeReference()
    class TopLevelReference(val scopeName: String) : IncludeReference()
    class TopLevelRepositoryReference(val scopeName: String, val ruleName: String) : IncludeReference()
}

internal fun parseInclude(include: String): IncludeReference {
    if (include == "\$base") {
        return IncludeReference.BaseReference
    } else if (include == "\$self") {
        return IncludeReference.SelfReference
    }

    val indexOfSharp = include.indexOf('#')
    return when {
        indexOfSharp == -1 -> IncludeReference.TopLevelReference(include)
        indexOfSharp == 0 -> IncludeReference.RelativeReference(include.substring(1))
        else -> IncludeReference.TopLevelRepositoryReference(
            include.substring(0, indexOfSharp),
            include.substring(indexOfSharp + 1)
        )
    }
}
