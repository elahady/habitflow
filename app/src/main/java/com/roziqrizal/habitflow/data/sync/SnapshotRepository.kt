package com.roziqrizal.habitflow.data.sync

import androidx.room.withTransaction
import com.roziqrizal.habitflow.data.HabitDatabase

/** Ekspor seluruh data ke [Snapshot] dan pulihkan dari [Snapshot]. Keduanya dalam satu transaksi. */
class SnapshotRepository(private val db: HabitDatabase) {

    private val dao = db.syncDao()

    /** Membaca semua tabel dalam satu transaksi supaya hasilnya konsisten. [settings] datang dari penyimpanan pengaturan. */
    suspend fun export(settings: SnapshotSettings, deviceId: String, appVersion: String, nowMillis: Long): Snapshot =
        db.withTransaction {
            Snapshot(
                createdAt = nowMillis,
                deviceId = deviceId,
                appVersion = appVersion,
                habits = dao.habits(),
                habitEntries = dao.habitEntries(),
                todos = dao.todos(),
                scheduleBlocks = dao.scheduleBlocks(),
                scheduleBlockHabits = dao.scheduleBlockHabits(),
                daysOff = dao.daysOff(),
                followUps = dao.followUps(),
                workDays = dao.workDays(),
                settings = settings,
            )
        }

    /**
     * Mengganti semua tabel dengan isi [snapshot]. Gagal di tengah (misalnya tautan blok ke habit yang tidak ada)
     * membatalkan seluruh transaksi, jadi data di HP tidak pernah setengah terganti. Pengaturan ([Snapshot.settings])
     * diterapkan terpisah oleh pemanggil setelah ini berhasil.
     */
    suspend fun restore(snapshot: Snapshot) = db.withTransaction {
        dao.clearScheduleBlockHabits()
        dao.clearScheduleBlocks()
        dao.clearDaysOff()
        dao.clearFollowUps()
        dao.clearWorkDays()
        dao.clearHabitEntries()
        dao.clearTodos()
        dao.clearHabits()

        dao.insertHabits(snapshot.habits)
        dao.insertTodos(snapshot.todos)
        dao.insertHabitEntries(snapshot.habitEntries)
        dao.insertScheduleBlocks(snapshot.scheduleBlocks)
        dao.insertScheduleBlockHabits(snapshot.scheduleBlockHabits)
        dao.insertDaysOff(snapshot.daysOff)
        dao.insertFollowUps(snapshot.followUps)
        dao.insertWorkDays(snapshot.workDays)
    }
}
