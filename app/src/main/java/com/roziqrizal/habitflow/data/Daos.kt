package com.roziqrizal.habitflow.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitDao {

    @Query("SELECT * FROM habits ORDER BY isMandatory DESC, sortOrder ASC, id ASC")
    fun observeAll(): Flow<List<Habit>>

    @Query("SELECT * FROM habits WHERE id = :id")
    suspend fun getById(id: Long): Habit?

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM habits")
    suspend fun nextSortOrder(): Int

    @Insert
    suspend fun insert(habit: Habit): Long

    @Update
    suspend fun update(habit: Habit)

    @Query("DELETE FROM habits WHERE id = :id AND isMandatory = 0")
    suspend fun deleteIfNotMandatory(id: Long): Int
}

@Dao
interface HabitEntryDao {

    @Query("SELECT * FROM habit_entries")
    fun observeAll(): Flow<List<HabitEntry>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entry: HabitEntry): Long

    @Query("DELETE FROM habit_entries WHERE habitId = :habitId AND date = :date")
    suspend fun delete(habitId: Long, date: String): Int

    @Query("DELETE FROM habit_entries WHERE habitId = :habitId")
    suspend fun deleteAllForHabit(habitId: Long): Int

    @Query("SELECT COUNT(*) FROM habit_entries WHERE habitId = :habitId AND date = :date")
    suspend fun count(habitId: Long, date: String): Int
}

@Dao
interface TodoDao {

    @Query("SELECT * FROM todos WHERE date = :date ORDER BY done ASC, id ASC")
    fun observeByDate(date: String): Flow<List<Todo>>

    @Query("SELECT * FROM todos")
    fun observeAll(): Flow<List<Todo>>

    @Query("SELECT * FROM todos WHERE done = 0 AND date < :today ORDER BY date ASC, id ASC")
    suspend fun undoneBefore(today: String): List<Todo>

    @Query("SELECT COUNT(*) FROM todos WHERE date = :date")
    suspend fun countByDate(date: String): Int

    @Insert
    suspend fun insert(todo: Todo): Long

    @Update
    suspend fun update(todo: Todo)

    @Query("DELETE FROM todos WHERE id = :id")
    suspend fun delete(id: Long): Int
}
