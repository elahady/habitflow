package com.roziqrizal.habitflow.data.team

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.roziqrizal.habitflow.HabitFlowApplication
import com.roziqrizal.habitflow.data.account.AccountSettings
import com.roziqrizal.habitflow.domain.team.TeamTodoAssignment
import com.roziqrizal.habitflow.domain.team.assignmentKey
import com.roziqrizal.habitflow.domain.team.newlyAssignedTodos
import com.roziqrizal.habitflow.notify.TeamNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Menjadwalkan polling to-do tim dengan WorkManager (tahap 26 langkah 4): worker periodik
 * terpisah tiap [PERIODIC_MINUTES] menit, bukan memperluas `SyncWorker` 19B (tujuannya beda -
 * cadangan snapshot, bukan tim) supaya tanggung jawab tidak bercampur. **Hanya aktif kalau sudah
 * login**, pola sama dengan [com.roziqrizal.habitflow.data.sync.HabitSyncScheduler].
 */
class TeamPollScheduler(context: Context, private val accountSettings: AccountSettings) {
    private val appContext = context.applicationContext
    private val work = WorkManager.getInstance(appContext)
    private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun onAccountChanged() {
        if (accountSettings.account.value != null) {
            TeamNotifier.ensureChannel(appContext)
            work.enqueueUniquePeriodicWork(
                PERIODIC, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<TeamPollWorker>(PERIODIC_MINUTES, TimeUnit.MINUTES)
                    .setConstraints(network)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
                    .build(),
            )
        } else {
            work.cancelUniqueWork(PERIODIC)
        }
    }

    fun observeAccount(scope: CoroutineScope) {
        scope.launch { accountSettings.account.collectLatest { onAccountChanged() } }
    }

    companion object {
        const val PERIODIC_MINUTES = 5L
        private const val PERIODIC = "habitflow-team-poll-periodic"

        fun newScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}

/** Dipanggil juga langsung dari [com.roziqrizal.habitflow.ui.TeamViewModel] setelah aksi tugaskan, supaya notifikasi tidak menunggu siklus berikutnya kalau sedang di app lain. */
suspend fun pollTeamTodosForNewAssignments(
    accountSettings: AccountSettings,
    teamSettings: TeamSettings,
    notify: (TeamTodoAssignment) -> Unit,
) {
    val account = accountSettings.account.value ?: return
    val client = TeamClient(account.token)
    val teams = when (val result = client.listTeams()) {
        is TeamApiResult.Success -> result.value
        else -> return
    }
    val alreadyNotified = teamSettings.notifiedKeys()
    val newlyNotified = mutableSetOf<String>()
    for (team in teams) {
        val todos = when (val result = client.listTodos(team.id)) {
            is TeamApiResult.Success -> result.value
            else -> continue
        }
        val assignments = todos.map { TeamTodoAssignment(it.id, it.title, it.assignedTo, it.done) }
        val newlyAssigned = newlyAssignedTodos(assignments, account.id, alreadyNotified + newlyNotified)
        newlyAssigned.forEach { todo ->
            notify(todo)
            newlyNotified += assignmentKey(todo.id, account.id)
        }
    }
    teamSettings.addNotifiedKeys(newlyNotified)
}

class TeamPollWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val graph = (applicationContext as HabitFlowApplication).graph
        pollTeamTodosForNewAssignments(graph.accountSettings, graph.teamSettings) { todo ->
            TeamNotifier.notifyAssigned(applicationContext, todo)
        }
        // Senyap-dan-coba-lagi: gagal jaringan/server bukan masalah besar, siklus berikutnya mencoba lagi.
        return Result.success()
    }
}
