package dev.textmate.grammar.rule

internal sealed class IncludeReference {
    data object BaseReference : IncludeReference()
    data object SelfReference : IncludeReference()
    class RelativeReference(val ruleName: String) : IncludeReference()
    class TopLevelReference(val scopeName: String) : IncludeReference()
    class TopLevelRepositoryReference(val scopeName: String, val ruleName: String) : IncludeReference()
}

internal fun parseInclude(include: String): IncludeReference =
    when (include) {
        "\$base" -> IncludeReference.BaseReference
        "\$self" -> IncludeReference.SelfReference
        else -> {
            when (val indexOfSharp = include.indexOf('#')) {
                -1 -> IncludeReference.TopLevelReference(include)
                0 -> IncludeReference.RelativeReference(include.substring(1))
                else -> IncludeReference.TopLevelRepositoryReference(
                    include.substring(0, indexOfSharp),
                    include.substring(indexOfSharp + 1)
                )
            }
        }
    }
