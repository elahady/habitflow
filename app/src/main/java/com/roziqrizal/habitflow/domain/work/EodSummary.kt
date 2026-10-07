package com.roziqrizal.habitflow.domain.work

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ID = Locale("id", "ID")
private val FULL_DATE = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", ID)
private val SHORT_DATE = DateTimeFormatter.ofPattern("d MMM", ID)

/**
 * Ringkasan EOD untuk dibagikan lewat share sheet. Hanya kelompok yang berisi yang ditampilkan.
 * [results] berisi follow-up hari ini beserta pilihan statusnya.
 */
fun buildEodSummary(date: LocalDate, results: List<Pair<FollowUp, EodAction>>, note: String): String {
    val lines = mutableListOf("EOD ${date.format(FULL_DATE)}")

    fun group(label: String, entries: List<String>) {
        if (entries.isEmpty()) return
        lines += ""
        lines += "$label:"
        entries.forEach { lines += "- $it" }
    }

    group("Selesai", results.filter { it.second is EodAction.Done }.map { it.first.title })
    group("Lanjut besok", results.filter { it.second is EodAction.Tomorrow }.map { it.first.title })
    group(
        "Pindah tanggal",
        results.mapNotNull { (item, action) ->
            (action as? EodAction.Reschedule)?.let { "${item.title} (${it.date.format(SHORT_DATE)})" }
        },
    )
    group(
        "Menunggu",
        results.mapNotNull { (item, action) ->
            (action as? EodAction.Wait)?.let { "${item.title} (cek ${it.recheckDate.format(SHORT_DATE)})" }
        },
    )

    val cleanNote = note.trim()
    if (cleanNote.isNotEmpty()) {
        lines += ""
        lines += "Catatan:"
        lines += cleanNote
    }
    return lines.joinToString("\n")
}
