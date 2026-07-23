package com.rameshta.formready.core.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

internal val FormReadyTypography = Typography().run {
    copy(
        displaySmall = displaySmall.copy(fontWeight = FontWeight.Bold, lineHeight = 48.sp),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = bodyLarge.copy(lineHeight = 24.sp),
    )
}
