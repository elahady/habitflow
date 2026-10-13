package com.roziqrizal.habitflow.ui.auth

import kotlin.math.ceil

enum class AuthScreen { LOGIN, REGISTER, VERIFY, FORGOT, RESET }

/** Kolom yang bisa menampilkan galat validasi lokal. */
enum class AuthField { NAME, EMAIL, PASSWORD, CONFIRMATION, CONSENT, CODE }

object AuthConfig {
    /** Versi kebijakan privasi yang dikirim saat daftar. Harus sama dengan `PRIVACY_POLICY_VERSION` di `.env` server. */
    const val PRIVACY_POLICY_VERSION = "1"
    const val CODE_LENGTH = 6
    const val MIN_PASSWORD_LENGTH = 8

    /** Server membatasi kirim ulang kode satu per 60 detik per email. */
    const val RESEND_COOLDOWN_MS = 60_000L

    /** Kode email berlaku 10 menit (kontrak 31A). */
    const val CODE_VALID_MS = 10 * 60_000L
}

/** Semua isi dan keadaan layar auth. Kolom isian ikut di sini supaya bertahan saat layar diputar. */
data class AuthUiState(
    val screen: AuthScreen = AuthScreen.LOGIN,
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val passwordConfirmation: String = "",
    val consent: Boolean = false,
    val code: String = "",
    /** Satu permintaan sedang berjalan. Semua tombol aksi nonaktif dan aksi kedua diabaikan. */
    val busy: Boolean = false,
    /** Kalimat tenang di bawah form. Null kalau tidak ada. */
    val error: String? = null,
    /** True kalau [error] karena jaringan, bukan salah pengguna: ditampilkan dengan warna netral. */
    val networkProblem: Boolean = false,
    /** Info non-galat (kode dikirim, kata sandi diganti). */
    val notice: String? = null,
    /** Layar Masuk menampilkan banner "Sesi berakhir, masuk lagi". */
    val sessionExpired: Boolean = false,
    val fieldErrors: Map<AuthField, String> = emptyMap(),
    /** Waktu (milidetik epoch) kode boleh dikirim ulang. 0 = tidak ada hitung mundur. */
    val resendAvailableAt: Long = 0,
    /** Waktu kode terakhir yang dikirim kedaluwarsa. 0 = tidak diketahui. */
    val codeExpiresAt: Long = 0,
) {
    fun resendSecondsLeft(now: Long): Int = secondsUntil(resendAvailableAt, now)

    fun codeSecondsLeft(now: Long): Int = secondsUntil(codeExpiresAt, now)

    private fun secondsUntil(at: Long, now: Long): Int = if (at <= now) 0 else ceil((at - now) / 1000.0).toInt()
}

/** Validasi lokal sebelum menghubungi server. Server tetap memeriksa ulang, ini hanya supaya galatnya cepat dan jelas. */
object AuthValidation {
    private val EMAIL = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

    fun normalizeEmail(raw: String): String = raw.trim().lowercase()

    fun isValidEmail(email: String): Boolean = EMAIL.matches(email)

    fun isValidPassword(password: String): Boolean = password.length >= AuthConfig.MIN_PASSWORD_LENGTH

    /** Menyisakan angka saja (kode yang ditempel sering membawa spasi atau tanda hubung), maksimal [AuthConfig.CODE_LENGTH]. */
    fun cleanCode(raw: String): String = raw.filter { it.isDigit() }.take(AuthConfig.CODE_LENGTH)

    fun isCompleteCode(code: String): Boolean = code.length == AuthConfig.CODE_LENGTH
}

object AuthMessages {
    const val NAME = "Isi nama kamu."
    const val EMAIL = "Isi email yang benar, misalnya nama@contoh.com."
    const val PASSWORD_REQUIRED = "Isi kata sandi."
    const val PASSWORD_TOO_SHORT = "Kata sandi minimal 8 karakter."
    const val CONFIRMATION = "Kata sandi tidak cocok."
    const val CONSENT = "Centang persetujuan kebijakan privasi untuk melanjutkan."
    const val CODE_INCOMPLETE = "Isi 6 angka kode dari email."
    const val CODE_WRONG = "Kode salah. Periksa lagi, atau kirim ulang kode."
    const val OFFLINE = "Tidak ada internet. Masuk dan daftar butuh koneksi, coba lagi saat tersambung."
    const val SERVER_PROBLEM = "Server sedang bermasalah. Coba lagi sebentar lagi."
    const val GOOGLE_FAILED = "Masuk dengan Google belum berhasil. Coba lagi, atau masuk dengan email."
    const val PASSWORD_CHANGED = "Kata sandi sudah diganti. Masuk dengan kata sandi baru."
    const val SESSION_GONE = "Sesi tidak berlaku. Coba lagi."
    const val VERIFY_RESENT_ON_LOGIN = "Email ini belum diverifikasi. Kami kirim kode baru ke emailmu."
    const val RESET_SENT = "Kalau email itu terdaftar, kode 6 angka sudah dikirim."

    fun codeResent(email: String) = "Kode baru sudah dikirim ke $email."
}
