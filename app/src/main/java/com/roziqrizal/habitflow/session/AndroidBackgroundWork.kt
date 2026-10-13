package com.roziqrizal.habitflow.session

import android.content.Context
import androidx.work.WorkManager
import com.roziqrizal.habitflow.data.health.StepsWorker
import com.roziqrizal.habitflow.data.sync.HabitSyncScheduler
import com.roziqrizal.habitflow.data.sync.SyncScheduler
import com.roziqrizal.habitflow.data.team.TeamPollScheduler
import com.roziqrizal.habitflow.notify.ScheduleNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** [BackgroundWork] untuk app: WorkManager, alarm, dan notifikasi. Dijalankan oleh [SessionManager]. */
class AndroidBackgroundWork(
    context: Context,
    private val syncScheduler: SyncScheduler,
    private val habitSyncScheduler: HabitSyncScheduler,
    private val teamPollScheduler: TeamPollScheduler,
    private val scope: CoroutineScope,
) : BackgroundWork {
    private val app = context.applicationContext

    override fun start() {
        syncScheduler.onConfigChanged()
        habitSyncScheduler.onAccountChanged()
        teamPollScheduler.onAccountChanged()
        StepsWorker.ensureScheduled(app)
        scope.launch { ScheduleNotifier.refresh(app, announce = false) }
    }

    override fun stop() {
        // Membatalkan seluruh pekerjaan terjadwal (sinkron, polling tim, langkah) sekaligus, lalu semua alarm dan notifikasi.
        WorkManager.getInstance(app).cancelAllWork()
        ScheduleNotifier.cancelAll(app)
    }
}
