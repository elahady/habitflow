package com.roziqrizal.habitflow.data

import androidx.room.withTransaction
import com.roziqrizal.habitflow.domain.work.FollowUp
import com.roziqrizal.habitflow.domain.work.FollowUpStatus
import com.roziqrizal.habitflow.domain.work.WorkDay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/** Pintu ke follow-up kerja dan catatan harian kerja (daily scrum dan EOD). */
class WorkRepository(private val db: HabitDatabase) {

    private val followUps = db.followUpDao()
    private val workDays = db.workDayDao()

    fun observeFollowUps(): Flow<List<FollowUp>> =
        followUps.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeWorkDays(): Flow<Map<LocalDate, WorkDay>> =
        workDays.observeAll().map { list -> list.mapNotNull { it.toDomain() }.associateBy { it.date } }

    /** Untuk penerima alarm dan notifikasi, yang tidak memakai Flow. */
    suspend fun getFollowUps(): List<FollowUp> = followUps.getAll().map { it.toDomain() }

    /** Catat cepat: langsung ke Inbox tanpa memilih apa pun. Judul kosong ditolak (mengembalikan false). */
    suspend fun quickAdd(title: String, nowMillis: Long): Boolean {
        val clean = title.trim()
        if (clean.isEmpty()) return false
        followUps.insert(FollowUp(0, clean, FollowUpStatus.INBOX, createdAt = nowMillis).toEntity())
        return true
    }

    /** Simpan baru (id 0) atau ubah yang ada. Mengembalikan id. */
    suspend fun save(item: FollowUp, nowMillis: Long): Long =
        if (item.id == 0L) {
            followUps.insert(item.copy(createdAt = if (item.createdAt == 0L) nowMillis else item.createdAt).toEntity())
        } else {
            followUps.update(item.toEntity())
            item.id
        }

    /** Simpan beberapa perubahan sekaligus, misalnya hasil satu sesi daily scrum atau EOD. */
    suspend fun saveAll(items: List<FollowUp>) = db.withTransaction {
        items.forEach { followUps.update(it.toEntity()) }
    }

    suspend fun delete(id: Long) {
        followUps.delete(id)
    }

    suspend fun completeScrum(date: LocalDate, nowMillis: Long) {
        val current = workDays.get(date.toString())?.toDomain() ?: WorkDay(date)
        workDays.upsert(current.copy(scrumDoneAt = nowMillis).toEntity())
    }

    /** Menyimpan catatan EOD dan menandai EOD selesai. Catatan kosong disimpan sebagai tanpa catatan. */
    suspend fun completeEod(date: LocalDate, note: String, nowMillis: Long) {
        val current = workDays.get(date.toString())?.toDomain() ?: WorkDay(date)
        workDays.upsert(current.copy(eodNote = note.trim().ifEmpty { null }, eodDoneAt = nowMillis).toEntity())
    }
}
