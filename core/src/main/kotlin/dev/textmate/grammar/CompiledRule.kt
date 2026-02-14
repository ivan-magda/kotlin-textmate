package dev.textmate.grammar

import dev.textmate.regex.CaptureIndex
import dev.textmate.regex.IOnigLib
import dev.textmate.regex.OnigScanner
import dev.textmate.regex.OnigString

class CompiledRule(
    onigLib: IOnigLib,
    private val regExps: List<String>,
    private val rules: List<RuleId>
) {
    private val scanner: OnigScanner = onigLib.createOnigScanner(regExps)

    fun findNextMatchSync(string: OnigString, startPosition: Int): FindNextMatchResult? {
        val result = scanner.findNextMatchSync(string, startPosition) ?: return null
        return FindNextMatchResult(
            ruleId = rules[result.index],
            captureIndices = result.captureIndices
        )
    }

    override fun toString(): String {
        return rules.indices.joinToString("\n") { i ->
            "   - ${rules[i]}: ${regExps[i]}"
        }
    }
}

data class FindNextMatchResult(
    val ruleId: RuleId,
    val captureIndices: List<CaptureIndex>
)
