package com.roziqrizal.habitflow.data.account

/**
 * Hasil satu panggilan API akun (tahap 31F). Tipe tertutup supaya pemanggil dipaksa menangani setiap
 * kemungkinan: berhasil, ditolak server dengan alasan, sesi tidak berlaku, atau tidak ada internet.
 */
sealed interface AccountResult<out T> {
    data class Success<T>(val value: T) : AccountResult<T>

    /**
     * Ditolak server. [code] adalah kode di body (lihat [AccountErrors], bisa null untuk galat server),
     * [message] kalimat bahasa Indonesia dari server, [retryAfterSeconds] dari header `Retry-After` pada 429.
     */
    data class Failed(
        val status: Int,
        val code: String?,
        val message: String,
        val retryAfterSeconds: Int? = null,
    ) : AccountResult<Nothing>

    /** 401 pada panggilan yang membawa token: sesi di server sudah tidak berlaku. */
    data object Unauthorized : AccountResult<Nothing>

    /** Server tidak terjangkau (tanpa internet, waktu habis, alamat tidak sah). */
    data object Offline : AccountResult<Nothing>
}

/** Kode galat di field `code` (kontrak 31A). */
object AccountErrors {
    const val EMAIL_NOT_VERIFIED = "email_not_verified"
    const val INVALID_CREDENTIALS = "invalid_credentials"
    const val EMAIL_TAKEN = "email_taken"
    const val CODE_INVALID = "code_invalid"
    const val CODE_EXPIRED = "code_expired"
    const val TOO_MANY_ATTEMPTS = "too_many_attempts"

    /** Bukan dari server: dipakai klien untuk 422 validasi Laravel (field `errors`). */
    const val VALIDATION = "validation"
}

/** Jawaban `GET /me`. [account] memakai token yang dikirim, karena server tidak mengembalikannya. */
data class MeInfo(
    val account: Account,
    val hasPassword: Boolean,
    val googleLinked: Boolean,
    val emailVerified: Boolean,
)

/** Kontrak API akun di `/api/v1` (docs/concept.md, tahap 31A). Diimplementasikan [AccountClient]; tes memakai klien palsu. */
interface AccountApi {
    suspend fun register(
        name: String,
        email: String,
        password: String,
        passwordConfirmation: String,
        privacyPolicyVersion: String,
    ): AccountResult<Unit>

    suspend fun verifyEmail(email: String, code: String, deviceName: String): AccountResult<Account>

    suspend fun resendVerification(email: String): AccountResult<Unit>

    suspend fun login(email: String, password: String, deviceName: String): AccountResult<Account>

    suspend fun loginWithGoogle(idToken: String, deviceName: String): AccountResult<Account>

    suspend fun forgotPassword(email: String): AccountResult<Unit>

    suspend fun resetPassword(email: String, code: String, password: String, passwordConfirmation: String): AccountResult<Unit>

    suspend fun logout(token: String): AccountResult<Unit>

    suspend fun logoutAll(token: String): AccountResult<Unit>

    /** [currentPassword] null untuk akun Google yang belum punya kata sandi. */
    suspend fun changePassword(
        token: String,
        currentPassword: String?,
        password: String,
        passwordConfirmation: String,
    ): AccountResult<Unit>

    /** Konfirmasi ulang wajib: [password] untuk akun bersandi, [googleIdToken] untuk akun Google tanpa sandi. */
    suspend fun deleteAccount(token: String, password: String?, googleIdToken: String?): AccountResult<Unit>

    suspend fun me(token: String): AccountResult<MeInfo>
}
