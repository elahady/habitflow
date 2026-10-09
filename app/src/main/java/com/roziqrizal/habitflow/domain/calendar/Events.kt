package com.roziqrizal.habitflow.domain.calendar

import com.roziqrizal.habitflow.domain.schedule.BlockStart
import com.roziqrizal.habitflow.domain.schedule.Days
import com.roziqrizal.habitflow.domain.schedule.EventMarker
import com.roziqrizal.habitflow.domain.schedule.MINUTES_PER_DAY
import com.roziqrizal.habitflow.domain.schedule.NotificationLevel
import com.roziqrizal.habitflow.domain.schedule.ResolvedBlock
import com.roziqrizal.habitflow.domain.schedule.ScheduleBlock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/*
 * Acara dan pengulangannya (tahap 22). Aturan lengkap di docs/rancangan.md bagian Kalender.
 */

enum class EventLabel { WORK, PERSONAL }

enum class RecurrenceType { NONE, DAILY, WEEKLY, MONTHLY_DATE, MONTHLY_WEEKDAY, YEARLY }

/** Urutan hari "terakhir" untuk pengulangan bulanan per urutan hari (misalnya Jumat terakhir). */
const val LAST_WEEK = -1

const val DEFAULT_EVENT_DURATION_MINUTES = 60
const val DEFAULT_REMINDER_MINUTES = 15
const val MAX_WEEK_INTERVAL = 12

/** Pilihan pengingat di editor, dalam menit sebelum mulai. Null (tanpa pengingat) ada di luar daftar ini. */
val REMINDER_CHOICES = listOf(5, 10, 15, 30, 60)

/**
 * Aturan pengulangan. [intervalWeeks] dan [weekDays] hanya dipakai [RecurrenceType.WEEKLY] (hari kosong berarti hari
 * tanggal mulai, bit 0 = Senin seperti [Days]). [weekOfMonth] hanya dipakai [RecurrenceType.MONTHLY_WEEKDAY]: 1 sampai 4
 * atau [LAST_WEEK], untuk hari dalam seminggu yang sama dengan tanggal mulai.
 */
data class Recurrence(
    val type: RecurrenceType = RecurrenceType.NONE,
    val intervalWeeks: Int = 1,
    val weekDays: Int = 0,
    val weekOfMonth: Int = 1,
)

/**
 * Satu acara HabitFlow. [startMinute] kosong berarti sepanjang hari. [until] adalah kejadian terakhir yang masih ikut
 * (tanggalnya ikut dihitung). [reminderMinutes] kosong berarti tanpa pengingat.
 */
data class CalendarEvent(
    val id: Long,
    val title: String,
    val label: EventLabel,
    val startDate: LocalDate,
    val startMinute: Int?,
    val durationMinutes: Int = DEFAULT_EVENT_DURATION_MINUTES,
    val recurrence: Recurrence = Recurrence(),
    val until: LocalDate? = null,
    val reminderMinutes: Int? = DEFAULT_REMINDER_MINUTES,
    val note: String? = null,
) {
    val allDay: Boolean get() = startMinute == null
}

/**
 * Pengecualian satu kejadian, dikenali lewat [originalDate] (tanggal menurut aturan pengulangan). [skipped] menghilangkan
 * kejadian itu. Kalau tidak, nilai yang tidak kosong menggantikan nilai acara hanya untuk kejadian itu.
 */
data class EventException(
    val eventId: Long,
    val originalDate: LocalDate,
    val skipped: Boolean = false,
    val newDate: LocalDate? = null,
    val newStartMinute: Int? = null,
    val newDurationMinutes: Int? = null,
    val newTitle: String? = null,
)

/** Satu kejadian acara di tanggal tertentu, sudah memperhitungkan pengecualian. */
data class EventOccurrence(
    val eventId: Long,
    val originalDate: LocalDate,
    val date: LocalDate,
    val startMinute: Int?,
    val durationMinutes: Int,
    val title: String,
    val label: EventLabel,
    val recurring: Boolean,
    /** Kejadian ini diubah sendiri (tanggal, jam, durasi, atau judul). */
    val changed: Boolean,
    val reminderMinutes: Int?,
    val note: String?,
    /** Terisi untuk acara dari kalender HP (hanya baca, tanpa pengingat): nama kalendernya. [eventId] lalu id acara di HP. */
    val calendarName: String? = null,
) {
    val allDay: Boolean get() = startMinute == null

    val fromPhone: Boolean get() = calendarName != null

    /** Menit selesai, dipotong di 24.00. Null untuk acara sepanjang hari. */
    val endMinute: Int? get() = startMinute?.let { minOf(it + durationMinutes, MINUTES_PER_DAY) }
}

/** Urutan hari dalam bulan untuk [date]: 1 sampai 4, atau [LAST_WEEK] untuk hari ke-5 di bulan itu. */
fun weekOfMonthFor(date: LocalDate): Int {
    val ordinal = (date.dayOfMonth - 1) / 7 + 1
    return if (ordinal >= 5) LAST_WEEK else ordinal
}

private fun isLastOfMonth(date: LocalDate): Boolean = date.plusDays(7).month != date.month

/** Apakah aturan pengulangan [CalendarEvent] jatuh di [date] (sebelum pengecualian). */
fun CalendarEvent.occursOn(date: LocalDate): Boolean {
    if (date.isBefore(startDate)) return false
    if (until != null && date.isAfter(until)) return false
    return when (recurrence.type) {
        RecurrenceType.NONE -> date == startDate
        RecurrenceType.DAILY -> true
        RecurrenceType.WEEKLY -> weeklyOccursOn(date)
        // Tanggal yang tidak ada di bulan itu (31 di bulan pendek) jatuh di hari terakhir bulan itu.
        RecurrenceType.MONTHLY_DATE -> date.dayOfMonth == minOf(startDate.dayOfMonth, date.lengthOfMonth())
        RecurrenceType.MONTHLY_WEEKDAY -> date.dayOfWeek == startDate.dayOfWeek && when (recurrence.weekOfMonth) {
            LAST_WEEK -> isLastOfMonth(date)
            else -> (date.dayOfMonth - 1) / 7 + 1 == recurrence.weekOfMonth
        }
        // 29 Februari di tahun biasa jatuh di 28 Februari.
        RecurrenceType.YEARLY ->
            date.month == startDate.month && date.dayOfMonth == minOf(startDate.dayOfMonth, date.lengthOfMonth())
    }
}

private fun CalendarEvent.weeklyOccursOn(date: LocalDate): Boolean {
    val mask = if (recurrence.weekDays == 0) Days.bit(startDate.dayOfWeek) else recurrence.weekDays
    if (!Days.contains(mask, date.dayOfWeek)) return false
    val interval = recurrence.intervalWeeks.coerceAtLeast(1)
    // Minggu dihitung mulai Senin; minggu pertama adalah minggu yang memuat tanggal mulai.
    val weeks = ChronoUnit.WEEKS.between(startDate.with(DayOfWeek.MONDAY), date.with(DayOfWeek.MONDAY))
    return weeks % interval == 0L
}

private fun CalendarEvent.occurrence(date: LocalDate, original: LocalDate, exception: EventException?): EventOccurrence =
    EventOccurrence(
        eventId = id,
        originalDate = original,
        date = date,
        startMinute = exception?.newStartMinute ?: startMinute,
        durationMinutes = exception?.newDurationMinutes ?: durationMinutes,
        title = exception?.newTitle ?: title,
        label = label,
        recurring = recurrence.type != RecurrenceType.NONE,
        changed = exception != null,
        reminderMinutes = reminderMinutes,
        note = note,
    )

/**
 * Semua kejadian di [from] sampai [to] (keduanya ikut), urut menurut tanggal lalu jam (sepanjang hari lebih dulu). Kejadian
 * yang dilewati hilang, kejadian yang diubah muncul di tanggal barunya (juga kalau tanggal aslinya di luar rentang). Pengecualian
 * untuk tanggal yang sudah bukan kejadian menurut aturan diabaikan.
 */
fun occurrencesBetween(
    events: List<CalendarEvent>,
    exceptions: List<EventException>,
    from: LocalDate,
    to: LocalDate,
): List<EventOccurrence> {
    if (to.isBefore(from)) return emptyList()
    val exceptionsByEvent = exceptions.groupBy { it.eventId }
    val days = generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.toList()
    val result = mutableListOf<EventOccurrence>()
    for (event in events) {
        val own = exceptionsByEvent[event.id].orEmpty().associateBy { it.originalDate }
        for (day in days) {
            if (day !in own && event.occursOn(day)) result += event.occurrence(day, day, null)
        }
        for (exception in own.values) {
            if (exception.skipped || !event.occursOn(exception.originalDate)) continue
            val date = exception.newDate ?: exception.originalDate
            if (!date.isBefore(from) && !date.isAfter(to)) result += event.occurrence(date, exception.originalDate, exception)
        }
    }
    return result.sortedWith(compareBy({ it.date }, { it.startMinute ?: -1 }, { it.title }))
}

fun occurrencesOn(events: List<CalendarEvent>, exceptions: List<EventException>, date: LocalDate): List<EventOccurrence> =
    occurrencesBetween(events, exceptions, date, date)

/** Pada hari libur, acara berlabel Kerja mati (timeline, Agenda, dan pengingat). Acara Pribadi tetap. */
fun List<EventOccurrence>.visibleOnDay(dayOff: Boolean): List<EventOccurrence> =
    if (dayOff) filter { it.label != EventLabel.WORK } else this

/** Pengingat satu kejadian: menit sejak 00.00 saat notifikasi muncul. */
data class EventReminder(val minute: Int, val occurrence: EventOccurrence)

/**
 * Pengingat untuk [occurrences] pada hari yang sama dengan tanggal kejadiannya. Hanya acara berjam dengan pengingat; pengingat
 * yang jatuh sebelum 00.00 (acara dini hari) tidak dibuat.
 */
fun eventReminders(occurrences: List<EventOccurrence>): List<EventReminder> =
    occurrences.mapNotNull { occurrence ->
        val start = occurrence.startMinute ?: return@mapNotNull null
        val before = occurrence.reminderMinutes ?: return@mapNotNull null
        (start - before).takeIf { it >= 0 }?.let { EventReminder(it, occurrence) }
    }.sortedWith(compareBy({ it.minute }, { it.occurrence.title }))

/** Kejadian berjam sebagai blok timeline (untuk Sekarang/Berikutnya). Sepanjang hari tidak menjadi blok. */
fun EventOccurrence.toResolvedBlock(): ResolvedBlock? {
    val start = startMinute ?: return null
    val end = endMinute ?: return null
    val block = ScheduleBlock(
        // Id negatif supaya tidak bertabrakan dengan blok jadwal; acara HP diberi rentang sendiri.
        id = if (fromPhone) -(PHONE_BLOCK_ID_BASE + eventId) else -eventId,
        name = title,
        start = BlockStart.Fixed(LocalTime.of(start / 60 % 24, start % 60)),
        durationMinutes = durationMinutes,
        level = NotificationLevel.INFO,
        sortOrder = EVENT_SORT_ORDER,
    )
    return ResolvedBlock(block, start, end.coerceAtLeast(start), EventMarker(label, calendarName))
}

private const val PHONE_BLOCK_ID_BASE = 1_000_000_000L

/** Acara diurutkan sesudah blok jadwal yang mulai di menit yang sama. */
const val EVENT_SORT_ORDER = 1000

/** Gabungan blok jadwal dan blok acara, urut seperti `resolveBlocks`. */
fun mergeBlocks(blocks: List<ResolvedBlock>, eventBlocks: List<ResolvedBlock>): List<ResolvedBlock> =
    (blocks + eventBlocks).sortedWith(compareBy({ it.startMinute }, { it.block.sortOrder }, { it.block.id }))
