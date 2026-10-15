package com.roziqrizal.habitflow.domain.work

import java.time.LocalDate

/**
 * Ringkasan email harian yang ditulis Claude lewat skill `habitflow-morning-routine` (tahap 32). Hanya dibaca di app:
 * tidak ada jalan menyuntingnya dari HP, supaya sumbernya tidak tercampur dengan catatan EOD yang ditulis manual.
 * [updatedAt] dalam milidetik.
 */
data class DailyBrief(val date: LocalDate, val emailDigest: String, val updatedAt: Long)

/** [brief] yang akan ditampilkan, dan [isToday] false kalau itu ringkasan hari sebelumnya (belum ada yang hari ini). */
data class DailyBriefCard(val brief: DailyBrief?, val isToday: Boolean)

/**
 * Ringkasan yang ditampilkan di kartu: yang terbaru dengan isi dan bertanggal paling lambat [today]. Yang bertanggal di
 * depan diabaikan (jam HP salah atau salah ketik), dan ringkasan tanpa isi (hanya catatan meeting) tidak dihitung.
 * Aturan yang sama dengan kartu di web.
 */
fun pickDailyBrief(briefs: List<DailyBrief>, today: LocalDate): DailyBriefCard {
    val brief = briefs
        .filter { it.emailDigest.isNotBlank() && !it.date.isAfter(today) }
        .maxByOrNull { it.date }
    return DailyBriefCard(brief, isToday = brief?.date == today)
}
