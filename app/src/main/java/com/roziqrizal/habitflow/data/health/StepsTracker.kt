package com.roziqrizal.habitflow.data.health

import com.roziqrizal.habitflow.data.HabitAutoSource
import com.roziqrizal.habitflow.data.HabitDatabase
import com.roziqrizal.habitflow.data.HabitEntry
import com.roziqrizal.habitflow.data.HealthSettings
import com.roziqrizal.habitflow.domain.health.shouldAutoCheckSteps
import java.time.LocalDate
import java.time.ZoneId

/**
 * Membaca langkah hari ini lalu mencentang habit bersumber langkah kalau targetnya tercapai (tahap 21). Dicentang
 * sekali per tanggal: centang yang dibatalkan manual tidak dicentang ulang di hari yang sama.
 */
class StepsTracker(
    private val source: StepsSource,
    private val db: HabitDatabase,
    private val settings: HealthSettings,
) {
    suspend fun refresh(today: LocalDate, zone: ZoneId, inBackground: Boolean): StepsReading {
        val reading = source.read(today, zone, inBackground)
        if (reading is StepsReading.Available && shouldAutoCheckSteps(reading.steps, settings.lastStepsAutoCheck, today)) {
            val habitId = db.habitDao().idByAutoSource(HabitAutoSource.STEPS)
            if (habitId != null) {
                db.habitEntryDao().insert(HabitEntry(habitId, today.toString()))
                settings.lastStepsAutoCheck = today
            }
        }
        return reading
    }

    suspend fun permissionsToRequest(): Set<String> = source.permissionsToRequest()
}
