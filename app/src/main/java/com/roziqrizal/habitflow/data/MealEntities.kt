package com.roziqrizal.habitflow.data

import androidx.room.Entity
import androidx.room.Index
import com.roziqrizal.habitflow.domain.meals.Meal
import com.roziqrizal.habitflow.domain.meals.MealKind
import java.time.LocalDate

/** Catatan makan, satu baris per tanggal dan jenis (tahap 23). [kind] adalah nama [MealKind]. */
@Entity(tableName = "meals", primaryKeys = ["date", "kind"])
data class MealEntity(
    val date: String,
    val kind: String,
    val minute: Int,
    val carb: Boolean,
    val protein: Boolean,
    val vegetable: Boolean,
    val fruit: Boolean,
    val fried: Boolean,
    val sweet: Boolean,
    val note: String?,
)

/**
 * Penanda bahwa habit pada tanggal itu pernah dicentang atau dibatalkan sendiri oleh pengguna (tahap 23). Centang otomatis
 * tidak menyentuh pasangan yang bertanda, jadi centang manual selalu menang.
 */
@Entity(tableName = "habit_manual_marks", primaryKeys = ["habitId", "date"], indices = [Index("date")])
data class HabitManualMark(
    val habitId: Long,
    val date: String,
)

fun MealEntity.toDomain(): Meal? {
    val mealKind = MealKind.entries.firstOrNull { it.name == kind } ?: return null
    return Meal(
        date = LocalDate.parse(date), kind = mealKind, minute = minute,
        carb = carb, protein = protein, vegetable = vegetable, fruit = fruit, fried = fried, sweet = sweet, note = note,
    )
}

fun Meal.toEntity() = MealEntity(
    date = date.toString(), kind = kind.name, minute = minute,
    carb = carb, protein = protein, vegetable = vegetable, fruit = fruit, fried = fried, sweet = sweet,
    note = note?.trim()?.takeIf(String::isNotEmpty),
)
