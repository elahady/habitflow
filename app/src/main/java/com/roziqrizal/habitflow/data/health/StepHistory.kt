package com.roziqrizal.habitflow.data.health

import java.time.LocalDate
import java.time.ZoneId

/**
 * Langkah per hari untuk [days] hari terakhir termasuk [today], terbaru dulu (tahap 30 langkah 4). Berhenti di bacaan pertama
 * yang tidak tersedia (izin belum ada, Health Connect belum terpasang, atau gagal), karena hari-hari lain akan gagal sama:
 * hasilnya hanya hari yang benar-benar terbaca, tanpa menebak. Dibaca dengan `inBackground = false` supaya jalan di layar;
 * di latar belakang tanpa izin latar belakang Health Connect melempar galat yang ditangkap di sini.
 */
suspend fun readRecentSteps(source: StepsSource, today: LocalDate, zone: ZoneId, days: Int): List<Pair<LocalDate, Long>> {
    val result = mutableListOf<Pair<LocalDate, Long>>()
    for (offset in 0 until days) {
        val date = today.minusDays(offset.toLong())
        val reading = runCatching { source.read(date, zone, inBackground = false) }.getOrNull()
        if (reading !is StepsReading.Available) break
        result += date to reading.steps
    }
    return result
}
