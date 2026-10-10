package com.roziqrizal.habitflow.data.team

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tim aktif dan jejak notifikasi tugas (tahap 26). Disimpan di `team.xml`, **sengaja tidak ikut
 * Auto Backup** (tidak didaftarkan di `res/xml/backup_rules.xml`), pola sama dengan
 * [com.roziqrizal.habitflow.data.account.AccountSettings].
 */
class TeamSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("team", Context.MODE_PRIVATE)

    private val _activeTeamId = MutableStateFlow(readActiveTeamId())
    val activeTeamId: StateFlow<Long?> = _activeTeamId.asStateFlow()

    private fun readActiveTeamId(): Long? = prefs.getLong(KEY_ACTIVE_TEAM, -1L).takeIf { it >= 0 }

    fun setActiveTeam(teamId: Long) {
        prefs.edit().putLong(KEY_ACTIVE_TEAM, teamId).apply()
        _activeTeamId.value = teamId
    }

    /** Kunci (lihat `assignmentKey`) tugas yang sudah dinotifikasi, supaya tidak diulang (tahap 26 langkah 4). */
    fun notifiedKeys(): Set<String> = prefs.getStringSet(KEY_NOTIFIED, emptySet()).orEmpty()

    fun addNotifiedKeys(keys: Set<String>) {
        if (keys.isEmpty()) return
        prefs.edit().putStringSet(KEY_NOTIFIED, notifiedKeys() + keys).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
        _activeTeamId.value = null
    }

    private companion object {
        const val KEY_ACTIVE_TEAM = "active_team_id"
        const val KEY_NOTIFIED = "notified_keys"
    }
}
