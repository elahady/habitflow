package com.roziqrizal.habitflow.ui.auth

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roziqrizal.habitflow.BuildConfig
import com.roziqrizal.habitflow.ui.AppIcons

/** Alamat kebijakan privasi di server (halaman publik, URL tetap). */
private val PRIVACY_URL = BuildConfig.API_BASE_URL + "/kebijakan-privasi"

/** Kerangka semua layar auth: latar `background`, kolom tunggal 24dp, bisa digulir dan naik bersama keyboard. */
@Composable
private fun AuthPage(onBack: (() -> Unit)?, content: @Composable ColumnScope.() -> Unit) {
    if (onBack != null) BackHandler(onBack = onBack)
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (onBack != null) {
                    IconButton(onClick = onBack) { Icon(AppIcons.ArrowLeft, contentDescription = "Kembali") }
                } else {
                    Spacer(Modifier.height(24.dp))
                }
                content()
            }
        }
    }
}

@Composable
private fun Header(title: String, subtitle: String, note: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium.copy(fontSize = 30.sp),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (note != null) {
            Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PrimaryButton(text: String, busy: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(if (busy) "Memproses..." else text, maxLines = 1)
    }
}

@Composable
private fun GoogleButton(text: String, busy: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        // Tint dimatikan supaya empat warna resmi Google tidak diwarnai ulang.
        Icon(AppIcons.Google, contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(8.dp))
        Text(text, maxLines = 1)
    }
}

/** Tautan teks bergaris bawah dengan area sentuh minimal 44dp. */
@Composable
private fun LinkButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = enabled, modifier = Modifier.heightIn(min = 44.dp)) {
        Text(text, textDecoration = TextDecoration.Underline)
    }
}

@Composable
private fun SwitchFooter(question: String, action: String, enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(question, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LinkButton(action, enabled = enabled, onClick = onClick)
    }
}

/** Kalimat dengan satu bagian yang menjadi tautan ke kebijakan privasi. */
@Composable
private fun PrivacyLinkText(before: String, link: String, after: String, modifier: Modifier = Modifier, small: Boolean = false) {
    val linkStyle = TextLinkStyles(SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline))
    Text(
        text = buildAnnotatedString {
            append(before)
            withLink(LinkAnnotation.Url(PRIVACY_URL, linkStyle)) { append(link) }
            append(after)
        },
        style = if (small) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

// --- Masuk -------------------------------------------------------------------------------------

@Composable
fun LoginScreen(state: AuthUiState, vm: AuthViewModel, pendingInvite: Boolean) {
    val context = LocalContext.current
    AuthPage(onBack = null) {
        if (pendingInvite) StatusCard(INVITE_NOTICE)
        if (state.sessionExpired) StatusCard("Sesi berakhir, masuk lagi.")
        state.notice?.let { StatusCard(it) }
        Header("Masuk", "Masuk untuk memakai Habitflow di HP ini.", note = "Masuk dan daftar butuh internet.")

        AuthTextField(
            label = "Email", value = state.email, onChange = vm::onEmail, enabled = !state.busy,
            error = state.fieldErrors[AuthField.EMAIL], keyboardType = KeyboardType.Email,
        )
        AuthTextField(
            label = "Kata sandi", value = state.password, onChange = vm::onPassword, enabled = !state.busy,
            error = state.fieldErrors[AuthField.PASSWORD], password = true, imeAction = ImeAction.Done,
            onImeAction = vm::submitLogin
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            LinkButton("Lupa kata sandi?", enabled = !state.busy) { vm.go(AuthScreen.FORGOT) }
        }
        ErrorLine(state)
        PrimaryButton("Masuk", state.busy, vm::submitLogin)
        OrDivider()
        GoogleButton("Masuk dengan Google", state.busy) { vm.signInWithGoogle(context) }
        PrivacyLinkText(
            before = "Dengan masuk memakai Google, kamu menyetujui ", link = "Kebijakan Privasi",
            after = ", termasuk penyimpanan data kesehatanmu di server.", small = true,
        )
        SwitchFooter("Belum punya akun?", "Daftar", !state.busy) { vm.go(AuthScreen.REGISTER) }
    }
}

// --- Daftar ------------------------------------------------------------------------------------

@Composable
fun RegisterScreen(state: AuthUiState, vm: AuthViewModel, pendingInvite: Boolean) {
    val context = LocalContext.current
    AuthPage(onBack = { vm.go(AuthScreen.LOGIN) }) {
        if (pendingInvite) StatusCard(INVITE_NOTICE)
        Header("Daftar", "Buat akun untuk menyimpan dan menyinkronkan habit-mu.", note = "Masuk dan daftar butuh internet.")

        AuthTextField(
            label = "Nama", value = state.name, onChange = vm::onName, enabled = !state.busy,
            error = state.fieldErrors[AuthField.NAME]
        )
        AuthTextField(
            label = "Email", value = state.email, onChange = vm::onEmail, enabled = !state.busy,
            error = state.fieldErrors[AuthField.EMAIL], keyboardType = KeyboardType.Email,
        )
        AuthTextField(
            label = "Kata sandi", value = state.password, onChange = vm::onPassword, enabled = !state.busy,
            error = state.fieldErrors[AuthField.PASSWORD], password = true, hint = "Minimal 8 karakter",
        )
        AuthTextField(
            label = "Ulangi kata sandi", value = state.passwordConfirmation, onChange = vm::onPasswordConfirmation,
            enabled = !state.busy, error = state.fieldErrors[AuthField.CONFIRMATION], password = true,
            imeAction = ImeAction.Done
        )

        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .toggleable(
                        value = state.consent, enabled = !state.busy, role = Role.Checkbox, onValueChange = vm::onConsent,
                    ),
            ) {
                Checkbox(checked = state.consent, onCheckedChange = null, enabled = !state.busy, modifier = Modifier.padding(end = 12.dp))
                PrivacyLinkText(
                    before = "Saya menyetujui ", link = "Kebijakan Privasi",
                    after = ", termasuk penyimpanan data kesehatan saya (berat, tensi, langkah, makan) di server.",
                    small = true,
                )
            }
            state.fieldErrors[AuthField.CONSENT]?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }

        ErrorLine(state)
        PrimaryButton("Daftar", state.busy, vm::submitRegister)
        OrDivider()
        GoogleButton("Daftar dengan Google", state.busy) { vm.signInWithGoogle(context) }
        SwitchFooter("Sudah punya akun?", "Masuk", !state.busy) { vm.go(AuthScreen.LOGIN) }
    }
}

// --- Verifikasi kode ---------------------------------------------------------------------------

@Composable
fun VerifyScreen(state: AuthUiState, vm: AuthViewModel) {
    val now = rememberTickingNow()
    val codeLeft = state.codeSecondsLeft(now)
    val resendLeft = state.resendSecondsLeft(now)
    AuthPage(onBack = { vm.go(AuthScreen.LOGIN) }) {
        state.notice?.let { StatusCard(it) }
        Header(
            "Cek email kamu",
            "Kami mengirim kode ${AuthConfig.CODE_LENGTH} angka ke ${state.email}. " + when {
                codeLeft > 0 -> "Kode berlaku ${formatCountdown(codeLeft)}."
                state.codeExpiresAt > 0 -> "Kode sudah kedaluwarsa, kirim ulang kode baru."
                else -> "Kode berlaku 10 menit."
            },
        )

        CodeField(
            value = state.code, onChange = vm::onCode, enabled = !state.busy,
            error = state.fieldErrors[AuthField.CODE],
        )
        ErrorLine(state)
        PrimaryButton("Verifikasi", state.busy, vm::submitVerify)
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            LinkButton(
                if (resendLeft > 0) "Kirim ulang kode ($resendLeft)" else "Kirim ulang kode",
                enabled = !state.busy && resendLeft == 0, onClick = vm::resendCode,
            )
            LinkButton("Ganti email", enabled = !state.busy) { vm.go(AuthScreen.REGISTER) }
        }
    }
}

// --- Lupa kata sandi ---------------------------------------------------------------------------

@Composable
fun ForgotScreen(state: AuthUiState, vm: AuthViewModel) {
    AuthPage(onBack = { vm.go(AuthScreen.LOGIN) }) {
        Header(
            "Lupa kata sandi",
            "Masukkan email akunmu. Kami kirim kode ${AuthConfig.CODE_LENGTH} angka untuk membuat kata sandi baru.",
        )
        AuthTextField(
            label = "Email", value = state.email, onChange = vm::onEmail, enabled = !state.busy,
            error = state.fieldErrors[AuthField.EMAIL], keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Done, onImeAction = vm::submitForgot
        )
        ErrorLine(state)
        PrimaryButton("Kirim kode", state.busy, vm::submitForgot)
        SwitchFooter("Ingat kata sandimu?", "Masuk", !state.busy) { vm.go(AuthScreen.LOGIN) }
    }
}

// --- Reset kata sandi --------------------------------------------------------------------------

@Composable
fun ResetScreen(state: AuthUiState, vm: AuthViewModel) {
    val now = rememberTickingNow()
    val resendLeft = state.resendSecondsLeft(now)
    AuthPage(onBack = { vm.go(AuthScreen.FORGOT) }) {
        StatusCard(state.notice ?: AuthMessages.RESET_SENT)
        Header(
            "Cek email kamu",
            "Masukkan kode ${AuthConfig.CODE_LENGTH} angka yang dikirim ke ${state.email}, lalu buat kata sandi baru.",
        )
        CodeField(
            value = state.code, onChange = vm::onCode, enabled = !state.busy,
            error = state.fieldErrors[AuthField.CODE],
        )
        AuthTextField(
            label = "Kata sandi baru", value = state.password, onChange = vm::onPassword, enabled = !state.busy,
            error = state.fieldErrors[AuthField.PASSWORD], password = true, hint = "Minimal 8 karakter",
        )
        AuthTextField(
            label = "Ulangi kata sandi baru", value = state.passwordConfirmation, onChange = vm::onPasswordConfirmation,
            enabled = !state.busy, error = state.fieldErrors[AuthField.CONFIRMATION], password = true,
            imeAction = ImeAction.Done, onImeAction = vm::submitReset
        )
        ErrorLine(state)
        PrimaryButton("Ganti kata sandi", state.busy, vm::submitReset)
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            LinkButton(
                if (resendLeft > 0) "Kirim ulang kode ($resendLeft)" else "Kirim ulang kode",
                enabled = !state.busy && resendLeft == 0, onClick = vm::resendCode,
            )
            LinkButton("Ganti email", enabled = !state.busy) { vm.go(AuthScreen.FORGOT) }
        }
    }
}

private const val INVITE_NOTICE =
    "Kamu diundang bergabung ke tim. Masuk atau daftar dulu, undangannya tetap tersimpan."

/**
 * Login diterima server, tapi HP ini masih menyimpan data akun lain (tahap 31H): tahan sampai pengguna memutuskan. Teksnya
 * tidak menyebut email akun lain karena tidak disimpan di HP selain sebagai pemilik data (id), dan jujur soal akibatnya.
 */
@Composable
fun OwnerConflictDialog(busy: Boolean, onConfirm: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = { if (!busy) onCancel() },
        title = { Text("Data akun lain masih di HP ini") },
        text = {
            Text(
                "HP ini masih menyimpan data akun lain. Kalau kamu lanjut masuk, semua data itu dihapus dari HP ini (habit, to-do, " +
                    "jadwal, kesehatan, pengaturan). Perubahan akun itu yang belum sempat tersinkron ikut hilang; data yang sudah " +
                    "tersinkron tetap aman di server.",
            )
        },
        confirmButton = { Button(onClick = onConfirm, enabled = !busy) { Text(if (busy) "Memproses..." else "Hapus dan masuk") } },
        dismissButton = { TextButton(onClick = onCancel, enabled = !busy) { Text("Batal") } },
    )
}
