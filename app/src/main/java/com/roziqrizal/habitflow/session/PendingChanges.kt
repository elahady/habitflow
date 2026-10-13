package com.roziqrizal.habitflow.session

import com.roziqrizal.habitflow.data.HabitDatabase

/** Jumlah baris yang belum sampai ke server, per sumber. */
data class ChangeCounts(
    /** Centang habit yang menunggu dikirim (antrean `habit_entry_outbox`). */
    val habitEntryOutbox: Int = 0,
    /** Penghapusan yang menunggu dikirim (`pending_deletes`: habit, to-do, follow-up). */
    val pendingDeletes: Int = 0,
    val unsyncedHabits: Int = 0,
    val unsyncedTodos: Int = 0,
    val unsyncedFollowUps: Int = 0,
) {
    val total: Int get() = habitEntryOutbox + pendingDeletes + unsyncedHabits + unsyncedTodos + unsyncedFollowUps
}

/**
 * Nasib data yang **hanya** hidup di snapshot cadangan (jadwal, kesehatan, makan, kalender, pengaturan), yang tidak punya
 * antrean kirim per baris.
 */
enum class BackupState {
    /** Sinkron otomatis mati: tidak ada cadangan snapshot, jadi data itu hanya ada di HP ini dan ikut terhapus. Cadangan itu opt-in, Keluar tidak mengunggahnya diam-diam. */
    Off,

    /** Sinkron otomatis menyala dan isi HP sama dengan cadangan terakhir yang berhasil terkirim. */
    Saved,

    /** Sinkron otomatis menyala tapi isi HP berbeda dari cadangan terakhir: ada perubahan yang akan hilang. */
    Stale,
}

/** Yang akan hilang kalau Keluar sekarang (tahap 31H): baris yang punya antrean kirim, ditambah keadaan cadangan snapshot. */
data class PendingChanges(val items: Int, val backup: BackupState) {
    /** Ada sesuatu yang perlu diperingatkan ke pengguna. */
    val any: Boolean get() = items > 0 || backup != BackupState.Saved

    companion object {
        fun of(counts: ChangeCounts, backup: BackupState) = PendingChanges(counts.total, backup)
    }
}

/** Sumber perubahan belum terkirim. Dipisah supaya urutan alur Keluar bisa diuji tanpa Room. */
interface PendingChangeSource {
    suspend fun counts(): ChangeCounts

    suspend fun backupState(): BackupState
}

class RoomPendingChangeSource(
    private val db: HabitDatabase,
    private val backupState: suspend () -> BackupState,
) : PendingChangeSource {
    override suspend fun counts() = ChangeCounts(
        habitEntryOutbox = db.habitSyncOutboxDao().entryOutboxCount(),
        pendingDeletes = db.habitSyncOutboxDao().pendingDeleteCount(),
        unsyncedHabits = db.habitDao().unsyncedCount(),
        unsyncedTodos = db.todoDao().unsyncedCount(),
        unsyncedFollowUps = db.followUpDao().unsyncedCount(),
    )

    override suspend fun backupState() = backupState.invoke()
}
