package com.roziqrizal.habitflow.data

import androidx.room.withTransaction
import com.roziqrizal.habitflow.data.sync.RemoteFollowUp
import com.roziqrizal.habitflow.domain.work.EventSource
import com.roziqrizal.habitflow.domain.work.FollowUp
import com.roziqrizal.habitflow.domain.work.FollowUpConflict
import com.roziqrizal.habitflow.domain.work.FollowUpStatus
import com.roziqrizal.habitflow.domain.work.WorkDay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/** Pintu ke follow-up kerja dan catatan harian kerja (daily scrum dan EOD). */
class WorkRepository(private val db: HabitDatabase) {

    private val followUps = db.followUpDao()
    private val workDays = db.workDayDao()
    private val outbox = db.habitSyncOutboxDao()

    fun observeFollowUps(): Flow<List<FollowUp>> =
        followUps.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeWorkDays(): Flow<Map<LocalDate, WorkDay>> =
        workDays.observeAll().map { list -> list.mapNotNull { it.toDomain() }.associateBy { it.date } }

    /** Untuk penerima alarm dan notifikasi, yang tidak memakai Flow. */
    suspend fun getFollowUps(): List<FollowUp> = followUps.getAll().map { it.toDomain() }

    /**
     * Catat cepat: langsung ke Inbox tanpa memilih apa pun. Judul kosong ditolak (mengembalikan false). [source] menautkan catatan
     * ke acara yang sedang berlangsung (tahap 22).
     */
    suspend fun quickAdd(title: String, nowMillis: Long, source: EventSource? = null): Boolean {
        val clean = title.trim()
        if (clean.isEmpty()) return false
        followUps.insert(
            FollowUp(
                0, clean, FollowUpStatus.INBOX, createdAt = nowMillis,
                eventId = source?.eventId, eventDate = source?.date, eventTitle = source?.title,
            ).toEntity(),
        )
        return true
    }

    /** Follow-up yang bentrok dengan perubahan di server dan menunggu pilihan pengguna (tahap 27 langkah 3). */
    fun observeConflicts(): Flow<List<FollowUpConflict>> = followUps.observeConflicts().map { list ->
        list.mapNotNull { entity ->
            val server = entity.conflictJson?.let { runCatching { RemoteFollowUp.fromJson(it) }.getOrNull() }
            server?.let { FollowUpConflict(entity.toDomain(), it.toDomain(entity.id)) }
        }
    }

    /** Simpan baru (id 0) atau ubah yang ada. Mengembalikan id. */
    suspend fun save(item: FollowUp, nowMillis: Long): Long =
        if (item.id == 0L) {
            followUps.insert(item.copy(createdAt = if (item.createdAt == 0L) nowMillis else item.createdAt).toEntity())
        } else {
            updateKeepingSync(item)
            item.id
        }

    /** Simpan beberapa perubahan sekaligus, misalnya hasil satu sesi daily scrum atau EOD. */
    suspend fun saveAll(items: List<FollowUp>) = db.withTransaction {
        items.forEach { updateKeepingSync(it) }
    }

    /**
     * Mengubah isi tanpa menghilangkan data sinkron server (`remoteId`, `remoteUpdatedAt`, versi bentrok) yang tidak ada di
     * [FollowUp]. Isi yang sama persis tidak ditandai dirty, supaya menyimpan ulang item yang tidak berubah (mis. di EOD)
     * tidak mengirim apa pun ke server.
     */
    private suspend fun updateKeepingSync(item: FollowUp) {
        val existing = followUps.getById(item.id) ?: return
        val updated = item.toEntity().copy(
            remoteId = existing.remoteId, remoteUpdatedAt = existing.remoteUpdatedAt,
            dirty = existing.dirty, conflictJson = existing.conflictJson,
        )
        if (updated != existing) followUps.update(updated.copy(dirty = true))
    }

    suspend fun delete(id: Long) = db.withTransaction {
        val remoteId = followUps.getById(id)?.remoteId
        followUps.delete(id)
        // Sama seperti habit/to-do: hanya dicatat kalau sudah pernah terkirim ke server.
        if (remoteId != null) {
            outbox.insertPendingDelete(
                PendingDelete(entity = PendingDeleteEntity.FOLLOW_UP, remoteId = remoteId, createdAt = System.currentTimeMillis()),
            )
        }
    }

    /**
     * Menyelesaikan bentrok. [useMine] true: versi HP dipertahankan dan menimpa server di push berikutnya. False: versi
     * terbaru dari server dipakai dan perubahan di HP dibuang.
     */
    suspend fun resolveConflict(id: Long, useMine: Boolean) = db.withTransaction {
        val entity = followUps.getById(id) ?: return@withTransaction
        val server = entity.conflictJson?.let { runCatching { RemoteFollowUp.fromJson(it) }.getOrNull() }
        when {
            server == null -> followUps.update(entity.copy(conflictJson = null))
            useMine -> followUps.update(entity.copy(remoteUpdatedAt = server.updatedAt, conflictJson = null, dirty = true))
            else -> followUps.update(server.applyTo(entity))
        }
    }

    // Dua fungsi di bawah mengubah entitas, bukan model domain, supaya `remoteUpdatedAt` dari server tetap ada
    // (dibutuhkan untuk deteksi bentrok) dan baris ditandai dirty agar terkirim (tahap 33 langkah 3c).
    suspend fun completeScrum(date: LocalDate, nowMillis: Long) {
        val current = workDays.get(date.toString()) ?: WorkDayEntity(date.toString(), null, null, null)
        workDays.upsert(current.copy(scrumDoneAt = nowMillis, dirty = true))
    }

    /** Menyimpan catatan EOD dan menandai EOD selesai. Catatan kosong disimpan sebagai tanpa catatan. */
    suspend fun completeEod(date: LocalDate, note: String, nowMillis: Long) {
        val current = workDays.get(date.toString()) ?: WorkDayEntity(date.toString(), null, null, null)
        workDays.upsert(current.copy(eodNote = note.trim().ifEmpty { null }, eodDoneAt = nowMillis, dirty = true))
    }
}
