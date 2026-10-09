package com.roziqrizal.habitflow.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.roziqrizal.habitflow.domain.work.FollowUp
import com.roziqrizal.habitflow.domain.work.FollowUpStatus
import com.roziqrizal.habitflow.domain.work.WorkDay
import java.time.LocalDate
import java.time.LocalTime

/**
 * Follow-up kerja. Tanggal disimpan ISO `yyyy-MM-dd`, jam `HH:mm`, waktu dibuat dan selesai dalam
 * milidetik. [status] adalah nama [FollowUpStatus].
 */
@Entity(tableName = "follow_ups", indices = [Index("status"), Index("date")])
data class FollowUpEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val status: String,
    val date: String?,
    val time: String?,
    val person: String?,
    val note: String?,
    val createdAt: Long,
    val doneAt: Long?,
    val pickedDate: String?,
    /** Tautan ke acara tempat follow-up dicatat (tahap 22). */
    val eventId: Long? = null,
    val eventDate: String? = null,
    val eventTitle: String? = null,
)

/** Catatan harian kerja: catatan EOD dan kapan daily scrum dan EOD diselesaikan. Satu baris per tanggal. */
@Entity(tableName = "work_days")
data class WorkDayEntity(
    @PrimaryKey val date: String,
    val eodNote: String?,
    val scrumDoneAt: Long?,
    val eodDoneAt: Long?,
)

fun FollowUpEntity.toDomain(): FollowUp = FollowUp(
    id = id,
    title = title,
    status = runCatching { FollowUpStatus.valueOf(status) }.getOrDefault(FollowUpStatus.INBOX),
    date = date?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
    time = time?.let { runCatching { LocalTime.parse(it) }.getOrNull() },
    person = person,
    note = note,
    createdAt = createdAt,
    doneAt = doneAt,
    pickedDate = pickedDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
    eventId = eventId,
    eventDate = eventDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
    eventTitle = eventTitle,
)

fun FollowUp.toEntity(): FollowUpEntity = FollowUpEntity(
    id = id,
    title = title,
    status = status.name,
    date = date?.toString(),
    time = time?.toString(),
    person = person?.trim()?.takeIf(String::isNotEmpty),
    note = note?.trim()?.takeIf(String::isNotEmpty),
    createdAt = createdAt,
    doneAt = doneAt,
    pickedDate = pickedDate?.toString(),
    eventId = eventId,
    eventDate = eventDate?.toString(),
    eventTitle = eventTitle?.trim()?.takeIf(String::isNotEmpty),
)

fun WorkDayEntity.toDomain(): WorkDay? = runCatching {
    WorkDay(LocalDate.parse(date), eodNote, scrumDoneAt, eodDoneAt)
}.getOrNull()

fun WorkDay.toEntity(): WorkDayEntity = WorkDayEntity(date.toString(), eodNote, scrumDoneAt, eodDoneAt)
