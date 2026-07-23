package com.rameshta.formready.feature.settings

internal data class LocalDiagnostics(
    val appVersion: String,
    val androidApi: Int,
    val availableMemoryMiB: Long,
    val availableStorageMiB: Long,
)

internal fun LocalDiagnostics.render(): String = buildString {
    appendLine("FormReady diagnostics")
    appendLine("App version: $appVersion")
    appendLine("Android API: $androidApi")
    appendLine("Available memory class: $availableMemoryMiB MiB")
    appendLine("Available internal storage class: $availableStorageMiB MiB")
    appendLine("Build type: local")
}
