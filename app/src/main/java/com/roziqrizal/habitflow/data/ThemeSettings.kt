package com.roziqrizal.habitflow.data

import android.app.UiModeManager
import android.content.Context
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Pilihan tampilan yang disimpan di SharedPreferences. Default [ThemeMode.SYSTEM].
 *
 * Di Android 12+ pilihan juga diteruskan ke sistem lewat [UiModeManager.setApplicationNightMode],
 * supaya splash dan latar jendela (values-night) ikut pilihan app, bukan tema HP.
 */
class ThemeSettings(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _mode = MutableStateFlow(
        prefs.getString(KEY_MODE, null)
            ?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } }
            ?: ThemeMode.SYSTEM
    )
    val mode: StateFlow<ThemeMode> = _mode.asStateFlow()

    fun setMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_MODE, mode.name).apply()
        _mode.value = mode
        applyToSystem(mode)
    }

    /** Menyamakan mode malam sistem dengan pilihan tersimpan, misalnya setelah app dipasang ulang. */
    fun syncWithSystem() = applyToSystem(_mode.value)

    private fun applyToSystem(mode: ThemeMode) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val nightMode = when (mode) {
            ThemeMode.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
            ThemeMode.LIGHT -> UiModeManager.MODE_NIGHT_NO
            ThemeMode.DARK -> UiModeManager.MODE_NIGHT_YES
        }
        appContext.getSystemService(UiModeManager::class.java).setApplicationNightMode(nightMode)
    }

    private companion object {
        const val KEY_MODE = "theme_mode"
    }
}
