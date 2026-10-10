package com.roziqrizal.habitflow.data

import androidx.room.withTransaction
import com.roziqrizal.habitflow.domain.MAX_TODOS_PER_DAY
import com.roziqrizal.habitflow.domain.canAddTodo
import com.roziqrizal.habitflow.domain.shouldCarryOver
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Satu-satunya pintu ke database untuk ViewModel. Aturan batas to-do dan pindah to-do
 * dijaga di sini, bukan di UI.
 */
class HabitRepository(private val db: HabitDatabase) {

    private val habits = db.habitDao()
    private val entries = db.habitEntryDao()
    private val todos = db.todoDao()
    private val outbox = db.habitSyncOutboxDao()

    fun observeHabits(): Flow<List<Habit>> = habits.observeAll()

    fun observeAllEntries(): Flow<List<HabitEntry>> = entries.observeAll()

    fun observeAllTodos(): Flow<List<Todo>> = todos.observeAll()

    /**
     * Centang atau batalkan habit pada tanggal tertentu. Pasangan habit dan tanggal ini ditandai sebagai diubah sendiri
     * (tahap 23), supaya centang otomatis habit makan tidak menimpanya.
     */
    suspend fun toggleHabit(habitId: Long, date: LocalDate) = db.withTransaction {
        val key = date.toString()
        entries.insertMark(HabitManualMark(habitId, key))
        if (entries.count(habitId, key) > 0) {
            entries.delete(habitId, key)
        } else {
            entries.insert(HabitEntry(habitId, key))
        }
        // Dicatat di antrian supaya terkirim ke server (tahap 28 langkah 5): endpoint toggle
        // server juga membalik status, jadi replay urutan ini apa adanya sudah cukup.
        outbox.insertEntryOutbox(HabitEntryOutbox(habitId = habitId, date = key, createdAt = System.currentTimeMillis()))
    }

    suspend fun addHabit(name: String, today: LocalDate) {
        val order = habits.nextSortOrder()
        habits.insert(Habit(name = name.trim(), createdAt = today.toString(), sortOrder = order))
    }

    suspend fun renameHabit(habit: Habit, newName: String) {
        habits.update(habit.copy(name = newName.trim(), dirty = true))
    }

    /** Status wajib bisa diatur untuk habit mana pun. Riwayat centang tidak berubah. */
    suspend fun setMandatory(habitId: Long, mandatory: Boolean) {
        habits.setMandatory(habitId, mandatory)
        habits.markDirty(habitId)
    }

    /** Habit wajib tidak bisa dihapus. Mengembalikan false kalau penghapusan ditolak. */
    suspend fun deleteHabit(habitId: Long): Boolean = db.withTransaction {
        val remoteId = habits.getByIdForSync(habitId)?.remoteId
        // Riwayat baru dihapus setelah habitnya benar-benar terhapus, supaya riwayat habit wajib
        // tidak ikut hilang saat penghapusannya ditolak.
        val deleted = habits.deleteIfNotMandatory(habitId) > 0
        if (deleted) {
            entries.deleteAllForHabit(habitId)
            entries.deleteMarksForHabit(habitId)
            // Kalau sudah pernah terkirim ke server, catat supaya ikut dihapus di sana juga
            // (tahap 28 langkah 5). Belum pernah terkirim - tidak ada yang perlu dihapus.
            if (remoteId != null) {
                outbox.insertPendingDelete(
                    PendingDelete(entity = PendingDeleteEntity.HABIT, remoteId = remoteId, createdAt = System.currentTimeMillis()),
                )
            }
        }
        deleted
    }

    /** Mengembalikan false kalau batas to-do untuk hari itu sudah penuh. */
    suspend fun addTodo(title: String, date: LocalDate): Boolean {
        val key = date.toString()
        if (!canAddTodo(todos.countByDate(key))) return false
        todos.insert(
            Todo(
                title = title.trim(),
                date = key,
                createdAt = System.currentTimeMillis(),
            )
        )
        return true
    }

    suspend fun toggleTodo(todo: Todo) {
        todos.update(todo.copy(done = !todo.done, dirty = true))
    }

    suspend fun deleteTodo(todoId: Long) = db.withTransaction {
        val remoteId = todos.getByIdForSync(todoId)?.remoteId
        todos.delete(todoId)
        // Sama seperti deleteHabit: hanya dicatat kalau sudah pernah terkirim ke server.
        if (remoteId != null) {
            outbox.insertPendingDelete(
                PendingDelete(entity = PendingDeleteEntity.TODO, remoteId = remoteId, createdAt = System.currentTimeMillis()),
            )
        }
    }

    /**
     * Pindahkan to-do yang belum selesai dari hari sebelumnya ke [today], selama masih ada
     * tempat di batas harian. To-do yang tidak muat tetap di tanggal lamanya, tidak dihapus.
     */
    suspend fun carryOver(today: LocalDate): Int = db.withTransaction {
        val key = today.toString()
        var room = MAX_TODOS_PER_DAY - todos.countByDate(key)
        var moved = 0
        todos.undoneBefore(key)
            .filter { shouldCarryOver(LocalDate.parse(it.date), it.done, today) }
            .forEach { todo ->
                if (room <= 0) return@forEach
                todos.update(todo.copy(date = key, dirty = true))
                room--
                moved++
            }
        moved
    }
}
