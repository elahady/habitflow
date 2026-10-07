package com.roziqrizal.habitflow.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.roziqrizal.habitflow.domain.prayer.PrayerName
import com.roziqrizal.habitflow.domain.schedule.BlockStart
import com.roziqrizal.habitflow.domain.schedule.NotificationLevel
import com.roziqrizal.habitflow.domain.schedule.ScheduleBlock
import com.roziqrizal.habitflow.domain.schedule.WorkAction
import java.time.LocalTime

/**
 * Blok jadwal. Waktu mulai disimpan di dua kolom: [startType] [START_FIXED] memakai
 * [startValue] sebagai menit sejak 00.00, [START_PRAYER] memakai [prayer] (nama [PrayerName])
 * dan [startValue] sebagai selisih menit. [activeDays] adalah bitmask `Days`.
 */
@Entity(tableName = "schedule_blocks")
data class ScheduleBlockEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val startType: Int,
    val startValue: Int,
    val prayer: String?,
    val durationMinutes: Int,
    val endMinuteOfDay: Int?,
    val activeDays: Int,
    val level: String,
    val sortOrder: Int,
    /** Nama [WorkAction] kalau notifikasi blok ini membuka daily scrum atau EOD di tab Kerja. */
    val workAction: String? = null,
) {
    companion object {
        const val START_FIXED = 0
        const val START_PRAYER = 1
    }
}

/** Tautan blok ke habit. Terhapus otomatis kalau blok atau habitnya dihapus. */
@Entity(
    tableName = "schedule_block_habits",
    primaryKeys = ["blockId", "habitId"],
    foreignKeys = [
        ForeignKey(
            entity = ScheduleBlockEntity::class,
            parentColumns = ["id"],
            childColumns = ["blockId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Habit::class,
            parentColumns = ["id"],
            childColumns = ["habitId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("habitId")],
)
data class ScheduleBlockHabit(
    val blockId: Long,
    val habitId: Long,
)

/** Tanggal (ISO) yang ditandai "Hari ini libur". */
@Entity(tableName = "days_off")
data class DayOff(
    @PrimaryKey val date: String,
)

fun ScheduleBlockEntity.toDomain(habitIds: Set<Long>): ScheduleBlock = ScheduleBlock(
    id = id,
    name = name,
    start = when (startType) {
        ScheduleBlockEntity.START_PRAYER ->
            BlockStart.Prayer(PrayerName.valueOf(prayer ?: PrayerName.SUBUH.name), startValue)
        else -> BlockStart.Fixed(LocalTime.of(startValue / 60, startValue % 60))
    },
    durationMinutes = durationMinutes,
    endMinuteOfDay = endMinuteOfDay,
    activeDays = activeDays,
    level = runCatching { NotificationLevel.valueOf(level) }.getOrDefault(NotificationLevel.INFO),
    sortOrder = sortOrder,
    workAction = workAction?.let { name -> WorkAction.entries.firstOrNull { it.name == name } },
    habitIds = habitIds,
)

fun ScheduleBlock.toEntity(): ScheduleBlockEntity {
    val (type, value, prayerName) = when (val s = start) {
        is BlockStart.Fixed -> Triple(ScheduleBlockEntity.START_FIXED, s.time.hour * 60 + s.time.minute, null)
        is BlockStart.Prayer -> Triple(ScheduleBlockEntity.START_PRAYER, s.offsetMinutes, s.name.name)
    }
    return ScheduleBlockEntity(
        id = id,
        name = name,
        startType = type,
        startValue = value,
        prayer = prayerName,
        durationMinutes = durationMinutes,
        endMinuteOfDay = endMinuteOfDay,
        activeDays = activeDays,
        level = level.name,
        sortOrder = sortOrder,
        workAction = workAction?.name,
    )
}
