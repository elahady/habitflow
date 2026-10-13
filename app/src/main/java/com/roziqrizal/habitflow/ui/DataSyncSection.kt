package com.roziqrizal.habitflow.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.roziqrizal.habitflow.data.sync.RestorePreview
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Aksi bagian "Data dan sinkron" di Pengaturan. Semuanya memakai sesi akun; tidak ada lagi alamat server atau token. */
data class SyncActions(
    val onSetEnabled: (Boolean) -> Unit,
    val onSync: () -> Unit,
    val onPrepareRestore: () -> Unit,
    val onDismissRestore: () -> Unit,
    val onConfirmRestore: () -> Unit,
)

private val STAMP = DateTimeFormatter.ofPattern("EEE d MMM HH.mm", Locale("id", "ID"))

internal fun formatStamp(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(STAMP)

/** Keterangan baris "Sinkron otomatis": apa yang dicadangkan dan kapan terakhir berhasil. */
internal fun backupDescription(lastSuccessAt: Long): String =
    "Cadangan jadwal, kesehatan, dan pengaturan ke akunmu. " +
        if (lastSuccessAt > 0) "Terakhir berhasil: ${formatStamp(lastSuccessAt)}." else "Belum pernah berhasil."

/**
 * Bagian "Data dan sinkron" di Pengaturan (31I): sakelar sinkron otomatis, tombol "Sinkron sekarang", dan "Cadangan dan
 * pulihkan". Pola tampilannya ada di docs/design/README.md bagian Pengaturan.
 */
@Composable
fun DataSyncSection(state: SyncUiState, actions: SyncActions) {
    SettingsCard {
        SwitchRow(
            title = "Sinkron otomatis",
            description = backupDescription(state.status.lastSuccessAt),
            checked = state.enabled,
            enabled = !state.busy,
            onCheckedChange = actions.onSetEnabled,
        )
        SettingsDivider()
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            TonalButton(
                text = "Sinkron sekarang",
                enabled = !state.busy,
                onClick = actions.onSync,
                modifier = Modifier.fillMaxWidth(),
            )
            val status = when {
                state.busy -> "Memproses..."
                state.message != null -> state.message
                else -> state.status.lastMessage
            }
            if (!status.isNullOrBlank()) {
                Text(
                    status,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        SettingsDivider()
        ChevronRow(
            title = "Cadangan dan pulihkan",
            description = "Pulihkan dari cadangan terakhir di akunmu",
            onClick = if (state.busy) null else actions.onPrepareRestore,
        )
    }

    state.restore?.let { RestoreDialog(it, onConfirm = actions.onConfirmRestore, onDismiss = actions.onDismissRestore) }
}

@Composable
private fun RestoreDialog(preview: RestorePreview.Ready, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val snapshot = preview.snapshot
    val summary = "${snapshot.habits.size} habit dan ${snapshot.todos.size} to-do"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pulihkan dari cadangan akun?") },
        text = {
            Text(
                "Cadangan akunmu dari ${formatStamp(if (preview.receivedAt > 0) preview.receivedAt else snapshot.createdAt)}, " +
                    "berisi $summary, beserta jadwal, data kesehatan, dan pengaturan.\n\n" +
                    "Semua data di HP ini diganti dengan cadangan itu. Ini tidak bisa dibatalkan.",
            )
        },
        confirmButton = { Button(onClick = onConfirm) { Text("Ganti semua data") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}
