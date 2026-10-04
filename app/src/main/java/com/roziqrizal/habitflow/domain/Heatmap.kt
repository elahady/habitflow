package com.roziqrizal.habitflow.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** Jumlah kolom (minggu) di heatmap, termasuk minggu ini. */
const val HEATMAP_WEEKS = 26

/**
 * Tanggal di kolom 0 baris 0 heatmap: Senin, sebanyak [HEATMAP_WEEKS] minggu
 * sampai ke minggu ini. Kolom terakhir adalah minggu yang memuat [today].
 */
fun heatmapStart(today: LocalDate): LocalDate =
    today
        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        .minusWeeks((HEATMAP_WEEKS - 1).toLong())

/** Tanggal untuk sel ([col], [row]). Baris 0 = Senin, baris 6 = Minggu. */
fun heatmapDate(start: LocalDate, col: Int, row: Int): LocalDate =
    start.plusDays(col * 7L + row)
