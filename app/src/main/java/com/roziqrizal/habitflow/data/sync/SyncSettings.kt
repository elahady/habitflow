package com.roziqrizal.habitflow.data.sync

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

data class SyncConfig(val url: String, val token: String, val enabled: Boolean) {
    val isConfigured: Boolean get() = url.isNotBlank() && token.isNotBlank()
}

data class SyncStatus(val lastSuccessAt: Long, val lastMessage: String?)

/**
 * Alamat server, token, dan status sinkron. Disimpan di berkas `sync.xml` yang **sengaja tidak ikut Auto Backup**
 * (lihat `res/xml/backup_rules.xml`), supaya token tidak ikut ke cadangan awan Google.
 */
class SyncSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("sync", Context.MODE_PRIVATE)

    private val _config = MutableStateFlow(
        SyncConfig(
            url = prefs.getString(KEY_URL, "").orEmpty(),
            token = prefs.getString(KEY_TOKEN, "").orEmpty(),
            enabled = prefs.getBoolean(KEY_ENABLED, false),
        ),
    )
    val config: StateFlow<SyncConfig> = _config.asStateFlow()

    private val _status = MutableStateFlow(
        SyncStatus(prefs.getLong(KEY_LAST_SUCCESS, 0L), prefs.getString(KEY_LAST_MESSAGE, null)),
    )
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    /** Pengenal perangkat ini, dibuat sekali. Ikut di snapshot supaya terlihat HP mana yang mengirim. */
    fun deviceId(): String =
        prefs.getString(KEY_DEVICE_ID, null) ?: UUID.randomUUID().toString().also { prefs.edit().putString(KEY_DEVICE_ID, it).apply() }

    fun setServer(url: String, token: String) {
        prefs.edit().putString(KEY_URL, url.trim()).putString(KEY_TOKEN, token.trim()).apply()
        _config.value = _config.value.copy(url = url.trim(), token = token.trim())
    }

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
        _config.value = _config.value.copy(enabled = enabled)
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
        const val KEY_URL = "server_url"
        const val KEY_TOKEN = "server_token"
        const val KEY_ENABLED = "enabled"
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_LAST_SUCCESS = "last_success_at"
        const val KEY_LAST_MESSAGE = "last_message"
    }
}
