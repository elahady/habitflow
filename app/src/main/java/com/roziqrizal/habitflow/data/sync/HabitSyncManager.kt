package com.roziqrizal.habitflow.data.sync

import com.roziqrizal.habitflow.data.Habit
import com.roziqrizal.habitflow.data.HabitDatabase
import com.roziqrizal.habitflow.data.HabitEntry
import com.roziqrizal.habitflow.data.PendingDeleteEntity
import com.roziqrizal.habitflow.data.Todo
import com.roziqrizal.habitflow.data.account.AccountSettings
import com.roziqrizal.habitflow.domain.sync.EntryMergeAction
import com.roziqrizal.habitflow.domain.sync.entryMergeAction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

/** Hasil satu putaran push atau pull, untuk ditampilkan di UI (tahap 28 langkah 5). Di memori saja. */
sealed interface HabitSyncStatus {
    data object Idle : HabitSyncStatus
    data object Syncing : HabitSyncStatus
    data class Success(val at: Long, val hadConflict: Boolean) : HabitSyncStatus
    data class Failed(val message: String) : HabitSyncStatus
}

/** Hasil satu ronde push field-update: [error] null berarti lanjut, [conflicted] dicatat untuk status UI. */
private class UpdateOutcome(val error: HabitApiResult<Unit>?, val conflicted: Boolean)

/**
 * Push dan pull habit/todo ke server (tahap 28 langkah 5). Room tetap sumber data utama untuk
 * baca/tulis sehari-hari - manajer ini hanya menyamakan dengan server di latar belakang.
 * Satu operasi dalam satu waktu ([mutex]), pola sama dengan [SyncManager].
 */
class HabitSyncManager(
    private val db: HabitDatabase,
    private val accountSettings: AccountSettings,
    private val clientFor: (token: String) -> HabitSyncClient = { HabitSyncClient(it) },
) {
    private val mutex = Mutex()
    private val habits = db.habitDao()
    private val entries = db.habitEntryDao()
    private val todos = db.todoDao()
    private val outbox = db.habitSyncOutboxDao()

    private val _status = MutableStateFlow<HabitSyncStatus>(HabitSyncStatus.Idle)
    val status: StateFlow<HabitSyncStatus> = _status.asStateFlow()

    private fun client(): HabitSyncClient? = accountSettings.account.value?.token?.let(clientFor)

    /** Kirim semua perubahan lokal tertunda. Berhenti di kegagalan jaringan/server pertama (seperti [SyncManager]). */
    suspend fun push(): HabitApiResult<Unit> = mutex.withLock { pushLocked() }

    /** Tarik habit dan to-do hari ini dari server. Tidak menimpa baris lokal yang masih dirty. */
    suspend fun pull(): HabitApiResult<Unit> = mutex.withLock { pullLocked() }

    /** Push lalu pull, dipakai tombol "Sinkron sekarang" dan worker periodik. */
    suspend fun syncNow(): HabitApiResult<Unit> = mutex.withLock {
        val pushed = pushLocked()
        if (pushed !is HabitApiResult.Success) return@withLock pushed
        pullLocked()
    }

    private suspend fun pushLocked(): HabitApiResult<Unit> {
        val client = client() ?: return HabitApiResult.Unauthorized
        _status.value = HabitSyncStatus.Syncing

        pushHabitDeletes(client)?.let { return fail(it) }
        pushHabitCreates(client)?.let { return fail(it) }
        val habitUpdates = pushHabitUpdates(client)
        habitUpdates.error?.let { return fail(it) }

        pushTodoDeletes(client)?.let { return fail(it) }
        pushTodoCreates(client)?.let { return fail(it) }
        val todoUpdates = pushTodoUpdates(client)
        todoUpdates.error?.let { return fail(it) }

        pushEntryOutbox(client)?.let { return fail(it) }

        _status.value = HabitSyncStatus.Success(System.currentTimeMillis(), habitUpdates.conflicted || todoUpdates.conflicted)
        return HabitApiResult.Success(Unit)
    }

    private fun fail(result: HabitApiResult<Unit>): HabitApiResult<Unit> {
        _status.value = HabitSyncStatus.Failed(result.messageOrEmpty())
        return result
    }

    private suspend fun pushHabitDeletes(client: HabitSyncClient): HabitApiResult<Unit>? {
        outbox.pendingDeletes(PendingDeleteEntity.HABIT).forEach { pending ->
            val result = client.deleteHabit(pending.remoteId)
            if (result.isSuccessOrAlreadyGone()) {
                outbox.deletePendingDelete(pending.id)
            } else {
                return result
            }
        }
        return null
    }

    private suspend fun pushTodoDeletes(client: HabitSyncClient): HabitApiResult<Unit>? {
        outbox.pendingDeletes(PendingDeleteEntity.TODO).forEach { pending ->
            val result = client.deleteTodo(pending.remoteId)
            if (result.isSuccessOrAlreadyGone()) {
                outbox.deletePendingDelete(pending.id)
            } else {
                return result
            }
        }
        return null
    }

    private suspend fun pushHabitCreates(client: HabitSyncClient): HabitApiResult<Unit>? {
        habits.newForSync().forEach { habit ->
            val result = client.createHabit(habit.name, habit.isMandatory)
            if (result is HabitApiResult.Success) {
                habits.markSynced(habit.id, result.value.id, result.value.updatedAt)
            } else {
                return result.asUnit()
            }
        }
        return null
    }

    private suspend fun pushTodoCreates(client: HabitSyncClient): HabitApiResult<Unit>? {
        todos.newForSync().forEach { todo ->
            val result = client.createTodo(todo.title, todo.date)
            when (result) {
                is HabitApiResult.Success -> todos.markSynced(todo.id, result.value.id, result.value.updatedAt)
                is HabitApiResult.Rejected -> Unit // sudah 5 to-do hari itu di server - ditinggalkan dirty, dicoba lagi nanti
                else -> return result.asUnit()
            }
        }
        return null
    }

    private suspend fun pushHabitUpdates(client: HabitSyncClient): UpdateOutcome {
        var conflicted = false
        habits.dirtyForSync().forEach { habit ->
            val remoteId = habit.remoteId ?: return@forEach
            val result = client.updateHabit(remoteId, habit.name, habit.isMandatory, habit.sortOrder, habit.remoteUpdatedAt)
            when (result) {
                is HabitApiResult.Success -> habits.applyRemote(
                    habit.id, result.value.name, result.value.isMandatory, result.value.sortOrder, remoteId, result.value.updatedAt,
                )
                is HabitApiResult.Conflict -> {
                    // Server menang (keputusan tahap 28 langkah 5): versi server ditimpa ke lokal, edit lokal yang bentrok hilang.
                    habits.applyRemote(
                        habit.id, result.server.name, result.server.isMandatory, result.server.sortOrder, remoteId, result.server.updatedAt,
                    )
                    conflicted = true
                }
                else -> return UpdateOutcome(result.asUnit(), conflicted)
            }
        }
        return UpdateOutcome(null, conflicted)
    }

    private suspend fun pushTodoUpdates(client: HabitSyncClient): UpdateOutcome {
        var conflicted = false
        todos.dirtyForSync().forEach { todo ->
            val remoteId = todo.remoteId ?: return@forEach
            val result = client.updateTodo(remoteId, todo.title, todo.date, todo.done, todo.remoteUpdatedAt)
            when (result) {
                is HabitApiResult.Success ->
                    todos.applyRemote(todo.id, result.value.title, result.value.date, result.value.done, remoteId, result.value.updatedAt)
                is HabitApiResult.Conflict -> {
                    todos.applyRemote(todo.id, result.server.title, result.server.date, result.server.done, remoteId, result.server.updatedAt)
                    conflicted = true
                }
                else -> return UpdateOutcome(result.asUnit(), conflicted)
            }
        }
        return UpdateOutcome(null, conflicted)
    }

    private suspend fun pushEntryOutbox(client: HabitSyncClient): HabitApiResult<Unit>? {
        outbox.entryOutbox().forEach { pending ->
            val remoteHabitId = habits.getByIdForSync(pending.habitId)?.remoteId ?: return@forEach // habit belum tersinkron, coba lagi nanti
            val result = client.toggleEntry(remoteHabitId, pending.date)
            if (result is HabitApiResult.Success) {
                outbox.deleteEntryOutbox(pending.id)
            } else {
                return result.asUnit()
            }
        }
        return null
    }

    private suspend fun pullLocked(): HabitApiResult<Unit> {
        val client = client() ?: return HabitApiResult.Unauthorized
        _status.value = HabitSyncStatus.Syncing
        val today = LocalDate.now().toString()

        val habitsResult = client.listHabits(today)
        if (habitsResult !is HabitApiResult.Success) return fail(habitsResult.asUnit())

        val todosResult = client.listTodos(today)
        if (todosResult !is HabitApiResult.Success) return fail(todosResult.asUnit())

        mergeHabits(habitsResult.value, today)
        mergeTodos(todosResult.value)

        _status.value = HabitSyncStatus.Success(System.currentTimeMillis(), hadConflict = false)
        return HabitApiResult.Success(Unit)
    }

    private suspend fun mergeHabits(remoteHabits: List<RemoteHabit>, today: String) {
        remoteHabits.forEach { remote ->
            val local = habits.byRemoteId(remote.id)
            val habitId = if (local == null) {
                habits.insert(
                    Habit(
                        name = remote.name, createdAt = today, sortOrder = remote.sortOrder, isMandatory = remote.isMandatory,
                        remoteId = remote.id, remoteUpdatedAt = remote.updatedAt, dirty = false,
                    ),
                )
            } else {
                if (!local.dirty) {
                    habits.applyRemote(local.id, remote.name, remote.isMandatory, remote.sortOrder, remote.id, remote.updatedAt)
                }
                local.id
            }

            val hasLocalEntry = entries.count(habitId, today) > 0
            val hasPendingOutbox = outbox.hasPendingEntry(habitId, today) > 0
            when (entryMergeAction(remote.done, hasLocalEntry, hasPendingOutbox)) {
                EntryMergeAction.Insert -> entries.insert(HabitEntry(habitId, today))
                EntryMergeAction.Delete -> entries.delete(habitId, today)
                EntryMergeAction.None -> Unit
            }
        }
    }

    private suspend fun mergeTodos(remoteTodos: List<RemoteTodo>) {
        remoteTodos.forEach { remote ->
            val local = todos.byRemoteId(remote.id)
            if (local == null) {
                todos.insert(
                    Todo(
                        title = remote.title, date = remote.date, done = remote.done, createdAt = System.currentTimeMillis(),
                        remoteId = remote.id, remoteUpdatedAt = remote.updatedAt, dirty = false,
                    ),
                )
            } else if (!local.dirty) {
                todos.applyRemote(local.id, remote.title, remote.date, remote.done, remote.id, remote.updatedAt)
            }
        }
    }
}

private fun HabitApiResult<*>.isSuccessOrAlreadyGone(): Boolean =
    this is HabitApiResult.Success || this is HabitApiResult.NotFound || this is HabitApiResult.Rejected

/**
 * Membuang nilai sukses (tidak dipakai pemanggil) sambil mempertahankan jenis kegagalan. [Conflict]
 * tidak pernah terjadi di sini. Lima cabang gagal selalu bertipe `HabitApiResult<Nothing>`, jadi
 * cast ke `HabitApiResult<Unit>` di sini selalu aman lewat kovarian - hanya Kotlin tidak menyimpulkannya sendiri.
 */
private fun <T> HabitApiResult<T>.asUnit(): HabitApiResult<Unit> = when (this) {
    is HabitApiResult.Success -> HabitApiResult.Success(Unit)
    is HabitApiResult.Conflict -> HabitApiResult.Success(Unit)
    is HabitApiResult.Rejected -> this as HabitApiResult<Unit>
    HabitApiResult.Unauthorized -> this as HabitApiResult<Unit>
    HabitApiResult.NotFound -> this as HabitApiResult<Unit>
    is HabitApiResult.ServerError -> this as HabitApiResult<Unit>
    is HabitApiResult.NetworkError -> this as HabitApiResult<Unit>
}

private fun HabitApiResult<*>.messageOrEmpty(): String = when (this) {
    is HabitApiResult.Rejected -> detail
    HabitApiResult.Unauthorized -> "Token ditolak server."
    HabitApiResult.NotFound -> "Tidak ditemukan di server."
    is HabitApiResult.ServerError -> "Server bermasalah (kode $code)."
    is HabitApiResult.NetworkError -> "Tidak bisa menjangkau server: $detail"
    else -> ""
}
