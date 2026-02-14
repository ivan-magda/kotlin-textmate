package dev.textmate.grammar

import dev.textmate.regex.IOnigLib

internal class RegExpSourceList {

    private val _items = mutableListOf<RegExpSource>()
    private var _hasAnchors = false
    private var _cached: CompiledRule? = null
    private var _anchorCache_A0_G0: CompiledRule? = null
    private var _anchorCache_A0_G1: CompiledRule? = null
    private var _anchorCache_A1_G0: CompiledRule? = null
    private var _anchorCache_A1_G1: CompiledRule? = null

    fun push(item: RegExpSource) {
        _items.add(item)
        _hasAnchors = _hasAnchors || item.hasAnchor
    }

    fun unshift(item: RegExpSource) {
        _items.add(0, item)
        _hasAnchors = _hasAnchors || item.hasAnchor
    }

    fun length(): Int = _items.size

    fun setSource(index: Int, newSource: String) {
        if (_items[index].source != newSource) {
            disposeCaches()
            _items[index].setSource(newSource)
        }
    }

    fun compile(onigLib: IOnigLib): CompiledRule {
        val cached = _cached
        if (cached != null) return cached
        val result = CompiledRule(onigLib, _items.map { it.source }, _items.map { it.ruleId })
        _cached = result
        return result
    }

    fun compileAG(onigLib: IOnigLib, allowA: Boolean, allowG: Boolean): CompiledRule {
        if (!_hasAnchors) return compile(onigLib)

        val cached = when {
            allowA && allowG -> _anchorCache_A1_G1
            allowA -> _anchorCache_A1_G0
            allowG -> _anchorCache_A0_G1
            else -> _anchorCache_A0_G0
        }
        if (cached != null) return cached

        val result = resolveAnchors(onigLib, allowA, allowG)
        when {
            allowA && allowG -> _anchorCache_A1_G1 = result
            allowA -> _anchorCache_A1_G0 = result
            allowG -> _anchorCache_A0_G1 = result
            else -> _anchorCache_A0_G0 = result
        }
        return result
    }

    private fun resolveAnchors(onigLib: IOnigLib, allowA: Boolean, allowG: Boolean): CompiledRule {
        val regExps = _items.map { it.resolveAnchors(allowA, allowG) }
        return CompiledRule(onigLib, regExps, _items.map { it.ruleId })
    }

    private fun disposeCaches() {
        _cached = null
        _anchorCache_A0_G0 = null
        _anchorCache_A0_G1 = null
        _anchorCache_A1_G0 = null
        _anchorCache_A1_G1 = null
    }
}
