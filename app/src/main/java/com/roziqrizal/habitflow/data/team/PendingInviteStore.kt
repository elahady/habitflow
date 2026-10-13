package com.roziqrizal.habitflow.data.team

import android.content.Context
import android.content.SharedPreferences
import com.roziqrizal.habitflow.data.PrefFile

/**
 * Token undangan tim dari deep link yang belum diproses (tahap 26, diperluas di 31G). Link bisa dibuka saat pengguna
 * belum masuk: tokennya harus bertahan melewati layar Masuk atau Daftar, termasuk kalau proses app dimatikan di
 * tengah jalan (mis. pindah ke app email untuk mengambil kode verifikasi), lalu diproses setelah masuk.
 *
 * Disimpan di berkas `pending_invite.xml` yang tidak ikut Auto Backup (tidak didaftarkan di `res/xml/backup_rules.xml`).
 * Token undangan bukan rahasia akun, tapi tidak ada alasan membawanya ke perangkat lain.
 */
class PendingInviteStore(private val prefs: SharedPreferences) {

    constructor(context: Context) : this(PrefFile.PENDING_INVITE.open(context))

    fun read(): String? = prefs.getString(KEY_TOKEN, null)?.takeIf { it.isNotBlank() }

    fun save(token: String) {
        if (token.isBlank()) return
        prefs.edit().putString(KEY_TOKEN, token).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY_TOKEN).apply()
    }

    private companion object {
        const val KEY_TOKEN = "token"
    }
}
