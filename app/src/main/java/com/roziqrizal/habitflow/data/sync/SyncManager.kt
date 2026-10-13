package com.roziqrizal.habitflow.data.sync

import com.roziqrizal.habitflow.data.account.AccountClient
import com.roziqrizal.habitflow.data.account.AccountSettings
import com.roziqrizal.habitflow.data.account.UnauthorizedListener
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
    private val accountSettings: AccountSettings,
    private val appSettings: AppSettingsGateway,
    private val appVersion: String,
    /** Dilapori token yang ditolak server (401). Wajib, supaya tidak ada jalur yang menangani 401 sendiri. */
    unauthorized: UnauthorizedListener,
    /** True kalau follow-up kerja sudah disinkron lewat akun (tahap 27) dan tidak perlu ikut snapshot cadangan. */
    private val followUpsOnAccount: () -> Boolean = { false },
    private val now: () -> Long = System::currentTimeMillis,
    private val clientFor: (token: String) -> SyncClient = { SyncClient(AccountClient.BASE_URL, it, unauthorized = unauthorized) },
) {
    private val mutex = Mutex()

    /** Cadangan memakai token akun. Tanpa sesi masuk tidak ada klien (hasilnya [SyncResult.NotConfigured]). */
    private fun client(): SyncClient? = accountSettings.account.value?.token?.let(clientFor)

    /** Uji alamat dan token tanpa mengubah status sinkron. */
    suspend fun testConnection(): SyncResult = mutex.withLock { client()?.ping() ?: SyncResult.NotConfigured }

    /** Mengunggah snapshot lengkap. Hasilnya dicatat di status (sukses menyimpan waktu, gagal menyimpan pesan). */
    suspend fun syncNow(): SyncResult = mutex.withLock {
        val client = client() ?: return@withLock SyncResult.NotConfigured
        val snapshot = snapshots.export(
            appSettings.read(), settings.deviceId(), appVersion, now(), includeFollowUps = !followUpsOnAccount(),
        )
        val result = client.upload(SnapshotCodec.encode(snapshot))
        if (result.isSuccess) settings.recordBackupFingerprint(SnapshotFingerprint.of(snapshot))
        if (result.isSuccess) settings.recordSuccess(now(), result.message()) else settings.recordFailure(result.message())
        result
    }

    /** True kalau isi HP berbeda dari cadangan terakhir yang berhasil terkirim (atau belum pernah ada cadangan). Dipakai peringatan Keluar. */
    suspend fun backupIsStale(): Boolean = mutex.withLock {
        val snapshot = snapshots.export(
            appSettings.read(), settings.deviceId(), appVersion, now(), includeFollowUps = !followUpsOnAccount(),
        )
        SnapshotFingerprint.of(snapshot) != settings.backupFingerprint
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
