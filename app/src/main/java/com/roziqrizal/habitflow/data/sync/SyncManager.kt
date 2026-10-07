package com.roziqrizal.habitflow.data.sync

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Membaca dan menerapkan pengaturan app yang ikut snapshot. Implementasinya memakai objek pengaturan yang sama dengan UI. */
interface AppSettingsGateway {
    fun read(): SnapshotSettings
    fun apply(settings: SnapshotSettings)
}

sealed interface RestorePreview {
    /** Snapshot server siap dipulihkan. [receivedAt] adalah waktu server menerimanya (milidetik). */
    data class Ready(val snapshot: Snapshot, val receivedAt: Long) : RestorePreview
    data class Failed(val message: String) : RestorePreview
}

sealed interface RestoreResult {
    data object Done : RestoreResult
    data class Failed(val message: String) : RestoreResult
}

/**
 * Menggabungkan klien, ekspor snapshot, dan status sinkron. Semua operasi berjalan satu per satu ([mutex]) supaya
 * sinkron otomatis dan tombol manual tidak saling menimpa.
 */
class SyncManager(
    private val snapshots: SnapshotRepository,
    private val settings: SyncSettings,
    private val appSettings: AppSettingsGateway,
    private val appVersion: String,
    private val allowLocalCleartext: Boolean,
    private val now: () -> Long = System::currentTimeMillis,
    private val clientFor: (url: String, token: String) -> SyncClient = { url, token -> SyncClient(url, token) },
) {
    private val mutex = Mutex()

    private fun client(): SyncClient? {
        val config = settings.config.value
        val url = SyncUrl.normalize(config.url, allowLocalCleartext) ?: return null
        if (config.token.isBlank()) return null
        return clientFor(url, config.token)
    }

    /** Uji alamat dan token tanpa mengubah status sinkron. */
    suspend fun testConnection(): SyncResult = mutex.withLock { client()?.ping() ?: SyncResult.NotConfigured }

    /** Mengunggah snapshot lengkap. Hasilnya dicatat di status (sukses menyimpan waktu, gagal menyimpan pesan). */
    suspend fun syncNow(): SyncResult = mutex.withLock {
        val client = client() ?: return@withLock SyncResult.NotConfigured
        val snapshot = snapshots.export(appSettings.read(), settings.deviceId(), appVersion, now())
        val result = client.upload(SnapshotCodec.encode(snapshot))
        if (result.isSuccess) settings.recordSuccess(now(), result.message()) else settings.recordFailure(result.message())
        result
    }

    /** Mengunduh snapshot terakhir untuk ditampilkan di konfirmasi pulihkan. Belum mengubah data apa pun. */
    suspend fun fetchRestorePreview(): RestorePreview = mutex.withLock {
        val client = client() ?: return@withLock RestorePreview.Failed(SyncResult.NotConfigured.message())
        when (val result = client.downloadLatest()) {
            is SyncResult.Downloaded -> try {
                RestorePreview.Ready(SnapshotCodec.decode(result.body), result.receivedAt)
            } catch (e: SnapshotFormatException) {
                RestorePreview.Failed(e.message ?: "Snapshot tidak bisa dibaca.")
            }
            else -> RestorePreview.Failed(result.message())
        }
    }

    /** Mengganti seluruh data di HP dengan [snapshot]. Gagal berarti data lama tetap utuh (satu transaksi). */
    suspend fun applyRestore(snapshot: Snapshot): RestoreResult = mutex.withLock {
        try {
            snapshots.restore(snapshot)
            appSettings.apply(snapshot.settings)
            RestoreResult.Done
        } catch (e: Exception) {
            RestoreResult.Failed("Pemulihan gagal, data di HP tidak berubah: ${e.message ?: e.javaClass.simpleName}")
        }
    }
}
