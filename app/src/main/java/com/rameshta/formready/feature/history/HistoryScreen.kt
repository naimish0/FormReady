package com.rameshta.formready.feature.history

import androidx.compose.runtime.Composable
import com.rameshta.formready.R
import com.rameshta.formready.ui.component.EmptyStateScreen

@Composable
fun HistoryScreen() {
    EmptyStateScreen(
        titleRes = R.string.history_title,
        bodyRes = R.string.history_empty,
    )
}
