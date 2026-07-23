package com.rameshta.formready.core.processing

import com.rameshta.formready.core.model.TargetSizePolicy

data class JpegQualityProbe(
    val quality: Int,
    val byteCount: Long,
)

data class JpegQualitySolution(
    val quality: Int,
    val probes: List<JpegQualityProbe>,
)

/**
 * A bounded quality search that keeps the highest passing candidate it actually measured.
 *
 * The initial coarse sweep deliberately samples independent quality points. The refinement
 * therefore never discards an already passing candidate based on an assumption that encoded
 * sizes are perfectly monotonic.
 */
object JpegQualitySolver {
    fun solve(
        byteCap: Long,
        encode: (quality: Int) -> Long,
    ): JpegQualitySolution? {
        require(byteCap > 0L)
        val probes = linkedMapOf<Int, JpegQualityProbe>()

        fun probe(quality: Int) {
            if (probes.size >= TargetSizePolicy.MAXIMUM_FULL_ENCODE_PASSES) return
            if (quality !in TargetSizePolicy.HARD_QUALITY_FLOOR..100) return
            if (quality in probes) return
            probes[quality] = JpegQualityProbe(quality, encode(quality))
        }

        listOf(100, 75, 50, TargetSizePolicy.HARD_QUALITY_FLOOR).forEach(::probe)
        repeat(TargetSizePolicy.MAXIMUM_FULL_ENCODE_PASSES - probes.size) {
            val passing = probes.values.filter { it.byteCount <= byteCap }
            if (passing.isEmpty()) return@repeat
            val best = passing.maxBy(JpegQualityProbe::quality)
            val upperFailure = probes.values
                .filter { it.quality > best.quality && it.byteCount > byteCap }
                .minByOrNull(JpegQualityProbe::quality)
            val next = upperFailure
                ?.let { failure -> (best.quality + failure.quality + 1) / 2 }
                ?: (best.quality + 5).coerceAtMost(100)
            probe(next)
        }

        val best = probes.values
            .filter { it.byteCount <= byteCap }
            .maxByOrNull(JpegQualityProbe::quality)
            ?: return null
        return JpegQualitySolution(best.quality, probes.values.toList())
    }
}
