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

    /** [privacyPolicyVersion] null kalau pengguna belum diminta menyetujui kebijakan privasi di layar ini. */
    suspend fun signInWithGoogleToken(idToken: String, privacyPolicyVersion: String? = null): SignInResult

    suspend fun forgotPassword(email: String): AccountResult<Unit>

    suspend fun resetPassword(
        email: String,
        code: String,
        password: String,
        passwordConfirmation: String,
    ): AccountResult<Unit>

    /**
     * Setelah login ditahan karena HP berisi data akun lain ([AccountErrors.OWNER_MISMATCH] atau [SignInResult.OwnerConflict]):
     * hapus data itu dan lanjutkan masuk. Memulai ulang proses, jadi biasanya tidak kembali. False kalau tidak ada yang ditahan.
     */
    suspend fun confirmReplaceLocalData(): Boolean

    /** Batal: login yang ditahan dicabut dan data di HP dibiarkan. */
    suspend fun cancelReplaceLocalData()
}
