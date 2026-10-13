package com.roziqrizal.habitflow.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roziqrizal.habitflow.data.HealthSettings
import com.roziqrizal.habitflow.data.account.MeInfo
import com.roziqrizal.habitflow.data.sync.HabitSyncStatus
import com.roziqrizal.habitflow.session.BackupState
import com.roziqrizal.habitflow.session.SignOutPlan
import com.roziqrizal.habitflow.session.SyncOutcome
import com.roziqrizal.habitflow.ui.auth.AuthTextField
import com.roziqrizal.habitflow.ui.theme.tokens

/** Aksi layar Profil. [Context] dibutuhkan Credential Manager untuk menampilkan pemilih akun Google. */
data class AccountActions(
    val onLoadMe: () -> Unit,
    val onRequestSignOut: () -> Unit,
    val onRequestSignOutAllDevices: () -> Unit,
    val onConfirmSignOut: () -> Unit,
    val onDismissSignOut: () -> Unit,
    val onRequestDelete: () -> Unit,
    val onDeleteWithPassword: (String) -> Unit,
    val onDeleteWithGoogle: (Context) -> Unit,
    val onDismissDelete: () -> Unit,
    val onOpenChangePassword: () -> Unit,
    val onChangePassword: (current: String, password: String, confirmation: String) -> Unit,
    val onDismissChangePassword: () -> Unit,
    val onSyncHabitsNow: () -> Unit,
)

/** Teks ringkas status sinkron habit dan to-do, pola sama dengan `SyncResult.message()`. */
internal fun HabitSyncStatus.describe(): String = when (this) {
    HabitSyncStatus.Idle -> "Habit dan to-do tersinkron otomatis saat app dibuka dan berkala."
    HabitSyncStatus.Syncing -> "Habit dan to-do: menyinkron..."
    is HabitSyncStatus.Success ->
        if (hadConflict) "Habit dan to-do: tersinkron, satu konflik diselesaikan otomatis." else "Habit dan to-do: tersinkron."
    is HabitSyncStatus.Failed -> "Habit dan to-do: gagal sinkron ($message), dicoba lagi nanti."
}

/** Keterangan baris "Cara masuk", mis. "Email dan kata sandi, Google terhubung". */
internal fun signInMethodText(me: MeInfo): String = when {
    me.hasPassword && me.googleLinked -> "Email dan kata sandi, Google terhubung"
    me.hasPassword -> "Email dan kata sandi"
    me.googleLinked -> "Google"
    else -> "Email"
}

/**
 * Layar penuh di atas tab, dibuka dari lingkaran inisial di Hari ini (31I). Memuat semua tindakan akun: ubah kata sandi,
 * sinkron, Keluar, Keluar dari semua perangkat, dan Hapus akun. Nilai tampilannya di docs/design/README.md bagian Profil.
 */
@Composable
fun ProfileScreen(
    state: AccountUiState,
    actions: AccountActions,
    healthSettings: HealthSettings,
    onClose: () -> Unit,
) {
    val account = state.account ?: return
    LaunchedEffect(Unit) { actions.onLoadMe() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 32.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose, modifier = Modifier.size(44.dp)) {
                Icon(AppIcons.ArrowLeft, contentDescription = "Kembali")
            }
            Text("Profil", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 4.dp))
        }

        Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier.size(72.dp).background(MaterialTheme.tokens.primaryFixed, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    initialOf(account.name),
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 30.sp, lineHeight = 36.sp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Text(
                account.name,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, lineHeight = 28.sp),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp),
            )
            Text(
                account.email,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }

        SectionLabel("Data tubuh")
        BodyDataCard(healthSettings)

        SectionLabel("Akun")
        SettingsCard {
            ChevronRow(
                title = "Cara masuk",
                description = when {
                    state.me != null -> signInMethodText(state.me)
                    state.meLoading -> "Memuat..."
                    else -> "Belum bisa dimuat. Ketuk untuk coba lagi."
                },
                onClick = if (state.me == null && !state.meLoading) actions.onLoadMe else null,
            )
            if (state.me != null) {
                SettingsDivider()
                ChevronRow(
                    title = if (state.me.hasPassword) "Ubah kata sandi" else "Buat kata sandi",
                    description = if (state.me.hasPassword) null else "Supaya bisa masuk dengan email juga",
                    onClick = actions.onOpenChangePassword,
                )
            }
        }

        SectionLabel("Sinkron")
        SettingsCard {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    state.habitSync.describe(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TonalButton(
                    text = "Sinkron sekarang",
                    enabled = state.habitSync != HabitSyncStatus.Syncing,
                    onClick = actions.onSyncHabitsNow,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
            }
        }

        state.message?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp, start = 4.dp),
            )
        }

        val signOutBusy = state.signOut != SignOutState.Hidden
        OutlinedButton(
            onClick = actions.onRequestSignOut,
            enabled = !signOutBusy,
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
        ) {
            Icon(AppIcons.LogOut, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text("Keluar")
        }
        TextButton(
            onClick = actions.onRequestSignOutAllDevices,
            enabled = !signOutBusy,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp),
        ) { Text("Keluar dari semua perangkat") }
        TextButton(
            onClick = actions.onRequestDelete,
            enabled = state.delete == DeleteState.Hidden,
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) { Text("Hapus akun") }
    }

    SignOutDialog(
        state = state.signOut,
        allDevices = state.signOutAllDevices,
        onConfirm = actions.onConfirmSignOut,
        onDismiss = actions.onDismissSignOut,
    )
    DeleteAccountDialog(state.delete, actions)
    ChangePasswordDialog(state.password, onSubmit = actions.onChangePassword, onDismiss = actions.onDismissChangePassword)
}

/** Huruf pertama nama untuk lingkaran avatar; "?" kalau namanya kosong. */
internal fun initialOf(name: String): String = name.trim().firstOrNull()?.uppercase() ?: "?"

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

/** Kalimat tambahan di depan [signOutBody] untuk "Keluar dari semua perangkat". */
internal const val SIGN_OUT_ALL_INTRO = "Semua perangkat yang masuk dengan akun ini akan keluar, termasuk HP ini."

@Composable
fun SignOutDialog(state: SignOutState, allDevices: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
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
                title = { Text(if (allDevices) "Keluar dari semua perangkat?" else "Keluar dari akun?") },
                text = {
                    Text(if (allDevices) "$SIGN_OUT_ALL_INTRO\n\n${signOutBody(state.plan)}" else signOutBody(state.plan))
                },
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
                    AuthTextField(
                        label = "Kata sandi",
                        value = password,
                        onChange = { password = it },
                        enabled = true,
                        error = state.error,
                        password = true,
                        imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                        onImeAction = { actions.onDeleteWithPassword(password) },
                        modifier = Modifier.padding(top = 12.dp),
                    )
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

/** Dialog Ubah kata sandi (akun bersandi) atau Buat kata sandi (akun Google yang belum punya). */
@Composable
fun ChangePasswordDialog(
    state: PasswordState,
    onSubmit: (current: String, password: String, confirmation: String) -> Unit,
    onDismiss: () -> Unit,
) {
    // Di luar `when` supaya isian bertahan selagi dialog berganti ke "Menyimpan..." dan kembali karena galat server.
    var current by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    LaunchedEffect(state is PasswordState.Hidden) {
        if (state is PasswordState.Hidden) {
            current = ""
            password = ""
            confirmation = ""
        }
    }
    when (state) {
        PasswordState.Hidden -> Unit
        PasswordState.Saving -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Menyimpan...") },
            text = { Text("Mengirim kata sandi baru ke server.") },
            confirmButton = {},
        )
        is PasswordState.Editing -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(if (state.hasPassword) "Ubah kata sandi" else "Buat kata sandi") },
                text = {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (state.hasPassword) {
                            AuthTextField(label = "Kata sandi saat ini", value = current, onChange = { current = it }, enabled = true, error = null, password = true)
                        }
                        AuthTextField(
                            label = "Kata sandi baru", value = password, onChange = { password = it }, enabled = true, error = null,
                            password = true, hint = "Minimal 8 karakter",
                        )
                        AuthTextField(
                            label = "Ulangi kata sandi baru", value = confirmation, onChange = { confirmation = it }, enabled = true,
                            error = null, password = true, imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                            onImeAction = { onSubmit(current, password, confirmation) },
                        )
                        state.error?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (state.networkProblem) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                            )
                        }
                        Text(
                            "Perangkat lain yang masuk dengan akun ini akan diminta masuk lagi. HP ini tetap masuk.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                },
                confirmButton = { Button(onClick = { onSubmit(current, password, confirmation) }) { Text("Simpan") } },
                dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
            )
        }
    }
}
