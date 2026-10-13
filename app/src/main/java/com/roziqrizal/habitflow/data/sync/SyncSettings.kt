package com.roziqrizal.habitflow.data.sync

import android.content.Context
import com.roziqrizal.habitflow.data.PrefFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

data class SyncStatus(val lastSuccessAt: Long, val lastMessage: String?)

/**
 * Status dan pilihan cadangan snapshot. Sejak 31F tidak ada lagi alamat server dan token di sini: cadangan memakai
 * token akun dan alamat dari `BuildConfig.API_BASE_URL`. Disimpan di berkas `sync.xml` yang **sengaja tidak ikut
 * Auto Backup** (lihat `res/xml/backup_rules.xml`).
 */
class SyncSettings(context: Context) {
    private val prefs = PrefFile.SYNC.open(context)

    init {
        // Sisa token cadangan 19B (sudah ditolak server sejak 31E) tidak perlu tinggal di HP.
        if (prefs.contains(LEGACY_URL) || prefs.contains(LEGACY_TOKEN)) {
            prefs.edit().remove(LEGACY_URL).remove(LEGACY_TOKEN).apply()
        }
    }

    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, false))

    /** Sinkron otomatis menyala. Hanya berlaku kalau sedang masuk ke akun. */
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _status = MutableStateFlow(
        SyncStatus(prefs.getLong(KEY_LAST_SUCCESS, 0L), prefs.getString(KEY_LAST_MESSAGE, null)),
    )
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    /** Pengenal perangkat ini, dibuat sekali. Ikut di snapshot supaya terlihat HP mana yang mengirim. */
    fun deviceId(): String =
        prefs.getString(KEY_DEVICE_ID, null) ?: UUID.randomUUID().toString().also { prefs.edit().putString(KEY_DEVICE_ID, it).apply() }

    /** Sidik jari isi HP saat cadangan terakhir berhasil terkirim (31H), atau null kalau belum pernah. */
    val backupFingerprint: String?
        get() = prefs.getString(KEY_BACKUP_FINGERPRINT, null)

    fun recordBackupFingerprint(fingerprint: String) {
        prefs.edit().putString(KEY_BACKUP_FINGERPRINT, fingerprint).apply()
    }

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
        _enabled.value = enabled
    }

    fun recordSuccess(at: Long, message: String) {
        prefs.edit().putLong(KEY_LAST_SUCCESS, at).putString(KEY_LAST_MESSAGE, message).apply()
        _status.value = SyncStatus(at, message)
    }

    /** Menyimpan pesan gagal. Waktu sukses terakhir tidak berubah. */
    fun recordFailure(message: String) {
        prefs.edit().putString(KEY_LAST_MESSAGE, message).apply()
        _status.value = _status.value.copy(lastMessage = message)
    }

    private companion object {
        const val LEGACY_URL = "server_url"
        const val LEGACY_TOKEN = "server_token"
        const val KEY_ENABLED = "enabled"
        const val KEY_BACKUP_FINGERPRINT = "backup_fingerprint"
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_LAST_SUCCESS = "last_success_at"
        const val KEY_LAST_MESSAGE = "last_message"
    }
}
