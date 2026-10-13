package com.roziqrizal.habitflow.ui.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.roziqrizal.habitflow.data.account.SessionState

/**
 * Gerbang di depan seluruh app (tahap 31G): isi app ([content]) hanya digambar kalau [session] adalah
 * [SessionState.SignedIn]. Selain itu yang tampil hanya layar auth. Sesi berakhir ([SessionState.Expired]) membuka
 * Masuk dengan banner dan email terisi.
 *
 * Tidak ada keadaan "Loading": `AccountSettings` membaca sesinya secara sinkron saat dibuat, jadi sesi sudah diketahui
 * sebelum layar pertama digambar dan gerbang tidak pernah perlu menunggu.
 *
 * Gerbang ini baru menahan **layar**. Alarm, notifikasi, dan pekerja latar belakang dimatikan oleh `SessionManager` di 31H.
 */
@Composable
fun AuthGate(
    session: SessionState,
    auth: AuthViewModel,
    /** Ada tautan undangan tim yang menunggu: layar Masuk dan Daftar menyebutnya. */
    pendingInvite: Boolean,
    content: @Composable () -> Unit,
) {
    LaunchedEffect(session) { auth.applySession(session) }
    if (session is SessionState.SignedIn) {
        content()
    } else {
        val state by auth.state.collectAsState()
        when (state.screen) {
            AuthScreen.LOGIN -> LoginScreen(state, auth, pendingInvite)
            AuthScreen.REGISTER -> RegisterScreen(state, auth, pendingInvite)
            AuthScreen.VERIFY -> VerifyScreen(state, auth)
            AuthScreen.FORGOT -> ForgotScreen(state, auth)
            AuthScreen.RESET -> ResetScreen(state, auth)
        }
    }
}
