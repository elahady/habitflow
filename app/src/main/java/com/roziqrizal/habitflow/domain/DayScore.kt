package com.roziqrizal.habitflow.domain

import java.time.LocalDate

/** Skor satu hari. Habit yang belum dibuat pada tanggal itu tidak dihitung sebagai gagal. */
data class DayScore(
    val date: LocalDate,
    val totalHabits: Int,
    val doneHabits: Int,
    val doneTodos: Int,
) {
    val level: Int get() = dayLevel(totalHabits, doneHabits, doneTodos)
    val complete: Boolean get() = isDayComplete(totalHabits, doneHabits)
}

/**
 * @param habitCreatedOn tanggal dibuat untuk setiap habit yang ada sekarang, dengan id habit sebagai kunci.
 * @param doneHabitIds id habit yang dicentang pada [date].
 * @param doneTodos jumlah to-do selesai pada [date].
 */
fun scoreDay(
    date: LocalDate,
    habitCreatedOn: Map<Long, LocalDate>,
    doneHabitIds: Set<Long>,
    doneTodos: Int,
): DayScore {
    val activeIds = habitCreatedOn.filterValues { !it.isAfter(date) }.keys
    return DayScore(
        date = date,
        totalHabits = activeIds.size,
        doneHabits = doneHabitIds.count { it in activeIds },
        doneTodos = doneTodos,
    )
}
