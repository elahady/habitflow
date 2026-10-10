package com.roziqrizal.habitflow.data

import androidx.room.withTransaction
import com.roziqrizal.habitflow.domain.schedule.crossesWaterTarget
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/**
 * Penghitung minuman per hari: gelas air (tahap 20), kopi dan minuman manis (tahap 23). Hitungan air yang naik melewati
 * target mencentang habit bersumber otomatis air. Mengurangi gelas tidak pernah membatalkan centang, dan centang manual tidak
 * dicentang ulang. Habit kopi dan minuman manis tidak dicentang di sini, tetapi oleh [MealRepository.applyAutoChecks].
 */
class DrinkRepository(private val db: HabitDatabase) {

    private val drinks = db.drinkDao()
    private val habits = db.habitDao()
    private val entries = db.habitEntryDao()

    fun observeGlasses(date: LocalDate): Flow<Int> = observeCount(date, DrinkKind.WATER)

    suspend fun glasses(date: LocalDate): Int = count(date, DrinkKind.WATER)

    /** Menambah satu gelas. Mengembalikan hitungan baru. */
    suspend fun addGlass(date: LocalDate): Int = change(date, DrinkKind.WATER, +1)

    /** Mengurangi satu gelas, paling rendah 0. Mengembalikan hitungan baru. */
    suspend fun removeGlass(date: LocalDate): Int = change(date, DrinkKind.WATER, -1)

    fun observeCount(date: LocalDate, kind: String): Flow<Int> =
        drinks.observe(date.toString(), kind).map { it?.count ?: 0 }

    suspend fun count(date: LocalDate, kind: String): Int = drinks.get(date.toString(), kind)?.count ?: 0

    /** Hitungan [kind] per tanggal di [from] sampai [to] (keduanya ikut), untuk ringkasan mingguan. */
    fun observeCounts(kind: String, from: LocalDate, to: LocalDate): Flow<Map<LocalDate, Int>> =
        drinks.observeBetween(kind, from.toString(), to.toString())
            .map { rows -> rows.associate { LocalDate.parse(it.date) to it.count } }

    /** Menambah satu gelas [kind]. Mengembalikan hitungan baru. */
    suspend fun add(date: LocalDate, kind: String): Int = change(date, kind, +1)

    /** Mengurangi satu gelas [kind], paling rendah 0. Mengembalikan hitungan baru. */
    suspend fun remove(date: LocalDate, kind: String): Int = change(date, kind, -1)

    private suspend fun change(date: LocalDate, kind: String, delta: Int): Int = db.withTransaction {
        val key = date.toString()
        val before = drinks.get(key, kind)?.count ?: 0
        val after = (before + delta).coerceAtLeast(0)
        if (after != before) {
            drinks.upsert(DrinkCount(key, kind, after))
            if (kind == DrinkKind.WATER && crossesWaterTarget(before, after)) {
                habits.idByAutoSource(HabitAutoSource.WATER)?.let { entries.insert(HabitEntry(it, key)) }
            }
        }
        after
    }
}
