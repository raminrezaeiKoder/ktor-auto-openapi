package com.example.autoswagger

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentSkipListSet

internal object Observed {
    private val map = ConcurrentHashMap<String, ConcurrentSkipListSet<Int>>()

    fun add(method: String, patternPath: String, code: Int) {
        val key = "$method $patternPath"
        map.computeIfAbsent(key) { ConcurrentSkipListSet() }.add(code)
    }

    fun get(method: String, patternPath: String): Set<Int> = map["$method $patternPath"] ?: emptySet()
}
