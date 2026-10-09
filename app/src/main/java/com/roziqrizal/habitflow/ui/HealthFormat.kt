package com.roziqrizal.habitflow.ui

import com.roziqrizal.habitflow.domain.health.BmiCategory
import com.roziqrizal.habitflow.domain.health.BpCategory
import com.roziqrizal.habitflow.domain.health.TrendDirection
import com.roziqrizal.habitflow.domain.health.WeightTrend
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs

/* Teks kesehatan dalam bahasa Indonesia: angka berkoma desimal, ribuan bertitik, kategori sebagai teks biasa. */

private val ID = Locale("id", "ID")

/** "3.240". */
fun formatSteps(value: Long): String = NumberFormat.getIntegerInstance(ID).format(value)

/** "72,4" (satu desimal). */
fun formatDecimal1(value: Double): String =
    NumberFormat.getNumberInstance(ID).apply { minimumFractionDigits = 1; maximumFractionDigits = 1 }.format(value)

/** "+0,8" atau "−1,5" (tanda minus Unicode), satu desimal. */
fun formatSigned1(value: Double): String {
    val text = formatDecimal1(abs(value))
    return when {
        value > 0 -> "+$text"
        value < 0 -> "−$text"
        else -> text
    }
}

val BmiCategory.label: String
    get() = when (this) {
        BmiCategory.KURUS -> "Kurus"
        BmiCategory.NORMAL -> "Normal"
        BmiCategory.GEMUK -> "Gemuk"
        BmiCategory.OBESITAS -> "Obesitas"
    }

val BpCategory.label: String
    get() = when (this) {
        BpCategory.OPTIMAL -> "Optimal"
        BpCategory.NORMAL -> "Normal"
        BpCategory.NORMAL_TINGGI -> "Normal-tinggi"
        BpCategory.HIPERTENSI_1 -> "Hipertensi derajat 1"
        BpCategory.HIPERTENSI_2 -> "Hipertensi derajat 2"
        BpCategory.HIPERTENSI_3 -> "Hipertensi derajat 3"
    }

/** Saran tenang untuk tensi sangat tinggi. Bukan diagnosis dan tidak memakai warna peringatan. */
const val BP_ADVICE_TEXT =
    "Istirahat sebentar lalu ukur ulang. Kalau tetap tinggi atau ada keluhan, hubungi dokter."

/** "Hari ini", "Kemarin", atau "3 hari lalu". */
fun relativeDay(timeMillis: Long, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): String {
    val date = Instant.ofEpochMilli(timeMillis).atZone(zone).toLocalDate()
    return when (val days = ChronoUnit.DAYS.between(date, today)) {
        0L -> "Hari ini"
        1L -> "Kemarin"
        else -> if (days < 0) "Hari ini" else "$days hari lalu"
    }
}

/** "turun 0,8 kg", "naik 1,2 kg", atau "stabil" untuk tren 4 minggu. */
fun WeightTrend.describe(): String = when (direction) {
    TrendDirection.TURUN -> "turun ${formatDecimal1(abs(deltaKg))} kg"
    TrendDirection.NAIK -> "naik ${formatDecimal1(abs(deltaKg))} kg"
    TrendDirection.STABIL -> "stabil"
}

/** Sisa ke target dalam kalimat: "1,4 kg lagi ke target", "di bawah target 2,3 kg", atau "tepat di target". */
fun describeRemaining(remainingKg: Double): String = when {
    remainingKg > 0 -> "${formatDecimal1(remainingKg)} kg lagi ke target"
    remainingKg < 0 -> "di bawah target ${formatDecimal1(abs(remainingKg))} kg"
    else -> "tepat di target"
}

/** Mengubah teks isian ("72,4" atau "72.4") menjadi angka, atau null kalau bukan angka. */
fun parseDecimal(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()
