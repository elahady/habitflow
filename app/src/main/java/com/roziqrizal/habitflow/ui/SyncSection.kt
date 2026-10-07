package com.roziqrizal.habitflow.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.roziqrizal.habitflow.data.sync.RestorePreview
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Aksi bagian Sinkron ke server di Tentang. */
data class SyncActions(
    /** Mengembalikan teks galat, atau null kalau tersimpan. */
    val onSaveServer: (url: String, token: String) -> String?,
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
    var showServerDialog by remember { mutableStateOf(false) }
    val config = state.config
    val enabledActions = !state.busy

    Text(
        if (state.status.lastSuccessAt > 0) "Terakhir berhasil: ${formatStamp(state.status.lastSuccessAt)}" else "Belum pernah berhasil",
        style = MaterialTheme.typography.bodyLarge,
    )
    Text(
        text = when {
            state.busy -> "Memproses..."
            state.message != null -> state.message
            !config.isConfigured -> "Isi alamat server dan token dulu."
            else -> state.status.lastMessage.orEmpty()
        }.orEmpty(),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text("Sinkron otomatis", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(
            checked = config.enabled,
            enabled = config.isConfigured && enabledActions,
            onCheckedChange = actions.onSetEnabled,
        )
    }

    TonalButton(
        text = "Sinkron sekarang",
        enabled = config.isConfigured && enabledActions,
        onClick = actions.onSync,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton(onClick = { showServerDialog = true }, enabled = enabledActions) { Text("Atur server") }
        TextButton(onClick = actions.onTest, enabled = config.isConfigured && enabledActions) { Text("Uji koneksi") }
    }
    TextButton(onClick = actions.onPrepareRestore, enabled = config.isConfigured && enabledActions) {
        Text("Pulihkan dari server")
    }

    if (showServerDialog) {
        ServerDialog(
            initialUrl = config.url,
            initialToken = config.token,
            onDismiss = { showServerDialog = false },
            onSave = { url, token ->
                val error = actions.onSaveServer(url, token)
                if (error == null) showServerDialog = false
                error
            },
        )
    }

    state.restore?.let { RestoreDialog(it, onConfirm = actions.onConfirmRestore, onDismiss = actions.onDismissRestore) }
}

@Composable
private fun ServerDialog(
    initialUrl: String,
    initialToken: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> String?,
) {
    var url by remember { mutableStateOf(initialUrl) }
    var token by remember { mutableStateOf(initialToken) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Atur server") },
        text = {
            androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = url, onValueChange = { url = it; error = null },
                    label = { Text("Alamat server") }, placeholder = { Text("https://...") },
                    singleLine = true, isError = error != null, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = token, onValueChange = { token = it; error = null },
                    label = { Text("Token") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
                )
                error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Text(
                    "Token dibuat di server dengan php artisan habitflow:token.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { Button(onClick = { error = onSave(url, token) }) { Text("Simpan") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
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
