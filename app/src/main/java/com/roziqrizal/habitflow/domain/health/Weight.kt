package com.roziqrizal.habitflow.domain.health

import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToLong

/*
 * Berat badan dan BMI (tahap 21). Aturan lengkapnya di docs/rancangan.md bagian Kesehatan. Kategori adalah
 * informasi, bukan diagnosis.
 */

const val WEIGHT_MIN_KG = 20.0
const val WEIGHT_MAX_KG = 300.0
const val HEIGHT_MIN_CM = 100.0
const val HEIGHT_MAX_CM = 250.0

/** Batas kategori BMI menurut Kemenkes RI. */
const val BMI_NORMAL_MIN = 18.5
const val BMI_NORMAL_MAX = 25.0
const val BMI_GEMUK_MAX = 27.0

/** Selisih berat 4 minggu di bawah ini dianggap stabil. */
const val WEIGHT_STABLE_KG = 0.2

/** Jendela tren berat, dalam hari. */
const val WEIGHT_TREND_DAYS = 28L

enum class BmiCategory { KURUS, NORMAL, GEMUK, OBESITAS }

private fun round1(value: Double): Double = (value * 10).roundToLong() / 10.0

fun isValidWeight(kg: Double): Boolean = kg in WEIGHT_MIN_KG..WEIGHT_MAX_KG

fun isValidHeight(cm: Double): Boolean = cm in HEIGHT_MIN_CM..HEIGHT_MAX_CM

/** BMI = kg / (meter x meter), dibulatkan satu desimal supaya sama dengan angka yang tampil. */
fun bmi(weightKg: Double, heightCm: Double): Double {
    val meters = heightCm / 100.0
    return round1(weightKg / (meters * meters))
}

/** Kategori dari BMI yang sudah dibulatkan satu desimal: 25,0 masih normal dan 27,0 masih gemuk. */
fun bmiCategory(bmi: Double): BmiCategory = when {
    bmi < BMI_NORMAL_MIN -> BmiCategory.KURUS
    bmi <= BMI_NORMAL_MAX -> BmiCategory.NORMAL
    bmi <= BMI_GEMUK_MAX -> BmiCategory.GEMUK
    else -> BmiCategory.OBESITAS
}

/** Target berat bawaan: batas atas BMI normal untuk [heightCm], satu desimal. */
fun defaultTargetKg(heightCm: Double): Double {
    val meters = heightCm / 100.0
    return round1(BMI_NORMAL_MAX * meters * meters)
}

/** Sisa ke target: positif berarti masih di atas target, negatif berarti sudah di bawahnya. */
fun remainingToTarget(latestKg: Double, targetKg: Double): Double = round1(latestKg - targetKg)

/** Satu catatan berat untuk menghitung tren. [timeMillis] menentukan urutan di hari yang sama. */
data class WeightPoint(val date: LocalDate, val timeMillis: Long, val kg: Double)

enum class TrendDirection { TURUN, NAIK, STABIL }

data class WeightTrend(val deltaKg: Double, val direction: TrendDirection)

/**
 * Tren 4 minggu: berat terbaru dikurangi berat paling awal dalam [WEIGHT_TREND_DAYS] hari terakhir sampai [today].
 * Butuh dua catatan atau lebih di jendela itu, kalau tidak null. Selisih di bawah [WEIGHT_STABLE_KG] dianggap stabil.
 */
fun weightTrend(points: List<WeightPoint>, today: LocalDate): WeightTrend? {
    val from = today.minusDays(WEIGHT_TREND_DAYS)
    val window = points.filter { !it.date.isBefore(from) && !it.date.isAfter(today) }.sortedBy { it.timeMillis }
    if (window.size < 2) return null
    val delta = round1(window.last().kg - window.first().kg)
    val direction = when {
        abs(delta) < WEIGHT_STABLE_KG -> TrendDirection.STABIL
        delta < 0 -> TrendDirection.TURUN
        else -> TrendDirection.NAIK
    }
    return WeightTrend(delta, direction)
}
