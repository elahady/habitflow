package com.roziqrizal.habitflow.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Pengaturan notifikasi jadwal. Notifikasi tetap "Sekarang · Berikutnya" nyala secara default. */
class NotificationSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _persistent = MutableStateFlow(prefs.getBoolean(KEY_PERSISTENT, true))
    val persistent: StateFlow<Boolean> = _persistent.asStateFlow()

    fun setPersistent(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PERSISTENT, enabled).apply()
        _persistent.value = enabled
    }

    private companion object {
        const val KEY_PERSISTENT = "notification_persistent"
    }
}
