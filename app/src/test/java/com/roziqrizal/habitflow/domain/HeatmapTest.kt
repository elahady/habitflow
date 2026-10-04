package com.roziqrizal.habitflow.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class HeatmapTest {

    // Minggu ini: Minggu 4 Oktober 2026. Senin minggu ini: 28 September 2026.
    private val sunday = LocalDate.of(2026, 10, 4)
    private val monday = LocalDate.of(2026, 9, 28)

    @Test
    fun `awal heatmap adalah Senin, 25 minggu sebelum Senin minggu ini`() {
        val start = heatmapStart(sunday)

        assertEquals(LocalDate.of(2026, 4, 6), start)
        assertEquals(DayOfWeek.MONDAY, start.dayOfWeek)
    }

    @Test
    fun `hari Senin menghasilkan awal yang sama dengan hari Minggu di minggu itu`() {
        assertEquals(heatmapStart(sunday), heatmapStart(monday))
    }

    @Test
    fun `sel pertama adalah awal heatmap`() {
        val start = heatmapStart(sunday)

        assertEquals(start, heatmapDate(start, col = 0, row = 0))
    }

    @Test
    fun `sel terakhir di kolom minggu ini dan baris Minggu adalah hari ini`() {
        val start = heatmapStart(sunday)

        assertEquals(sunday, heatmapDate(start, col = HEATMAP_WEEKS - 1, row = 6))
    }

    @Test
    fun `baris menunjukkan hari dalam minggu, Senin sampai Minggu`() {
        val start = heatmapStart(sunday)
        val lastWeek = HEATMAP_WEEKS - 1

        assertEquals(DayOfWeek.MONDAY, heatmapDate(start, lastWeek, 0).dayOfWeek)
        assertEquals(DayOfWeek.WEDNESDAY, heatmapDate(start, lastWeek, 2).dayOfWeek)
        assertEquals(DayOfWeek.SUNDAY, heatmapDate(start, lastWeek, 6).dayOfWeek)
    }

    @Test
    fun `kolom bergeser tujuh hari per minggu`() {
        val start = heatmapStart(sunday)

        assertEquals(start.plusDays(7), heatmapDate(start, col = 1, row = 0))
        assertEquals(start.plusDays(7 * 10 + 3), heatmapDate(start, col = 10, row = 3))
    }
}
