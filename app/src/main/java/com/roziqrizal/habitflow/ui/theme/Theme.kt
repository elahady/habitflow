package com.roziqrizal.habitflow.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/*
 * Palet diambil dari homepage roziqrizal.com (resources/css/app.css, token --color-*).
 * Ubah di sana dulu, lalu samakan di sini.
 */

private val LightColors = lightColorScheme(
    primary = Color(0xFF4D6359),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCFE8DB),
    onPrimaryContainer = Color(0xFF273C33),
    secondary = Color(0xFF48626E),
    secondaryContainer = Color(0xFFCBE7F5),
    background = Color(0xFFFAF9F7),
    onBackground = Color(0xFF1B1C1B),
    surface = Color(0xFFFAF9F7),
    onSurface = Color(0xFF1B1C1B),
    onSurfaceVariant = Color(0xFF424845),
    surfaceVariant = Color(0xFFE3E2E0),
    surfaceContainer = Color(0xFFEFEEEC),
    surfaceContainerHigh = Color(0xFFE9E8E6),
    outline = Color(0xFF727874),
    outlineVariant = Color(0xFFC2C8C3),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB3CCBF),
    onPrimary = Color(0xFF1E3028),
    primaryContainer = Color(0xFF354B42),
    onPrimaryContainer = Color(0xFFCFE8DB),
    secondary = Color(0xFFAFCBD9),
    secondaryContainer = Color(0xFF30495A),
    background = Color(0xFF121413),
    onBackground = Color(0xFFE3E2E0),
    surface = Color(0xFF121413),
    onSurface = Color(0xFFE3E2E0),
    onSurfaceVariant = Color(0xFFC2C8C3),
    surfaceVariant = Color(0xFF424845),
    surfaceContainer = Color(0xFF1E201F),
    surfaceContainerHigh = Color(0xFF282A29),
    outline = Color(0xFF8C928E),
    outlineVariant = Color(0xFF424845),
)

/** Warna kotak heatmap untuk level 0-4. Level 0 abu-abu netral, level 4 paling pekat. */
data class HeatColors(val levels: List<Color>) {
    fun forLevel(level: Int): Color = levels[level.coerceIn(0, 4)]
}

private val LightHeat = HeatColors(
    listOf(
        Color(0xFFE3E2E0),
        Color(0xFFCFE8DB),
        Color(0xFFB3CCBF),
        Color(0xFF8FA79B),
        Color(0xFF4D6359),
    )
)

private val DarkHeat = HeatColors(
    listOf(
        Color(0xFF2B2D2C),
        Color(0xFF354B42),
        Color(0xFF4D6359),
        Color(0xFF8FA79B),
        Color(0xFFCFE8DB),
    )
)

val LocalHeatColors = staticCompositionLocalOf { LightHeat }

@Composable
fun HabitFlowTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val heat = if (darkTheme) DarkHeat else LightHeat
    CompositionLocalProvider(LocalHeatColors provides heat) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            content = content,
        )
    }
}
