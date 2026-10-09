package com.roziqrizal.habitflow.ui

import com.roziqrizal.habitflow.domain.calendar.CalendarEvent
import com.roziqrizal.habitflow.domain.calendar.EventLabel
import com.roziqrizal.habitflow.domain.calendar.EventOccurrence
import com.roziqrizal.habitflow.domain.calendar.Holiday
import com.roziqrizal.habitflow.domain.calendar.LAST_WEEK
import com.roziqrizal.habitflow.domain.calendar.Recurrence
import com.roziqrizal.habitflow.domain.calendar.RecurrenceType
import com.roziqrizal.habitflow.domain.schedule.Days
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/* Teks acara dan Agenda dalam bahasa Indonesia (tahap 22). */

private val ID = Locale("id", "ID")

val EventLabel.title: String
    get() = when (this) {
        EventLabel.WORK -> "Kerja"
        EventLabel.PERSONAL -> "Pribadi"
    }

private val DAY_SHORT = listOf("Sen", "Sel", "Rab", "Kam", "Jum", "Sab", "Min")
private val DAY_LONG = listOf("Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu", "Minggu")
private val ORDINAL = mapOf(1 to "pertama", 2 to "kedua", 3 to "ketiga", 4 to "keempat", LAST_WEEK to "terakhir")

fun dayShortName(day: DayOfWeek): String = DAY_SHORT[day.value - 1]

fun dayLongName(day: DayOfWeek): String = DAY_LONG[day.value - 1]

/** "Senin kedua" atau "Jumat terakhir": pilihan bulanan per urutan hari untuk [startDate]. */
fun monthlyWeekdayLabel(startDate: LocalDate, weekOfMonth: Int): String =
    "${dayLongName(startDate.dayOfWeek)} ${ORDINAL[weekOfMonth] ?: weekOfMonth.toString()}"

/** Keterangan pengulangan untuk Agenda, misalnya "Setiap 2 minggu (Sel)" atau "Setiap bulan, Senin kedua". */
fun describeRecurrence(event: CalendarEvent): String {
    val r: Recurrence = event.recurrence
    return when (r.type) {
        RecurrenceType.NONE -> "Sekali"
        RecurrenceType.DAILY -> "Setiap hari"
        RecurrenceType.WEEKLY -> {
            val mask = if (r.weekDays == 0) Days.bit(event.startDate.dayOfWeek) else r.weekDays
            val days = DayOfWeek.entries.filter { Days.contains(mask, it) }.joinToString(", ") { dayShortName(it) }
            val every = if (r.intervalWeeks <= 1) "Setiap minggu" else "Setiap ${r.intervalWeeks} minggu"
            "$every ($days)"
        }
        RecurrenceType.MONTHLY_DATE -> "Setiap bulan tanggal ${event.startDate.dayOfMonth}"
        RecurrenceType.MONTHLY_WEEKDAY -> "Setiap bulan, ${monthlyWeekdayLabel(event.startDate, r.weekOfMonth)}"
        RecurrenceType.YEARLY -> "Setiap tahun"
    }
}

/** "14.00–15.00" untuk acara berjam, atau "Sepanjang hari". */
fun occurrenceTime(occurrence: EventOccurrence): String {
    val start = occurrence.startMinute ?: return "Sepanjang hari"
    val end = occurrence.endMinute ?: return formatMinute(start)
    return "${formatMinute(start)}–${formatMinute(end)}"
}

/** Keterangan satu baris Agenda: label, lalu pengulangan (acara HabitFlow) atau nama kalender HP. */
fun occurrenceCaption(occurrence: EventOccurrence, recurrence: String?): String =
    listOfNotNull(occurrence.label.title, occurrence.calendarName ?: recurrence, if (occurrence.changed) "diubah" else null)
        .joinToString(" · ")

private val DAY_HEADER = DateTimeFormatter.ofPattern("EEEE, d MMMM", ID)

/** "Hari ini" untuk [today], selain itu "Jumat, 9 Oktober". */
fun agendaDayTitle(date: LocalDate, today: LocalDate): String =
    if (date == today) "Hari ini · ${date.format(DAY_HEADER)}" else date.format(DAY_HEADER)

private val MONTH_TITLE = DateTimeFormatter.ofPattern("MMMM yyyy", ID)

fun monthTitle(year: Int, month: Int): String = LocalDate.of(year, month, 1).format(MONTH_TITLE)

/** "Libur: <nama>" atau "Cuti bersama: <nama>", ditambah "(dibatalkan)" kalau libur nasionalnya dibatalkan. */
fun holidayLine(holiday: Holiday, cancelled: Boolean): String =
    (if (holiday.cutiBersama) holiday.label else "Libur: ${holiday.name}") + if (cancelled) " (dibatalkan)" else ""
