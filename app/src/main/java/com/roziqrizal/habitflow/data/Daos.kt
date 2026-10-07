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

    @Query("UPDATE habits SET isMandatory = :mandatory WHERE id = :id")
    suspend fun setMandatory(id: Long, mandatory: Boolean)

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

@Dao
interface ScheduleDao {

    @Query("SELECT * FROM schedule_blocks ORDER BY sortOrder ASC, id ASC")
    fun observeBlocks(): Flow<List<ScheduleBlockEntity>>

    @Query("SELECT * FROM schedule_block_habits")
    fun observeLinks(): Flow<List<ScheduleBlockHabit>>

    @Query("SELECT date FROM days_off")
    fun observeDaysOff(): Flow<List<String>>

    @Query("SELECT * FROM schedule_blocks ORDER BY sortOrder ASC, id ASC")
    suspend fun getBlocks(): List<ScheduleBlockEntity>

    @Query("SELECT * FROM schedule_block_habits")
    suspend fun getLinks(): List<ScheduleBlockHabit>

    @Query("SELECT COUNT(*) FROM days_off WHERE date = :date")
    suspend fun countDayOff(date: String): Int

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM schedule_blocks")
    suspend fun nextSortOrder(): Int

    @Insert
    suspend fun insertBlock(block: ScheduleBlockEntity): Long

    @Update
    suspend fun updateBlock(block: ScheduleBlockEntity)

    @Query("DELETE FROM schedule_blocks WHERE id = :id")
    suspend fun deleteBlock(id: Long): Int

    @Query("DELETE FROM schedule_block_habits WHERE blockId = :blockId")
    suspend fun clearLinks(blockId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLinks(links: List<ScheduleBlockHabit>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDayOff(dayOff: DayOff)

    @Query("DELETE FROM days_off WHERE date = :date")
    suspend fun deleteDayOff(date: String)

    @Query("SELECT habitId FROM schedule_block_habits WHERE blockId = :blockId")
    suspend fun habitIdsOf(blockId: Long): List<Long>
}

@Dao
interface FollowUpDao {

    @Query("SELECT * FROM follow_ups")
    fun observeAll(): Flow<List<FollowUpEntity>>

    @Query("SELECT * FROM follow_ups")
    suspend fun getAll(): List<FollowUpEntity>

    @Insert
    suspend fun insert(item: FollowUpEntity): Long

    @Update
    suspend fun update(item: FollowUpEntity)

    @Query("DELETE FROM follow_ups WHERE id = :id")
    suspend fun delete(id: Long): Int
}

@Dao
interface WorkDayDao {

    @Query("SELECT * FROM work_days")
    fun observeAll(): Flow<List<WorkDayEntity>>

    @Query("SELECT * FROM work_days WHERE date = :date")
    suspend fun get(date: String): WorkDayEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(day: WorkDayEntity)
}
