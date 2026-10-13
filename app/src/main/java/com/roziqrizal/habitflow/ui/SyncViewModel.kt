package com.roziqrizal.habitflow.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roziqrizal.habitflow.data.sync.RestorePreview
import com.roziqrizal.habitflow.data.sync.RestoreResult
import com.roziqrizal.habitflow.data.sync.SyncManager
import com.roziqrizal.habitflow.data.sync.SyncScheduler
import com.roziqrizal.habitflow.data.sync.SyncSettings
import com.roziqrizal.habitflow.data.sync.SyncStatus
import com.roziqrizal.habitflow.data.sync.message
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SyncUiState(
    /** Sinkron otomatis menyala. */
    val enabled: Boolean,
    val status: SyncStatus,
    val busy: Boolean = false,
    /** Hasil operasi terakhir yang dipicu pengguna, untuk ditampilkan di bawah tombol. */
    val message: String? = null,
    /** Snapshot server yang menunggu konfirmasi pulihkan. */
    val restore: RestorePreview.Ready? = null,
)

/** Cadangan snapshot di Pengaturan. Selalu memakai sesi akun; layar ini baru bisa dibuka setelah masuk (gerbang 31G). */
class SyncViewModel(
    private val manager: SyncManager,
    private val settings: SyncSettings,
    private val scheduler: SyncScheduler,
) : ViewModel() {

    private val busy = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)
    private val restore = MutableStateFlow<RestorePreview.Ready?>(null)

    private val persisted = combine(settings.enabled, settings.status) { enabled, status ->
        SyncUiState(enabled = enabled, status = status)
    }

    val state: StateFlow<SyncUiState> = combine(persisted, busy, message, restore) { base, busy, message, restore ->
        base.copy(busy = busy, message = message, restore = restore)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SyncUiState(enabled = settings.enabled.value, status = settings.status.value),
    )

    fun setEnabled(enabled: Boolean) {
        settings.setEnabled(enabled)
        scheduler.onConfigChanged()
        // Begitu dinyalakan, kirim cadangan pertama tanpa menunggu perubahan atau jadwal harian.
        if (enabled) syncNow()
    }

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
                RestoreResult.Done -> "Data dipulihkan dari cadangan akun."
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
