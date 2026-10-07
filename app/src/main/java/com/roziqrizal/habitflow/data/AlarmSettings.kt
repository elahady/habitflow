package com.roziqrizal.habitflow.data

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import com.roziqrizal.habitflow.domain.prayer.PrayerName
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate

/** Pengaturan alarm (nada, tanggal yang dimatikan sekali) dan pengingat adzan per waktu sholat. */
class AlarmSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _ringtone = MutableStateFlow(prefs.getString(KEY_RINGTONE, null))
    /** Uri nada pilihan pengguna, atau null untuk nada alarm bawaan HP. */
    val ringtone: StateFlow<String?> = _ringtone.asStateFlow()

    private val _skipped = MutableStateFlow(readSkipped())
    /** Tanggal yang alarmnya dimatikan sekali. Tanggal yang sudah lewat dibuang saat dibaca. */
    val skipped: StateFlow<Set<LocalDate>> = _skipped.asStateFlow()

    private val _adzan = MutableStateFlow(PrayerName.entries.associateWith { prefs.getBoolean(adzanKey(it), true) })
    /** Pengingat adzan per waktu sholat. Default semua nyala. */
    val adzan: StateFlow<Map<PrayerName, Boolean>> = _adzan.asStateFlow()

    fun ringtoneUri(): Uri =
        _ringtone.value?.let(Uri::parse) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)

    fun setRingtone(uri: String?) {
        prefs.edit().apply { if (uri == null) remove(KEY_RINGTONE) else putString(KEY_RINGTONE, uri) }.apply()
        _ringtone.value = uri
    }

    fun setSkipped(date: LocalDate, skip: Boolean) {
        val updated = if (skip) _skipped.value + date else _skipped.value - date
        prefs.edit().putStringSet(KEY_SKIPPED, updated.map { it.toString() }.toSet()).apply()
        _skipped.value = updated
    }

    fun isAdzanEnabled(name: PrayerName): Boolean = _adzan.value[name] ?: true

    fun setAdzanEnabled(name: PrayerName, enabled: Boolean) {
        prefs.edit().putBoolean(adzanKey(name), enabled).apply()
        _adzan.value = _adzan.value + (name to enabled)
    }

    private fun readSkipped(): Set<LocalDate> {
        val today = LocalDate.now()
        return prefs.getStringSet(KEY_SKIPPED, emptySet()).orEmpty()
            .mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
            .filter { !it.isBefore(today) }
            .toSet()
    }

    private fun adzanKey(name: PrayerName) = "adzan_${name.name}"

    private companion object {
        const val KEY_RINGTONE = "alarm_ringtone"
        const val KEY_SKIPPED = "alarm_skipped"
    }
}
