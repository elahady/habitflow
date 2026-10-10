package com.roziqrizal.habitflow.data.sync

import android.content.Context
import androidx.room.InvalidationTracker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.roziqrizal.habitflow.HabitFlowApplication
import com.roziqrizal.habitflow.data.HabitDatabase
import com.roziqrizal.habitflow.data.account.AccountSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Menjadwalkan sinkron habit/todo dengan WorkManager (tahap 28 langkah 5), pola sama dengan
 * [SyncScheduler] 19B: push cepat (debounce ~1 menit) setiap ada perubahan data, ditambah push+pull
 * periodik sebagai jaring pengaman. **Hanya aktif kalau sudah login** - tanpa akun, app tetap
 * lokal-saja seperti sebelum fitur ini ada.
 */
class HabitSyncScheduler(context: Context, private val accountSettings: AccountSettings) {

    private val work = WorkManager.getInstance(context.applicationContext)
    private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    private fun isActive(): Boolean = accountSettings.account.value != null

    /** Dipanggil saat status login berubah: pasang jadwal periodik kalau sudah login, batalkan kalau logout. */
    fun onAccountChanged() {
        if (isActive()) {
            work.enqueueUniquePeriodicWork(
                PERIODIC, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<HabitPeriodicSyncWorker>(PERIODIC_MINUTES, TimeUnit.MINUTES)
                    .setConstraints(network)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
                    .build(),
            )
        } else {
            work.cancelUniqueWork(PERIODIC)
            work.cancelUniqueWork(AFTER_CHANGE)
        }
    }

    /** Ada perubahan data: push sekitar [DEBOUNCE_MINUTES] menit lagi. Perubahan berikutnya mengulang hitungan mundur. */
    fun scheduleAfterChange() {
        if (!isActive()) return
        work.enqueueUniqueWork(
            AFTER_CHANGE, ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<HabitPushWorker>()
                .setInitialDelay(DEBOUNCE_MINUTES, TimeUnit.MINUTES)
                .setConstraints(network)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
                .build(),
        )
    }

    /** Memantau perubahan tabel terkait sinkron selama proses hidup, dan status login untuk nyala/mati jadwal periodik. */
    fun observeChanges(db: HabitDatabase, scope: CoroutineScope) {
        scope.launch {
            // Dipasang dari thread latar supaya membuka database tidak menahan thread utama.
            db.invalidationTracker.addObserver(object : InvalidationTracker.Observer(TABLES) {
                override fun onInvalidated(tables: Set<String>) = scheduleAfterChange()
            })
        }
        scope.launch { accountSettings.account.collectLatest { onAccountChanged() } }
    }

    companion object {
        const val DEBOUNCE_MINUTES = 1L
        const val PERIODIC_MINUTES = 30L
        private const val PERIODIC = "habitflow-habit-sync-periodic"
        private const val AFTER_CHANGE = "habitflow-habit-sync-after-change"
        private val TABLES = arrayOf("habits", "habit_entries", "todos", "habit_entry_outbox", "pending_deletes")

        fun newScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}

/** Push saja, dipicu debounce setelah perubahan data. Gagal jaringan/server dicoba ulang. */
class HabitPushWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val manager = (applicationContext as HabitFlowApplication).graph.habitSyncManager
        return toWorkResult(manager.push())
    }

    private fun toWorkResult(result: HabitApiResult<Unit>): Result = when {
        result is HabitApiResult.Success -> Result.success()
        result.retryable && runAttemptCount < MAX_ATTEMPTS -> Result.retry()
        else -> Result.failure()
    }

    private companion object {
        const val MAX_ATTEMPTS = 5
    }
}

/** Push lalu pull, dijalankan periodik sebagai jaring pengaman. */
class HabitPeriodicSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val manager = (applicationContext as HabitFlowApplication).graph.habitSyncManager
        val result = manager.syncNow()
        return when {
            result is HabitApiResult.Success -> Result.success()
            result.retryable && runAttemptCount < MAX_ATTEMPTS -> Result.retry()
            else -> Result.failure()
        }
    }

    private companion object {
        const val MAX_ATTEMPTS = 5
    }
}
