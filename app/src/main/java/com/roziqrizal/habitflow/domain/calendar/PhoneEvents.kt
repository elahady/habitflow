package com.roziqrizal.habitflow.domain.calendar

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Mengubah satu baris `CalendarContract.Instances` menjadi kejadian di rentang [from] sampai [to] (keduanya ikut), tahap 22.
 * Dipisah dari kueri supaya bisa diuji tanpa Android.
 *
 * Acara berjam masuk di tanggal mulainya menurut [zone] (yang mulai sebelum [from] tidak ikut). Acara sepanjang hari memakai
 * tanggal UTC, cara Kalender Android menyimpannya, dengan akhir yang eksklusif, dan dipecah satu kejadian per tanggal.
 */
fun phoneInstanceToOccurrences(
    eventId: Long,
    title: String,
    beginMillis: Long,
    endMillis: Long,
    allDay: Boolean,
    label: EventLabel,
    calendarName: String,
    from: LocalDate,
    to: LocalDate,
    zone: ZoneId,
): List<EventOccurrence> {
    fun occurrence(date: LocalDate, startMinute: Int?, duration: Int) = EventOccurrence(
        eventId = eventId,
        originalDate = date,
        date = date,
        startMinute = startMinute,
        durationMinutes = duration,
        title = title,
        label = label,
        recurring = false,
        changed = false,
        reminderMinutes = null,
        note = null,
        calendarName = calendarName,
    )

    if (allDay) {
        val first = Instant.ofEpochMilli(beginMillis).atZone(ZoneOffset.UTC).toLocalDate()
        val lastInclusive = Instant.ofEpochMilli(endMillis - 1).atZone(ZoneOffset.UTC).toLocalDate()
        val last = if (lastInclusive.isBefore(first)) first else lastInclusive
        return generateSequence(first) { it.plusDays(1) }
            .takeWhile { !it.isAfter(last) }
            .filter { !it.isBefore(from) && !it.isAfter(to) }
            .map { occurrence(it, null, 0) }
            .toList()
    }

    val start = Instant.ofEpochMilli(beginMillis).atZone(zone)
    val date = start.toLocalDate()
    if (date.isBefore(from) || date.isAfter(to)) return emptyList()
    val minutes = ((endMillis - beginMillis) / 60_000L).toInt().coerceAtLeast(1)
    return listOf(occurrence(date, start.hour * 60 + start.minute, minutes))
}
