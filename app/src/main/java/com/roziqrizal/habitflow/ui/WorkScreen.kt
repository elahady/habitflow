package com.roziqrizal.habitflow.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.unit.dp
import com.roziqrizal.habitflow.domain.work.FollowUp
import com.roziqrizal.habitflow.domain.work.FollowUpStatus
import com.roziqrizal.habitflow.domain.work.WorkSection
import com.roziqrizal.habitflow.domain.work.daysOverdue
import com.roziqrizal.habitflow.domain.work.personSuggestions
import java.time.LocalDate
import java.time.LocalTime

/** Keterangan satu baris di bawah judul: tanggal, jam, orang, lalu penanda (lewat tanggal atau dipilih). */
fun followUpCaption(item: FollowUp, today: LocalDate, includePicked: Boolean = true): String {
    val parts = mutableListOf<String>()
    item.date?.let { parts += if (item.status == FollowUpStatus.WAITING) "cek ${formatShortDate(it)}" else formatShortDate(it) }
    item.time?.let { parts += formatTime(it) }
    item.person?.let { parts += it }
    item.daysOverdue(today)?.let { parts += "Lewat $it hari" }
    if (includePicked && item.pickedDate == today) parts += "Dipilih hari ini"
    return parts.joinToString(" · ")
}

private fun WorkSection.title(): String = when (this) {
    WorkSection.INBOX -> "Inbox"
    WorkSection.TODAY -> "Hari ini"
    WorkSection.OVERDUE -> "Lewat tanggal"
    WorkSection.WAITING -> "Menunggu"
    WorkSection.LATER -> "Nanti"
}

private val SECTION_ORDER = listOf(
    WorkSection.INBOX, WorkSection.TODAY, WorkSection.OVERDUE, WorkSection.WAITING, WorkSection.LATER,
)

/** Kartu follow-up dengan checkbox selesai di kiri. Tap kartu membuka editor. */
@Composable
fun FollowUpCard(item: FollowUp, today: LocalDate, onDone: () -> Unit, onClick: () -> Unit) {
    val caption = followUpCaption(item, today)
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = false, onCheckedChange = { onDone() })
            Column(modifier = Modifier.weight(1f).padding(vertical = 4.dp)) {
                Text(item.title, style = MaterialTheme.typography.bodyLarge)
                if (caption.isNotEmpty()) {
                    Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun WorkScreen(
    state: WorkUiState,
    onOpenScrum: () -> Unit,
    onOpenEod: () -> Unit,
    onSelectPerson: (String?) -> Unit,
    onDone: (FollowUp) -> Unit,
    onSave: (FollowUp) -> Unit,
    onDelete: (Long) -> Unit,
) {
    var editing by remember { mutableStateOf<FollowUp?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("Kerja", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 4.dp)) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                TonalButton(text = "Daily scrum", onClick = onOpenScrum, modifier = Modifier.weight(1f))
                TonalButton(text = "EOD", onClick = onOpenEod, modifier = Modifier.weight(1f))
            }
        }
        if (state.people.isNotEmpty()) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = state.selectedPerson == null,
                            onClick = { onSelectPerson(null) },
                            label = { Text("Semua") },
                        )
                    }
                    items(state.people) { name ->
                        FilterChip(
                            selected = name.equals(state.selectedPerson, ignoreCase = true),
                            onClick = { onSelectPerson(name) },
                            label = { Text(name) },
                        )
                    }
                }
            }
        }

        SECTION_ORDER.forEach { section ->
            val list = state.sections[section].orEmpty()
            if (list.isEmpty() && section != WorkSection.TODAY) return@forEach
            item(key = "title-$section") { SectionTitle("${section.title()} (${list.size})") }
            if (list.isEmpty()) {
                item(key = "empty-$section") {
                    Text(
                        "Belum ada yang dikerjakan hari ini.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
            items(list, key = { "fu-${it.id}" }) { item ->
                FollowUpCard(item = item, today = state.today, onDone = { onDone(item) }, onClick = { editing = item })
            }
        }
    }

    editing?.let { item ->
        FollowUpEditorDialog(
            initial = item,
            all = state.all,
            onDismiss = { editing = null },
            onSave = {
                onSave(it)
                editing = null
            },
            onDelete = {
                onDelete(item.id)
                editing = null
            },
        )
    }
}

private val EDITABLE_STATUSES = listOf(FollowUpStatus.INBOX, FollowUpStatus.ACTIVE, FollowUpStatus.WAITING)

private fun FollowUpStatus.label(): String = when (this) {
    FollowUpStatus.INBOX -> "Inbox"
    FollowUpStatus.ACTIVE -> "Aktif"
    FollowUpStatus.WAITING -> "Menunggu"
    FollowUpStatus.DONE -> "Selesai"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FollowUpEditorDialog(
    initial: FollowUp,
    all: List<FollowUp>,
    onDismiss: () -> Unit,
    onSave: (FollowUp) -> Unit,
    onDelete: () -> Unit,
) {
    var title by remember { mutableStateOf(initial.title) }
    var status by remember { mutableStateOf(initial.status.takeIf { it in EDITABLE_STATUSES } ?: FollowUpStatus.ACTIVE) }
    var date by remember { mutableStateOf(initial.date) }
    var time by remember { mutableStateOf(initial.time) }
    var person by remember { mutableStateOf(initial.person.orEmpty()) }
    var note by remember { mutableStateOf(initial.note.orEmpty()) }
    var pickingDate by remember { mutableStateOf(false) }
    var pickingTime by remember { mutableStateOf(false) }

    val suggestions = remember(person, all) { personSuggestions(all, person) }
    val valid = title.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Follow-up") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title, onValueChange = { title = it }, label = { Text("Judul") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Status", style = MaterialTheme.typography.labelMedium)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    EDITABLE_STATUSES.forEachIndexed { index, s ->
                        SegmentedButton(
                            selected = s == status,
                            onClick = { status = s },
                            shape = SegmentedButtonDefaults.itemShape(index, EDITABLE_STATUSES.size),
                            label = { Text(s.label()) },
                        )
                    }
                }

                Text(
                    if (status == FollowUpStatus.WAITING) "Tanggal cek ulang" else "Tanggal tindak lanjut",
                    style = MaterialTheme.typography.labelMedium,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { pickingDate = true }) { Text(date?.let(::formatShortDate) ?: "Pilih tanggal") }
                    if (date != null) {
                        TextButton(onClick = { date = null; time = null }) { Text("Hapus") }
                    }
                }
                if (date != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { pickingTime = true }) { Text(time?.let(::formatTime) ?: "Pilih jam (opsional)") }
                        if (time != null) TextButton(onClick = { time = null }) { Text("Hapus") }
                    }
                    if (time != null) {
                        Text(
                            "Diingatkan pada jam ini.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                OutlinedTextField(
                    value = person, onValueChange = { person = it }, label = { Text("Orang terkait") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                if (suggestions.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        suggestions.forEach { name ->
                            FilterChip(selected = false, onClick = { person = name }, label = { Text(name) })
                        }
                    }
                }
                OutlinedTextField(
                    value = note, onValueChange = { note = it }, label = { Text("Catatan") },
                    minLines = 2, modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                enabled = valid,
                onClick = {
                    onSave(
                        initial.copy(
                            title = title.trim(),
                            status = status,
                            date = date,
                            time = time.takeIf { date != null },
                            person = person,
                            note = note,
                        ),
                    )
                },
            ) { Text("Simpan") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) { Text("Hapus") }
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = onDismiss) { Text("Batal") }
            }
        },
    )

    if (pickingDate) {
        AppDatePickerDialog(initial = date, onDismiss = { pickingDate = false }, onPick = { date = it; pickingDate = false })
    }
    if (pickingTime) {
        AppTimePickerDialog(initial = time, onDismiss = { pickingTime = false }, onPick = { time = it; pickingTime = false })
    }
}
