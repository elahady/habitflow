package com.roziqrizal.habitflow.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Satu baris = satu panggilan [HabitRepository.toggleHabit] belum terkirim ke server (tahap 28
 * langkah 5). Diproses FIFO ([createdAt]): endpoint server `POST habits/{id}/entries` juga
 * membalik status (toggle), jadi me-replay urutan toggle lokal apa adanya menghasilkan state
 * akhir yang sama di server tanpa perlu tahu state absolut.
 */
@Entity(tableName = "habit_entry_outbox")
data class HabitEntryOutbox(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val habitId: Long,
    val date: String,
    val createdAt: Long,
)

/** Jenis entitas untuk [PendingDelete]. */
object PendingDeleteEntity {
    const val HABIT = "habit"
    const val TODO = "todo"
}

/**
 * Habit/todo yang sudah dihapus secara lokal tapi masih perlu dihapus di server (tahap 28
 * langkah 5). Hanya diisi kalau baris lokalnya sudah punya `remoteId` (sudah pernah terkirim) -
 * kalau belum pernah ke server, tidak ada yang perlu dihapus di sana.
 */
@Entity(tableName = "pending_deletes")
data class PendingDelete(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entity: String,
    val remoteId: Long,
    val createdAt: Long,
)
