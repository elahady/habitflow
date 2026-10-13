package com.roziqrizal.habitflow.ui.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roziqrizal.habitflow.data.account.GoogleSignIn
import com.roziqrizal.habitflow.data.account.SessionState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Pembungkus tipis [AuthStateMachine] untuk layar: menjalankan aksi di `viewModelScope`. Semua aturan ada di mesin. */
class AuthViewModel(
    private val machine: AuthStateMachine,
    private val googleSignIn: GoogleSignIn,
) : ViewModel() {

    val state: StateFlow<AuthUiState> = machine.state

    fun applySession(session: SessionState) = machine.applySession(session)

    fun go(screen: AuthScreen) = machine.go(screen)

    fun onName(value: String) = machine.onName(value)
    fun onEmail(value: String) = machine.onEmail(value)
    fun onPassword(value: String) = machine.onPassword(value)
    fun onPasswordConfirmation(value: String) = machine.onPasswordConfirmation(value)
    fun onConsent(value: Boolean) = machine.onConsent(value)

    /** Mengirim otomatis begitu 6 angka terisi di layar Verifikasi. Di layar Reset, pengguna menekan tombol sendiri. */
    fun onCode(raw: String) {
        machine.onCode(raw)
        val s = machine.state.value
        if (s.screen == AuthScreen.VERIFY && AuthValidation.isCompleteCode(s.code)) submitVerify()
    }

    fun submitLogin() {
        viewModelScope.launch { machine.submitLogin() }
    }
    fun submitRegister() {
        viewModelScope.launch { machine.submitRegister() }
    }
    fun submitVerify() {
        viewModelScope.launch { machine.submitVerify() }
    }
    fun resendCode() {
        viewModelScope.launch { machine.resendCode() }
    }
    fun submitForgot() {
        viewModelScope.launch { machine.submitForgot() }
    }
    fun submitReset() {
        viewModelScope.launch { machine.submitReset() }
    }

    fun confirmReplaceLocalData() {
        viewModelScope.launch { machine.confirmReplace() }
    }

    fun cancelReplaceLocalData() {
        viewModelScope.launch { machine.cancelReplace() }
    }

    /** [context] harus Activity: Credential Manager menampilkan pemilih akun di atasnya. */
    fun signInWithGoogle(context: Context) {
        viewModelScope.launch { machine.signInWithGoogle { googleSignIn.requestIdToken(context) } }
    }
}
