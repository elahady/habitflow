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

    fun observeHabits(): Flow<List<Habit>> = habits.observeAll()

    fun observeAllEntries(): Flow<List<HabitEntry>> = entries.observeAll()

    fun observeAllTodos(): Flow<List<Todo>> = todos.observeAll()

    /** Centang atau batalkan habit pada tanggal tertentu. */
    suspend fun toggleHabit(habitId: Long, date: LocalDate) {
        val key = date.toString()
        if (entries.count(habitId, key) > 0) {
            entries.delete(habitId, key)
        } else {
            entries.insert(HabitEntry(habitId, key))
        }
    }

    suspend fun addHabit(name: String, today: LocalDate) {
        val order = habits.nextSortOrder()
        habits.insert(Habit(name = name.trim(), createdAt = today.toString(), sortOrder = order))
    }

    suspend fun renameHabit(habit: Habit, newName: String) {
        habits.update(habit.copy(name = newName.trim()))
    }

    /** Habit wajib tidak bisa dihapus. Mengembalikan false kalau penghapusan ditolak. */
    suspend fun deleteHabit(habitId: Long): Boolean = db.withTransaction {
        // Riwayat baru dihapus setelah habitnya benar-benar terhapus, supaya riwayat habit wajib
        // tidak ikut hilang saat penghapusannya ditolak.
        val deleted = habits.deleteIfNotMandatory(habitId) > 0
        if (deleted) entries.deleteAllForHabit(habitId)
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
        todos.update(todo.copy(done = !todo.done))
    }

    suspend fun deleteTodo(todoId: Long) {
        todos.delete(todoId)
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
                todos.update(todo.copy(date = key))
                room--
                moved++
            }
        moved
    }
}
