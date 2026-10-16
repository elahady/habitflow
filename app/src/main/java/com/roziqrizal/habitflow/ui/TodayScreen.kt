package com.roziqrizal.habitflow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roziqrizal.habitflow.data.Todo
import com.roziqrizal.habitflow.data.sync.HealthFocusPayload
import com.roziqrizal.habitflow.domain.MAX_TODOS_PER_DAY
import com.roziqrizal.habitflow.domain.TODOS_FOR_LEVEL_4
import com.roziqrizal.habitflow.domain.meals.COFFEE_LIMIT
import com.roziqrizal.habitflow.domain.meals.MealKind
import com.roziqrizal.habitflow.domain.meals.coffeeOverLimit
import com.roziqrizal.habitflow.domain.schedule.GLASSES_TARGET
import com.roziqrizal.habitflow.ui.theme.LocalHeatColors
import com.roziqrizal.habitflow.domain.health.computeFocus
import com.roziqrizal.habitflow.ui.theme.tokens
import java.time.LocalDate
import java.time.LocalTime
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
    onAddCoffee: () -> Unit,
    onRemoveCoffee: () -> Unit,
    onAddSweetDrink: () -> Unit,
    onRemoveSweetDrink: () -> Unit,
    onOpenMeal: (MealKind) -> Unit,
    health: HealthUiState,
    onRequestStepsAccess: () -> Unit,
    onOpenHealthConnectStore: () -> Unit,
    onRecordBp: () -> Unit,
    onRecordWeight: () -> Unit,
    onAddTodo: (String) -> Unit,
    onToggleTodo: (Todo) -> Unit,
    onDeleteTodo: (Todo) -> Unit,
    accountName: String? = null,
    onOpenProfile: () -> Unit = {},
    alarmPrompt: ExactAlarmPrompt? = null,
    /** Dipanggil kalau fokus (atau teksnya) berubah, supaya sinkron bisa mengirimnya ke server (tahap 30 langkah 4). */
    onFocusComputed: (HealthFocusPayload) -> Unit = {},
) {
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    val now = LocalTime.now()
    val minuteOfDay = now.hour * 60 + now.minute
    val focus = computeFocus(
        focusInputOf(
            health = health,
            coffee = state.coffee,
            sweet = state.sweet,
            mealLoggedToday = state.meals.isNotEmpty(),
            minuteOfDay = minuteOfDay,
        ),
    )
    // Langkah masih "Loading" berarti data kesehatan belum selesai dimuat: fokus yang dihitung saat itu belum layak dikirim
    // (misalnya "Catat berat pertama" padahal berat sudah ada). Yang terbit hanya fokus yang isinya berubah.
    val focusText = focusContent(focus, health, state.coffee, state.sweet)
    val focusReady = health.steps !is StepsUiState.Loading
    LaunchedEffect(focus, focusText, focusReady) {
        if (focusReady) {
            onFocusComputed(
                HealthFocusPayload(
                    rule = focus.rule.name,
                    walkMinutes = focus.walkMinutes,
                    title = focusText.title,
                    sentence = focusText.sentence,
                    note = focusText.note,
                    reasons = focusText.reasons,
                    computedAtMillis = System.currentTimeMillis(),
                ),
            )
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { DayGreeting(date = state.date, hour = now.hour, accountName = accountName, onOpenProfile = onOpenProfile) }

        if (alarmPrompt != null) item { ExactAlarmPromptCard(alarmPrompt) }

        item { FocusCard(focus = focus, health = health, coffee = state.coffee, sweet = state.sweet) }

        if (work.inboxCount > 0 || work.overdueCount > 0) {
            item { WorkSummaryCard(work, onOpenWork) }
        }

        item { ScheduleCard(
                state = schedule,
                onSetDayOff = onSetDayOff,
                onSetHolidayCancelled = onSetHolidayCancelled,
                onOpenEditor = onOpenSchedule,
            ) }

        item { HealthSummaryCards(health, onRecordBp = onRecordBp, onRecordWeight = onRecordWeight) }

        item { StepsCard(health.steps, onRequestAccess = onRequestStepsAccess, onOpenHealthConnectStore = onOpenHealthConnectStore) }

        item { DayProgress(state) }

        item {
            WaterCard(
                glasses = state.glasses,
                coffee = state.coffee,
                sweet = state.sweet,
                onAdd = onAddGlass,
                onRemove = onRemoveGlass,
                onAddCoffee = onAddCoffee,
                onRemoveCoffee = onRemoveCoffee,
                onAddSweet = onAddSweetDrink,
                onRemoveSweet = onRemoveSweetDrink,
            )
        }

        item { MealCard(meals = state.meals, onOpen = onOpenMeal) }

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

/** "Selamat pagi" sampai "Selamat malam" menurut jam. */
private fun greetingFor(hour: Int): String = when (hour) {
    in 4..10 -> "Selamat pagi"
    in 11..14 -> "Selamat siang"
    in 15..17 -> "Selamat sore"
    else -> "Selamat malam"
}

/** Kepala layar: tanggal, sapaan serif dengan nama depan akun, dan lingkaran inisial di kanan. */
@Composable
private fun DayGreeting(date: LocalDate, hour: Int, accountName: String?, onOpenProfile: () -> Unit) {
    val dateText = date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale("id", "ID")))
    val firstName = accountName?.trim()?.substringBefore(' ')?.takeIf { it.isNotEmpty() }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            Text(dateText, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = if (firstName != null) "${greetingFor(hour)}, $firstName" else greetingFor(hour),
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp, lineHeight = 29.sp),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (firstName != null) {
            // Lingkaran inisial membuka layar Profil (31I). Area sentuh 48dp lewat padding di luar lingkaran 36dp.
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClickLabel = "Buka profil", onClick = onOpenProfile)
                    .padding(6.dp)
                    .background(MaterialTheme.tokens.primaryFixed, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    firstName.first().uppercase(),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

/** Kemajuan habit dan to-do hari ini berdampingan, lalu baris level dan streak. */
@Composable
private fun DayProgress(state: TodayUiState) {
    val heat = LocalHeatColors.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ProgressCard("Habit hari ini", state.doneHabits, state.totalHabits, Modifier.weight(1f).fillMaxHeight())
            ProgressCard("To-do hari ini", state.doneTodos, MAX_TODOS_PER_DAY, Modifier.weight(1f).fillMaxHeight())
        }
        Row(modifier = Modifier.padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
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
        Text(
            text = "Level 4 butuh semua habit dan $TODOS_FOR_LEVEL_4 to-do selesai.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@Composable
private fun ProgressCard(label: String, done: Int, total: Int, modifier: Modifier = Modifier) {
    AppCard(modifier = modifier, contentPadding = PaddingValues(14.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        SerifValue("$done / $total", modifier = Modifier.padding(top = 8.dp))
        ThinProgress(if (total > 0) done.toFloat() / total else 0f, modifier = Modifier.padding(top = 8.dp))
    }
}

/**
 * Penghitung gelas air hari ini (tahap 20). Gelas ke-8 mencentang habit "Air putih 2 liter". Di bawahnya penghitung kopi dan
 * minuman manis (tahap 23); kopi di atas batas memunculkan teks tenang, tanpa warna status.
 */
@Composable
private fun WaterCard(
    glasses: Int,
    coffee: Int,
    sweet: Int,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
    onAddCoffee: () -> Unit,
    onRemoveCoffee: () -> Unit,
    onAddSweet: () -> Unit,
    onRemoveSweet: () -> Unit,
) {
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
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            DrinkCounter("Kopi", coffee, onAddCoffee, onRemoveCoffee, Modifier.weight(1f))
            DrinkCounter("Manis", sweet, onAddSweet, onRemoveSweet, Modifier.weight(1f))
        }
        if (coffeeOverLimit(coffee)) {
            Text(
                text = "Kopi hari ini sudah $coffee gelas, di atas batas $COFFEE_LIMIT.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

/** "−" dan "+ Kopi (1)": hitungan satu jenis minuman dengan area sentuh minimal 48dp. */
@Composable
private fun DrinkCounter(label: String, count: Int, onAdd: () -> Unit, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        TextButton(
            onClick = onRemove,
            enabled = count > 0,
            modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp),
        ) { Text("−") }
        TextButton(onClick = onAdd, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) { Text("+ $label ($count)") }
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
