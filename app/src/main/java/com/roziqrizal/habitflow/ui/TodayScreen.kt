package com.roziqrizal.habitflow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.roziqrizal.habitflow.data.Todo
import com.roziqrizal.habitflow.domain.MAX_TODOS_PER_DAY
import com.roziqrizal.habitflow.domain.TODOS_FOR_LEVEL_4
import com.roziqrizal.habitflow.domain.schedule.GLASSES_TARGET
import com.roziqrizal.habitflow.ui.theme.LocalHeatColors
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun TodayScreen(
    state: TodayUiState,
    schedule: ScheduleUiState,
    work: WorkUiState,
    onOpenWork: () -> Unit,
    onSetDayOff: (Boolean) -> Unit,
    onSetHolidayCancelled: (Boolean) -> Unit,
    onOpenSchedule: () -> Unit,
    onToggleHabit: (Long) -> Unit,
    onAddGlass: () -> Unit,
    onRemoveGlass: () -> Unit,
    health: HealthUiState,
    onRequestStepsAccess: () -> Unit,
    onOpenHealthConnectStore: () -> Unit,
    onRecordBp: () -> Unit,
    onRecordWeight: () -> Unit,
    onAddTodo: (String) -> Unit,
    onToggleTodo: (Todo) -> Unit,
    onDeleteTodo: (Todo) -> Unit,
) {
    var showAddDialog by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScheduleCard(
                state = schedule,
                onSetDayOff = onSetDayOff,
                onSetHolidayCancelled = onSetHolidayCancelled,
                onOpenEditor = onOpenSchedule,
            ) }

        if (work.inboxCount > 0 || work.overdueCount > 0) {
            item { WorkSummaryCard(work, onOpenWork) }
        }

        item { DayHeader(state) }

        item { StepsCard(health.steps, onRequestAccess = onRequestStepsAccess, onOpenHealthConnectStore = onOpenHealthConnectStore) }

        item { WaterCard(glasses = state.glasses, onAdd = onAddGlass, onRemove = onRemoveGlass) }

        item { HealthSummaryCards(health, onRecordBp = onRecordBp, onRecordWeight = onRecordWeight) }

        item { SectionTitle("Habit") }
        items(state.habits, key = { "habit-${it.habit.id}" }) { item ->
            HabitRow(
                name = item.habit.name,
                mandatory = item.habit.isMandatory,
                checked = item.doneToday,
                onToggle = { onToggleHabit(item.habit.id) },
            )
        }

        item { SectionTitle("To-do hari ini (${state.todos.size}/$MAX_TODOS_PER_DAY)") }
        if (state.todos.isEmpty()) {
            item {
                Text(
                    text = "Belum ada to-do. Habit dulu, to-do menyusul.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
        items(state.todos, key = { "todo-${it.id}" }) { todo ->
            TodoRow(
                todo = todo,
                onToggle = { onToggleTodo(todo) },
                onDelete = { onDeleteTodo(todo) },
            )
        }
        item {
            TonalButton(
                text = "+ To-do",
                enabled = state.canAddTodo,
                onClick = { showAddDialog = true },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (!state.canAddTodo) {
            item {
                Text(
                    text = "To-do hari ini sudah penuh ($MAX_TODOS_PER_DAY/$MAX_TODOS_PER_DAY).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
    }

    if (showAddDialog) {
        AddTodoDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { title ->
                onAddTodo(title)
                showAddDialog = false
            },
        )
    }
}

@Composable
private fun DayHeader(state: TodayUiState) {
    val heat = LocalHeatColors.current
    val dateText = state.date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale("id", "ID")))

    AppCard(hero = true, contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 16.dp)) {
        Text(dateText, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 16.dp)) {
            StatBlock(value = "${state.doneHabits}/${state.totalHabits}", label = "Habit")
            Spacer(Modifier.width(24.dp))
            StatBlock(value = "${state.doneTodos}/$MAX_TODOS_PER_DAY", label = "To-do")
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .background(heat.forLevel(state.level), RoundedCornerShape(4.dp))
                    .semantics { contentDescription = "Level hari ini ${state.level}" },
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Level ${state.level}  ·  Streak ${state.streak} hari",
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Level 4 butuh semua habit dan $TODOS_FOR_LEVEL_4 to-do selesai.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Penghitung gelas air hari ini (tahap 20). Gelas ke-8 mencentang habit "Air putih 2 liter". */
@Composable
private fun WaterCard(glasses: Int, onAdd: () -> Unit, onRemove: () -> Unit) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Text("Air putih", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "$glasses / $GLASSES_TARGET",
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = if (glasses >= GLASSES_TARGET) "gelas hari ini · Target tercapai" else "gelas hari ini",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(
                onClick = onRemove,
                enabled = glasses > 0,
                modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp),
            ) { Text("−") }
            TonalButton(text = "+ Segelas", onClick = onAdd)
        }
        LinearProgressIndicator(
            progress = { (glasses.toFloat() / GLASSES_TARGET).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(8.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
    }
}

@Composable
private fun HabitRow(
    name: String,
    mandatory: Boolean,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
        onClick = onToggle,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = checked, onCheckedChange = null)
            Spacer(Modifier.width(4.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                if (mandatory) {
                    Text(
                        text = "Wajib",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun TodoRow(
    todo: Todo,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        onClick = onToggle,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = todo.done, onCheckedChange = null)
            Text(
                text = todo.title,
                style = MaterialTheme.typography.bodyMedium,
                textDecoration = if (todo.done) TextDecoration.LineThrough else null,
                color = if (todo.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onDelete) { Text("Hapus") }
        }
    }
}

@Composable
private fun AddTodoDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var title by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("To-do baru") },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Apa yang mau dikerjakan?") },
                singleLine = true,
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(title) },
                enabled = title.isNotBlank(),
            ) { Text("Simpan") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        },
    )
}

/** Ringkasan follow-up kerja di dashboard. Hanya tampil kalau ada Inbox atau yang lewat tanggal. */
@Composable
private fun WorkSummaryCard(work: WorkUiState, onOpen: () -> Unit) {
    AppCard(modifier = Modifier.fillMaxWidth(), onClick = onOpen) {
        Text(
            "Kerja · Inbox ${work.inboxCount} · Lewat tanggal ${work.overdueCount} · Hari ini ${work.todayCount}",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
