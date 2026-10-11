package com.roziqrizal.habitflow.data.sync

import com.roziqrizal.habitflow.data.FollowUpEntity
import com.roziqrizal.habitflow.data.toDomain
import com.roziqrizal.habitflow.domain.work.FollowUp
import org.json.JSONObject
import java.time.Instant

/**
 * Follow-up kerja seperti dikembalikan server (tahap 27 langkah 3). Waktu dibuat dan selesai dalam milidetik
 * (server mengirim ISO), [time] dalam `HH:mm`, tanggal dalam `yyyy-MM-dd`, [updatedAt] dibiarkan teks apa adanya
 * dari server karena hanya dikirim balik untuk deteksi konflik.
 */
data class RemoteFollowUp(
    val id: Long,
    val title: String,
    val status: String,
    val date: String?,
    val time: String?,
    val person: String?,
    val note: String?,
    val doneAt: Long?,
    val pickedDate: String?,
    val eventId: Long?,
    val eventDate: String?,
    val eventTitle: String?,
    val createdAt: Long,
    val updatedAt: String,
) {
    /** Bentuk yang sama dengan JSON server, supaya [fromJson] bisa membaca keduanya (dipakai menyimpan versi bentrok). */
    fun toJson(): String = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("status", status)
        put("date", date ?: JSONObject.NULL)
        put("time", time ?: JSONObject.NULL)
        put("person", person ?: JSONObject.NULL)
        put("note", note ?: JSONObject.NULL)
        put("done_at", doneAt?.let { Instant.ofEpochMilli(it).toString() } ?: JSONObject.NULL)
        put("picked_date", pickedDate ?: JSONObject.NULL)
        put("event_id", eventId ?: JSONObject.NULL)
        put("event_date", eventDate ?: JSONObject.NULL)
        put("event_title", eventTitle ?: JSONObject.NULL)
        put("created_at", Instant.ofEpochMilli(createdAt).toString())
        put("updated_at", updatedAt)
    }.toString()

    /** Salinan lokal baru dari versi server ini (belum pernah ada di HP): tidak dirty, tanpa bentrok. */
    fun toNewEntity(): FollowUpEntity = FollowUpEntity(
        title = title, status = status, date = date, time = time, person = person, note = note,
        createdAt = createdAt, doneAt = doneAt, pickedDate = pickedDate,
        eventId = eventId, eventDate = eventDate, eventTitle = eventTitle,
        remoteId = id, remoteUpdatedAt = updatedAt, dirty = false, conflictJson = null,
    )

    /** Menimpa isi [local] dengan versi server ini. Waktu dibuat di HP dan id lokal dipertahankan. */
    fun applyTo(local: FollowUpEntity): FollowUpEntity = local.copy(
        title = title, status = status, date = date, time = time, person = person, note = note, doneAt = doneAt,
        pickedDate = pickedDate, eventId = eventId, eventDate = eventDate, eventTitle = eventTitle,
        remoteId = id, remoteUpdatedAt = updatedAt, dirty = false, conflictJson = null,
    )

    /** Versi server ini sebagai follow-up biasa di bawah id lokal [localId], untuk ditampilkan di dialog bentrok. */
    fun toDomain(localId: Long): FollowUp = toNewEntity().copy(id = localId).toDomain()

    companion object {
        fun fromJson(json: String): RemoteFollowUp = fromJson(JSONObject(json))

        fun fromJson(o: JSONObject): RemoteFollowUp = RemoteFollowUp(
            id = o.getLong("id"),
            title = o.getString("title"),
            status = o.getString("status"),
            date = o.str("date")?.take(10),
            time = o.str("time")?.take(5),
            person = o.str("person"),
            note = o.str("note"),
            doneAt = o.str("done_at")?.let { Instant.parse(it).toEpochMilli() },
            pickedDate = o.str("picked_date")?.take(10),
            eventId = if (o.isNull("event_id")) null else o.getLong("event_id"),
            eventDate = o.str("event_date")?.take(10),
            eventTitle = o.str("event_title"),
            createdAt = Instant.parse(o.getString("created_at")).toEpochMilli(),
            updatedAt = o.getString("updated_at"),
        )

        private fun JSONObject.str(key: String): String? = if (!has(key) || isNull(key)) null else getString(key)
    }
}

/** Isi follow-up lokal sebagai badan POST/PUT ke server. Nilai kosong dikirim sebagai null supaya menghapus isi di server. */
fun FollowUpEntity.toRequestJson(): JSONObject = JSONObject().apply {
    put("title", title)
    put("status", status)
    put("date", date ?: JSONObject.NULL)
    put("time", time?.take(5) ?: JSONObject.NULL)
    put("person", person ?: JSONObject.NULL)
    put("note", note ?: JSONObject.NULL)
    put("done_at", doneAt?.let { Instant.ofEpochMilli(it).toString() } ?: JSONObject.NULL)
    put("picked_date", pickedDate ?: JSONObject.NULL)
    put("event_id", eventId ?: JSONObject.NULL)
    put("event_date", eventDate ?: JSONObject.NULL)
    put("event_title", eventTitle ?: JSONObject.NULL)
}
