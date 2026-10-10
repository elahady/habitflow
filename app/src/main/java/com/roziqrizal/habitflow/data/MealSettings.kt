package com.roziqrizal.habitflow.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Pengaturan catatan makan (tahap 23): pengingat sebelum batas tidur bisa dimatikan. Awalnya nyala. */
class MealSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _reminder = MutableStateFlow(prefs.getBoolean(KEY_REMINDER, true))

    /** Pengingat "Belum dicatat: ..." satu jam sebelum batas tidur. */
    val reminder: StateFlow<Boolean> = _reminder.asStateFlow()

    fun setReminder(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_REMINDER, enabled).apply()
        _reminder.value = enabled
    }

    private companion object {
        const val KEY_REMINDER = "meal_reminder"
    }
}
