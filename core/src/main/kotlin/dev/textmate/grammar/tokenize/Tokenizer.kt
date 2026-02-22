package dev.textmate.grammar.tokenize

import dev.textmate.grammar.Grammar
import dev.textmate.grammar.InjectionPriority
import dev.textmate.grammar.InjectionRule
import dev.textmate.grammar.rule.BeginEndRule
import dev.textmate.grammar.rule.BeginWhileRule
import dev.textmate.grammar.rule.CaptureRule
import dev.textmate.grammar.rule.MatchRule
import dev.textmate.grammar.rule.RuleId
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
 * Injection grammars supported via [matchRuleOrInjections]. Pending: time limits.
 */
internal fun tokenizeString(
    grammar: Grammar,
    lineText: OnigString,
    isFirstLine: Boolean,
    linePos: Int,
    stack: StateStackImpl,
    lineTokens: LineTokens,
    checkWhile: Boolean = false
): StateStackImpl {
    val lineLength = lineText.content.length

    var currentStack = stack
    var currentLinePos = linePos
    var currentIsFirstLine = isFirstLine
    var anchorPosition = -1
    var stop = false

    if (checkWhile) {
        val r = checkWhileConditions(grammar, lineText, currentIsFirstLine, currentLinePos, currentStack, lineTokens)
        currentStack = r.stack
        currentLinePos = r.linePos
        currentIsFirstLine = r.isFirstLine
        anchorPosition = r.anchorPosition
    }

    while (!stop) {
        val r = matchRuleOrInjections(grammar, lineText, currentIsFirstLine, currentLinePos, currentStack, anchorPosition)

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
 * Match the best rule (normal or injection) against the line at [linePos].
 * Port of `matchRuleOrInjections` from vscode-textmate `tokenizeString.ts`.
 */
internal fun matchRuleOrInjections(
    grammar: Grammar,
    lineText: OnigString,
    isFirstLine: Boolean,
    linePos: Int,
    stack: StateStackImpl,
    anchorPosition: Int
): MatchRuleResult? {
    val matchResult = matchRule(grammar, lineText, isFirstLine, linePos, stack, anchorPosition)

    val injections = grammar.getInjections()
    if (injections.isEmpty()) return matchResult

    val injectionResult = matchInjections(injections, grammar, lineText, isFirstLine, linePos, stack, anchorPosition)
        ?: return matchResult

    if (matchResult == null) return injectionResult.matchRuleResult

    val matchStart = matchResult.captureIndices[0].start
    val injStart = injectionResult.matchRuleResult.captureIndices[0].start

    if (injStart < matchStart || (injectionResult.priorityMatch && injStart == matchStart)) {
        return injectionResult.matchRuleResult
    }
    return matchResult
}

/**
 * Best injection match with priority metadata for tie-breaking against normal rules.
 */
private class MatchInjectionsResult(
    val priorityMatch: Boolean,
    val matchRuleResult: MatchRuleResult
)

/**
 * Scan all applicable injection rules and return the best match.
 * Port of `matchInjections` from vscode-textmate `tokenizeString.ts`.
 */
private fun matchInjections(
    injections: List<InjectionRule>,
    grammar: Grammar,
    lineText: OnigString,
    isFirstLine: Boolean,
    linePos: Int,
    stack: StateStackImpl,
    anchorPosition: Int
): MatchInjectionsResult? {
    var bestMatchStart = Int.MAX_VALUE
    var bestResult: MatchRuleResult? = null
    var bestPriority = InjectionPriority.DEFAULT

    val scopes = stack.contentNameScopesList?.getScopeNames() ?: return null

    for (injection in injections) {
        if (!injection.matcher(scopes)) continue

        val rule = grammar.getRule(injection.ruleId) ?: continue
        val ruleScanner = rule.compileAG(
            grammar,
            endRegexSource = null,
            allowA = isFirstLine,
            allowG = linePos == anchorPosition
        )
        val matchResult = ruleScanner.findNextMatchSync(lineText, linePos) ?: continue
        if (matchResult.captureIndices.isEmpty()) continue

        val matchStart = matchResult.captureIndices[0].start
        if (matchStart >= bestMatchStart) continue

        bestMatchStart = matchStart
        bestResult = MatchRuleResult(matchResult.captureIndices, matchResult.ruleId)
        bestPriority = injection.priority

        if (bestMatchStart == linePos) break
    }

    val result = bestResult ?: return null
    return MatchInjectionsResult(
        priorityMatch = bestPriority == InjectionPriority.HIGH,
        matchRuleResult = result
    )
}

/**
 * Handle capture rules for a match.
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
            val top = localStack.removeAt(localStack.lastIndex)
            lineTokens.produceFromScopes(top.scopes, top.endPos)
        }

        if (localStack.isNotEmpty()) {
            lineTokens.produceFromScopes(localStack.last().scopes, captureIndex.start)
        } else {
            lineTokens.produce(stack, captureIndex.start)
        }

        if (captureRule.retokenizeCapturedWithRuleId != RuleId.NO_RULE) {
            val scopeName = captureRule.getName(lineTextContent, captureIndices)
            val nameScopesList = checkNotNull(stack.contentNameScopesList) {
                "contentNameScopesList must not be null during capture retokenization"
            }.pushAttributed(scopeName, grammar)
            val contentName = captureRule.getContentName(lineTextContent, captureIndices)
            val contentNameScopesList = nameScopesList.pushAttributed(contentName, grammar)

            val stackClone = stack.push(
                ruleId = captureRule.retokenizeCapturedWithRuleId,
                enterPos = captureIndex.start,
                anchorPos = -1,
                beginRuleCapturedEOL = false,
                endRule = null,
                nameScopesList = nameScopesList,
                contentNameScopesList = contentNameScopesList
            )
            val onigSubStr = grammar.createOnigString(
                lineTextContent.substring(0, captureIndex.end)
            )
            tokenizeString(
                grammar = grammar,
                lineText = onigSubStr,
                isFirstLine = isFirstLine && captureIndex.start == 0,
                linePos = captureIndex.start,
                stack = stackClone,
                lineTokens = lineTokens
            )
            continue
        }

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
        val top = localStack.removeAt(localStack.lastIndex)
        lineTokens.produceFromScopes(top.scopes, top.endPos)
    }
}

private class LocalStackElement(
    val scopes: AttributedScopeStack,
    val endPos: Int
)

private data class WhileCheckResult(
    val stack: StateStackImpl,
    val linePos: Int,
    val anchorPosition: Int,
    val isFirstLine: Boolean
)

/**
 * Check while conditions for BeginWhileRule frames on the stack.
 * Port of `_checkWhileConditions` from vscode-textmate `tokenizeString.ts`.
 */
private fun checkWhileConditions(
    grammar: Grammar,
    lineText: OnigString,
    isFirstLine: Boolean,
    linePos: Int,
    stack: StateStackImpl,
    lineTokens: LineTokens
): WhileCheckResult {
    var anchorPosition = if (stack.beginRuleCapturedEOL) 0 else -1
    var currentStack: StateStackImpl = stack
    var currentLinePos = linePos
    var currentIsFirstLine = isFirstLine

    // Walk stack bottom-to-top, collect BeginWhileRule frames
    data class WhileStackEntry(val stack: StateStackImpl, val rule: BeginWhileRule)
    val whileRules = mutableListOf<WhileStackEntry>()
    var node: StateStackImpl? = currentStack
    while (node != null) {
        val nodeRule = node.getRule(grammar)
        if (nodeRule is BeginWhileRule) {
            whileRules.add(WhileStackEntry(node, nodeRule))
        }
        node = node.pop()
    }

    // Process in reverse (outermost BeginWhileRule first)
    while (whileRules.isNotEmpty()) {
        val entry = whileRules.removeAt(whileRules.lastIndex)
        val ruleScanner = entry.rule.compileWhileAG(
            grammar, entry.stack.endRule,
            allowA = currentIsFirstLine,
            allowG = currentLinePos == anchorPosition
        )
        val r = ruleScanner.findNextMatchSync(lineText, currentLinePos)

        if (r != null) {
            if (r.ruleId != RuleId.WHILE_RULE) {
                // Shouldn't happen — pop and stop
                currentStack = checkNotNull(entry.stack.pop()) {
                    "BeginWhileRule must have a parent stack frame"
                }
                break
            }
            if (r.captureIndices.isNotEmpty()) {
                lineTokens.produce(entry.stack, r.captureIndices[0].start)
                handleCaptures(
                    grammar, lineText, currentIsFirstLine, entry.stack, lineTokens,
                    entry.rule.whileCaptures, r.captureIndices
                )
                lineTokens.produce(entry.stack, r.captureIndices[0].end)
                anchorPosition = r.captureIndices[0].end
                if (r.captureIndices[0].end > currentLinePos) {
                    currentLinePos = r.captureIndices[0].end
                    currentIsFirstLine = false
                }
            }
        } else {
            // While condition failed — pop this rule and stop
            currentStack = checkNotNull(entry.stack.pop()) {
                "BeginWhileRule must have a parent stack frame"
            }
            break
        }
    }

    return WhileCheckResult(currentStack, currentLinePos, anchorPosition, currentIsFirstLine)
}
