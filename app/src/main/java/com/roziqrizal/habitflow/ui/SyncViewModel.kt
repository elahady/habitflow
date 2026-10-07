package com.roziqrizal.habitflow.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roziqrizal.habitflow.BuildConfig
import com.roziqrizal.habitflow.data.sync.RestorePreview
import com.roziqrizal.habitflow.data.sync.RestoreResult
import com.roziqrizal.habitflow.data.sync.SyncConfig
import com.roziqrizal.habitflow.data.sync.SyncManager
import com.roziqrizal.habitflow.data.sync.SyncScheduler
import com.roziqrizal.habitflow.data.sync.SyncSettings
import com.roziqrizal.habitflow.data.sync.SyncStatus
import com.roziqrizal.habitflow.data.sync.SyncUrl
import com.roziqrizal.habitflow.data.sync.message
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SyncUiState(
    val config: SyncConfig,
    val status: SyncStatus,
    val busy: Boolean = false,
    /** Hasil operasi terakhir yang dipicu pengguna, untuk ditampilkan di bawah tombol. */
    val message: String? = null,
    /** Snapshot server yang menunggu konfirmasi pulihkan. */
    val restore: RestorePreview.Ready? = null,
)

class SyncViewModel(
    private val manager: SyncManager,
    private val settings: SyncSettings,
    private val scheduler: SyncScheduler,
) : ViewModel() {

    private val busy = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)
    private val restore = MutableStateFlow<RestorePreview.Ready?>(null)

    val state: StateFlow<SyncUiState> = combine(settings.config, settings.status, busy, message, restore) { config, status, busy, message, restore ->
        SyncUiState(config, status, busy, message, restore)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SyncUiState(settings.config.value, settings.status.value),
    )

    /** Menyimpan alamat dan token. Mengembalikan teks galat kalau alamat tidak boleh dipakai, atau null kalau berhasil. */
    fun saveServer(url: String, token: String): String? {
        if (token.isBlank()) return "Token wajib diisi."
        val normalized = SyncUrl.normalize(url, allowLocalCleartext = BuildConfig.DEBUG)
            ?: return "Alamat harus diawali https:// dan tidak boleh kosong."
        settings.setServer(normalized, token)
        scheduler.onConfigChanged()
        message.value = null
        return null
    }

    fun setEnabled(enabled: Boolean) {
        if (enabled && !settings.config.value.isConfigured) return
        settings.setEnabled(enabled)
        scheduler.onConfigChanged()
        // Begitu dinyalakan, kirim cadangan pertama tanpa menunggu perubahan atau jadwal harian.
        if (enabled) syncNow()
    }

    fun testConnection() = launchBusy { message.value = manager.testConnection().message() }

    fun syncNow() = launchBusy { message.value = manager.syncNow().message() }

    /** Mengunduh snapshot terakhir untuk konfirmasi. Belum mengubah data apa pun. */
    fun prepareRestore() = launchBusy {
        when (val preview = manager.fetchRestorePreview()) {
            is RestorePreview.Ready -> restore.value = preview
            is RestorePreview.Failed -> message.value = preview.message
        }
    }

    fun dismissRestore() {
        restore.value = null
    }

    fun confirmRestore() {
        val preview = restore.value ?: return
        restore.value = null
        launchBusy {
            message.value = when (val result = manager.applyRestore(preview.snapshot)) {
                RestoreResult.Done -> "Data dipulihkan dari server."
                is RestoreResult.Failed -> result.message
            }
        }
    }

    private fun launchBusy(block: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true
        message.value = null
        viewModelScope.launch {
            try {
                block()
            } finally {
                busy.value = false
            }
        }
    }
}
