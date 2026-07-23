package com.rameshta.formready.feature.scanner

internal object ScannerPageOrdering {
    fun <T> move(items: List<T>, index: Int, delta: Int): List<T> {
        val destination = index + delta
        if (index !in items.indices || destination !in items.indices) return items
        return items.toMutableList().apply {
            add(destination, removeAt(index))
        }
    }
}
