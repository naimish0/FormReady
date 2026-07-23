package com.rameshta.formready.feature.presets

import androidx.compose.runtime.Composable
import com.rameshta.formready.R
import com.rameshta.formready.ui.component.EmptyStateScreen

@Composable
fun PresetsScreen() {
    EmptyStateScreen(
        titleRes = R.string.presets_title,
        bodyRes = R.string.presets_empty,
    )
}
