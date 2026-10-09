package com.roziqrizal.habitflow.data

import android.content.Context
import com.roziqrizal.habitflow.domain.health.BpFrequency
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate

/**
 * Pengaturan kesehatan (tahap 21): tinggi badan, target berat, dan pengingat. Tinggi dan target kosong (null) sampai
 * diisi; target kosong berarti memakai target bawaan (batas atas BMI normal).
 */
class HealthSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _heightCm = MutableStateFlow(readDouble(KEY_HEIGHT))
    val heightCm: StateFlow<Double?> = _heightCm.asStateFlow()

    private val _targetKg = MutableStateFlow(readDouble(KEY_TARGET))
    val targetKg: StateFlow<Double?> = _targetKg.asStateFlow()

    private val _weightReminder = MutableStateFlow(prefs.getBoolean(KEY_WEIGHT_REMINDER, true))
    val weightReminder: StateFlow<Boolean> = _weightReminder.asStateFlow()

    private val _bpFrequency = MutableStateFlow(readFrequency())
    val bpFrequency: StateFlow<BpFrequency> = _bpFrequency.asStateFlow()

    fun setHeightCm(value: Double?) {
        writeDouble(KEY_HEIGHT, value)
        _heightCm.value = value
    }

    fun setTargetKg(value: Double?) {
        writeDouble(KEY_TARGET, value)
        _targetKg.value = value
    }

    fun setWeightReminder(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WEIGHT_REMINDER, enabled).apply()
        _weightReminder.value = enabled
    }

    fun setBpFrequency(value: BpFrequency) {
        prefs.edit().putString(KEY_BP_FREQUENCY, value.name).apply()
        _bpFrequency.value = value
    }

    /** Tanggal terakhir habit langkah dicentang otomatis. Khusus perangkat ini, tidak ikut snapshot. */
    var lastStepsAutoCheck: LocalDate?
        get() = prefs.getString(KEY_LAST_STEPS_CHECK, null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        set(value) {
            prefs.edit().putString(KEY_LAST_STEPS_CHECK, value?.toString()).apply()
        }

    private fun readDouble(key: String): Double? =
        if (prefs.contains(key)) prefs.getString(key, null)?.toDoubleOrNull() else null

    // Disimpan sebagai teks supaya satu desimal tidak berubah oleh pembulatan float.
    private fun writeDouble(key: String, value: Double?) {
        prefs.edit().apply { if (value == null) remove(key) else putString(key, value.toString()) }.apply()
    }

    private fun readFrequency(): BpFrequency =
        BpFrequency.entries.firstOrNull { it.name == prefs.getString(KEY_BP_FREQUENCY, null) } ?: BpFrequency.WEEKLY

    private companion object {
        const val KEY_HEIGHT = "health_height_cm"
        const val KEY_TARGET = "health_target_kg"
        const val KEY_WEIGHT_REMINDER = "health_weight_reminder"
        const val KEY_BP_FREQUENCY = "health_bp_frequency"
        const val KEY_LAST_STEPS_CHECK = "health_last_steps_auto_check"
    }
}
