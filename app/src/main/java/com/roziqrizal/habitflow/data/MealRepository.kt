package com.roziqrizal.habitflow.data

import androidx.room.withTransaction
import com.roziqrizal.habitflow.domain.meals.Meal
import com.roziqrizal.habitflow.domain.meals.MealKind
import com.roziqrizal.habitflow.domain.meals.MealRule
import com.roziqrizal.habitflow.domain.meals.satisfiedRules
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/**
 * Catatan makan (tahap 23) dan centang otomatis habit makan dari catatan itu serta penghitung kopi dan minuman manis.
 * Satu catatan per tanggal dan jenis.
 */
class MealRepository(private val db: HabitDatabase) {

    private val meals = db.mealDao()
    private val drinks = db.drinkDao()
    private val habits = db.habitDao()
    private val entries = db.habitEntryDao()

    fun observeMeals(date: LocalDate): Flow<List<Meal>> =
        meals.observeByDate(date.toString()).map { rows -> rows.mapNotNull { it.toDomain() } }

    /** Catatan di [from] sampai [to] (keduanya ikut), untuk ringkasan mingguan. */
    fun observeMealsBetween(from: LocalDate, to: LocalDate): Flow<List<Meal>> =
        meals.observeBetween(from.toString(), to.toString()).map { rows -> rows.mapNotNull { it.toDomain() } }

    suspend fun mealsOn(date: LocalDate): List<Meal> = meals.getByDate(date.toString()).mapNotNull { it.toDomain() }

    /** Simpan catatan baru atau ganti yang sudah ada untuk tanggal dan jenis yang sama. */
    suspend fun save(meal: Meal) {
        meals.upsert(meal.toEntity())
    }

    suspend fun delete(date: LocalDate, kind: MealKind) {
        meals.delete(date.toString(), kind.name)
    }

    /**
     * Mencentang habit makan di [date] yang aturannya terpenuhi, dengan [bedtimeMinute] sebagai batas tidur hari itu. Hanya
     * menambah centang: habit yang sudah tercentang, yang pernah dicentang atau dibatalkan sendiri (bertanda), dan yang dibuat
     * sesudah [date] dilewati. Aman dipanggil berulang. Mengembalikan jumlah habit yang baru dicentang.
     */
    suspend fun applyAutoChecks(date: LocalDate, bedtimeMinute: Int): Int = db.withTransaction {
        val key = date.toString()
        val rules = satisfiedRules(
            meals = meals.getByDate(key).mapNotNull { it.toDomain() },
            coffeeCount = drinks.get(key, DrinkKind.COFFEE)?.count ?: 0,
            sweetCount = drinks.get(key, DrinkKind.SWEET)?.count ?: 0,
            bedtimeMinute = bedtimeMinute,
        )
        var added = 0
        rules.forEach { rule ->
            val habitId = habits.idByAutoSource(rule.autoSource()) ?: return@forEach
            val habit = habits.getById(habitId) ?: return@forEach
            if (LocalDate.parse(habit.createdAt).isAfter(date)) return@forEach
            if (entries.markCount(habitId, key) > 0 || entries.count(habitId, key) > 0) return@forEach
            if (entries.insert(HabitEntry(habitId, key)) != -1L) added++
        }
        added
    }

    private fun MealRule.autoSource(): String = when (this) {
        MealRule.DINNER_ON_TIME -> HabitAutoSource.DINNER
        MealRule.NO_FRIED_OR_SWEET -> HabitAutoSource.NO_FRIED_SWEET
        MealRule.NO_SWEET_DRINK -> HabitAutoSource.NO_SWEET_DRINK
        MealRule.COFFEE_WITHIN_LIMIT -> HabitAutoSource.COFFEE
    }
}
