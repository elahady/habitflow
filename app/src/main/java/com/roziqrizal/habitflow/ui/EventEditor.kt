package com.roziqrizal.habitflow.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.roziqrizal.habitflow.domain.calendar.CalendarEvent
import com.roziqrizal.habitflow.domain.calendar.DEFAULT_EVENT_DURATION_MINUTES
import com.roziqrizal.habitflow.domain.calendar.EventException
import com.roziqrizal.habitflow.domain.calendar.EventLabel
import com.roziqrizal.habitflow.domain.calendar.EventOccurrence
import com.roziqrizal.habitflow.domain.calendar.MAX_WEEK_INTERVAL
import com.roziqrizal.habitflow.domain.calendar.REMINDER_CHOICES
import com.roziqrizal.habitflow.domain.calendar.Recurrence
import com.roziqrizal.habitflow.domain.calendar.RecurrenceType
import com.roziqrizal.habitflow.domain.calendar.weekOfMonthFor
import com.roziqrizal.habitflow.domain.schedule.Days
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

private enum class Repeat(val label: String) {
    NONE("Sekali"), DAILY("Harian"), WEEKLY("Mingguan"), MONTHLY("Bulanan"), YEARLY("Tahunan")
}

private enum class MonthlyMode { DATE, WEEKDAY }

private const val MAX_DURATION_MINUTES = 24 * 60

private fun CalendarEvent.repeatKind(): Repeat = when (recurrence.type) {
    RecurrenceType.NONE -> Repeat.NONE
    RecurrenceType.DAILY -> Repeat.DAILY
    RecurrenceType.WEEKLY -> Repeat.WEEKLY
    RecurrenceType.MONTHLY_DATE, RecurrenceType.MONTHLY_WEEKDAY -> Repeat.MONTHLY
    RecurrenceType.YEARLY -> Repeat.YEARLY
}

private fun minuteToTime(minute: Int): LocalTime = LocalTime.of(minute / 60 % 24, minute % 60)

private fun LocalTime.toMinute(): Int = hour * 60 + minute

/**
 * Editor acara (tahap 22): judul, label, sepanjang hari, tanggal dan jam, durasi, pengulangan (dengan pilihan sesuai jenisnya),
 * tanggal berakhir, pengingat, dan catatan. [initial] dengan id 0 berarti acara baru. Pola layarnya di docs/design/README.md.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EventEditorDialog(initial: CalendarEvent, onSave: (CalendarEvent) -> Unit, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf(initial.title) }
    var label by remember { mutableStateOf(initial.label) }
    var allDay by remember { mutableStateOf(initial.allDay) }
    var date by remember { mutableStateOf(initial.startDate) }
    var time by remember { mutableStateOf(initial.startMinute?.let(::minuteToTime) ?: LocalTime.of(9, 0)) }
    var durationText by remember { mutableStateOf(initial.durationMinutes.toString()) }
    var repeat by remember { mutableStateOf(initial.repeatKind()) }
    var intervalText by remember { mutableStateOf(initial.recurrence.intervalWeeks.coerceAtLeast(1).toString()) }
    var weekDays by remember {
        mutableStateOf(if (initial.recurrence.weekDays == 0) Days.bit(initial.startDate.dayOfWeek) else initial.recurrence.weekDays)
    }
    var monthlyMode by remember {
        mutableStateOf(if (initial.recurrence.type == RecurrenceType.MONTHLY_WEEKDAY) MonthlyMode.WEEKDAY else MonthlyMode.DATE)
    }
    var until by remember { mutableStateOf(initial.until) }
    var reminder by remember { mutableStateOf(initial.reminderMinutes) }
    var note by remember { mutableStateOf(initial.note.orEmpty()) }

    var pickingDate by remember { mutableStateOf(false) }
    var pickingTime by remember { mutableStateOf(false) }
    var pickingUntil by remember { mutableStateOf(false) }

    val duration = durationText.trim().toIntOrNull()?.takeIf { it in 5..MAX_DURATION_MINUTES }
    val interval = intervalText.trim().toIntOrNull()?.takeIf { it in 1..MAX_WEEK_INTERVAL }
    val valid = title.isNotBlank() &&
        (allDay || duration != null) &&
        (repeat != Repeat.WEEKLY || (interval != null && weekDays != 0)) &&
        (until == null || !until!!.isBefore(date))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "Acara baru" else "Ubah acara") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title, onValueChange = { title = it }, label = { Text("Judul acara") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )

                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    EventLabel.entries.forEachIndexed { index, option ->
                        SegmentedButton(
                            selected = option == label,
                            onClick = { label = option },
                            shape = SegmentedButtonDefaults.itemShape(index, EventLabel.entries.size),
                            colors = appSegmentedColors(),
                            label = { Text(option.title) },
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Sepanjang hari", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Switch(checked = allDay, onCheckedChange = { allDay = it })
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { pickingDate = true }) { Text(formatShortDate(date)) }
                    if (!allDay) TextButton(onClick = { pickingTime = true }) { Text(formatTime(time)) }
                }
                if (!allDay) {
                    OutlinedTextField(
                        value = durationText, onValueChange = { durationText = it },
                        label = { Text("Durasi (menit)") }, singleLine = true, isError = duration == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Text("Pengulangan", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Repeat.entries.forEach { option ->
                        FilterChip(
                            colors = appFilterChipColors(), selected = option == repeat, onClick = { repeat = option },
                            label = { Text(option.label) },
                        )
                    }
                }

                if (repeat == Repeat.WEEKLY) {
                    OutlinedTextField(
                        value = intervalText, onValueChange = { intervalText = it },
                        label = { Text("Setiap berapa minggu (1 sampai $MAX_WEEK_INTERVAL)") }, singleLine = true,
                        isError = interval == null, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        DayOfWeek.entries.forEach { day ->
                            FilterChip(
                                colors = appFilterChipColors(),
                                selected = Days.contains(weekDays, day),
                                onClick = { weekDays = weekDays xor Days.bit(day) },
                                label = { Text(dayShortName(day)) },
                            )
                        }
                    }
                }

                if (repeat == Repeat.MONTHLY) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            colors = appFilterChipColors(), selected = monthlyMode == MonthlyMode.DATE,
                            onClick = { monthlyMode = MonthlyMode.DATE }, label = { Text("Tanggal ${date.dayOfMonth}") },
                        )
                        FilterChip(
                            colors = appFilterChipColors(), selected = monthlyMode == MonthlyMode.WEEKDAY,
                            onClick = { monthlyMode = MonthlyMode.WEEKDAY },
                            label = { Text(monthlyWeekdayLabel(date, weekOfMonthFor(date))) },
                        )
                    }
                }

                if (repeat != Repeat.NONE) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { pickingUntil = true }) {
                            Text(until?.let { "Berakhir: ${formatShortDate(it)}" } ?: "Berakhir: tidak pernah")
                        }
                        if (until != null) TextButton(onClick = { until = null }) { Text("Hapus") }
                    }
                    if (until != null && until!!.isBefore(date)) {
                        Text(
                            "Tanggal berakhir tidak boleh sebelum tanggal mulai.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Text("Pengingat", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        colors = appFilterChipColors(), selected = reminder == null, onClick = { reminder = null },
                        label = { Text("Tanpa") },
                    )
                    REMINDER_CHOICES.forEach { minutes ->
                        FilterChip(
                            colors = appFilterChipColors(), selected = reminder == minutes, onClick = { reminder = minutes },
                            label = { Text("$minutes menit") },
                        )
                    }
                }

                OutlinedTextField(
                    value = note, onValueChange = { note = it }, label = { Text("Catatan (opsional)") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                enabled = valid,
                onClick = {
                    val recurrence = when (repeat) {
                        Repeat.NONE -> Recurrence(RecurrenceType.NONE)
                        Repeat.DAILY -> Recurrence(RecurrenceType.DAILY)
                        Repeat.WEEKLY -> Recurrence(RecurrenceType.WEEKLY, interval!!, weekDays)
                        Repeat.MONTHLY -> if (monthlyMode == MonthlyMode.WEEKDAY) {
                            Recurrence(RecurrenceType.MONTHLY_WEEKDAY, weekOfMonth = weekOfMonthFor(date))
                        } else {
                            Recurrence(RecurrenceType.MONTHLY_DATE)
                        }
                        Repeat.YEARLY -> Recurrence(RecurrenceType.YEARLY)
                    }
                    onSave(
                        initial.copy(
                            title = title.trim(),
                            label = label,
                            startDate = date,
                            startMinute = if (allDay) null else time.toMinute(),
                            durationMinutes = if (allDay) DEFAULT_EVENT_DURATION_MINUTES else duration!!,
                            recurrence = recurrence,
                            until = if (repeat == Repeat.NONE) null else until,
                            reminderMinutes = if (allDay) null else reminder,
                            note = note.trim().ifEmpty { null },
                        ),
                    )
                },
            ) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )

    if (pickingDate) AppDatePickerDialog(initial = date, onDismiss = { pickingDate = false }, onPick = { date = it; pickingDate = false })
    if (pickingTime) AppTimePickerDialog(initial = time, onDismiss = { pickingTime = false }, onPick = { time = it; pickingTime = false })
    if (pickingUntil) {
        AppDatePickerDialog(initial = until ?: date, onDismiss = { pickingUntil = false }, onPick = { until = it; pickingUntil = false })
    }
}

/**
 * Editor satu kejadian (tahap 22): judul, tanggal, jam, dan durasi untuk kejadian itu saja. Hasilnya pengecualian berisi
 * semua nilai itu, jadi perubahan seri sesudahnya tidak menggesernya.
 */
@Composable
fun OccurrenceEditorDialog(
    occurrence: EventOccurrence,
    onSave: (EventException) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember { mutableStateOf(occurrence.title) }
    var date by remember { mutableStateOf(occurrence.date) }
    var time by remember { mutableStateOf(occurrence.startMinute?.let(::minuteToTime) ?: LocalTime.of(9, 0)) }
    var durationText by remember { mutableStateOf(occurrence.durationMinutes.toString()) }
    var pickingDate by remember { mutableStateOf(false) }
    var pickingTime by remember { mutableStateOf(false) }

    val duration = durationText.trim().toIntOrNull()?.takeIf { it in 5..MAX_DURATION_MINUTES }
    val valid = title.isNotBlank() && (occurrence.allDay || duration != null)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ubah kejadian ini") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title, onValueChange = { title = it }, label = { Text("Judul") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { pickingDate = true }) { Text(formatShortDate(date)) }
                    if (!occurrence.allDay) TextButton(onClick = { pickingTime = true }) { Text(formatTime(time)) }
                }
                if (!occurrence.allDay) {
                    OutlinedTextField(
                        value = durationText, onValueChange = { durationText = it },
                        label = { Text("Durasi (menit)") }, singleLine = true, isError = duration == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = valid,
                onClick = {
                    onSave(
                        EventException(
                            eventId = occurrence.eventId,
                            originalDate = occurrence.originalDate,
                            skipped = false,
                            newDate = date,
                            newStartMinute = if (occurrence.allDay) null else time.toMinute(),
                            newDurationMinutes = if (occurrence.allDay) null else duration!!,
                            newTitle = title.trim(),
                        ),
                    )
                },
            ) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )

    if (pickingDate) AppDatePickerDialog(initial = date, onDismiss = { pickingDate = false }, onPick = { date = it; pickingDate = false })
    if (pickingTime) AppTimePickerDialog(initial = time, onDismiss = { pickingTime = false }, onPick = { time = it; pickingTime = false })
}

/** Acara kosong untuk editor "Acara baru" pada [date]. */
fun newEvent(date: LocalDate, nextHour: Int): CalendarEvent = CalendarEvent(
    id = 0,
    title = "",
    label = EventLabel.WORK,
    startDate = date,
    startMinute = (nextHour.coerceIn(0, 23)) * 60,
)
