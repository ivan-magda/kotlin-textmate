package dev.textmate.grammar

import dev.textmate.regex.CaptureIndex
import dev.textmate.regex.OnigString

/**
 * Result of matching a rule against a line.
 * Named to avoid collision with [dev.textmate.regex.MatchResult].
 */
internal data class MatchRuleResult(
    val captureIndices: List<CaptureIndex>,
    val matchedRuleId: RuleId
)

/**
 * Core tokenization loop — port of `_tokenizeString` from vscode-textmate `tokenizeString.ts`.
 * Simplified for Stage 4b: no injection grammars, no time limits, no BeginWhile condition checking.
 */
internal fun tokenizeString(
    grammar: Grammar,
    lineText: OnigString,
    isFirstLine: Boolean,
    linePos: Int,
    stack: StateStackImpl,
    lineTokens: LineTokens
): StateStackImpl {
    val lineLength = lineText.content.length

    var currentStack = stack
    var currentLinePos = linePos
    var currentIsFirstLine = isFirstLine
    var anchorPosition = if (stack.beginRuleCapturedEOL) 0 else -1
    var stop = false

    while (!stop) {
        val r = matchRule(grammar, lineText, currentIsFirstLine, currentLinePos, currentStack, anchorPosition)

        if (r == null) {
            // No match — produce token to end of line
            lineTokens.produce(currentStack, lineLength)
            stop = true
            break
        }

        val captureIndices = r.captureIndices
        val matchedRuleId = r.matchedRuleId

        if (captureIndices.isEmpty()) {
            // Defensive: OnigScanner should always return capture group 0 for a match
            lineTokens.produce(currentStack, lineLength)
            break
        }

        val hasAdvanced = captureIndices[0].end > currentLinePos

        if (matchedRuleId == RuleId.END_RULE) {
            // We matched the `end` for this rule => pop it
            val poppedRule = currentStack.getRule(grammar) as? BeginEndRule
                ?: error("END_RULE matched but current rule is not a BeginEndRule")

            lineTokens.produce(currentStack, captureIndices[0].start)

            currentStack = currentStack.withContentNameScopesList(currentStack.nameScopesList)

            handleCaptures(grammar, lineText, currentIsFirstLine, currentStack, lineTokens, poppedRule.endCaptures, captureIndices)

            lineTokens.produce(currentStack, captureIndices[0].end)

            // pop
            val popped = currentStack
            currentStack = currentStack.pop() ?: currentStack
            anchorPosition = popped.getAnchorPos()

            if (!hasAdvanced && popped.getEnterPos() == currentLinePos) {
                // [1] Grammar pushed & popped a rule without advancing
                currentStack = popped
                lineTokens.produce(currentStack, lineLength)
                stop = true
                break
            }
        } else {
            // We matched a rule
            val rule = grammar.getRule(matchedRuleId) ?: run {
                // Should not happen, but be safe
                lineTokens.produce(currentStack, lineLength)
                stop = true
                return@tokenizeString currentStack
            }

            lineTokens.produce(currentStack, captureIndices[0].start)

            val beforePush = currentStack

            val scopeName = rule.getName(lineText.content, captureIndices)
            val contentNameScopesList = checkNotNull(currentStack.contentNameScopesList) {
                "contentNameScopesList must not be null during tokenization"
            }
            val nameScopesList = contentNameScopesList.pushAttributed(scopeName, grammar)

            currentStack = currentStack.push(
                ruleId = matchedRuleId,
                enterPos = currentLinePos,
                anchorPos = anchorPosition,
                beginRuleCapturedEOL = captureIndices[0].end == lineLength,
                endRule = null,
                nameScopesList = nameScopesList,
                contentNameScopesList = nameScopesList
            )

            when (rule) {
                is BeginEndRule -> {
                    handleCaptures(grammar, lineText, currentIsFirstLine, currentStack, lineTokens, rule.beginCaptures, captureIndices)
                    lineTokens.produce(currentStack, captureIndices[0].end)
                    anchorPosition = captureIndices[0].end

                    val contentName = rule.getContentName(lineText.content, captureIndices)
                    val contentNameScopesList = nameScopesList?.pushAttributed(contentName, grammar)
                    currentStack = currentStack.withContentNameScopesList(contentNameScopesList)

                    if (rule.endHasBackReferences) {
                        currentStack = currentStack.withEndRule(
                            rule.getEndWithResolvedBackReferences(lineText.content, captureIndices)
                        )
                    }

                    if (!hasAdvanced && beforePush.hasSameRuleAs(currentStack)) {
                        // [2] Grammar pushed the same rule without advancing
                        currentStack = currentStack.pop() ?: currentStack
                        lineTokens.produce(currentStack, lineLength)
                        stop = true
                        break
                    }
                }

                is BeginWhileRule -> {
                    handleCaptures(grammar, lineText, currentIsFirstLine, currentStack, lineTokens, rule.beginCaptures, captureIndices)
                    lineTokens.produce(currentStack, captureIndices[0].end)
                    anchorPosition = captureIndices[0].end

                    val contentName = rule.getContentName(lineText.content, captureIndices)
                    val contentNameScopesList = nameScopesList?.pushAttributed(contentName, grammar)
                    currentStack = currentStack.withContentNameScopesList(contentNameScopesList)

                    if (rule.whileHasBackReferences) {
                        currentStack = currentStack.withEndRule(
                            rule.getWhileWithResolvedBackReferences(lineText.content, captureIndices)
                        )
                    }

                    if (!hasAdvanced && beforePush.hasSameRuleAs(currentStack)) {
                        // [3] Grammar pushed the same rule without advancing
                        currentStack = currentStack.pop() ?: currentStack
                        lineTokens.produce(currentStack, lineLength)
                        stop = true
                        break
                    }
                }

                is MatchRule -> {
                    handleCaptures(grammar, lineText, currentIsFirstLine, currentStack, lineTokens, rule.captures, captureIndices)
                    lineTokens.produce(currentStack, captureIndices[0].end)

                    // pop rule immediately since it is a MatchRule
                    currentStack = currentStack.pop() ?: currentStack

                    if (!hasAdvanced) {
                        // [4] Grammar is not advancing, nor is it pushing/popping
                        currentStack = currentStack.safePop()
                        lineTokens.produce(currentStack, lineLength)
                        stop = true
                        break
                    }
                }

                else -> {
                    // IncludeOnlyRule or CaptureRule — should not reach here via match
                    lineTokens.produce(currentStack, captureIndices[0].end)
                }
            }
        }

        if (captureIndices[0].end > currentLinePos) {
            currentLinePos = captureIndices[0].end
            currentIsFirstLine = false
        }
    }

    return currentStack
}

/**
 * Match the current rule against the line at `linePos`.
 */
internal fun matchRule(
    grammar: Grammar,
    lineText: OnigString,
    isFirstLine: Boolean,
    linePos: Int,
    stack: StateStackImpl,
    anchorPosition: Int
): MatchRuleResult? {
    val rule = stack.getRule(grammar) ?: return null
    val ruleScanner = rule.compileAG(
        grammar,
        stack.endRule,
        allowA = isFirstLine,
        allowG = linePos == anchorPosition
    )
    val r = ruleScanner.findNextMatchSync(lineText, linePos) ?: return null
    return MatchRuleResult(
        captureIndices = r.captureIndices,
        matchedRuleId = r.ruleId
    )
}

/**
 * Handle capture rules for a match.
 * Simplified for Stage 4b: no `retokenizeCapturedWithRuleId` support.
 */
internal fun handleCaptures(
    grammar: Grammar,
    lineText: OnigString,
    isFirstLine: Boolean,
    stack: StateStackImpl,
    lineTokens: LineTokens,
    captures: List<CaptureRule?>,
    captureIndices: List<CaptureIndex>
) {
    if (captures.isEmpty()) return

    val lineTextContent = lineText.content
    val len = minOf(captures.size, captureIndices.size)
    val localStack = mutableListOf<LocalStackElement>()
    val maxEnd = captureIndices[0].end

    for (i in 0 until len) {
        val captureRule = captures[i] ?: continue

        val captureIndex = captureIndices[i]

        if (captureIndex.length == 0) continue
        if (captureIndex.start > maxEnd) break

        // Pop captures whose endPos <= captureIndex.start
        while (localStack.isNotEmpty() && localStack.last().endPos <= captureIndex.start) {
            val top = localStack.removeLast()
            lineTokens.produceFromScopes(top.scopes, top.endPos)
        }

        if (localStack.isNotEmpty()) {
            lineTokens.produceFromScopes(localStack.last().scopes, captureIndex.start)
        } else {
            lineTokens.produce(stack, captureIndex.start)
        }

        // Skip retokenizeCapturedWithRuleId for Stage 4b

        val captureRuleScopeName = captureRule.getName(lineTextContent, captureIndices)
        if (captureRuleScopeName != null) {
            val base = if (localStack.isNotEmpty()) localStack.last().scopes else stack.contentNameScopesList
            val captureRuleScopesList = base?.pushAttributed(captureRuleScopeName, grammar)
            if (captureRuleScopesList != null) {
                localStack.add(LocalStackElement(captureRuleScopesList, captureIndex.end))
            }
        }
    }

    // Pop remaining local stack elements
    while (localStack.isNotEmpty()) {
        val top = localStack.removeLast()
        lineTokens.produceFromScopes(top.scopes, top.endPos)
    }
}

private class LocalStackElement(
    val scopes: AttributedScopeStack,
    val endPos: Int
)
