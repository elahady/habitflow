package com.roziqrizal.habitflow.domain.health

/*
 * Tensi (tahap 21), kategori menurut PERHI/ESH. Kategori adalah informasi, bukan diagnosis, dan tidak pernah memicu
 * alarm: tensi sangat tinggi hanya memunculkan saran tenang.
 */

const val SYSTOLIC_MIN = 50
const val SYSTOLIC_MAX = 300
const val DIASTOLIC_MIN = 30
const val DIASTOLIC_MAX = 200
const val PULSE_MIN = 20
const val PULSE_MAX = 250

/** Mulai dari sini saran khusus ditampilkan: sistolik ke atas atau diastolik ke atas. */
const val ADVICE_SYSTOLIC = 180
const val ADVICE_DIASTOLIC = 110

enum class BpCategory { OPTIMAL, NORMAL, NORMAL_TINGGI, HIPERTENSI_1, HIPERTENSI_2, HIPERTENSI_3 }

private val CATEGORIES = BpCategory.entries

private fun systolicLevel(value: Int): Int = when {
    value < 120 -> 0
    value < 130 -> 1
    value < 140 -> 2
    value < 160 -> 3
    value < 180 -> 4
    else -> 5
}

private fun diastolicLevel(value: Int): Int = when {
    value < 80 -> 0
    value < 85 -> 1
    value < 90 -> 2
    value < 100 -> 3
    value < 110 -> 4
    else -> 5
}

/** Kategori diambil dari yang lebih tinggi antara tingkat sistolik dan tingkat diastolik. */
fun bpCategory(systolic: Int, diastolic: Int): BpCategory =
    CATEGORIES[maxOf(systolicLevel(systolic), diastolicLevel(diastolic))]

/** Saran tenang (istirahat, ukur ulang, hubungi dokter kalau tetap tinggi atau ada keluhan) untuk tensi ≥ 180/110. */
fun bpNeedsAdvice(systolic: Int, diastolic: Int): Boolean = systolic >= ADVICE_SYSTOLIC || diastolic >= ADVICE_DIASTOLIC

/** Sistolik harus lebih besar dari diastolik, dan keduanya dalam rentang wajar. Nadi opsional. */
fun isValidBloodPressure(systolic: Int, diastolic: Int, pulse: Int? = null): Boolean =
    systolic in SYSTOLIC_MIN..SYSTOLIC_MAX &&
        diastolic in DIASTOLIC_MIN..DIASTOLIC_MAX &&
        systolic > diastolic &&
        (pulse == null || pulse in PULSE_MIN..PULSE_MAX)
