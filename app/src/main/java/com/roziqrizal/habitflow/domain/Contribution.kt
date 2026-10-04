package com.roziqrizal.habitflow.domain

import java.time.LocalDate

/** Level untuk sel yang tidak digambar: habit belum dibuat pada tanggal itu. */
const val CELL_NOT_DRAWN = -1

/** Level sel habit yang sudah dicentang. Level tertinggi supaya beda jelas dari belum. */
private const val HABIT_DONE_LEVEL = 4

/** Habit aktif pada [date] kalau sudah dibuat pada tanggal itu atau sebelumnya. */
fun isActiveOn(createdOn: LocalDate, date: LocalDate): Boolean = !createdOn.isAfter(date)

/**
 * Level sel heatmap satu habit pada satu hari. Sebelum habit dibuat tidak digambar,
 * supaya hari itu tidak dihitung sebagai gagal.
 */
fun habitLevelOn(createdOn: LocalDate, done: Boolean, date: LocalDate): Int = when {
    !isActiveOn(createdOn, date) -> CELL_NOT_DRAWN
    done -> HABIT_DONE_LEVEL
    else -> 0
}

/** Hari yang lengkap (semua habit aktif selesai) di antara [candidates]. To-do tidak ikut. */
fun completeDays(
    habitCreatedOn: Map<Long, LocalDate>,
    doneIdsByDate: Map<LocalDate, Set<Long>>,
    doneTodosByDate: Map<LocalDate, Int>,
    candidates: Set<LocalDate>,
): Set<LocalDate> = candidates
    .filterTo(mutableSetOf()) { day ->
        scoreDay(day, habitCreatedOn, doneIdsByDate[day].orEmpty(), doneTodosByDate[day] ?: 0).complete
    }

/** Jumlah hari berturut-turut terpanjang di [days]. */
fun longestStreak(days: Set<LocalDate>): Int {
    var best = 0
    var run = 0
    var previous: LocalDate? = null
    for (day in days.sorted()) {
        run = if (previous != null && previous.plusDays(1) == day) run + 1 else 1
        best = maxOf(best, run)
        previous = day
    }
    return best
}
