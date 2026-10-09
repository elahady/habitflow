package com.roziqrizal.habitflow.domain.calendar

import java.time.LocalDate

/**
 * Isi satu hari untuk Agenda, timeline, dan pengingat (tahap 22): kejadian yang berlaku, hari libur, dan apakah hari itu libur.
 * [holiday] terisi walau libur nasionalnya dibatalkan ([holidayCancelled]), supaya Agenda tetap menyebutnya.
 */
data class AgendaDay(
    val date: LocalDate,
    val holiday: Holiday? = null,
    val holidayCancelled: Boolean = false,
    /** Libur manual ("Hari ini libur"). */
    val manual: Boolean = false,
    val dayOff: Boolean = false,
    val items: List<EventOccurrence> = emptyList(),
)

/**
 * Menyusun hari-hari dari [from] sampai [to] (keduanya ikut). Kejadian acara HabitFlow dan kalender HP ([phone]) digabung, lalu
 * pada hari libur (manual atau libur nasional yang tidak dibatalkan) yang berlabel Kerja dibuang. Urutan item: sepanjang hari
 * dulu, lalu menurut jam.
 */
fun buildAgenda(
    from: LocalDate,
    to: LocalDate,
    events: List<CalendarEvent>,
    exceptions: List<EventException>,
    phone: List<EventOccurrence>,
    holidays: HolidayCalendar,
    manualDaysOff: Set<LocalDate>,
    cancelledHolidays: Set<LocalDate>,
): List<AgendaDay> {
    if (to.isBefore(from)) return emptyList()
    val own = occurrencesBetween(events, exceptions, from, to)
    val byDate = (own + phone.filter { !it.date.isBefore(from) && !it.date.isAfter(to) })
        .groupBy { it.date }
    return generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.map { date ->
        val info = dayOffInfo(date, manualDaysOff, holidays, cancelledHolidays)
        val items = byDate[date].orEmpty()
            .sortedWith(compareBy({ it.startMinute ?: -1 }, { it.title }))
            .let { list -> if (info.isOff) list.filter { it.label != EventLabel.WORK } else list }
        AgendaDay(
            date = date,
            holiday = info.holiday,
            holidayCancelled = info.holidayCancelled,
            manual = info.manual,
            dayOff = info.isOff,
            items = items,
        )
    }.toList()
}

/** Tanggal di bulan [year]-[month] sebagai sel kalender: kosong (null) di awal supaya kolom pertama adalah Senin. */
fun monthCells(year: Int, month: Int): List<LocalDate?> {
    val first = LocalDate.of(year, month, 1)
    val leading = first.dayOfWeek.value - 1 // Senin = 0
    return List(leading) { null } + (1..first.lengthOfMonth()).map { first.withDayOfMonth(it) }
}
