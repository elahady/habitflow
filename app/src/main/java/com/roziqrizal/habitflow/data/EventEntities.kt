package com.roziqrizal.habitflow.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.roziqrizal.habitflow.domain.calendar.CalendarEvent
import com.roziqrizal.habitflow.domain.calendar.EventException
import com.roziqrizal.habitflow.domain.calendar.EventLabel
import com.roziqrizal.habitflow.domain.calendar.Recurrence
import com.roziqrizal.habitflow.domain.calendar.RecurrenceType
import java.time.LocalDate

/**
 * Acara HabitFlow (tahap 22). Tanggal ISO `yyyy-MM-dd`. [startMinute] kosong berarti sepanjang hari. [label] dan
 * [recurrence] adalah nama `EventLabel` dan `RecurrenceType`.
 */
@Entity(tableName = "events")
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val label: String,
    val startDate: String,
    val startMinute: Int?,
    val durationMinutes: Int,
    val recurrence: String,
    val intervalWeeks: Int,
    val weekDays: Int,
    val weekOfMonth: Int,
    val untilDate: String?,
    val reminderMinutes: Int?,
    val note: String?,
)

/** Pengecualian satu kejadian acara. Terhapus otomatis kalau acaranya dihapus. */
@Entity(
    tableName = "event_exceptions",
    primaryKeys = ["eventId", "originalDate"],
    foreignKeys = [
        ForeignKey(
            entity = EventEntity::class,
            parentColumns = ["id"],
            childColumns = ["eventId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("eventId")],
)
data class EventExceptionEntity(
    val eventId: Long,
    val originalDate: String,
    val skipped: Boolean,
    val newDate: String?,
    val newStartMinute: Int?,
    val newDurationMinutes: Int?,
    val newTitle: String?,
)

/** Tanggal libur nasional yang dibatalkan pengguna (kantor tetap masuk). */
@Entity(tableName = "holiday_cancellations")
data class HolidayCancellation(
    @PrimaryKey val date: String,
)

fun EventEntity.toDomain(): CalendarEvent? {
    val start = runCatching { LocalDate.parse(startDate) }.getOrNull() ?: return null
    return CalendarEvent(
        id = id,
        title = title,
        label = EventLabel.entries.firstOrNull { it.name == label } ?: EventLabel.PERSONAL,
        startDate = start,
        startMinute = startMinute,
        durationMinutes = durationMinutes,
        recurrence = Recurrence(
            type = RecurrenceType.entries.firstOrNull { it.name == recurrence } ?: RecurrenceType.NONE,
            intervalWeeks = intervalWeeks,
            weekDays = weekDays,
            weekOfMonth = weekOfMonth,
        ),
        until = untilDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
        reminderMinutes = reminderMinutes,
        note = note,
    )
}

fun CalendarEvent.toEntity(): EventEntity = EventEntity(
    id = id,
    title = title.trim(),
    label = label.name,
    startDate = startDate.toString(),
    startMinute = startMinute,
    durationMinutes = durationMinutes,
    recurrence = recurrence.type.name,
    intervalWeeks = recurrence.intervalWeeks,
    weekDays = recurrence.weekDays,
    weekOfMonth = recurrence.weekOfMonth,
    untilDate = until?.toString(),
    reminderMinutes = reminderMinutes,
    note = note?.trim()?.takeIf { it.isNotEmpty() },
)

fun EventExceptionEntity.toDomain(): EventException? {
    val original = runCatching { LocalDate.parse(originalDate) }.getOrNull() ?: return null
    return EventException(
        eventId = eventId,
        originalDate = original,
        skipped = skipped,
        newDate = newDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
        newStartMinute = newStartMinute,
        newDurationMinutes = newDurationMinutes,
        newTitle = newTitle,
    )
}

fun EventException.toEntity(): EventExceptionEntity = EventExceptionEntity(
    eventId = eventId,
    originalDate = originalDate.toString(),
    skipped = skipped,
    newDate = newDate?.toString(),
    newStartMinute = newStartMinute,
    newDurationMinutes = newDurationMinutes,
    newTitle = newTitle?.trim()?.takeIf { it.isNotEmpty() },
)
