package com.roziqrizal.habitflow.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/*
 * Palet diambil dari design tokens Rizqflow (Rizqflow/docs/design/tokens.css), yang juga
 * dipakai homepage roziqrizal.com. Ubah di sana dulu, lalu samakan di sini.
 */

internal val HabitFlowLightScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF4D6359),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF8FA79B),
    onPrimaryContainer = Color(0xFF273C33),
    inversePrimary = Color(0xFFB3CCBF),
    secondary = Color(0xFF48626E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCBE7F5),
    onSecondaryContainer = Color(0xFF4E6874),
    background = Color(0xFFFAF9F7),
    onBackground = Color(0xFF1B1C1B),
    surface = Color(0xFFFAF9F7),
    onSurface = Color(0xFF1B1C1B),
    surfaceVariant = Color(0xFFE3E2E0),
    onSurfaceVariant = Color(0xFF424845),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5F3F1),
    surfaceContainer = Color(0xFFEFEEEC),
    surfaceContainerHigh = Color(0xFFE9E8E6),
    surfaceContainerHighest = Color(0xFFE3E2E0),
    inverseSurface = Color(0xFF30312F),
    inverseOnSurface = Color(0xFFF2F0EE),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    outline = Color(0xFF727874),
    outlineVariant = Color(0xFFC2C8C3),
)

internal val HabitFlowDarkScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFFB3CCBF),
    onPrimary = Color(0xFF1F352B),
    primaryContainer = Color(0xFF354B42),
    onPrimaryContainer = Color(0xFFCFE8DB),
    inversePrimary = Color(0xFF4D6359),
    secondary = Color(0xFFAFCBD9),
    onSecondary = Color(0xFF1B3542),
    secondaryContainer = Color(0xFF314A56),
    onSecondaryContainer = Color(0xFFCBE7F5),
    background = Color(0xFF121412),
    onBackground = Color(0xFFE3E3E0),
    surface = Color(0xFF121412),
    onSurface = Color(0xFFE3E3E0),
    surfaceVariant = Color(0xFF333533),
    onSurfaceVariant = Color(0xFFC2C8C3),
    surfaceContainerLowest = Color(0xFF0D0F0E),
    surfaceContainerLow = Color(0xFF1A1C1A),
    surfaceContainer = Color(0xFF1E201E),
    surfaceContainerHigh = Color(0xFF282A28),
    surfaceContainerHighest = Color(0xFF333533),
    inverseSurface = Color(0xFFE3E3E0),
    inverseOnSurface = Color(0xFF30312F),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF8C928E),
    outlineVariant = Color(0xFF424845),
)

/**
 * Token di luar ColorScheme Material 3 versi yang dipakai proyek ini. primaryFixed adalah latar
 * tombol tonal dan indikator navigasi; nilainya sama dengan token Rizqflow.
 */
@Immutable
data class HabitFlowTokens(val primaryFixed: Color)

internal val LightTokens = HabitFlowTokens(primaryFixed = Color(0xFFCFE8DB))
internal val DarkTokens = HabitFlowTokens(primaryFixed = Color(0xFF354B42))

internal val LocalHabitFlowTokens = staticCompositionLocalOf { LightTokens }

val MaterialTheme.tokens: HabitFlowTokens
    @Composable
    @ReadOnlyComposable
    get() = LocalHabitFlowTokens.current

/** Warna kotak heatmap untuk level 0-4. Level 0 abu-abu netral, level 4 paling pekat. */
data class HeatColors(val levels: List<Color>) {
    fun forLevel(level: Int): Color = levels[level.coerceIn(0, 4)]
}

internal val LightHeat = HeatColors(
    listOf(
        Color(0xFFE3E2E0), // surface-container-highest
        Color(0xFFCFE8DB), // primary-fixed
        Color(0xFFB3CCBF), // inverse-primary
        Color(0xFF8FA79B), // primary-container
        Color(0xFF4D6359), // primary
    )
)

internal val DarkHeat = HeatColors(
    listOf(
        Color(0xFF333533), // surface-container-highest
        Color(0xFF354B42), // primary-container
        Color(0xFF4D6359), // inverse-primary (mode gelap)
        Color(0xFF8FA79B), // primary-container terang
        Color(0xFFCFE8DB), // on-primary-container
    )
)
