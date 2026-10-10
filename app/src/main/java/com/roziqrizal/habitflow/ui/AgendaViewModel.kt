package com.roziqrizal.habitflow.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roziqrizal.habitflow.data.EventRepository
import com.roziqrizal.habitflow.data.ScheduleRepository
import com.roziqrizal.habitflow.data.calendar.CalendarSettings
import com.roziqrizal.habitflow.data.calendar.PhoneCalendarSource
import com.roziqrizal.habitflow.domain.calendar.AgendaDay
import com.roziqrizal.habitflow.domain.calendar.CalendarEvent
import com.roziqrizal.habitflow.domain.calendar.EventException
import com.roziqrizal.habitflow.domain.calendar.buildAgenda
import com.roziqrizal.habitflow.domain.calendar.monthCells
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** Jumlah hari di Agenda: hari ini sampai enam hari lagi. */
const val AGENDA_DAYS = 7

data class AgendaUiState(
    val today: LocalDate,
    /** Tujuh hari ke depan. */
    val week: List<AgendaDay> = emptyList(),
    val month: YearMonth = YearMonth.from(today),
    /** Sel bulan (kosong di awal supaya kolom pertama Senin) dengan isi tiap harinya. */
    val monthCells: List<LocalDate?> = emptyList(),
    val monthDays: Map<LocalDate, AgendaDay> = emptyMap(),
    val selectedDay: AgendaDay? = null,
    /** Acara HabitFlow menurut id, untuk keterangan pengulangan dan editor. */
    val events: Map<Long, CalendarEvent> = emptyMap(),
    val phoneCalendarsEnabled: Boolean = false,
)

/**
 * Data layar Agenda (tahap 22): tujuh hari ke depan dan satu bulan, dari acara HabitFlow, kalender HP, dan libur. Acara Kerja mati
 * di hari libur mengikuti [buildAgenda].
 */
class AgendaViewModel(
    private val events: EventRepository,
    private val schedule: ScheduleRepository,
    private val phone: PhoneCalendarSource,
    private val calendarSettings: CalendarSettings,
    private val clock: DayClock,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) : ViewModel() {

    private val month = MutableStateFlow(YearMonth.from(clock.date.value))
    private val selected = MutableStateFlow<LocalDate?>(null)

    /** Rentang yang perlu data kalender HP: tujuh hari ke depan digabung bulan yang tampil. */
    private val phoneOccurrences = combine(
        calendarSettings.selection, phone.changes(), clock.date, month,
    ) { selection, _, today, shown ->
        val from = minOf(today, shown.atDay(1))
        val to = maxOf(today.plusDays(AGENDA_DAYS - 1L), shown.atEndOfMonth())
        phone.occurrencesBetween(from, to, selection, zone())
    }.flowOn(Dispatchers.IO)

    private val dayOffs = combine(schedule.observeManualDaysOff(), schedule.observeHolidayCancellations()) { manual, cancelled ->
        manual to cancelled
    }

    val state: StateFlow<AgendaUiState> = combine(
        events.observeEvents(),
        events.observeExceptions(),
        phoneOccurrences,
        dayOffs,
        combine(clock.date, month, selected) { today, shown, day -> Triple(today, shown, day) },
    ) { allEvents, exceptions, phoneOcc, (manual, cancelled), (today, shown, selectedDate) ->
        val week = buildAgenda(
            today, today.plusDays(AGENDA_DAYS - 1L), allEvents, exceptions, phoneOcc, schedule.holidays, manual, cancelled,
        )
        val monthDays = buildAgenda(
            shown.atDay(1), shown.atEndOfMonth(), allEvents, exceptions, phoneOcc, schedule.holidays, manual, cancelled,
        ).associateBy { it.date }
        AgendaUiState(
            today = today,
            week = week,
            month = shown,
            monthCells = monthCells(shown.year, shown.monthValue),
            monthDays = monthDays,
            selectedDay = selectedDate?.let { monthDays[it] },
            events = allEvents.associateBy { it.id },
            phoneCalendarsEnabled = calendarSettings.selection.value.isNotEmpty(),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AgendaUiState(today = clock.date.value),
    )

    fun shiftMonth(delta: Int) {
        month.value = month.value.plusMonths(delta.toLong())
        selected.value = null
    }

    fun selectDay(date: LocalDate?) {
        selected.value = date
    }

    fun saveEvent(event: CalendarEvent) {
        if (event.title.isBlank()) return
        viewModelScope.launch { events.save(event) }
    }

    fun deleteEvent(id: Long) {
        viewModelScope.launch { events.delete(id) }
    }

    fun skip(eventId: Long, originalDate: LocalDate) {
        viewModelScope.launch { events.skip(eventId, originalDate) }
    }

    fun changeOccurrence(exception: EventException) {
        viewModelScope.launch { events.change(exception) }
    }

    fun restoreOccurrence(eventId: Long, originalDate: LocalDate) {
        viewModelScope.launch { events.restore(eventId, originalDate) }
    }
}
