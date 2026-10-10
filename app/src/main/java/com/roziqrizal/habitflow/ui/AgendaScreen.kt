package com.roziqrizal.habitflow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.roziqrizal.habitflow.domain.calendar.AgendaDay
import com.roziqrizal.habitflow.domain.calendar.CalendarEvent
import com.roziqrizal.habitflow.domain.calendar.EventException
import com.roziqrizal.habitflow.domain.calendar.EventOccurrence
import com.roziqrizal.habitflow.domain.calendar.RecurrenceType
import com.roziqrizal.habitflow.ui.theme.tokens
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

private enum class AgendaMode(val label: String) { WEEK("7 hari"), MONTH("Bulan") }

private val TIME_COLUMN_WIDTH = 96.dp

/** Tindakan atas satu baris acara HabitFlow, dari dialog menu. */
private enum class OccurrenceAction { MENU, EDIT_OCCURRENCE, CONFIRM_DELETE }

/**
 * Agenda (tahap 22): acara tujuh hari ke depan dan tampilan bulan, dari acara HabitFlow dan kalender HP. Layar penuh di atas
 * tab Kerja. Pola layarnya di docs/design/README.md bagian Kalender.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgendaScreen(
    state: AgendaUiState,
    onShiftMonth: (Int) -> Unit,
    onSelectDay: (LocalDate?) -> Unit,
    onSaveEvent: (CalendarEvent) -> Unit,
    onDeleteEvent: (Long) -> Unit,
    onSkip: (Long, LocalDate) -> Unit,
    onChangeOccurrence: (EventException) -> Unit,
    onRestoreOccurrence: (Long, LocalDate) -> Unit,
    onClose: () -> Unit,
) {
    var modeName by remember { mutableStateOf(AgendaMode.WEEK.name) }
    val mode = AgendaMode.valueOf(modeName)
    var editingEvent by remember { mutableStateOf<CalendarEvent?>(null) }
    var picked by remember { mutableStateOf<EventOccurrence?>(null) }
    var action by remember { mutableStateOf(OccurrenceAction.MENU) }

    fun open(occurrence: EventOccurrence) {
        if (occurrence.fromPhone) return
        picked = occurrence
        action = OccurrenceAction.MENU
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            TextButton(onClick = onClose) { Text("‹ Kembali") }
            Text("Agenda", style = MaterialTheme.typography.headlineMedium)
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
                    AgendaMode.entries.forEachIndexed { index, option ->
                        SegmentedButton(
                            selected = option == mode,
                            onClick = { modeName = option.name },
                            shape = SegmentedButtonDefaults.itemShape(index, AgendaMode.entries.size),
                            colors = appSegmentedColors(),
                            label = { Text(option.label) },
                        )
                    }
                }
                TonalButton(
                    text = "+ Acara",
                    onClick = {
                        val date = if (mode == AgendaMode.MONTH) state.selectedDay?.date ?: state.today else state.today
                        editingEvent = newEvent(date, LocalTime.now().hour + 1)
                    },
                )
            }
        }

        if (mode == AgendaMode.WEEK) {
            state.week.forEach { day ->
                item(key = "day-${day.date}") { DaySection(day, state, ::open) }
            }
        } else {
            item(key = "month-header") {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = { onShiftMonth(-1) }) { Text("‹") }
                    Text(
                        monthTitle(state.month.year, state.month.monthValue),
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { onShiftMonth(1) }) { Text("›") }
                }
            }
            item(key = "month-weekdays") {
                Row(modifier = Modifier.fillMaxWidth()) {
                    DayOfWeek.entries.forEach { day ->
                        Text(
                            dayShortName(day),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            val weeks = state.monthCells.let { cells -> cells.plus(List((7 - cells.size % 7) % 7) { null }) }.chunked(7)
            weeks.forEachIndexed { index, cells ->
                item(key = "month-week-$index") {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        cells.forEach { date ->
                            MonthCell(
                                date = date,
                                day = date?.let { state.monthDays[it] },
                                isToday = date == state.today,
                                isSelected = date != null && date == state.selectedDay?.date,
                                onClick = { if (date != null) onSelectDay(if (state.selectedDay?.date == date) null else date) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
            state.selectedDay?.let { day ->
                item(key = "month-selected") { DaySection(day, state, ::open) }
            }
        }
    }

    editingEvent?.let { event ->
        EventEditorDialog(
            initial = event,
            onSave = {
                onSaveEvent(it)
                editingEvent = null
            },
            onDismiss = { editingEvent = null },
        )
    }

    picked?.let { occurrence ->
        val event = state.events[occurrence.eventId]
        when {
            event == null -> picked = null
            action == OccurrenceAction.EDIT_OCCURRENCE -> OccurrenceEditorDialog(
                occurrence = occurrence,
                onSave = {
                    onChangeOccurrence(it)
                    picked = null
                },
                onDismiss = { picked = null },
            )
            action == OccurrenceAction.CONFIRM_DELETE -> AlertDialog(
                onDismissRequest = { picked = null },
                title = { Text("Hapus acara?") },
                text = {
                    Text(
                        if (event.recurrence.type == RecurrenceType.NONE) {
                            "\"${event.title}\" dihapus."
                        } else {
                            "\"${event.title}\" dan semua kejadiannya dihapus."
                        },
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onDeleteEvent(event.id)
                            picked = null
                        },
                    ) { Text("Hapus") }
                },
                dismissButton = { TextButton(onClick = { picked = null }) { Text("Batal") } },
            )
            else -> AlertDialog(
                onDismissRequest = { picked = null },
                title = { Text(occurrence.title) },
                text = {
                    Column {
                        TextButton(
                            onClick = {
                                editingEvent = event
                                picked = null
                            },
                        ) { Text("Ubah acara") }
                        if (occurrence.recurring) {
                            TextButton(onClick = { action = OccurrenceAction.EDIT_OCCURRENCE }) { Text("Ubah kejadian ini") }
                            if (occurrence.changed) {
                                TextButton(
                                    onClick = {
                                        onRestoreOccurrence(occurrence.eventId, occurrence.originalDate)
                                        picked = null
                                    },
                                ) { Text("Kembalikan kejadian ini") }
                            }
                            TextButton(
                                onClick = {
                                    onSkip(occurrence.eventId, occurrence.originalDate)
                                    picked = null
                                },
                            ) { Text("Lewati kejadian ini") }
                        }
                        TextButton(onClick = { action = OccurrenceAction.CONFIRM_DELETE }) { Text("Hapus acara") }
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = { picked = null }) { Text("Batal") } },
            )
        }
    }
}

/** Satu hari: judul, baris libur, lalu acaranya atau "Tidak ada acara.". */
@Composable
private fun DaySection(day: AgendaDay, state: AgendaUiState, onOpen: (EventOccurrence) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            agendaDayTitle(day.date, state.today),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )
        day.holiday?.let { holiday ->
            Text(
                holidayLine(holiday, day.holidayCancelled),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        if (day.manual && day.holiday == null) {
            Text("Hari libur", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 4.dp))
        }
        if (day.items.isEmpty()) {
            Text(
                "Tidak ada acara.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        day.items.forEach { occurrence ->
            OccurrenceRow(
                occurrence = occurrence,
                recurrence = state.events[occurrence.eventId]?.takeIf { !occurrence.fromPhone }?.let(::describeRecurrence),
                onClick = { onOpen(occurrence) },
            )
        }
    }
}

/** Baris acara: jam selebar 96dp, judul, dan keterangan. Hanya acara HabitFlow yang bisa ditekan. */
@Composable
private fun OccurrenceRow(occurrence: EventOccurrence, recurrence: String?, onClick: () -> Unit) {
    val rowModifier = Modifier.fillMaxWidth().let { if (occurrence.fromPhone) it else it.clickable(onClick = onClick) }
    Row(modifier = rowModifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
        Text(
            occurrenceTime(occurrence),
            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
            modifier = Modifier.width(TIME_COLUMN_WIDTH),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(occurrence.title, style = MaterialTheme.typography.bodyLarge)
            Text(
                occurrenceCaption(occurrence, recurrence),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Sel bulan setinggi 48dp: angka tanggal, titik kalau ada acara, latar hari ini, dan garis bawah untuk hari libur. */
@Composable
private fun MonthCell(
    date: LocalDate?,
    day: AgendaDay?,
    isToday: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (date == null) {
        Box(modifier = modifier.height(48.dp))
        return
    }
    val off = day?.dayOff == true
    val background = when {
        isSelected -> MaterialTheme.colorScheme.surfaceContainerHighest
        isToday -> MaterialTheme.tokens.primaryFixed
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(48.dp)
            .padding(2.dp)
            .background(background, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                color = if (off) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (off) TextDecoration.Underline else null,
            )
            Box(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(6.dp)
                    .background(
                        if (day?.items?.isNotEmpty() == true) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent,
                        CircleShape,
                    ),
            )
        }
    }
}
