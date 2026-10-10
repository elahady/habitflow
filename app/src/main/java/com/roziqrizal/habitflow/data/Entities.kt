package com.roziqrizal.habitflow.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Tanggal disimpan sebagai string ISO `yyyy-MM-dd`. Urutan string sama dengan urutan tanggal,
 * sehingga query rentang tanggal cukup memakai `BETWEEN`.
 */
@Entity(tableName = "habits")
data class Habit(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: String,
    val sortOrder: Int,
    val isMandatory: Boolean = false,
    /** Sumber centang otomatis ([HabitAutoSource]), atau null kalau habit hanya dicentang manual. */
    val autoSource: String? = null,
)

/**
 * Sumber centang otomatis habit: gelas air (tahap 20), langkah dari Health Connect (tahap 21), dan habit makan dari catatan
 * makan serta penghitung kopi dan minuman manis (tahap 23).
 */
object HabitAutoSource {
    const val WATER = "WATER"
    const val STEPS = "STEPS"
    const val DINNER = "DINNER"
    const val NO_FRIED_SWEET = "NO_FRIED_SWEET"
    const val NO_SWEET_DRINK = "NO_SWEET_DRINK"
    const val COFFEE = "COFFEE"
}

/** Satu baris = habit selesai pada tanggal itu. */
@Entity(
    tableName = "habit_entries",
    primaryKeys = ["habitId", "date"],
    indices = [Index("date")],
)
data class HabitEntry(
    val habitId: Long,
    val date: String,
)

@Entity(tableName = "todos", indices = [Index("date")])
data class Todo(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val date: String,
    val done: Boolean = false,
    val createdAt: Long,
)
