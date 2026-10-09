package com.roziqrizal.habitflow.data

import androidx.room.withTransaction
import com.roziqrizal.habitflow.domain.schedule.crossesWaterTarget
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/**
 * Penghitung gelas air per hari (tahap 20). Hitungan yang naik melewati target mencentang habit bersumber
 * otomatis air. Mengurangi gelas tidak pernah membatalkan centang, dan centang manual tidak dicentang ulang.
 */
class DrinkRepository(private val db: HabitDatabase) {

    private val drinks = db.drinkDao()
    private val habits = db.habitDao()
    private val entries = db.habitEntryDao()

    fun observeGlasses(date: LocalDate): Flow<Int> =
        drinks.observe(date.toString(), DrinkKind.WATER).map { it?.count ?: 0 }

    suspend fun glasses(date: LocalDate): Int = drinks.get(date.toString(), DrinkKind.WATER)?.count ?: 0

    /** Menambah satu gelas. Mengembalikan hitungan baru. */
    suspend fun addGlass(date: LocalDate): Int = change(date, +1)

    /** Mengurangi satu gelas, paling rendah 0. Mengembalikan hitungan baru. */
    suspend fun removeGlass(date: LocalDate): Int = change(date, -1)

    private suspend fun change(date: LocalDate, delta: Int): Int = db.withTransaction {
        val key = date.toString()
        val before = drinks.get(key, DrinkKind.WATER)?.count ?: 0
        val after = (before + delta).coerceAtLeast(0)
        if (after != before) {
            drinks.upsert(DrinkCount(key, DrinkKind.WATER, after))
            if (crossesWaterTarget(before, after)) {
                habits.idByAutoSource(HabitAutoSource.WATER)?.let { entries.insert(HabitEntry(it, key)) }
            }
        }
        after
    }
}
