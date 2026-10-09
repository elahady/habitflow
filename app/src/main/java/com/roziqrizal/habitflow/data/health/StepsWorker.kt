package com.roziqrizal.habitflow.data.health

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.roziqrizal.habitflow.HabitFlowApplication
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/** Cek langkah di latar belakang sekitar tiap jam, supaya habit tercentang walau app tertutup (tahap 21). */
class StepsWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val graph = (applicationContext as HabitFlowApplication).graph
        // Kegagalan membaca (misalnya Health Connect sedang tidak siap) tidak perlu diulang: pembacaan berikutnya sejam lagi.
        runCatching { graph.stepsTracker.refresh(LocalDate.now(), ZoneId.systemDefault(), inBackground = true) }
        return Result.success()
    }

    companion object {
        private const val UNIQUE_NAME = "steps-check"

        /** Memasang jadwal sejam sekali. Aman dipanggil berulang: jadwal yang sudah ada dipertahankan. */
        fun ensureScheduled(context: Context) {
            val request = PeriodicWorkRequestBuilder<StepsWorker>(1, TimeUnit.HOURS).build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
