package com.roziqrizal.habitflow.ui

import com.roziqrizal.habitflow.data.account.AccountResult
import com.roziqrizal.habitflow.ui.auth.AuthMessages
import com.roziqrizal.habitflow.ui.auth.AuthValidation

/** Dialog Ubah atau Buat kata sandi di Profil (tahap 31I). */
sealed interface PasswordState {
    data object Hidden : PasswordState

    /** [hasPassword] false untuk akun Google yang belum punya kata sandi: tanpa kolom "kata sandi saat ini". */
    data class Editing(val hasPassword: Boolean, val error: String? = null, val networkProblem: Boolean = false) : PasswordState

    data object Saving : PasswordState
}

/** Isian dialog Ubah kata sandi yang divalidasi di HP dulu, supaya galat sederhana tidak perlu menunggu server. */
object PasswordChangeRules {
    const val CURRENT_REQUIRED = "Isi kata sandi saat ini."
    const val OFFLINE = "Tidak ada internet. Ubah kata sandi butuh koneksi, coba lagi saat tersambung."
    const val CHANGED = "Kata sandi sudah diganti. Perangkat lain perlu masuk lagi."
    const val CREATED = "Kata sandi sudah dibuat. Perangkat lain perlu masuk lagi."
    const val SESSION_ENDED = "Sesi berakhir. Masuk lagi dulu."

    /** Pesan galat pertama yang ditemukan, atau null kalau isiannya boleh dikirim. */
    fun validate(hasPassword: Boolean, current: String, password: String, confirmation: String): String? = when {
        hasPassword && current.isEmpty() -> CURRENT_REQUIRED
        !AuthValidation.isValidPassword(password) -> AuthMessages.PASSWORD_TOO_SHORT
        password != confirmation -> AuthMessages.CONFIRMATION
        else -> null
    }

    /**
     * Keadaan dialog sesudah server menjawab: berhasil menutup dialog (null), salah paham tetap di dialog dengan alasannya.
     * Tanpa internet memakai warna netral karena bukan salah pengguna.
     */
    fun after(result: AccountResult<Unit>, hasPassword: Boolean): PasswordState? = when (result) {
        is AccountResult.Success -> null
        AccountResult.Offline -> PasswordState.Editing(hasPassword, OFFLINE, networkProblem = true)
        // Sesi yang berakhir ditangani gerbang auth (layar Masuk dengan banner); dialog ditutup.
        AccountResult.Unauthorized -> null
        is AccountResult.Failed -> PasswordState.Editing(hasPassword, result.message)
    }

    fun successMessage(hadPassword: Boolean): String = if (hadPassword) CHANGED else CREATED
}
