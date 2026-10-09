package com.roziqrizal.habitflow.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Pengingat kerja (tahap 20): minum air dan break bisa dimatikan sendiri-sendiri. Awalnya nyala. */
class WorkReminderSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _water = MutableStateFlow(prefs.getBoolean(KEY_WATER, true))
    val water: StateFlow<Boolean> = _water.asStateFlow()

    private val _breaks = MutableStateFlow(prefs.getBoolean(KEY_BREAK, true))
    val breaks: StateFlow<Boolean> = _breaks.asStateFlow()

    fun setWater(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WATER, enabled).apply()
        _water.value = enabled
    }

    fun setBreaks(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BREAK, enabled).apply()
        _breaks.value = enabled
    }

    private companion object {
        const val KEY_WATER = "work_reminder_water"
        const val KEY_BREAK = "work_reminder_break"
    }
}
