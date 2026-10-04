package com.roziqrizal.habitflow.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** Bentuk seperti Rizqflow: kecil 8, sedang 16 (kartu), besar 20 (kartu hero). */
val HabitFlowShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
)

internal val LocalHeatColors = staticCompositionLocalOf { LightHeat }

@Composable
fun HabitFlowTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalHeatColors provides if (darkTheme) DarkHeat else LightHeat,
        LocalHabitFlowTokens provides if (darkTheme) DarkTokens else LightTokens,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) HabitFlowDarkScheme else HabitFlowLightScheme,
            typography = HabitFlowTypography,
            shapes = HabitFlowShapes,
            content = content,
        )
    }
}
