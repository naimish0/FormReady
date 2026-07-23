package com.rameshta.formready.core.model

enum class ValidationOutcome {
    PASS,
    WARNING,
    FAIL,
}

enum class Readiness {
    READY,
    READY_WITH_WARNINGS,
    NOT_READY,
}

data class ValidationRuleResult(
    val ruleId: String,
    val outcome: ValidationOutcome,
    val expected: String,
    val actual: String,
    val explanation: String,
    val fixAction: String?,
    val isHardRule: Boolean,
)

fun Iterable<ValidationRuleResult>.readiness(): Readiness {
    val results = toList()
    return when {
        results.any { it.isHardRule && it.outcome == ValidationOutcome.FAIL } ->
            Readiness.NOT_READY
        results.any { it.outcome == ValidationOutcome.WARNING } ->
            Readiness.READY_WITH_WARNINGS
        else -> Readiness.READY
    }
}
