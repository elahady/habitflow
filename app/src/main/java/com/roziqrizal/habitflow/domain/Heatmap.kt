package com.roziqrizal.habitflow.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** Jumlah kolom (minggu) minimal di heatmap, termasuk minggu ini. */
const val HEATMAP_WEEKS = 26

/** Jumlah kolom maksimal saat heatmap ditambah minggu untuk mengisi lebar layar. */
const val HEATMAP_MAX_WEEKS = 53

/**
 * Tanggal di kolom 0 baris 0 heatmap: Senin, sebanyak [weeks] minggu
 * sampai ke minggu ini. Kolom terakhir adalah minggu yang memuat [today].
 */
fun heatmapStart(today: LocalDate, weeks: Int = HEATMAP_WEEKS): LocalDate =
    today
        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        .minusWeeks((weeks - 1).toLong())

/**
 * Jumlah kolom yang muat di lebar [available] untuk kolom selebar [step] dengan jarak [gap]
 * antar kolom, dibatasi antara [HEATMAP_WEEKS] dan [HEATMAP_MAX_WEEKS].
 */
fun heatmapWeeksFor(available: Float, step: Float, gap: Float): Int =
    ((available + gap) / step).toInt().coerceIn(HEATMAP_WEEKS, HEATMAP_MAX_WEEKS)

/** Tanggal untuk sel ([col], [row]). Baris 0 = Senin, baris 6 = Minggu. */
fun heatmapDate(start: LocalDate, col: Int, row: Int): LocalDate =
    start.plusDays(col * 7L + row)

/** Posisi sel di heatmap: kolom (minggu) dan baris (hari, 0 = Senin). */
data class HeatmapCell(val col: Int, val row: Int)

/**
 * Sel terdekat dari titik ([x], [y]) di grid berisi [weeks] kolom dan 7 baris, dengan kotak
 * selebar `step - gap` dan jarak [gap]. Celah antar kotak dibagi dua ke kotak di kiri-kanan
 * atau atas-bawahnya, jadi seluruh area grid mengenai satu kotak. Titik yang lebih jauh dari
 * setengah celah di luar grid menghasilkan null.
 */
fun heatmapCellAt(x: Float, y: Float, step: Float, gap: Float, weeks: Int): HeatmapCell? {
    val half = gap / 2
    val width = step * weeks - gap
    val height = step * 7 - gap
    if (x < -half || y < -half || x > width + half || y > height + half) return null
    val col = ((x + half) / step).toInt().coerceIn(0, weeks - 1)
    val row = ((y + half) / step).toInt().coerceIn(0, 6)
    return HeatmapCell(col, row)
}
