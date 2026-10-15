package com.roziqrizal.habitflow.data.sync

import com.roziqrizal.habitflow.data.WorkDayEntity
import com.roziqrizal.habitflow.domain.work.WorkDay
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate

/**
 * Catatan harian kerja seperti dikembalikan server (tahap 33 langkah 3c). Waktu selesai dalam milidetik (server
 * mengirim ISO), tanggal `yyyy-MM-dd`, [updatedAt] dibiarkan teks apa adanya karena hanya dikirim balik untuk
 * deteksi konflik.
 */
data class RemoteWorkDay(
    val date: String,
    val eodNote: String?,
    val scrumDoneAt: Long?,
    val eodDoneAt: Long?,
    val updatedAt: String,
) {
    /** Salinan lokal baru dari versi server ini: tidak dirty. */
    fun toEntity(): WorkDayEntity = WorkDayEntity(date, eodNote, scrumDoneAt, eodDoneAt, remoteUpdatedAt = updatedAt, dirty = false)

    /** Versi server ini sebagai model domain, untuk digabung dengan versi HP. */
    fun toDomain(): WorkDay = WorkDay(LocalDate.parse(date), eodNote, scrumDoneAt, eodDoneAt)

    companion object {
        fun fromJson(o: JSONObject): RemoteWorkDay = RemoteWorkDay(
            date = o.getString("date").take(10).also { LocalDate.parse(it) },
            eodNote = o.str("eod_note"),
            scrumDoneAt = o.str("scrum_done_at")?.let { Instant.parse(it).toEpochMilli() },
            eodDoneAt = o.str("eod_done_at")?.let { Instant.parse(it).toEpochMilli() },
            updatedAt = o.getString("updated_at"),
        )

        /** Null untuk baris rusak (tanggal atau waktu tidak terbaca), supaya satu baris rusak tidak menggagalkan semuanya. */
        fun fromJsonOrNull(o: JSONObject): RemoteWorkDay? = runCatching { fromJson(o) }.getOrNull()

        private fun JSONObject.str(key: String): String? = if (!has(key) || isNull(key)) null else getString(key)
    }
}

/**
 * Badan PUT ke server. `eod_note` kosong dikirim sebagai null supaya mengosongkan catatan. [WorkDayEntity.remoteUpdatedAt]
 * ikut sebagai `updated_at` kalau ada: server menolaknya dengan 409 kalau sudah berubah di tempat lain.
 */
fun WorkDayEntity.toRequestJson(): JSONObject = JSONObject().apply {
    put("eod_note", eodNote?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
    put("scrum_done_at", scrumDoneAt?.let { Instant.ofEpochMilli(it).toString() } ?: JSONObject.NULL)
    put("eod_done_at", eodDoneAt?.let { Instant.ofEpochMilli(it).toString() } ?: JSONObject.NULL)
    remoteUpdatedAt?.let { put("updated_at", it) }
}
