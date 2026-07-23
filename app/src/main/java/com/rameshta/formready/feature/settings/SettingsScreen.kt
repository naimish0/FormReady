package com.rameshta.formready.feature.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.rameshta.formready.R
import com.rameshta.formready.core.data.settings.DefaultDimensionUnit
import com.rameshta.formready.core.data.settings.DefaultImageFormat
import com.rameshta.formready.core.data.settings.ThemePreference
import com.rameshta.formready.core.data.settings.UserSettings
import com.rameshta.formready.core.monetization.ProState
import com.rameshta.formready.core.monetization.ProStatus
import com.rameshta.formready.ui.component.BeginnerGuidanceCard
import com.rameshta.formready.ui.component.OptionalSection

@Composable
fun SettingsScreen(
    settings: UserSettings,
    onThemeSelected: (ThemePreference) -> Unit,
    onDynamicColourChanged: (Boolean) -> Unit,
    onSettingsChanged: (UserSettings.() -> UserSettings) -> Unit,
    onRestoreSettings: () -> Unit,
    onClearHistory: () -> Unit,
    onClearTemporaryFiles: () -> Unit,
    proState: ProState,
    onPurchasePro: () -> Unit,
    onRestorePro: () -> Unit,
) {
    val context = LocalContext.current
    var pendingAction by remember { mutableStateOf<SettingsDestructiveAction?>(null) }
    val versionName = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "—"
    }
    val privacyPolicyUrl = stringResource(R.string.privacy_policy_url)
    val diagnostics = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri, "w")?.bufferedWriter()?.use { writer ->
                    writer.write(
                        LocalDiagnostics(
                            appVersion = versionName,
                            androidApi = Build.VERSION.SDK_INT,
                            availableMemoryMiB = Runtime.getRuntime().maxMemory() / 1_048_576,
                            availableStorageMiB = context.filesDir.usableSpace / 1_048_576,
                        ).render(),
                    )
                }
            }
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text(
                stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
            BeginnerGuidanceCard(
                title = stringResource(R.string.settings_beginner_title),
                body = stringResource(R.string.settings_beginner_help),
            )
        }
        item {
            SettingHeading(stringResource(R.string.settings_language))
            Button(
                onClick = {
                    val action = if (Build.VERSION.SDK_INT >= 33) {
                        Settings.ACTION_APP_LOCALE_SETTINGS
                    } else {
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                    }
                    context.startActivity(
                        Intent(action).setData(Uri.parse("package:${context.packageName}")),
                    )
                },
            ) { Text(stringResource(R.string.settings_language_system)) }
        }
        item {
            SettingHeading(stringResource(R.string.settings_theme))
            ThemePreference.entries.forEach { preference ->
                ChoiceRow(
                    selected = settings.theme == preference,
                    label = when (preference) {
                        ThemePreference.SYSTEM -> stringResource(R.string.theme_system)
                        ThemePreference.LIGHT -> stringResource(R.string.theme_light)
                        ThemePreference.DARK -> stringResource(R.string.theme_dark)
                    },
                    onClick = { onThemeSelected(preference) },
                )
            }
        }
        item {
            ToggleRow(
                stringResource(R.string.settings_dynamic_colour),
                settings.useDynamicColour,
                onDynamicColourChanged,
            )
        }
        item {
            SettingHeading(stringResource(R.string.settings_units))
            DefaultDimensionUnit.entries.forEach { unit ->
                ChoiceRow(
                    settings.dimensionUnit == unit,
                    stringResource(
                        when (unit) {
                            DefaultDimensionUnit.PIXELS -> R.string.unit_pixels
                            DefaultDimensionUnit.MILLIMETRES -> R.string.unit_millimetres
                            DefaultDimensionUnit.CENTIMETRES -> R.string.unit_centimetres
                            DefaultDimensionUnit.INCHES -> R.string.unit_inches_long
                        },
                    ),
                ) { onSettingsChanged { copy(dimensionUnit = unit) } }
            }
        }
        item {
            SettingHeading(stringResource(R.string.settings_byte_units))
            Text(stringResource(R.string.byte_unit_decimal))
        }
        item {
            SettingHeading(stringResource(R.string.settings_output_destination))
            Text(stringResource(R.string.settings_output_destination_picker))
        }
        item {
            SettingHeading(stringResource(R.string.settings_default_format))
            DefaultImageFormat.entries.forEach { format ->
                ChoiceRow(
                    settings.defaultImageFormat == format,
                    stringResource(
                        if (format == DefaultImageFormat.JPEG) {
                            R.string.image_format_jpeg
                        } else {
                            R.string.image_format_png
                        },
                    ),
                ) {
                    onSettingsChanged { copy(defaultImageFormat = format) }
                }
            }
        }
        item {
            OptionalSection(
                title = stringResource(R.string.settings_privacy_options_title),
                summary = stringResource(R.string.settings_privacy_options_help),
            ) {
                ToggleRow(
                    stringResource(R.string.settings_quality_guard),
                    settings.qualityGuardEnabled,
                ) {
                    onSettingsChanged { copy(qualityGuardEnabled = it) }
                }
                ToggleRow(
                    stringResource(R.string.settings_safety_margin),
                    settings.safetyMarginEnabled,
                ) {
                    onSettingsChanged { copy(safetyMarginEnabled = it) }
                }
                ToggleRow(
                    stringResource(R.string.settings_remove_metadata),
                    settings.removeMetadataByDefault,
                ) { onSettingsChanged { copy(removeMetadataByDefault = it) } }
                ToggleRow(
                    stringResource(R.string.settings_history_enabled),
                    settings.historyEnabled,
                ) {
                    onSettingsChanged { copy(historyEnabled = it) }
                }
                ToggleRow(
                    stringResource(R.string.settings_thumbnails),
                    settings.thumbnailsEnabled,
                ) {
                    onSettingsChanged { copy(thumbnailsEnabled = it) }
                }
                ToggleRow(
                    stringResource(R.string.settings_auto_cleanup),
                    settings.automaticCleanupEnabled,
                ) { onSettingsChanged { copy(automaticCleanupEnabled = it) } }
                ToggleRow(
                    stringResource(R.string.settings_privacy_mode),
                    settings.privacyModeEnabled,
                ) { onSettingsChanged { copy(privacyModeEnabled = it) } }
                ToggleRow(
                    stringResource(R.string.settings_reduced_motion),
                    settings.reducedMotion,
                ) {
                    onSettingsChanged { copy(reducedMotion = it) }
                }
            }
        }
        item {
            OptionalSection(
                title = stringResource(R.string.settings_advanced_title),
                summary = stringResource(R.string.settings_advanced_help),
            ) {
                Text(
                    stringResource(R.string.settings_privacy_summary),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = { pendingAction = SettingsDestructiveAction.CLEAR_HISTORY }) {
                    Text(stringResource(R.string.settings_clear_history))
                }
                Button(onClick = { pendingAction = SettingsDestructiveAction.CLEAR_TEMPORARY }) {
                    Text(stringResource(R.string.settings_clear_temporary))
                }
                Button(onClick = { pendingAction = SettingsDestructiveAction.RESTORE_DEFAULTS }) {
                    Text(stringResource(R.string.settings_restore_defaults))
                }
            }
        }
        if (proState.isConfigured) {
            item {
                SettingHeading(stringResource(R.string.settings_pro_title))
                Text(
                    stringResource(
                        if (proState.isEntitled) {
                            R.string.settings_pro_active
                        } else {
                            R.string.settings_pro_benefits
                        },
                    ),
                )
                Text(
                    stringResource(
                        when (proState.status) {
                            ProStatus.UNCONFIGURED -> R.string.settings_pro_unavailable
                            ProStatus.CONNECTING -> R.string.settings_pro_connecting
                            ProStatus.AVAILABLE -> R.string.settings_pro_available
                            ProStatus.PENDING -> R.string.settings_pro_pending
                            ProStatus.PURCHASED -> R.string.settings_pro_purchased
                            ProStatus.CANCELLED -> R.string.settings_pro_cancelled
                            ProStatus.OFFLINE -> R.string.settings_pro_offline
                            ProStatus.ERROR -> R.string.settings_pro_error
                        },
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (
                    !proState.isEntitled &&
                    proState.status in setOf(ProStatus.AVAILABLE, ProStatus.CANCELLED) &&
                    proState.formattedPrice != null
                ) {
                    Button(onClick = onPurchasePro) {
                        Text(
                            stringResource(
                                R.string.settings_pro_buy,
                                proState.formattedPrice,
                            ),
                        )
                    }
                }
                Button(onClick = onRestorePro) {
                    Text(stringResource(R.string.settings_pro_restore))
                }
                Text(
                    stringResource(R.string.settings_pro_client_only_notice),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            OptionalSection(
                title = stringResource(R.string.settings_support_options_title),
                summary = stringResource(R.string.settings_support_options_help),
            ) {
                Text(stringResource(R.string.settings_version, versionName))
                Text(stringResource(R.string.settings_licenses_summary))
                if (privacyPolicyUrl.isNotBlank()) {
                    Button(onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(privacyPolicyUrl)))
                    }) {
                        Text(stringResource(R.string.settings_privacy_policy))
                    }
                } else {
                    Text(stringResource(R.string.settings_privacy_policy_pending))
                }
                Button(onClick = {
                    diagnostics.launch("formready-diagnostics.txt")
                }) { Text(stringResource(R.string.settings_export_diagnostics)) }
                Button(onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(
                                Intent.ACTION_SENDTO,
                                Uri.parse("mailto:naimish.app@gmail.com"),
                            ),
                        )
                    }
                }) {
                    Text(stringResource(R.string.settings_support))
                }
            }
        }
    }
    pendingAction?.let { action ->
        AlertDialog(
            onDismissRequest = { pendingAction = null },
            title = { Text(stringResource(action.titleRes)) },
            text = { Text(stringResource(action.messageRes)) },
            confirmButton = {
                TextButton(onClick = {
                    when (action) {
                        SettingsDestructiveAction.CLEAR_HISTORY -> onClearHistory()
                        SettingsDestructiveAction.CLEAR_TEMPORARY -> onClearTemporaryFiles()
                        SettingsDestructiveAction.RESTORE_DEFAULTS -> onRestoreSettings()
                    }
                    pendingAction = null
                }) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingAction = null }) {
                    Text(stringResource(R.string.action_keep))
                }
            },
        )
    }
}

private enum class SettingsDestructiveAction(
    val titleRes: Int,
    val messageRes: Int,
) {
    CLEAR_HISTORY(R.string.settings_clear_history, R.string.settings_clear_history_confirm),
    CLEAR_TEMPORARY(
        R.string.settings_clear_temporary,
        R.string.settings_clear_temporary_confirm,
    ),
    RESTORE_DEFAULTS(
        R.string.settings_restore_defaults,
        R.string.settings_restore_defaults_confirm,
    ),
}

@Composable
private fun SettingHeading(value: String) {
    Text(value, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun ChoiceRow(selected: Boolean, label: String, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label)
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChanged: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChanged)
    }
}
