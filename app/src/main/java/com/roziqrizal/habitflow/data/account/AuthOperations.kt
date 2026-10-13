package com.roziqrizal.habitflow.data.account

/**
 * Operasi akun yang dipakai layar Masuk, Daftar, Verifikasi, Lupa dan Reset kata sandi (tahap 31G). Diimplementasikan
 * [AccountManager]; tes mesin keadaan layar memakai implementasi palsu.
 */
interface AuthOperations {
    suspend fun register(
        name: String,
        email: String,
        password: String,
        passwordConfirmation: String,
        privacyPolicyVersion: String,
    ): AccountResult<Unit>

    suspend fun verifyEmail(email: String, code: String): AccountResult<Account>

    suspend fun resendVerification(email: String): AccountResult<Unit>

    suspend fun login(email: String, password: String): AccountResult<Account>

    suspend fun signInWithGoogleToken(idToken: String): SignInResult

    suspend fun forgotPassword(email: String): AccountResult<Unit>

    suspend fun resetPassword(
        email: String,
        code: String,
        password: String,
        passwordConfirmation: String,
    ): AccountResult<Unit>
}
