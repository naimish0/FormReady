package com.rameshta.formready.core.processing

import com.rameshta.formready.core.model.NormalizedTransform
import com.rameshta.formready.core.model.ByteUnit
import com.rameshta.formready.core.model.DimensionRule
import com.rameshta.formready.core.model.PhysicalUnit
import com.rameshta.formready.core.model.OutputFormat
import com.rameshta.formready.core.model.OutputSpecification
import com.rameshta.formready.core.model.ProcessingPlan
import com.rameshta.formready.core.model.ValidationOutcome
import com.rameshta.formready.core.model.ValidationRuleResult
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

object PhotoPlanCodec {
    private const val SCHEMA_VERSION = 1

    fun encode(plan: ProcessingPlan): String = JSONObject()
        .put("schemaVersion", SCHEMA_VERSION)
        .put("jobId", plan.jobId.toString())
        .put(
            "output",
            JSONObject()
                .put("format", plan.output.format.name)
                .putNullable("widthPx", plan.output.widthPx)
                .putNullable("heightPx", plan.output.heightPx)
                .put("dimensionRule", plan.output.dimensionRule.name)
                .put("byteUnit", plan.output.byteUnit.name)
                .putNullable("physicalWidth", plan.output.physicalWidth)
                .putNullable("physicalHeight", plan.output.physicalHeight)
                .putNullable("physicalUnit", plan.output.physicalUnit?.name)
                .putNullable("maximumBytes", plan.output.maximumBytes)
                .putNullable("minimumBytes", plan.output.minimumBytes)
                .putNullable("dpi", plan.output.dpi)
                .putNullable("safetyMarginBytes", plan.output.safetyMarginBytes)
                .put("backgroundArgb", plan.output.backgroundArgb),
        )
        .put(
            "transforms",
            JSONArray().apply {
                plan.transforms.forEach { transform ->
                    put(
                        when (transform) {
                            is NormalizedTransform.Rotate -> JSONObject()
                                .put("type", "rotate")
                                .put("degrees", transform.degreesClockwise.toDouble())
                            is NormalizedTransform.Crop -> JSONObject()
                                .put("type", "crop")
                                .put("left", transform.left.toDouble())
                                .put("top", transform.top.toDouble())
                                .put("right", transform.right.toDouble())
                                .put("bottom", transform.bottom.toDouble())
                            is NormalizedTransform.FitPad -> JSONObject()
                                .put("type", "fitPad")
                                .put("backgroundArgb", transform.backgroundArgb)
                        },
                    )
                }
            },
        )
        .put("hardRuleIds", JSONArray(plan.hardRuleIds.toList()))
        .put("advisoryRuleIds", JSONArray(plan.advisoryRuleIds.toList()))
        .toString()

    fun decode(value: String): ProcessingPlan {
        val root = JSONObject(value)
        require(root.getInt("schemaVersion") == SCHEMA_VERSION)
        val output = root.getJSONObject("output")
        val transformsJson = root.getJSONArray("transforms")
        val transforms = buildList {
            for (index in 0 until transformsJson.length()) {
                val transform = transformsJson.getJSONObject(index)
                add(
                    when (transform.getString("type")) {
                        "rotate" -> NormalizedTransform.Rotate(
                            transform.getDouble("degrees").toFloat(),
                        )
                        "crop" -> NormalizedTransform.Crop(
                            left = transform.getDouble("left").toFloat(),
                            top = transform.getDouble("top").toFloat(),
                            right = transform.getDouble("right").toFloat(),
                            bottom = transform.getDouble("bottom").toFloat(),
                        )
                        "fitPad" -> NormalizedTransform.FitPad(
                            transform.getInt("backgroundArgb"),
                        )
                        else -> error("Unknown photo transform")
                    },
                )
            }
        }
        return ProcessingPlan(
            jobId = UUID.fromString(root.getString("jobId")),
            transforms = transforms,
            output = OutputSpecification(
                format = OutputFormat.valueOf(output.getString("format")),
                widthPx = output.optionalInt("widthPx"),
                heightPx = output.optionalInt("heightPx"),
                dimensionRule = output.optString(
                    "dimensionRule",
                    DimensionRule.EXACT.name,
                ).let(DimensionRule::valueOf),
                byteUnit = output.optString("byteUnit", ByteUnit.KB.name)
                    .let(ByteUnit::valueOf),
                physicalWidth = output.optionalDouble("physicalWidth"),
                physicalHeight = output.optionalDouble("physicalHeight"),
                physicalUnit = output.optionalString("physicalUnit")?.let(PhysicalUnit::valueOf),
                maximumBytes = output.optionalLong("maximumBytes"),
                minimumBytes = output.optionalLong("minimumBytes"),
                dpi = output.optionalInt("dpi"),
                safetyMarginBytes = output.optionalLong("safetyMarginBytes"),
                backgroundArgb = output.optInt("backgroundArgb", 0xFFFFFFFF.toInt()),
            ),
            hardRuleIds = root.getJSONArray("hardRuleIds").stringSet(),
            advisoryRuleIds = root.getJSONArray("advisoryRuleIds").stringSet(),
        )
    }

    private fun JSONObject.putNullable(key: String, value: Any?): JSONObject =
        put(key, value ?: JSONObject.NULL)

    private fun JSONObject.optionalInt(key: String): Int? =
        if (isNull(key)) null else getInt(key)

    private fun JSONObject.optionalLong(key: String): Long? =
        if (isNull(key)) null else getLong(key)

    private fun JSONObject.optionalDouble(key: String): Double? =
        if (!has(key) || isNull(key)) null else getDouble(key)

    private fun JSONObject.optionalString(key: String): String? =
        if (!has(key) || isNull(key)) null else getString(key)

    private fun JSONArray.stringSet(): Set<String> = buildSet {
        for (index in 0 until length()) add(getString(index))
    }
}

object ValidationResultCodec {
    fun encode(results: List<ValidationRuleResult>): String = JSONArray().apply {
        results.forEach { result ->
            put(
                JSONObject()
                    .put("ruleId", result.ruleId)
                    .put("outcome", result.outcome.name)
                    .put("expected", result.expected)
                    .put("actual", result.actual)
                    .put("explanation", result.explanation)
                    .put("fixAction", result.fixAction ?: JSONObject.NULL)
                    .put("isHardRule", result.isHardRule),
            )
        }
    }.toString()

    fun decode(value: String): List<ValidationRuleResult> {
        val array = JSONArray(value)
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(
                    ValidationRuleResult(
                        ruleId = item.getString("ruleId"),
                        outcome = ValidationOutcome.valueOf(item.getString("outcome")),
                        expected = item.getString("expected"),
                        actual = item.getString("actual"),
                        explanation = item.getString("explanation"),
                        fixAction = if (item.isNull("fixAction")) null else {
                            item.getString("fixAction")
                        },
                        isHardRule = item.getBoolean("isHardRule"),
                    ),
                )
            }
        }
    }
}
