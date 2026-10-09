package com.roziqrizal.habitflow.data

import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.ZoneId

/** Pintu ke catatan berat dan tensi (tahap 21). Aturan angka dijaga di `domain/health`, bukan di sini. */
class HealthRepository(private val db: HabitDatabase) {

    private val health = db.healthDao()

    fun observeWeights(): Flow<List<WeightEntry>> = health.observeWeights()

    fun observeBloodPressures(): Flow<List<BloodPressureEntry>> = health.observeBloodPressures()

    suspend fun addWeight(kg: Double, timeMillis: Long): Long =
        health.insertWeight(WeightEntry(timeMillis = timeMillis, kg = kg))

    suspend fun addBloodPressure(systolic: Int, diastolic: Int, pulse: Int?, note: String?, timeMillis: Long): Long =
        health.insertBloodPressure(
            BloodPressureEntry(
                timeMillis = timeMillis,
                systolic = systolic,
                diastolic = diastolic,
                pulse = pulse,
                note = note?.trim()?.takeIf { it.isNotEmpty() },
            ),
        )

    /** Untuk pengingat: sudah ada catatan berat pada [date] (menurut [zone])? */
    suspend fun hasWeightOn(date: LocalDate, zone: ZoneId): Boolean {
        val (from, until) = dayBounds(date, zone)
        return health.weightCountBetween(from, until) > 0
    }

    suspend fun hasBloodPressureOn(date: LocalDate, zone: ZoneId): Boolean {
        val (from, until) = dayBounds(date, zone)
        return health.bloodPressureCountBetween(from, until) > 0
    }

    private fun dayBounds(date: LocalDate, zone: ZoneId): Pair<Long, Long> =
        date.atStartOfDay(zone).toInstant().toEpochMilli() to date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
}
