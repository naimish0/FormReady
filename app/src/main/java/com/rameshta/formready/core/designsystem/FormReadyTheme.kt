package com.rameshta.formready.core.designsystem

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val BrandBlue = Color(0xFF2563EB)
private val BrandBlueDark = Color(0xFF93C5FD)
private val Slate950 = Color(0xFF0F172A)
private val Slate900 = Color(0xFF172033)
private val Slate100 = Color(0xFFF1F5F9)
private val Slate50 = Color(0xFFF8FAFC)

private val LightScheme = lightColorScheme(
    primary = BrandBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDBEAFE),
    onPrimaryContainer = Color(0xFF172554),
    background = Slate50,
    onBackground = Slate950,
    surface = Color.White,
    onSurface = Slate950,
    surfaceVariant = Slate100,
    onSurfaceVariant = Color(0xFF334155),
    error = Color(0xFFDC2626),
)

private val DarkScheme = darkColorScheme(
    primary = BrandBlueDark,
    onPrimary = Color(0xFF172554),
    primaryContainer = Color(0xFF1E3A8A),
    onPrimaryContainer = Color(0xFFDBEAFE),
    background = Slate950,
    onBackground = Slate100,
    surface = Slate900,
    onSurface = Slate100,
    surfaceVariant = Color(0xFF273449),
    onSurfaceVariant = Color(0xFFCBD5E1),
    error = Color(0xFFFCA5A5),
)

@Immutable
data class FormReadySemanticColors(
    val success: Color,
    val warning: Color,
    val failure: Color,
)

private val LocalSemanticColors = staticCompositionLocalOf {
    FormReadySemanticColors(
        success = Color(0xFF16A34A),
        warning = Color(0xFFD97706),
        failure = Color(0xFFDC2626),
    )
}

object FormReadyThemeTokens {
    val semanticColors: FormReadySemanticColors
        @Composable get() = LocalSemanticColors.current
}

@Composable
fun FormReadyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    useDynamicColour: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        useDynamicColour && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && darkTheme ->
            dynamicDarkColorScheme(context)
        useDynamicColour && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            dynamicLightColorScheme(context)
        darkTheme -> DarkScheme
        else -> LightScheme
    }
    val semanticColors = if (darkTheme) {
        FormReadySemanticColors(
            success = Color(0xFF4ADE80),
            warning = Color(0xFFFBBF24),
            failure = Color(0xFFFCA5A5),
        )
    } else {
        FormReadySemanticColors(
            success = Color(0xFF16A34A),
            warning = Color(0xFFD97706),
            failure = Color(0xFFDC2626),
        )
    }

    androidx.compose.runtime.CompositionLocalProvider(LocalSemanticColors provides semanticColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = FormReadyTypography,
            content = content,
        )
    }
}
