package com.roziqrizal.habitflow.ui

import android.content.Context
import android.content.Intent
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.roziqrizal.habitflow.domain.work.EodAction
import com.roziqrizal.habitflow.domain.work.FollowUp
import com.roziqrizal.habitflow.domain.work.InboxChoice
import com.roziqrizal.habitflow.domain.work.buildEodSummary
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val FULL_DATE = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale("id", "ID"))

/**
 * Daily scrum: kandidat hari ini dengan kotak "Kerjakan hari ini" yang langsung tersimpan, lalu
 * Nanti dan Menunggu yang bisa diambil. "Selesai daily scrum" menandai selesai dan menutup layar.
 */
@Composable
fun ScrumScreen(
    state: WorkUiState,
    onPick: (FollowUp, Boolean) -> Unit,
    onFinish: () -> Unit,
    onClose: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            TextButton(onClick = onClose) { Text("‹ Kembali") }
            Text("Daily scrum", style = MaterialTheme.typography.headlineMedium)
            Text(
                state.today.format(FULL_DATE),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (state.scrumCandidates.isEmpty()) {
            item {
                Text(
                    "Tidak ada follow-up yang jatuh tempo atau lewat tanggal.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
        items(state.scrumCandidates, key = { "scrum-${it.id}" }) { item -> PickCard(item, state.today, onPick) }

        if (state.scrumLater.isNotEmpty()) {
            item { SectionTitle("Ambil dari Nanti (${state.scrumLater.size})") }
            items(state.scrumLater, key = { "later-${it.id}" }) { item -> PickCard(item, state.today, onPick) }
        }
        if (state.inbox.isNotEmpty()) {
            item {
                Text(
                    "Inbox (${state.inbox.size}) dirapikan saat EOD.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
        item { TonalButton(text = "Selesai daily scrum", onClick = onFinish, modifier = Modifier.fillMaxWidth()) }
    }
}

@Composable
private fun PickCard(item: FollowUp, today: LocalDate, onPick: (FollowUp, Boolean) -> Unit) {
    val picked = item.pickedDate == today
    val caption = followUpCaption(item, today, includePicked = false)
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        onClick = { onPick(item, !picked) },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = picked, onCheckedChange = null)
            Column(modifier = Modifier.weight(1f).padding(vertical = 4.dp, horizontal = 4.dp)) {
                Text(item.title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    listOf(caption, "Kerjakan hari ini").filter { it.isNotEmpty() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private enum class EodKind { DONE, TOMORROW, RESCHEDULE, WAIT }
private enum class InboxKind { KEEP, LATER, DATE, WAIT, DELETE }

/**
 * EOD: status setiap follow-up hari ini (awalnya Lanjut besok), merapikan Inbox (awalnya Biarkan),
 * catatan EOD, lalu simpan atau bagikan ringkasan.
 */
@Composable
fun EodScreen(
    state: WorkUiState,
    onSave: (today: List<FollowUp>, actions: Map<Long, EodAction>, inbox: List<FollowUp>, choices: Map<Long, InboxChoice>, note: String) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val todayItems = state.eodToday
    val inboxItems = state.inbox

    val eodKinds = remember { mutableStateMapOf<Long, EodKind>() }
    val eodDates = remember { mutableStateMapOf<Long, LocalDate>() }
    val inboxKinds = remember { mutableStateMapOf<Long, InboxKind>() }
    val inboxDates = remember { mutableStateMapOf<Long, LocalDate>() }
    var note by remember { mutableStateOf(state.workDay?.eodNote.orEmpty()) }
    // Pemilih tanggal terbuka untuk salah satu: (id item, apakah dari Inbox, jenis yang menunggu tanggal).
    var pickingFor by remember { mutableStateOf<Triple<Long, Boolean, String>?>(null) }

    fun actionFor(item: FollowUp): EodAction = when (eodKinds[item.id] ?: EodKind.TOMORROW) {
        EodKind.DONE -> EodAction.Done
        EodKind.TOMORROW -> EodAction.Tomorrow
        EodKind.RESCHEDULE -> EodAction.Reschedule(eodDates[item.id] ?: state.today.plusDays(1))
        EodKind.WAIT -> EodAction.Wait(eodDates[item.id] ?: state.today.plusDays(1))
    }

    fun inboxChoiceFor(item: FollowUp): InboxChoice = when (inboxKinds[item.id] ?: InboxKind.KEEP) {
        InboxKind.KEEP -> InboxChoice.Keep
        InboxKind.LATER -> InboxChoice.Later
        InboxKind.DATE -> InboxChoice.OnDate(inboxDates[item.id] ?: state.today.plusDays(1))
        InboxKind.WAIT -> InboxChoice.Wait(inboxDates[item.id] ?: state.today.plusDays(1))
        InboxKind.DELETE -> InboxChoice.Delete
    }

    val actions = todayItems.associate { it.id to actionFor(it) }

    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = onClose) { Text("‹ Kembali") }
        Text("EOD", style = MaterialTheme.typography.headlineMedium)
        Text(
            state.today.format(FULL_DATE),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SectionTitle("Hari ini (${todayItems.size})")
        if (todayItems.isEmpty()) {
            Text(
                "Tidak ada follow-up yang dikerjakan hari ini.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        todayItems.forEach { item ->
            val kind = eodKinds[item.id] ?: EodKind.TOMORROW
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Text(item.title, style = MaterialTheme.typography.bodyLarge)
                val caption = followUpCaption(item, state.today)
                if (caption.isNotEmpty()) {
                    Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    FilterChip(colors = appFilterChipColors(), selected = kind == EodKind.DONE, onClick = { eodKinds[item.id] = EodKind.DONE }, label = { Text("Selesai") })
                    FilterChip(colors = appFilterChipColors(), selected = kind == EodKind.TOMORROW, onClick = { eodKinds[item.id] = EodKind.TOMORROW }, label = { Text("Lanjut besok") })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                    FilterChip(
                        colors = appFilterChipColors(),
                        selected = kind == EodKind.RESCHEDULE,
                        onClick = { pickingFor = Triple(item.id, false, "RESCHEDULE") },
                        label = { Text(if (kind == EodKind.RESCHEDULE) "Pindah ${eodDates[item.id]?.let(::formatShortDate)}" else "Pindah tanggal") },
                    )
                    FilterChip(
                        colors = appFilterChipColors(),
                        selected = kind == EodKind.WAIT,
                        onClick = { pickingFor = Triple(item.id, false, "WAIT") },
                        label = { Text(if (kind == EodKind.WAIT) "Menunggu ${eodDates[item.id]?.let(::formatShortDate)}" else "Menunggu") },
                    )
                }
            }
        }

        if (inboxItems.isNotEmpty()) {
            SectionTitle("Inbox (${inboxItems.size})")
            inboxItems.forEach { item ->
                val kind = inboxKinds[item.id] ?: InboxKind.KEEP
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Text(item.title, style = MaterialTheme.typography.bodyLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                        FilterChip(colors = appFilterChipColors(), selected = kind == InboxKind.KEEP, onClick = { inboxKinds[item.id] = InboxKind.KEEP }, label = { Text("Biarkan") })
                        FilterChip(colors = appFilterChipColors(), selected = kind == InboxKind.LATER, onClick = { inboxKinds[item.id] = InboxKind.LATER }, label = { Text("Nanti") })
                        FilterChip(colors = appFilterChipColors(), selected = kind == InboxKind.DELETE, onClick = { inboxKinds[item.id] = InboxKind.DELETE }, label = { Text("Hapus") })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                        FilterChip(
                            colors = appFilterChipColors(),
                            selected = kind == InboxKind.DATE,
                            onClick = { pickingFor = Triple(item.id, true, "DATE") },
                            label = { Text(if (kind == InboxKind.DATE) formatShortDate(inboxDates[item.id] ?: state.today) else "Pilih tanggal") },
                        )
                        FilterChip(
                            colors = appFilterChipColors(),
                            selected = kind == InboxKind.WAIT,
                            onClick = { pickingFor = Triple(item.id, true, "WAIT") },
                            label = { Text(if (kind == InboxKind.WAIT) "Menunggu ${inboxDates[item.id]?.let(::formatShortDate)}" else "Menunggu") },
                        )
                    }
                }
            }
        }

        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text("Catatan EOD") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        TonalButton(
            text = "Simpan EOD",
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                onSave(todayItems, actions, inboxItems, inboxItems.associate { it.id to inboxChoiceFor(it) }, note)
                onClose()
            },
        )
        TextButton(
            onClick = {
                val summary = buildEodSummary(state.today, todayItems.map { it to actionFor(it) }, note)
                shareText(context, summary)
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Bagikan") }
    }

    pickingFor?.let { (id, fromInbox, kind) ->
        AppDatePickerDialog(
            initial = if (fromInbox) inboxDates[id] else eodDates[id],
            onDismiss = { pickingFor = null },
            onPick = { date ->
                if (fromInbox) {
                    inboxDates[id] = date
                    inboxKinds[id] = if (kind == "WAIT") InboxKind.WAIT else InboxKind.DATE
                } else {
                    eodDates[id] = date
                    eodKinds[id] = if (kind == "WAIT") EodKind.WAIT else EodKind.RESCHEDULE
                }
                pickingFor = null
            },
        )
    }
}

private fun shareText(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, "Bagikan EOD"))
}
