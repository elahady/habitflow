package com.roziqrizal.habitflow.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.roziqrizal.habitflow.data.sync.RestorePreview
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Aksi bagian Sinkron ke server di Tentang. */
data class SyncActions(
    val onSetEnabled: (Boolean) -> Unit,
    val onTest: () -> Unit,
    val onSync: () -> Unit,
    val onPrepareRestore: () -> Unit,
    val onDismissRestore: () -> Unit,
    val onConfirmRestore: () -> Unit,
)

private val STAMP = DateTimeFormatter.ofPattern("EEE d MMM HH.mm", Locale("id", "ID"))

private fun formatStamp(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(STAMP)

/** Pola tampilannya ada di docs/design/README.md bagian Tentang. */
@Composable
fun SyncSection(state: SyncUiState, actions: SyncActions) {
    val enabledActions = !state.busy && state.signedIn

    Text(
        if (state.status.lastSuccessAt > 0) "Terakhir berhasil: ${formatStamp(state.status.lastSuccessAt)}" else "Belum pernah berhasil",
        style = MaterialTheme.typography.bodyLarge,
    )
    Text(
        text = when {
            state.busy -> "Memproses..."
            state.message != null -> state.message
            !state.signedIn -> "Masuk ke akun dulu."
            else -> state.status.lastMessage.orEmpty()
        }.orEmpty(),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text("Sinkron otomatis", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(
            checked = state.enabled,
            enabled = enabledActions,
            onCheckedChange = actions.onSetEnabled,
        )
    }

    TonalButton(
        text = "Sinkron sekarang",
        enabled = enabledActions,
        onClick = actions.onSync,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton(onClick = actions.onTest, enabled = enabledActions) { Text("Uji koneksi") }
        TextButton(onClick = actions.onPrepareRestore, enabled = enabledActions) { Text("Pulihkan dari server") }
    }

    state.restore?.let { RestoreDialog(it, onConfirm = actions.onConfirmRestore, onDismiss = actions.onDismissRestore) }
}

@Composable
private fun RestoreDialog(preview: RestorePreview.Ready, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val snapshot = preview.snapshot
    val summary = "${snapshot.habits.size} habit, ${snapshot.followUps.size} follow-up, ${snapshot.todos.size} to-do"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pulihkan dari server?") },
        text = {
            Text(
                "Snapshot server dari ${formatStamp(if (preview.receivedAt > 0) preview.receivedAt else snapshot.createdAt)}, " +
                    "berisi $summary.\n\nSemua data di HP ini akan diganti. Ini tidak bisa dibatalkan.",
            )
        },
        confirmButton = { Button(onClick = onConfirm) { Text("Ganti semua data") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}
