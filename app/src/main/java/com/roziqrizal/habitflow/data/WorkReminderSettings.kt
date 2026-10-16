package com.roziqrizal.habitflow.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Pengingat kerja (tahap 20): minum air dan break bisa dimatikan sendiri-sendiri. Awalnya nyala. */
class WorkReminderSettings(context: Context) {
    private val prefs = PrefFile.SETTINGS.open(context)

    private val _water = MutableStateFlow(prefs.getBoolean(KEY_WATER, true))
    val water: StateFlow<Boolean> = _water.asStateFlow()

    private val _breaks = MutableStateFlow(prefs.getBoolean(KEY_BREAK, true))
    val breaks: StateFlow<Boolean> = _breaks.asStateFlow()

    private val _waterAlarm = MutableStateFlow(prefs.getBoolean(KEY_WATER_ALARM, false))
    /**
     * Pengingat minum berbunyi sebagai alarm (bunyi dan getar sampai dijawab) alih-alih notifikasi senyap. Awalnya mati
     * dan hanya di HP ini (tidak ikut snapshot). Nada mengikuti pengaturan Alarm ([AlarmSettings]).
     */
    val waterAlarm: StateFlow<Boolean> = _waterAlarm.asStateFlow()

    fun setWaterAlarm(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WATER_ALARM, enabled).apply()
        _waterAlarm.value = enabled
    }

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
        const val KEY_WATER_ALARM = "work_reminder_water_alarm"
    }
}
