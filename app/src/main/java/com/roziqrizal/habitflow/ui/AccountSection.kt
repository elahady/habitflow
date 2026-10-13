package com.roziqrizal.habitflow.ui

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.roziqrizal.habitflow.data.sync.HabitSyncStatus
import com.roziqrizal.habitflow.session.BackupState
import com.roziqrizal.habitflow.session.SignOutPlan
import com.roziqrizal.habitflow.session.SyncOutcome

/** Aksi bagian Akun di Tentang. [Context] dibutuhkan Credential Manager untuk menampilkan pemilih akun. */
data class AccountActions(
    val onSignIn: (Context) -> Unit,
    val onRequestSignOut: () -> Unit,
    val onConfirmSignOut: () -> Unit,
    val onDismissSignOut: () -> Unit,
    val onRequestDelete: () -> Unit,
    val onDeleteWithPassword: (String) -> Unit,
    val onDeleteWithGoogle: (Context) -> Unit,
    val onDismissDelete: () -> Unit,
    val onSyncHabitsNow: () -> Unit,
)

/** Teks ringkas status sinkron habit/to-do (tahap 28 langkah 5), pola sama dengan `SyncResult.message()`. */
private fun HabitSyncStatus.describe(): String? = when (this) {
    HabitSyncStatus.Idle -> null
    HabitSyncStatus.Syncing -> "Habit & to-do: menyinkron..."
    is HabitSyncStatus.Success ->
        if (hadConflict) "Habit & to-do: tersinkron, satu konflik diselesaikan otomatis." else "Habit & to-do: tersinkron."
    is HabitSyncStatus.Failed -> "Habit & to-do: gagal sinkron ($message), dicoba lagi nanti."
}

/**
 * Akun di Tentang: nama, Keluar, dan Hapus akun (tahap 31H). Layar Profil di 31I menggantikan tata letaknya; dialognya
 * ([SignOutDialog], [DeleteAccountDialog]) dipakai ulang di sana.
 */
@Composable
fun AccountSection(state: AccountUiState, actions: AccountActions) {
    val context = LocalContext.current
    val account = state.account

    if (account == null) {
        Text(
            "Masuk dengan Google untuk pakai To-Do Tim dan sinkron habit ke web.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TonalButton(
            text = "Masuk dengan Google",
            enabled = !state.busy,
            onClick = { actions.onSignIn(context) },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
    } else {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(account.name, style = MaterialTheme.typography.bodyLarge)
                Text(account.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = actions.onRequestSignOut, enabled = state.signOut == SignOutState.Hidden) { Text("Keluar") }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            state.habitSync.describe()?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            }
            TextButton(onClick = actions.onSyncHabitsNow, enabled = state.habitSync != HabitSyncStatus.Syncing) { Text("Sinkron sekarang") }
        }
        TextButton(
            onClick = actions.onRequestDelete,
            enabled = state.delete == DeleteState.Hidden,
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) { Text("Hapus akun") }
    }

    state.message?.let {
        Text(
            it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }

    SignOutDialog(state.signOut, onConfirm = actions.onConfirmSignOut, onDismiss = actions.onDismissSignOut)
    DeleteAccountDialog(state.delete, actions)
}

/** Teks inti dialog Keluar: apa yang hilang, dan peringatan keras kalau masih ada perubahan yang belum terkirim. */
internal fun signOutBody(plan: SignOutPlan): String = buildString {
    append("Semua data di HP ini akan dihapus (habit, to-do, jadwal, kesehatan, pengaturan).")
    val pending = plan.pending
    when {
        plan.sync is SyncOutcome.Offline -> {
            append("\n\nKamu sedang tidak tersambung ke internet, jadi perubahan terbaru belum bisa dikirim ke server.")
        }
        plan.sync is SyncOutcome.Failed -> append("\n\nPerubahan terbaru belum bisa dikirim: ${plan.sync.reason}")
    }
    if (pending.items > 0) {
        append("\n\n${pending.items} perubahan belum terkirim ke server dan akan hilang kalau kamu keluar sekarang.")
    }
    when (pending.backup) {
        BackupState.Off ->
            append("\n\nSinkron otomatis mati, jadi jadwal, data kesehatan, dan pengaturan hanya ada di HP ini dan ikut terhapus.")
        BackupState.Stale ->
            append("\n\nAda perubahan jadwal, kesehatan, atau pengaturan yang belum masuk cadangan terakhir dan akan hilang.")
        BackupState.Saved -> Unit
    }
    if (!pending.any && plan.sync == SyncOutcome.Done) append("\n\nSemua perubahan sudah tersinkron.")
}

@Composable
fun SignOutDialog(state: SignOutState, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    when (state) {
        SignOutState.Hidden -> Unit
        SignOutState.Preparing, SignOutState.SigningOut -> AlertDialog(
            onDismissRequest = {},
            title = { Text(if (state == SignOutState.Preparing) "Memeriksa perubahan..." else "Keluar...") },
            text = {
                Text(
                    if (state == SignOutState.Preparing) "Mengirim perubahan terakhir ke server kalau ada internet." else "Menghapus data di HP ini.",
                )
            },
            confirmButton = {},
        )
        is SignOutState.Ready -> {
            val warn = state.plan.pending.any
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Keluar dari akun?") },
                text = { Text(signOutBody(state.plan)) },
                confirmButton = {
                    Button(
                        onClick = onConfirm,
                        colors = if (warn) {
                            ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        } else {
                            ButtonDefaults.buttonColors()
                        },
                    ) { Text(if (warn) "Keluar dan hapus" else "Keluar") }
                },
                dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
            )
        }
    }
}

@Composable
fun DeleteAccountDialog(state: DeleteState, actions: AccountActions) {
    val context = LocalContext.current
    var password by remember { mutableStateOf("") }
    when (state) {
        DeleteState.Hidden, DeleteState.Checking -> Unit
        DeleteState.Deleting -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Menghapus akun...") },
            text = { Text("Menghapus semua data di server dan di HP ini.") },
            confirmButton = {},
        )
        is DeleteState.NeedsPassword -> AlertDialog(
            onDismissRequest = actions.onDismissDelete,
            title = { Text("Hapus akun?") },
            text = {
                Column {
                    Text(
                        "Akun dan semua datanya (habit, to-do, kesehatan, follow-up, keanggotaan tim) dihapus permanen di server dan " +
                            "di HP ini. Ini tidak bisa dibatalkan. Masukkan kata sandi untuk mengonfirmasi.",
                    )
                    OutlinedTextField(
                        value = password, onValueChange = { password = it },
                        label = { Text("Kata sandi") }, singleLine = true, isError = state.error != null,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    )
                    state.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                Button(
                    onClick = { actions.onDeleteWithPassword(password) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("Hapus akun permanen") }
            },
            dismissButton = { TextButton(onClick = actions.onDismissDelete) { Text("Batal") } },
        )
        is DeleteState.NeedsGoogle -> AlertDialog(
            onDismissRequest = actions.onDismissDelete,
            title = { Text("Hapus akun?") },
            text = {
                Column {
                    Text(
                        "Akun dan semua datanya dihapus permanen di server dan di HP ini. Ini tidak bisa dibatalkan. Akunmu tidak " +
                            "punya kata sandi, jadi pilih akun Google yang sama untuk mengonfirmasi.",
                    )
                    state.error?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { actions.onDeleteWithGoogle(context) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("Pilih akun Google dan hapus") }
            },
            dismissButton = { TextButton(onClick = actions.onDismissDelete) { Text("Batal") } },
        )
    }
}
