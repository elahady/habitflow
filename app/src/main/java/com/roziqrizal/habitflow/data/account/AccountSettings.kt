package com.roziqrizal.habitflow.data.account

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Account(val email: String, val name: String, val avatar: String?, val token: String)

/**
 * Akun Google dan token API (tahap 25) - beda dari [com.roziqrizal.habitflow.data.sync.SyncSettings]
 * yang menyimpan token cadangan 19B. Disimpan di berkas `account.xml`, **sengaja tidak ikut Auto
 * Backup** (tidak didaftarkan di `res/xml/backup_rules.xml`) supaya token tidak ikut ke cadangan
 * awan Google, sama seperti alasan `sync.xml` dikeluarkan.
 */
class AccountSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("account", Context.MODE_PRIVATE)

    private val _account = MutableStateFlow(readAccount())
    val account: StateFlow<Account?> = _account.asStateFlow()

    private fun readAccount(): Account? {
        val token = prefs.getString(KEY_TOKEN, null) ?: return null
        val email = prefs.getString(KEY_EMAIL, null) ?: return null
        val name = prefs.getString(KEY_NAME, null) ?: return null
        return Account(email, name, prefs.getString(KEY_AVATAR, null), token)
    }

    fun save(account: Account) {
        prefs.edit()
            .putString(KEY_TOKEN, account.token)
            .putString(KEY_EMAIL, account.email)
            .putString(KEY_NAME, account.name)
            .putString(KEY_AVATAR, account.avatar)
            .apply()
        _account.value = account
    }

    fun clear() {
        prefs.edit().clear().apply()
        _account.value = null
    }

    private companion object {
        const val KEY_TOKEN = "token"
        const val KEY_EMAIL = "email"
        const val KEY_NAME = "name"
        const val KEY_AVATAR = "avatar"
    }
}
