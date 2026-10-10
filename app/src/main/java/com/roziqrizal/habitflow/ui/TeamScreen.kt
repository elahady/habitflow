package com.roziqrizal.habitflow.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.roziqrizal.habitflow.data.account.Account
import com.roziqrizal.habitflow.data.team.RemoteTeamTodo
import com.roziqrizal.habitflow.data.team.TeamMember

/** Tab "Tugas Rumah" (tahap 26): to-do dibagi dengan pasangan, terpisah dari habit/to-do pribadi. */
@Composable
fun TeamScreen(
    state: TeamUiState,
    onCreateTeam: (String) -> Unit,
    onCreateInvite: () -> Unit,
    onInviteShared: () -> Unit,
    onJoinWithToken: (String) -> Unit,
    onAddTodo: (String) -> Unit,
    onSetDone: (RemoteTeamTodo, Boolean) -> Unit,
    onAssign: (RemoteTeamTodo, Long?) -> Unit,
    onDelete: (RemoteTeamTodo) -> Unit,
    onSignIn: (Context) -> Unit,
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text("Tugas Rumah", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        }

        when {
            state.account == null -> item { SignInPrompt(busy = state.busy, onSignIn = { onSignIn(context) }) }
            state.loading && state.team == null -> item {
                Row(modifier = Modifier.fillMaxWidth().padding(top = 24.dp), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator()
                }
            }
            state.team == null -> item {
                NoTeamState(busy = state.busy, onCreateTeam = onCreateTeam, onJoinWithToken = onJoinWithToken)
            }
            else -> {
                item {
                    TeamHeader(
                        teamName = state.team.name,
                        members = state.team.members,
                        busy = state.busy,
                        onCreateInvite = onCreateInvite,
                    )
                }
                item {
                    AddTodoField(busy = state.busy, onAdd = onAddTodo)
                }
                items(state.todos, key = { it.id }) { todo ->
                    TeamTodoRow(
                        todo = todo,
                        members = state.team.members,
                        myId = state.account.id,
                        onSetDone = { onSetDone(todo, it) },
                        onAssign = { onAssign(todo, it) },
                        onDelete = { onDelete(todo) },
                    )
                }
                if (state.todos.isEmpty()) {
                    item {
                        Text(
                            "Belum ada tugas. Tambah dari kolom di atas.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }

        state.error?.let { error ->
            item {
                Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }

    state.inviteUrl?.let { url ->
        val send = Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, "Ayo gabung Tugas Rumah di Habitflow: $url")
        context.startActivity(Intent.createChooser(send, "Bagikan undangan"))
        onInviteShared()
    }
}

@Composable
private fun SignInPrompt(busy: Boolean, onSignIn: () -> Unit) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            "Masuk dengan Google untuk membuat atau bergabung ke tim tugas rumah.",
            style = MaterialTheme.typography.bodyMedium,
        )
        TonalButton(
            text = "Masuk dengan Google",
            enabled = !busy,
            onClick = onSignIn,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )
    }
}

@Composable
private fun NoTeamState(busy: Boolean, onCreateTeam: (String) -> Unit, onJoinWithToken: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var pasted by rememberSaveable { mutableStateOf("") }

    AppCard(modifier = Modifier.fillMaxWidth()) {
        Text("Belum gabung tim", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        Text(
            "Buat tim baru, atau tempel link undangan yang dikirim pasangan Anda.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Nama tim (misal: Keluarga)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        TonalButton(
            text = "Buat tim",
            enabled = !busy && name.isNotBlank(),
            onClick = { onCreateTeam(name) },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = pasted,
            onValueChange = { pasted = it },
            label = { Text("Link undangan") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(
            enabled = !busy && pasted.isNotBlank(),
            onClick = { onJoinWithToken(extractInviteToken(pasted)) },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        ) { Text("Gabung") }
    }
}

@Composable
private fun TeamHeader(teamName: String, members: List<TeamMember>, busy: Boolean, onCreateInvite: () -> Unit) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(teamName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(
                    members.joinToString(", ") { it.name },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onCreateInvite, enabled = !busy) { Text("Undang") }
        }
    }
}

@Composable
private fun AddTodoField(busy: Boolean, onAdd: (String) -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Tugas baru") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Button(
            enabled = !busy && title.isNotBlank(),
            onClick = {
                onAdd(title)
                title = ""
            },
        ) { Text("Tambah") }
    }
}

@Composable
private fun TeamTodoRow(
    todo: RemoteTeamTodo,
    members: List<TeamMember>,
    myId: Long,
    onSetDone: (Boolean) -> Unit,
    onAssign: (Long?) -> Unit,
    onDelete: () -> Unit,
) {
    var showAssignMenu by remember { mutableStateOf(false) }
    val assigneeName = members.find { it.id == todo.assignedTo }?.name

    AppCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 4.dp, end = 8.dp, top = 4.dp, bottom = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Checkbox(checked = todo.done, onCheckedChange = onSetDone)
            Column(modifier = Modifier.weight(1f)) {
                Text(todo.title, style = MaterialTheme.typography.bodyLarge)
                Box {
                    TextButton(onClick = { showAssignMenu = true }) {
                        Text(
                            assigneeName?.let { if (todo.assignedTo == myId) "Anda" else it } ?: "Belum ditugaskan",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    DropdownMenu(expanded = showAssignMenu, onDismissRequest = { showAssignMenu = false }) {
                        DropdownMenuItem(text = { Text("Belum ditugaskan") }, onClick = { onAssign(null); showAssignMenu = false })
                        members.forEach { member ->
                            DropdownMenuItem(
                                text = { Text(member.name) },
                                onClick = { onAssign(member.id); showAssignMenu = false },
                            )
                        }
                    }
                }
            }
            TextButton(onClick = onDelete) { Text("Hapus") }
        }
    }
}

/** Dari `https://habitflow.roziqrizal.com/invite/{token}` atau token mentah yang ditempel langsung. */
internal fun extractInviteToken(pasted: String): String {
    val trimmed = pasted.trim()
    val marker = "/invite/"
    val index = trimmed.lastIndexOf(marker)
    return if (index >= 0) trimmed.substring(index + marker.length).trim('/', ' ') else trimmed
}

/** Dialog join tim dari deep link (tahap 26 langkah 2). */
@Composable
fun JoinTeamDialog(
    joinState: JoinInviteState?,
    account: Account?,
    onSignIn: (Context) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Undangan tim") },
        text = {
            when (joinState) {
                null, JoinInviteState.InProgress -> {
                    if (account == null) {
                        Text("Masuk dengan Google dulu untuk bisa bergabung ke tim.")
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.padding(end = 12.dp))
                            Text("Memproses undangan...")
                        }
                    }
                }
                is JoinInviteState.Success -> Text("Berhasil gabung ke tim \"${joinState.teamName}\".")
                is JoinInviteState.Failed -> Text(joinState.message)
            }
        },
        confirmButton = {
            if (account == null && (joinState == null || joinState is JoinInviteState.Failed)) {
                TextButton(onClick = { onSignIn(context) }) { Text("Masuk dengan Google") }
            } else {
                TextButton(onClick = onDismiss) { Text("Tutup") }
            }
        },
        dismissButton = if (account != null) {
            { TextButton(onClick = onDismiss) { Text("Nanti") } }
        } else {
            null
        },
    )
}
