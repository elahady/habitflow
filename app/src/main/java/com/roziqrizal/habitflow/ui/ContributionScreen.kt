package com.roziqrizal.habitflow.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.roziqrizal.habitflow.domain.CELL_NOT_DRAWN
import com.roziqrizal.habitflow.domain.isActiveOn
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContributionScreen(state: ContributionUiState) {
    // Tanggal yang dipilih disimpan sebagai teks ISO karena LocalDate tidak otomatis bisa disimpan.
    var selectedIso by rememberSaveable { mutableStateOf<String?>(null) }
    val onDayClick: (LocalDate) -> Unit = { selectedIso = it.toString() }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = "Kontribusi",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
        item { StreakCard(state.currentStreak, state.longestStreak) }

        item { SectionTitle("Gabungan") }
        item {
            HeatmapGrid(
                today = state.today,
                levelFor = { state.combined[it] ?: 0 },
                onDayClick = onDayClick,
                description = "Heatmap gabungan 26 minggu terakhir",
            )
        }

        item { SectionTitle("Per habit") }
        items(state.perHabit, key = { it.habit.id }) { heat ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = heat.habit.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                HeatmapGrid(
                    today = state.today,
                    levelFor = { heat.levels[it] ?: CELL_NOT_DRAWN },
                    onDayClick = onDayClick,
                    description = "Heatmap ${heat.habit.name} 26 minggu terakhir",
                    showLegend = false,
                )
            }
        }
    }

    selectedIso?.let { iso ->
        ModalBottomSheet(onDismissRequest = { selectedIso = null }) {
            DayDetail(state, LocalDate.parse(iso))
        }
    }
}

@Composable
private fun StreakCard(current: Int, longest: Int) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Streak sekarang: $current hari",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Streak terpanjang: $longest hari",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, start = 4.dp),
    )
}

@Composable
private fun DayDetail(state: ContributionUiState, date: LocalDate) {
    val dateText = date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale("id", "ID")))
    val doneIds = state.doneIdsByDate[date].orEmpty()
    val activeHabits = state.habits.filter { isActiveOn(state.createdOn.getValue(it.id), date) }
    val todos = state.todosByDate[date].orEmpty()
    val doneHabits = activeHabits.count { it.id in doneIds }

    Column(modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 32.dp)) {
        Text(
            text = dateText,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "Habit $doneHabits/${activeHabits.size} selesai",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SectionTitle("Habit")
        if (activeHabits.isEmpty()) {
            Text(
                text = "Belum ada habit pada hari ini.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        activeHabits.forEach { habit ->
            StatusRow(name = habit.name, done = habit.id in doneIds)
        }

        SectionTitle("To-do")
        if (todos.isEmpty()) {
            Text(
                text = "Tidak ada to-do di hari ini.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        todos.forEach { todo ->
            StatusRow(name = todo.title, done = todo.done)
        }
    }
}

@Composable
private fun StatusRow(name: String, done: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = done, onCheckedChange = null)
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            textDecoration = if (done) TextDecoration.LineThrough else null,
            color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
    }
}
