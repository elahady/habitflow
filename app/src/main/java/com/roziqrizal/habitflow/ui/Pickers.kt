package com.roziqrizal.habitflow.ui

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val SHORT = DateTimeFormatter.ofPattern("EEE d MMM", Locale("id", "ID"))

/** Tanggal singkat untuk keterangan kartu, misalnya "Rab 7 Okt". */
fun formatShortDate(date: LocalDate): String = date.format(SHORT)

/** Jam ke teks "09.30", memakai pemisah titik seperti jam lain di app. */
fun formatTime(time: LocalTime): String = "%02d.%02d".format(time.hour, time.minute)

/** Dialog pilih tanggal Material 3. [onPick] dipanggil dengan tanggal terpilih. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDatePickerDialog(initial: LocalDate?, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = (initial ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedDateMillis != null,
                onClick = {
                    state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                },
            ) { Text("Pilih") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    ) { DatePicker(state = state) }
}

/** Dialog pilih jam 24 jam. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTimePickerDialog(initial: LocalTime?, onDismiss: () -> Unit, onPick: (LocalTime) -> Unit) {
    val start = initial ?: LocalTime.of(9, 0)
    val state = rememberTimePickerState(initialHour = start.hour, initialMinute = start.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onPick(LocalTime.of(state.hour, state.minute)) }) { Text("Pilih") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
        text = { TimePicker(state = state) },
    )
}
