package com.roziqrizal.habitflow.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ContributionTest {

    private val day = LocalDate.of(2026, 10, 4)

    // --- habitLevelOn ---

    @Test
    fun `sebelum habit dibuat tidak digambar, meski ditandai selesai`() {
        assertEquals(CELL_NOT_DRAWN, habitLevelOn(createdOn = day, done = false, date = day.minusDays(1)))
        assertEquals(CELL_NOT_DRAWN, habitLevelOn(createdOn = day, done = true, date = day.minusDays(1)))
    }

    @Test
    fun `hari dibuat tanpa centang level 0, dengan centang level 4`() {
        assertEquals(0, habitLevelOn(createdOn = day, done = false, date = day))
        assertEquals(4, habitLevelOn(createdOn = day, done = true, date = day))
    }

    // --- completeDays ---

    @Test
    fun `hari lengkap hanya menghitung habit yang sudah aktif pada hari itu`() {
        val created = mapOf(1L to day.minusDays(5), 2L to day)
        val doneByDay = mapOf(day.minusDays(1) to setOf(1L), day to setOf(1L, 2L))

        val complete = completeDays(created, doneByDay, emptyMap(), setOf(day.minusDays(1), day))

        // Kemarin hanya habit 1 yang aktif, jadi lengkap. Hari ini habit 1 dan 2 lengkap.
        assertEquals(setOf(day.minusDays(1), day), complete)
    }

    @Test
    fun `to-do selesai tidak membuat hari lengkap`() {
        val created = mapOf(1L to day)

        val complete = completeDays(created, emptyMap(), mapOf(day to 3), setOf(day))

        assertEquals(emptySet<LocalDate>(), complete)
    }

    @Test
    fun `tanpa habit tidak ada hari lengkap`() {
        assertEquals(emptySet<LocalDate>(), completeDays(emptyMap(), emptyMap(), emptyMap(), setOf(day)))
    }

    // --- longestStreak ---

    @Test
    fun `tanpa hari lengkap streak terpanjang 0`() {
        assertEquals(0, longestStreak(emptySet()))
    }

    @Test
    fun `streak terpanjang diambil dari rentang terpanjang, bukan total hari`() {
        val first = (0L..2L).map { day.minusDays(20 + it) }
        val second = (0L..4L).map { day.minusDays(10 + it) }

        assertEquals(5, longestStreak((first + second).toSet()))
    }

    @Test
    fun `urutan tanggal di input tidak berpengaruh`() {
        val days = setOf(day, day.minusDays(2), day.minusDays(1))

        assertEquals(3, longestStreak(days))
    }

    @Test
    fun `hari yang terpisah satu hari tidak tersambung`() {
        val days = setOf(day, day.minusDays(2))

        assertEquals(1, longestStreak(days))
    }
}
