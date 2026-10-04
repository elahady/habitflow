package com.roziqrizal.habitflow.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HabitRulesTest {

    private val today = LocalDate.of(2026, 10, 4)

    // --- dayLevel ---

    @Test
    fun `tanpa habit selalu level 0`() {
        assertEquals(0, dayLevel(totalHabits = 0, doneHabits = 0, doneTodos = 5))
    }

    @Test
    fun `tidak ada habit selesai berarti level 0 walau to-do banyak`() {
        assertEquals(0, dayLevel(totalHabits = 7, doneHabits = 0, doneTodos = 5))
    }

    @Test
    fun `di bawah 50 persen habit berarti level 1`() {
        // 3 dari 7 = 42%
        assertEquals(1, dayLevel(totalHabits = 7, doneHabits = 3, doneTodos = 0))
    }

    @Test
    fun `tepat 50 persen habit berarti level 2`() {
        assertEquals(2, dayLevel(totalHabits = 4, doneHabits = 2, doneTodos = 0))
    }

    @Test
    fun `lebih dari 50 persen tapi belum semua berarti level 2`() {
        // 4 dari 7 = 57%
        assertEquals(2, dayLevel(totalHabits = 7, doneHabits = 4, doneTodos = 5))
    }

    @Test
    fun `semua habit selesai tanpa to-do berarti level 3`() {
        assertEquals(3, dayLevel(totalHabits = 7, doneHabits = 7, doneTodos = 0))
    }

    @Test
    fun `semua habit dan satu to-do tetap level 3`() {
        assertEquals(3, dayLevel(totalHabits = 7, doneHabits = 7, doneTodos = 1))
    }

    @Test
    fun `semua habit dan dua to-do berarti level 4`() {
        assertEquals(4, dayLevel(totalHabits = 7, doneHabits = 7, doneTodos = 2))
    }

    @Test
    fun `to-do tidak bisa menaikkan hari ke level 4 kalau habit belum lengkap`() {
        assertEquals(2, dayLevel(totalHabits = 7, doneHabits = 6, doneTodos = 5))
    }

    // --- isDayComplete ---

    @Test
    fun `hari lengkap hanya kalau semua habit selesai dan ada habit`() {
        assertTrue(isDayComplete(totalHabits = 7, doneHabits = 7))
        assertFalse(isDayComplete(totalHabits = 7, doneHabits = 6))
        assertFalse(isDayComplete(totalHabits = 0, doneHabits = 0))
    }

    // --- currentStreak ---

    @Test
    fun `streak tiga hari sampai kemarin tetap terhitung walau hari ini belum lengkap`() {
        val complete = setOf(today.minusDays(3), today.minusDays(2), today.minusDays(1))
        assertEquals(3, currentStreak(complete, today))
    }

    @Test
    fun `streak termasuk hari ini kalau hari ini sudah lengkap`() {
        val complete = setOf(today.minusDays(2), today.minusDays(1), today)
        assertEquals(3, currentStreak(complete, today))
    }

    @Test
    fun `streak putus di tengah hanya menghitung hari setelah putusnya`() {
        val complete = setOf(today.minusDays(2), today)
        assertEquals(1, currentStreak(complete, today))
    }

    @Test
    fun `streak nol kalau kemarin dan hari ini tidak lengkap`() {
        val complete = setOf(today.minusDays(2))
        assertEquals(0, currentStreak(complete, today))
    }

    // --- shouldCarryOver ---

    @Test
    fun `to-do belum selesai dari kemarin dipindah`() {
        assertTrue(shouldCarryOver(today.minusDays(1), done = false, today = today))
    }

    @Test
    fun `to-do yang sudah selesai tidak dipindah`() {
        assertFalse(shouldCarryOver(today.minusDays(1), done = true, today = today))
    }

    @Test
    fun `to-do hari ini dan hari depan tidak dipindah`() {
        assertFalse(shouldCarryOver(today, done = false, today = today))
        assertFalse(shouldCarryOver(today.plusDays(1), done = false, today = today))
    }

    // --- canAddTodo ---

    @Test
    fun `to-do bisa ditambah sampai batas lima`() {
        assertTrue(canAddTodo(MAX_TODOS_PER_DAY - 1))
        assertFalse(canAddTodo(MAX_TODOS_PER_DAY))
    }
}
