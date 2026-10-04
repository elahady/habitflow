package com.roziqrizal.habitflow.domain

import java.time.LocalDate

/** Batas to-do per hari, termasuk to-do yang dipindah dari hari sebelumnya. */
const val MAX_TODOS_PER_DAY = 5

/** Jumlah to-do selesai minimal untuk naik ke level 4 (hanya jika semua habit selesai). */
const val TODOS_FOR_LEVEL_4 = 2

/**
 * Level warna hari, 0 sampai 4. Habit selalu jadi syarat: to-do tidak bisa menaikkan
 * hari ke level 4 kalau habit belum lengkap.
 */
fun dayLevel(totalHabits: Int, doneHabits: Int, doneTodos: Int): Int {
    require(totalHabits >= 0 && doneHabits in 0..totalHabits && doneTodos >= 0)

    if (totalHabits == 0 || doneHabits == 0) return 0
    if (doneHabits < totalHabits) {
        // Dihitung tanpa pembagian supaya 50% tepat tidak terbulatkan ke bawah.
        return if (doneHabits * 2 < totalHabits) 1 else 2
    }
    return if (doneTodos >= TODOS_FOR_LEVEL_4) 4 else 3
}

/** Hari dianggap lengkap jika semua habit selesai. To-do tidak ikut dihitung. */
fun isDayComplete(totalHabits: Int, doneHabits: Int): Boolean =
    totalHabits > 0 && doneHabits == totalHabits

/**
 * Jumlah hari berturut-turut yang lengkap, dihitung mundur dari hari ini.
 * Kalau hari ini belum lengkap, hitungan mulai dari kemarin, sehingga streak tidak langsung putus.
 */
fun currentStreak(completeDays: Set<LocalDate>, today: LocalDate): Int {
    var day = if (today in completeDays) today else today.minusDays(1)
    var streak = 0
    while (day in completeDays) {
        streak++
        day = day.minusDays(1)
    }
    return streak
}

/** To-do yang belum selesai dan tanggalnya sebelum hari ini dipindah ke hari ini. */
fun shouldCarryOver(todoDate: LocalDate, done: Boolean, today: LocalDate): Boolean =
    !done && todoDate.isBefore(today)

fun canAddTodo(currentCount: Int): Boolean = currentCount < MAX_TODOS_PER_DAY
