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

    @Query("SELECT id FROM habits WHERE autoSource = :source ORDER BY id ASC LIMIT 1")
    suspend fun idByAutoSource(source: String): Long?
}

@Dao
interface DrinkDao {

    @Query("SELECT * FROM drink_counts WHERE date = :date AND kind = :kind")
    fun observe(date: String, kind: String): Flow<DrinkCount?>

    @Query("SELECT * FROM drink_counts WHERE date = :date AND kind = :kind")
    suspend fun get(date: String, kind: String): DrinkCount?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: DrinkCount)
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

    @Query("SELECT date FROM holiday_cancellations")
    fun observeHolidayCancellations(): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM holiday_cancellations WHERE date = :date")
    suspend fun countHolidayCancellation(date: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertHolidayCancellation(cancellation: HolidayCancellation)

    @Query("DELETE FROM holiday_cancellations WHERE date = :date")
    suspend fun deleteHolidayCancellation(date: String)

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

@Dao
interface HealthDao {

    @Query("SELECT * FROM weight_entries ORDER BY timeMillis ASC, id ASC")
    fun observeWeights(): Flow<List<WeightEntry>>

    @Query("SELECT * FROM blood_pressure_entries ORDER BY timeMillis ASC, id ASC")
    fun observeBloodPressures(): Flow<List<BloodPressureEntry>>

    @Query("SELECT COUNT(*) FROM weight_entries WHERE timeMillis >= :from AND timeMillis < :until")
    suspend fun weightCountBetween(from: Long, until: Long): Int

    @Query("SELECT COUNT(*) FROM blood_pressure_entries WHERE timeMillis >= :from AND timeMillis < :until")
    suspend fun bloodPressureCountBetween(from: Long, until: Long): Int

    @Insert
    suspend fun insertWeight(entry: WeightEntry): Long

    @Insert
    suspend fun insertBloodPressure(entry: BloodPressureEntry): Long
}

/** Baca dan tulis massal seluruh tabel untuk snapshot cadangan (tahap 19B). */
@Dao
interface SyncDao {

    @Query("SELECT * FROM habits") suspend fun habits(): List<Habit>
    @Query("SELECT * FROM habit_entries") suspend fun habitEntries(): List<HabitEntry>
    @Query("SELECT * FROM todos") suspend fun todos(): List<Todo>
    @Query("SELECT * FROM schedule_blocks") suspend fun scheduleBlocks(): List<ScheduleBlockEntity>
    @Query("SELECT * FROM schedule_block_habits") suspend fun scheduleBlockHabits(): List<ScheduleBlockHabit>
    @Query("SELECT * FROM days_off") suspend fun daysOff(): List<DayOff>
    @Query("SELECT * FROM follow_ups") suspend fun followUps(): List<FollowUpEntity>
    @Query("SELECT * FROM work_days") suspend fun workDays(): List<WorkDayEntity>
    @Query("SELECT * FROM drink_counts") suspend fun drinkCounts(): List<DrinkCount>
    @Query("SELECT * FROM weight_entries") suspend fun weightEntries(): List<WeightEntry>
    @Query("SELECT * FROM blood_pressure_entries") suspend fun bloodPressureEntries(): List<BloodPressureEntry>
    @Query("SELECT * FROM events") suspend fun events(): List<EventEntity>
    @Query("SELECT * FROM event_exceptions") suspend fun eventExceptions(): List<EventExceptionEntity>
    @Query("SELECT * FROM holiday_cancellations") suspend fun holidayCancellations(): List<HolidayCancellation>

    // Pengecualian dihapus sebelum acaranya (kunci asing).
    @Query("DELETE FROM event_exceptions") suspend fun clearEventExceptions()
    @Query("DELETE FROM events") suspend fun clearEvents()
    @Query("DELETE FROM holiday_cancellations") suspend fun clearHolidayCancellations()

    @Query("DELETE FROM drink_counts") suspend fun clearDrinkCounts()
    @Query("DELETE FROM weight_entries") suspend fun clearWeightEntries()
    @Query("DELETE FROM blood_pressure_entries") suspend fun clearBloodPressureEntries()
    @Query("DELETE FROM schedule_block_habits") suspend fun clearScheduleBlockHabits()
    @Query("DELETE FROM schedule_blocks") suspend fun clearScheduleBlocks()
    @Query("DELETE FROM days_off") suspend fun clearDaysOff()
    @Query("DELETE FROM follow_ups") suspend fun clearFollowUps()
    @Query("DELETE FROM work_days") suspend fun clearWorkDays()
    @Query("DELETE FROM habit_entries") suspend fun clearHabitEntries()
    @Query("DELETE FROM todos") suspend fun clearTodos()
    @Query("DELETE FROM habits") suspend fun clearHabits()

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertHabits(items: List<Habit>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertHabitEntries(items: List<HabitEntry>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertTodos(items: List<Todo>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertScheduleBlocks(items: List<ScheduleBlockEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertScheduleBlockHabits(items: List<ScheduleBlockHabit>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertDaysOff(items: List<DayOff>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertFollowUps(items: List<FollowUpEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertWorkDays(items: List<WorkDayEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertDrinkCounts(items: List<DrinkCount>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertWeightEntries(items: List<WeightEntry>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertBloodPressureEntries(items: List<BloodPressureEntry>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertEvents(items: List<EventEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertEventExceptions(items: List<EventExceptionEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertHolidayCancellations(items: List<HolidayCancellation>)
}

/** Acara HabitFlow dan pengecualian kejadiannya (tahap 22). */
@Dao
interface EventDao {

    @Query("SELECT * FROM events ORDER BY startDate ASC, id ASC")
    fun observeEvents(): Flow<List<EventEntity>>

    @Query("SELECT * FROM event_exceptions")
    fun observeExceptions(): Flow<List<EventExceptionEntity>>

    @Query("SELECT * FROM events")
    suspend fun getEvents(): List<EventEntity>

    @Query("SELECT * FROM event_exceptions")
    suspend fun getExceptions(): List<EventExceptionEntity>

    @Insert
    suspend fun insert(event: EventEntity): Long

    // @Update, bukan REPLACE: REPLACE menghapus baris lama dulu sehingga pengecualiannya ikut terhapus lewat cascade.
    @Update
    suspend fun update(event: EventEntity)

    @Query("DELETE FROM events WHERE id = :id")
    suspend fun delete(id: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertException(exception: EventExceptionEntity)

    @Query("DELETE FROM event_exceptions WHERE eventId = :eventId AND originalDate = :originalDate")
    suspend fun deleteException(eventId: Long, originalDate: String): Int
}
