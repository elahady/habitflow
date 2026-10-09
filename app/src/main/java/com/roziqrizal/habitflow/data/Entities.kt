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

/** Sumber centang otomatis habit: gelas air (tahap 20) dan langkah dari Health Connect (tahap 21). */
object HabitAutoSource {
    const val WATER = "WATER"
    const val STEPS = "STEPS"
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
