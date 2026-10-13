package com.roziqrizal.habitflow.ui.auth

import com.roziqrizal.habitflow.data.account.AccountErrors
import com.roziqrizal.habitflow.data.account.AccountResult
import com.roziqrizal.habitflow.data.account.AuthOperations
import com.roziqrizal.habitflow.data.account.GoogleIdTokenResult
import com.roziqrizal.habitflow.data.account.SessionState
import com.roziqrizal.habitflow.data.account.SignInResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Mesin keadaan murni untuk layar Masuk, Daftar, Verifikasi, Lupa dan Reset kata sandi (tahap 31G). Tidak memakai
 * Android sama sekali, jadi setiap jalur bisa diuji di JVM. Berhasil masuk **tidak** berpindah layar di sini:
 * [AccountSettings][com.roziqrizal.habitflow.data.account.AccountSettings] menyimpan sesi dan gerbang
 * ([AuthGate]) menggantinya dengan isi app.
 *
 * Aksi bersifat `suspend` dan dijalankan satu per satu: selama [AuthUiState.busy], aksi lain diabaikan, sehingga
 * tombol yang ditekan dua kali hanya mengirim satu permintaan.
 */
class AuthStateMachine(
    private val ops: AuthOperations,
    private val now: () -> Long = System::currentTimeMillis,
    private val privacyPolicyVersion: String = AuthConfig.PRIVACY_POLICY_VERSION,
) {
    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    /** Sesi terakhir yang sudah diterapkan, supaya pemanggilan berulang (mis. setelah layar diputar) tidak mereset isian. */
    private var appliedSession: SessionState? = null

    // --- isian ---------------------------------------------------------------------------------

    fun onName(value: String) = edit(AuthField.NAME) { it.copy(name = value) }

    fun onEmail(value: String) = edit(AuthField.EMAIL) { it.copy(email = value) }

    fun onPassword(value: String) = edit(AuthField.PASSWORD) { it.copy(password = value) }

    fun onPasswordConfirmation(value: String) = edit(AuthField.CONFIRMATION) { it.copy(passwordConfirmation = value) }

    fun onConsent(value: Boolean) = edit(AuthField.CONSENT) { it.copy(consent = value) }

    fun onCode(raw: String) = edit(AuthField.CODE) { it.copy(code = AuthValidation.cleanCode(raw)) }

    private fun edit(field: AuthField, change: (AuthUiState) -> AuthUiState) = _state.update {
        change(it).copy(fieldErrors = it.fieldErrors - field, error = null, networkProblem = false)
    }

    /** Pindah layar. Kata sandi dan kode dikosongkan; email, nama, dan persetujuan dipertahankan. */
    fun go(screen: AuthScreen) = _state.update {
        it.copy(
            screen = screen, error = null, networkProblem = false, notice = null, fieldErrors = emptyMap(),
            password = "", passwordConfirmation = "", code = "",
        )
    }

    /**
     * Mengikuti sesi akun. Sesi berakhir membuka Masuk dengan banner dan email terisi; masuk atau keluar mengosongkan
     * semua isian (kata sandi tidak boleh tersisa). Memanggil dengan sesi yang sama dua kali tidak berpengaruh.
     */
    fun applySession(session: SessionState) {
        if (session == appliedSession) return
        val first = appliedSession == null
        appliedSession = session
        when (session) {
            is SessionState.Expired ->
                _state.value = AuthUiState(email = session.email, sessionExpired = true)
            is SessionState.SignedIn -> _state.value = AuthUiState()
            SessionState.SignedOut -> if (!first) _state.value = AuthUiState()
        }
    }

    // --- aksi ----------------------------------------------------------------------------------

    suspend fun submitLogin() = guarded {
        val s = _state.value
        val email = AuthValidation.normalizeEmail(s.email)
        val errors = buildMap {
            if (!AuthValidation.isValidEmail(email)) put(AuthField.EMAIL, AuthMessages.EMAIL)
            if (s.password.isEmpty()) put(AuthField.PASSWORD, AuthMessages.PASSWORD_REQUIRED)
        }
        if (reject(errors)) return@guarded

        when (val result = ops.login(email, s.password)) {
            is AccountResult.Success -> _state.update { it.copy(password = "") }
            is AccountResult.Failed ->
                if (result.code == AccountErrors.EMAIL_NOT_VERIFIED) askForVerification(email) else fail(result)
            AccountResult.Unauthorized -> failWith(AuthMessages.SESSION_GONE)
            AccountResult.Offline -> offline()
        }
    }

    suspend fun submitRegister() = guarded {
        val s = _state.value
        val email = AuthValidation.normalizeEmail(s.email)
        val errors = buildMap {
            if (s.name.isBlank()) put(AuthField.NAME, AuthMessages.NAME)
            if (!AuthValidation.isValidEmail(email)) put(AuthField.EMAIL, AuthMessages.EMAIL)
            if (!AuthValidation.isValidPassword(s.password)) put(AuthField.PASSWORD, AuthMessages.PASSWORD_TOO_SHORT)
            if (s.password != s.passwordConfirmation) put(AuthField.CONFIRMATION, AuthMessages.CONFIRMATION)
            if (!s.consent) put(AuthField.CONSENT, AuthMessages.CONSENT)
        }
        if (reject(errors)) return@guarded

        when (
            val result = ops.register(s.name.trim(), email, s.password, s.passwordConfirmation, privacyPolicyVersion)
        ) {
            is AccountResult.Success -> _state.update {
                it.copy(
                    screen = AuthScreen.VERIFY, email = email, password = "", passwordConfirmation = "", code = "",
                    resendAvailableAt = now() + AuthConfig.RESEND_COOLDOWN_MS,
                    codeExpiresAt = now() + AuthConfig.CODE_VALID_MS,
                )
            }
            is AccountResult.Failed -> fail(result)
            AccountResult.Unauthorized -> failWith(AuthMessages.SESSION_GONE)
            AccountResult.Offline -> offline()
        }
    }

    suspend fun submitVerify() = guarded {
        val s = _state.value
        if (!AuthValidation.isCompleteCode(s.code)) {
            reject(mapOf(AuthField.CODE to AuthMessages.CODE_INCOMPLETE))
            return@guarded
        }
        when (val result = ops.verifyEmail(AuthValidation.normalizeEmail(s.email), s.code)) {
            is AccountResult.Success -> Unit
            is AccountResult.Failed -> {
                _state.update { it.copy(code = "") }
                if (result.code == AccountErrors.CODE_INVALID) failWith(AuthMessages.CODE_WRONG) else fail(result)
            }
            AccountResult.Unauthorized -> failWith(AuthMessages.SESSION_GONE)
            AccountResult.Offline -> offline()
        }
    }

    /** Kirim ulang kode di layar Verifikasi atau Reset. Diabaikan selama hitung mundur belum habis. */
    suspend fun resendCode() = guarded {
        val s = _state.value
        if (s.resendSecondsLeft(now()) > 0) return@guarded
        val email = AuthValidation.normalizeEmail(s.email)
        val result = when (s.screen) {
            AuthScreen.VERIFY -> ops.resendVerification(email)
            AuthScreen.RESET -> ops.forgotPassword(email)
            else -> return@guarded
        }
        when (result) {
            is AccountResult.Success -> _state.update {
                it.copy(
                    code = "", notice = AuthMessages.codeResent(email),
                    resendAvailableAt = now() + AuthConfig.RESEND_COOLDOWN_MS,
                    codeExpiresAt = now() + AuthConfig.CODE_VALID_MS,
                )
            }
            is AccountResult.Failed -> fail(result)
            AccountResult.Unauthorized -> failWith(AuthMessages.SESSION_GONE)
            AccountResult.Offline -> offline()
        }
    }

    suspend fun submitForgot() = guarded {
        val email = AuthValidation.normalizeEmail(_state.value.email)
        if (reject(if (AuthValidation.isValidEmail(email)) emptyMap() else mapOf(AuthField.EMAIL to AuthMessages.EMAIL))) {
            return@guarded
        }
        // Server menjawab sama untuk email terdaftar dan tidak, jadi layar juga tidak membocorkannya.
        when (val result = ops.forgotPassword(email)) {
            is AccountResult.Success -> _state.update {
                it.copy(
                    screen = AuthScreen.RESET, email = email, password = "", passwordConfirmation = "", code = "",
                    notice = AuthMessages.RESET_SENT,
                    resendAvailableAt = now() + AuthConfig.RESEND_COOLDOWN_MS,
                    codeExpiresAt = now() + AuthConfig.CODE_VALID_MS,
                )
            }
            is AccountResult.Failed -> fail(result)
            AccountResult.Unauthorized -> failWith(AuthMessages.SESSION_GONE)
            AccountResult.Offline -> offline()
        }
    }

    suspend fun submitReset() = guarded {
        val s = _state.value
        val errors = buildMap {
            if (!AuthValidation.isCompleteCode(s.code)) put(AuthField.CODE, AuthMessages.CODE_INCOMPLETE)
            if (!AuthValidation.isValidPassword(s.password)) put(AuthField.PASSWORD, AuthMessages.PASSWORD_TOO_SHORT)
            if (s.password != s.passwordConfirmation) put(AuthField.CONFIRMATION, AuthMessages.CONFIRMATION)
        }
        if (reject(errors)) return@guarded

        when (
            val result = ops.resetPassword(AuthValidation.normalizeEmail(s.email), s.code, s.password, s.passwordConfirmation)
        ) {
            is AccountResult.Success -> _state.update {
                it.copy(
                    screen = AuthScreen.LOGIN, password = "", passwordConfirmation = "", code = "",
                    notice = AuthMessages.PASSWORD_CHANGED, sessionExpired = false,
                    resendAvailableAt = 0, codeExpiresAt = 0,
                )
            }
            is AccountResult.Failed -> {
                if (result.code == AccountErrors.CODE_INVALID) {
                    _state.update { it.copy(code = "") }
                    failWith(AuthMessages.CODE_WRONG)
                } else {
                    if (result.code == AccountErrors.CODE_EXPIRED) _state.update { it.copy(code = "") }
                    fail(result)
                }
            }
            AccountResult.Unauthorized -> failWith(AuthMessages.SESSION_GONE)
            AccountResult.Offline -> offline()
        }
    }

    /**
     * Masuk atau daftar dengan Google. [requestIdToken] menampilkan pemilih akun (butuh Activity), jadi disuntikkan
     * pemanggil. Dibatalkan pengguna tidak menampilkan galat.
     */
    suspend fun signInWithGoogle(requestIdToken: suspend () -> GoogleIdTokenResult) = guarded {
        when (val token = requestIdToken()) {
            GoogleIdTokenResult.Cancelled -> Unit
            is GoogleIdTokenResult.Failed -> failWith(AuthMessages.GOOGLE_FAILED)
            is GoogleIdTokenResult.Success -> when (val result = ops.signInWithGoogleToken(token.idToken)) {
                is SignInResult.Success, SignInResult.Cancelled -> Unit
                is SignInResult.Failed -> failWith(result.message)
                SignInResult.Offline -> offline()
            }
        }
    }

    // --- bantuan -------------------------------------------------------------------------------

    /** Login ditolak karena email belum terverifikasi: buka Verifikasi dan minta kode baru, karena login tidak mengirimnya. */
    private suspend fun askForVerification(email: String) {
        _state.update {
            it.copy(
                screen = AuthScreen.VERIFY, email = email, password = "", code = "", fieldErrors = emptyMap(),
                notice = AuthMessages.VERIFY_RESENT_ON_LOGIN,
                resendAvailableAt = now() + AuthConfig.RESEND_COOLDOWN_MS,
            )
        }
        when (val sent = ops.resendVerification(email)) {
            is AccountResult.Success -> _state.update { it.copy(codeExpiresAt = now() + AuthConfig.CODE_VALID_MS) }
            is AccountResult.Failed -> fail(sent)
            AccountResult.Unauthorized -> failWith(AuthMessages.SESSION_GONE)
            AccountResult.Offline -> offline()
        }
    }

    /** Menjalankan [block] hanya kalau tidak ada aksi lain yang berjalan. */
    private suspend fun guarded(block: suspend () -> Unit) {
        while (true) {
            val current = _state.value
            if (current.busy) return
            val started = current.copy(busy = true, error = null, networkProblem = false, notice = null)
            if (_state.compareAndSet(current, started)) break
        }
        try {
            block()
        } finally {
            _state.update { it.copy(busy = false) }
        }
    }

    /** Menyimpan galat validasi. Mengembalikan true kalau ada galat (aksi harus berhenti). */
    private fun reject(errors: Map<AuthField, String>): Boolean {
        if (errors.isEmpty()) return false
        _state.update { it.copy(fieldErrors = errors) }
        return true
    }

    private fun fail(result: AccountResult.Failed) =
        failWith(if (result.status >= 500) AuthMessages.SERVER_PROBLEM else result.message)

    private fun failWith(message: String) = _state.update { it.copy(error = message, networkProblem = false) }

    private fun offline() = _state.update { it.copy(error = AuthMessages.OFFLINE, networkProblem = true) }
}
