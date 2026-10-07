package com.roziqrizal.habitflow.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.roziqrizal.habitflow.data.Habit
import com.roziqrizal.habitflow.domain.prayer.PrayerName
import com.roziqrizal.habitflow.domain.schedule.BlockStart
import com.roziqrizal.habitflow.domain.schedule.Days
import com.roziqrizal.habitflow.domain.schedule.NotificationLevel
import com.roziqrizal.habitflow.domain.schedule.ScheduleBlock
import java.time.DayOfWeek
import java.time.LocalTime

private val PrayerName.label: String
    get() = when (this) {
        PrayerName.SUBUH -> "Subuh"
        PrayerName.DZUHUR -> "Dzuhur"
        PrayerName.ASHAR -> "Ashar"
        PrayerName.MAGHRIB -> "Maghrib"
        PrayerName.ISYA -> "Isya"
    }

private val NotificationLevel.label: String
    get() = when (this) {
        NotificationLevel.INFO -> "Info"
        NotificationLevel.REMINDER -> "Pengingat"
    }

private val DAY_LABELS = listOf("Sen", "Sel", "Rab", "Kam", "Jum", "Sab", "Min")

private fun describeStart(start: BlockStart): String = when (start) {
    is BlockStart.Fixed -> formatMinute(start.time.hour * 60 + start.time.minute)
    is BlockStart.Prayer -> {
        val offset = when {
            start.offsetMinutes == 0 -> ""
            start.offsetMinutes > 0 -> " + ${start.offsetMinutes} mnt"
            else -> " − ${-start.offsetMinutes} mnt"
        }
        start.name.label + offset
    }
}

private fun describeBlock(block: ScheduleBlock): String {
    val end = block.endMinuteOfDay
    val length = when {
        end != null -> "sampai ${formatMinute(end)}"
        block.durationMinutes == 0 -> "titik waktu"
        else -> "${block.durationMinutes} mnt"
    }
    return "${describeStart(block.start)} · $length"
}

@Composable
fun ScheduleScreen(
    state: ScheduleEditorState,
    onSave: (ScheduleBlock) -> Unit,
    onDelete: (Long) -> Unit,
    onClose: () -> Unit,
) {
    // null = dialog tertutup, id 0 = blok baru.
    var editing by remember { mutableStateOf<ScheduleBlock?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            TextButton(onClick = onClose) { Text("‹ Kembali") }
            Text("Atur jadwal", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Waktu sholat dihitung untuk ${state.location.name}.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        item {
            TonalButton(
                text = "+ Blok",
                onClick = {
                    editing = ScheduleBlock(
                        id = 0,
                        name = "",
                        start = BlockStart.Fixed(LocalTime.of(8, 0)),
                        durationMinutes = 60,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        items(state.blocks, key = { it.id }) { block ->
            AppCard(modifier = Modifier.fillMaxWidth(), onClick = { editing = block }) {
                Text(block.name, style = MaterialTheme.typography.bodyLarge)
                Text(describeBlock(block), style = MaterialTheme.typography.bodyMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DayLetters(block.activeDays)
                    Text(
                        " · ${block.level.label}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    editing?.let { block ->
        BlockEditorDialog(
            initial = block,
            habits = state.habits,
            onDismiss = { editing = null },
            onSave = {
                onSave(it)
                editing = null
            },
            onDelete = if (block.id != 0L) {
                {
                    onDelete(block.id)
                    editing = null
                }
            } else {
                null
            },
        )
    }
}

private val TIME_PATTERN = Regex("""^(\d{1,2})[.:](\d{2})$""")

/** "6.35" atau "06:35" menjadi menit sejak 00.00, atau null kalau bukan jam yang sah. */
private fun parseMinute(text: String): Int? {
    val match = TIME_PATTERN.matchEntire(text.trim()) ?: return null
    val hour = match.groupValues[1].toInt()
    val minute = match.groupValues[2].toInt()
    return if (hour in 0..23 && minute in 0..59) hour * 60 + minute else null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BlockEditorDialog(
    initial: ScheduleBlock,
    habits: List<Habit>,
    onDismiss: () -> Unit,
    onSave: (ScheduleBlock) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val initialStart = initial.start
    var name by remember { mutableStateOf(initial.name) }
    var byPrayer by remember { mutableStateOf(initialStart is BlockStart.Prayer) }
    var timeText by remember {
        mutableStateOf(
            (initialStart as? BlockStart.Fixed)?.let { formatMinute(it.time.hour * 60 + it.time.minute) } ?: "08.00",
        )
    }
    var prayer by remember { mutableStateOf((initialStart as? BlockStart.Prayer)?.name ?: PrayerName.SUBUH) }
    var offsetText by remember { mutableStateOf(((initialStart as? BlockStart.Prayer)?.offsetMinutes ?: 0).toString()) }
    var durationText by remember { mutableStateOf(initial.durationMinutes.toString()) }
    var endText by remember { mutableStateOf(initial.endMinuteOfDay?.let(::formatMinute).orEmpty()) }
    var days by remember { mutableStateOf(initial.activeDays) }
    var level by remember { mutableStateOf(initial.level) }
    var habitIds by remember { mutableStateOf(initial.habitIds) }

    val start: BlockStart? = if (byPrayer) {
        offsetText.trim().toIntOrNull()?.takeIf { it in -720..720 }?.let { BlockStart.Prayer(prayer, it) }
    } else {
        parseMinute(timeText)?.let { BlockStart.Fixed(LocalTime.of(it / 60, it % 60)) }
    }
    val duration = durationText.trim().toIntOrNull()?.takeIf { it in 0..1440 }
    val endMinute = if (endText.isBlank()) null else parseMinute(endText)
    val endValid = endText.isBlank() || endMinute != null
    val valid = name.isNotBlank() && start != null && duration != null && endValid && days != 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "Blok baru" else "Ubah blok") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, label = { Text("Nama blok") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )

                Text("Mulai", style = MaterialTheme.typography.labelMedium)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    listOf("Jam tetap", "Waktu sholat").forEachIndexed { index, label ->
                        SegmentedButton(
                            selected = (index == 1) == byPrayer,
                            onClick = { byPrayer = index == 1 },
                            shape = SegmentedButtonDefaults.itemShape(index, 2),
                            label = { Text(label) },
                        )
                    }
                }
                if (byPrayer) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PrayerName.entries.forEach { p ->
                            FilterChip(selected = p == prayer, onClick = { prayer = p }, label = { Text(p.label) })
                        }
                    }
                    OutlinedTextField(
                        value = offsetText, onValueChange = { offsetText = it },
                        label = { Text("Selisih menit (negatif = sebelum)") },
                        singleLine = true, isError = start == null, modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    OutlinedTextField(
                        value = timeText, onValueChange = { timeText = it },
                        label = { Text("Jam (jj.mm)") },
                        singleLine = true, isError = start == null, modifier = Modifier.fillMaxWidth(),
                    )
                }

                OutlinedTextField(
                    value = durationText, onValueChange = { durationText = it },
                    label = { Text("Durasi (menit, 0 = titik waktu)") },
                    singleLine = true, isError = duration == null, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = endText, onValueChange = { endText = it },
                    label = { Text("Selesai tepat jam (opsional, jj.mm)") },
                    singleLine = true, isError = !endValid, modifier = Modifier.fillMaxWidth(),
                )

                Text("Hari aktif", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    DayOfWeek.entries.forEach { day ->
                        FilterChip(
                            selected = Days.contains(days, day),
                            onClick = { days = days xor Days.bit(day) },
                            label = { Text(DAY_LABELS[day.value - 1]) },
                        )
                    }
                }

                Text("Notifikasi", style = MaterialTheme.typography.labelMedium)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    NotificationLevel.entries.forEachIndexed { index, l ->
                        SegmentedButton(
                            selected = l == level,
                            onClick = { level = l },
                            shape = SegmentedButtonDefaults.itemShape(index, NotificationLevel.entries.size),
                            label = { Text(l.label) },
                        )
                    }
                }

                Text("Habit yang ditautkan", style = MaterialTheme.typography.labelMedium)
                habits.forEach { habit ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = habit.id in habitIds,
                            onCheckedChange = { checked -> habitIds = if (checked) habitIds + habit.id else habitIds - habit.id },
                        )
                        Text(habit.name, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = valid,
                onClick = {
                    onSave(
                        initial.copy(
                            name = name.trim(),
                            start = start!!,
                            durationMinutes = duration!!,
                            endMinuteOfDay = endMinute,
                            activeDays = days,
                            level = level,
                            habitIds = habitIds,
                        ),
                    )
                },
            ) { Text("Simpan") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Hapus") }
                TextButton(onClick = onDismiss) { Text("Batal") }
            }
        },
    )
}

private val DAY_LETTERS = listOf("S", "S", "R", "K", "J", "S", "M")

/** Tujuh huruf hari (Senin sampai Minggu); hari aktif tebal, hari tidak aktif redup. */
@Composable
private fun DayLetters(mask: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        DayOfWeek.entries.forEach { day ->
            val active = Days.contains(mask, day)
            Text(
                DAY_LETTERS[day.value - 1],
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
            )
        }
    }
}
