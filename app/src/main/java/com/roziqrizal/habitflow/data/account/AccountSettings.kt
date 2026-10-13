package com.roziqrizal.habitflow.data.account

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Account(val id: Long, val email: String, val name: String, val avatar: String?, val token: String)

/** Keadaan sesi akun di HP ini (tahap 31F). */
sealed interface SessionState {
    /** Belum masuk, atau sudah keluar. */
    data object SignedOut : SessionState

    data class SignedIn(val account: Account) : SessionState

    /**
     * Server menolak token (401). Token sudah dibuang, tetapi data lokal dan [email] dipertahankan supaya layar
     * Masuk bisa menampilkan "Sesi berakhir, masuk lagi" dengan email terisi.
     */
    data class Expired(val email: String, val name: String?) : SessionState
}

/**
 * Sesi akun dan pemilik data lokal (tahap 25, diperluas di 31F). Beda dari
 * [com.roziqrizal.habitflow.data.sync.SyncSettings] yang menyimpan status cadangan. Disimpan di berkas
 * `account.xml`, **sengaja tidak ikut Auto Backup** (tidak didaftarkan di `res/xml/backup_rules.xml`) supaya
 * token tidak ikut ke cadangan awan Google, sama seperti alasan `sync.xml` dikeluarkan.
 *
 * [ownerAccountId] adalah akun pemilik data di database lokal. Sengaja terpisah dari sesi: ia bertahan saat sesi
 * berakhir atau keluar, karena datanya memang masih di HP. Hanya [clearOwnerAccountId] yang menghapusnya, dipanggil
 * bersama penghapusan data lokal (31H).
 */
class AccountSettings(private val prefs: SharedPreferences) {

    constructor(context: Context) : this(context.applicationContext.getSharedPreferences("account", Context.MODE_PRIVATE))

    private val _session = MutableStateFlow(readSession())
    val session: StateFlow<SessionState> = _session.asStateFlow()

    private val _account = MutableStateFlow((_session.value as? SessionState.SignedIn)?.account)

    /** Akun yang sedang masuk, atau null kalau [session] bukan [SessionState.SignedIn]. Dipakai pekerjaan latar belakang. */
    val account: StateFlow<Account?> = _account.asStateFlow()

    /** Akun pemilik data di database lokal, atau null kalau belum ada pemilik. */
    val ownerAccountId: Long?
        get() = prefs.getLong(KEY_OWNER_ID, -1L).takeIf { it >= 0 }

    private fun readSession(): SessionState {
        val email = prefs.getString(KEY_EMAIL, null)
        val name = prefs.getString(KEY_NAME, null)
        val id = prefs.getLong(KEY_ID, -1L).takeIf { it >= 0 }
        val token = prefs.getString(KEY_TOKEN, null)
        return when {
            token != null && email != null && name != null && id != null ->
                SessionState.SignedIn(Account(id, email, name, prefs.getString(KEY_AVATAR, null), token))
            prefs.getBoolean(KEY_EXPIRED, false) && email != null -> SessionState.Expired(email, name)
            else -> SessionState.SignedOut
        }
    }

    @Synchronized
    fun save(account: Account) {
        prefs.edit()
            .putLong(KEY_ID, account.id)
            .putString(KEY_TOKEN, account.token)
            .putString(KEY_EMAIL, account.email)
            .putString(KEY_NAME, account.name)
            .putString(KEY_AVATAR, account.avatar)
            .putBoolean(KEY_EXPIRED, false)
            .apply()
        publish(SessionState.SignedIn(account))
    }

    /**
     * Menandai sesi berakhir, tetapi hanya kalau [token] masih token yang sedang dipakai. Permintaan yang
     * terlambat dengan token lama tidak boleh mematikan sesi baru. Mengembalikan true kalau sesi benar-benar ditandai.
     */
    @Synchronized
    fun expire(token: String): Boolean {
        val current = _session.value as? SessionState.SignedIn ?: return false
        if (current.account.token != token) return false
        prefs.edit().remove(KEY_TOKEN).putBoolean(KEY_EXPIRED, true).apply()
        publish(SessionState.Expired(current.account.email, current.account.name))
        return true
    }

    /** Mengakhiri sesi (keluar). [ownerAccountId] tidak disentuh karena datanya masih di HP. */
    @Synchronized
    fun clear() {
        prefs.edit()
            .remove(KEY_ID).remove(KEY_TOKEN).remove(KEY_EMAIL).remove(KEY_NAME).remove(KEY_AVATAR).remove(KEY_EXPIRED)
            .apply()
        publish(SessionState.SignedOut)
    }

    @Synchronized
    fun setOwnerAccountId(id: Long) {
        prefs.edit().putLong(KEY_OWNER_ID, id).apply()
    }

    @Synchronized
    fun clearOwnerAccountId() {
        prefs.edit().remove(KEY_OWNER_ID).apply()
    }

    private fun publish(state: SessionState) {
        _account.value = (state as? SessionState.SignedIn)?.account
        _session.value = state
    }

    private companion object {
        const val KEY_ID = "id"
        const val KEY_TOKEN = "token"
        const val KEY_EMAIL = "email"
        const val KEY_NAME = "name"
        const val KEY_AVATAR = "avatar"
        const val KEY_EXPIRED = "expired"
        const val KEY_OWNER_ID = "owner_account_id"
    }
}
