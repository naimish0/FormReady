package com.rameshta.formready.feature.scanner

import kotlin.math.abs

internal object ManualCropGeometry {
    fun isValid(corners: List<Float>): Boolean {
        if (corners.size != 8 || corners.any { it !in 0f..1f }) return false
        val points = List(4) { index -> corners[index * 2] to corners[index * 2 + 1] }
        val crosses = points.indices.map { index ->
            val first = points[index]
            val second = points[(index + 1) % 4]
            val third = points[(index + 2) % 4]
            (second.first - first.first) * (third.second - second.second) -
                (second.second - first.second) * (third.first - second.first)
        }
        val area = points.indices.sumOf { index ->
            val current = points[index]
            val next = points[(index + 1) % 4]
            (current.first * next.second - next.first * current.second).toDouble()
        }.let(::abs) / 2.0
        return area >= MINIMUM_NORMALIZED_AREA &&
            (crosses.all { it > 0f } || crosses.all { it < 0f })
    }

    private const val MINIMUM_NORMALIZED_AREA = 0.01
}
