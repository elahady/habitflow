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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Menjadwalkan sinkron otomatis dengan WorkManager, hanya saat ada internet: sekali sehari, dan sekitar
 * [DEBOUNCE_MINUTES] menit setelah perubahan data terakhir (setiap perubahan baru mengulang hitungan mundur).
 */
class SyncScheduler(context: Context, private val settings: SyncSettings) {

    private val work = WorkManager.getInstance(context.applicationContext)
    private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** Dipanggil saat pengaturan sinkron berubah: nyalakan jadwal harian kalau aktif dan lengkap, kalau tidak batalkan semua. */
    fun onConfigChanged() {
        if (isActive()) {
            work.enqueueUniquePeriodicWork(
                DAILY, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<SyncWorker>(1, TimeUnit.DAYS)
                    .setConstraints(network)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
                    .build(),
            )
        } else {
            work.cancelUniqueWork(DAILY)
            work.cancelUniqueWork(AFTER_CHANGE)
        }
    }

    /** Ada perubahan data: sinkron sekitar [DEBOUNCE_MINUTES] menit lagi. Perubahan berikutnya mengulang hitungan mundur. */
    fun scheduleAfterChange() {
        if (!isActive()) return
        work.enqueueUniqueWork(
            AFTER_CHANGE, ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<SyncWorker>()
                .setInitialDelay(DEBOUNCE_MINUTES, TimeUnit.MINUTES)
                .setConstraints(network)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
                .build(),
        )
    }

    /** Memantau perubahan seluruh tabel selama proses hidup dan menjadwalkan sinkron tertunda. */
    fun observeChanges(db: HabitDatabase, scope: CoroutineScope) {
        scope.launch {
            // Dipasang dari thread latar supaya membuka database tidak menahan thread utama.
            db.invalidationTracker.addObserver(object : InvalidationTracker.Observer(TABLES) {
                override fun onInvalidated(tables: Set<String>) = scheduleAfterChange()
            })
        }
    }

    private fun isActive(): Boolean = settings.config.value.let { it.enabled && it.isConfigured }

    companion object {
        const val DEBOUNCE_MINUTES = 5L
        private const val DAILY = "habitflow-sync-daily"
        private const val AFTER_CHANGE = "habitflow-sync-after-change"
        private val TABLES = arrayOf(
            "habits", "habit_entries", "todos", "schedule_blocks", "schedule_block_habits",
            "days_off", "follow_ups", "work_days", "drink_counts", "weight_entries", "blood_pressure_entries",
            "events", "event_exceptions", "holiday_cancellations",
        )

        fun newScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}

/** Menjalankan satu sinkron. Gagal jaringan atau server dicoba ulang (maksimal 5 kali), gagal lain berhenti dan tercatat di status. */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val manager = (applicationContext as HabitFlowApplication).graph.syncManager
        val result = manager.syncNow()
        return when {
            result.isSuccess || result is SyncResult.NotConfigured -> Result.success()
            result.retryable && runAttemptCount < MAX_ATTEMPTS -> Result.retry()
            else -> Result.failure()
        }
    }

    private companion object {
        const val MAX_ATTEMPTS = 5
    }
}
