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

    @Test
    fun `jumlah minggu lain tetap berakhir di minggu ini`() {
        val start = heatmapStart(sunday, weeks = 30)

        assertEquals(DayOfWeek.MONDAY, start.dayOfWeek)
        assertEquals(sunday, heatmapDate(start, col = 29, row = 6))
    }

    @Test
    fun `jumlah minggu mengikuti lebar yang tersedia`() {
        // Kolom 12dp dengan jarak 3dp: 30 kolom butuh 30 x 12 - 3 = 357dp.
        assertEquals(30, heatmapWeeksFor(available = 357f, step = 12f, gap = 3f))
        assertEquals(29, heatmapWeeksFor(available = 356f, step = 12f, gap = 3f))
    }

    @Test
    fun `jumlah minggu dibatasi minimal dan maksimal`() {
        assertEquals(HEATMAP_WEEKS, heatmapWeeksFor(available = 100f, step = 12f, gap = 3f))
        assertEquals(HEATMAP_MAX_WEEKS, heatmapWeeksFor(available = 2000f, step = 12f, gap = 3f))
    }

    // Kotak 9, jarak 3, langkah 12, 26 kolom: lebar grid 309, tinggi 81.
    private fun cellAt(x: Float, y: Float) = heatmapCellAt(x, y, step = 12f, gap = 3f, weeks = 26)

    @Test
    fun `titik di dalam kotak mengenai kotak itu`() {
        assertEquals(HeatmapCell(0, 0), cellAt(4f, 4f))
        assertEquals(HeatmapCell(2, 3), cellAt(2 * 12f + 8f, 3 * 12f + 1f))
    }

    @Test
    fun `celah dibagi dua ke kotak terdekat`() {
        // Kotak kolom 0 berakhir di 9, kolom 1 mulai di 12. Batasnya di 10,5.
        assertEquals(HeatmapCell(0, 0), cellAt(10.4f, 4f))
        assertEquals(HeatmapCell(1, 0), cellAt(10.6f, 4f))
        assertEquals(HeatmapCell(0, 0), cellAt(4f, 10.4f))
        assertEquals(HeatmapCell(0, 1), cellAt(4f, 10.6f))
    }

    @Test
    fun `tepi grid masih mengenai kotak terluar`() {
        assertEquals(HeatmapCell(0, 0), cellAt(-1f, -1f))
        assertEquals(HeatmapCell(25, 6), cellAt(310f, 82f))
    }

    @Test
    fun `titik jauh di luar grid tidak mengenai kotak`() {
        assertEquals(null, cellAt(-2f, 4f))
        assertEquals(null, cellAt(4f, -2f))
        assertEquals(null, cellAt(311f, 4f))
        assertEquals(null, cellAt(4f, 83f))
    }
}
