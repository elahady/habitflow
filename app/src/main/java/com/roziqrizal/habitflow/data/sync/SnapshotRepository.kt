package com.roziqrizal.habitflow.data.sync

import androidx.room.withTransaction
import com.roziqrizal.habitflow.data.HabitDatabase

/** Ekspor seluruh data ke [Snapshot] dan pulihkan dari [Snapshot]. Keduanya dalam satu transaksi. */
class SnapshotRepository(private val db: HabitDatabase) {

    private val dao = db.syncDao()

    /** Membaca semua tabel dalam satu transaksi supaya hasilnya konsisten. [settings] datang dari penyimpanan pengaturan. */
    suspend fun export(settings: SnapshotSettings, deviceId: String, appVersion: String, nowMillis: Long): Snapshot =
        db.withTransaction {
            val habitRows = dao.habits()
            val blockRows = dao.scheduleBlocks()
            val eventRows = dao.events()
            Snapshot(
                createdAt = nowMillis,
                deviceId = deviceId,
                appVersion = appVersion,
                habits = habitRows,
                habitEntries = dao.habitEntries(),
                todos = dao.todos(),
                scheduleBlocks = blockRows,
                scheduleBlockHabits = consistentLinks(dao.scheduleBlockHabits(), habitRows, blockRows),
                daysOff = dao.daysOff(),
                followUps = dao.followUps(),
                workDays = dao.workDays(),
                drinkCounts = dao.drinkCounts(),
                weightEntries = dao.weightEntries(),
                bloodPressureEntries = dao.bloodPressureEntries(),
                events = eventRows,
                eventExceptions = consistentExceptions(dao.eventExceptions(), eventRows),
                holidayCancellations = dao.holidayCancellations(),
                meals = dao.meals(),
                habitManualMarks = dao.habitManualMarks(),
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
        dao.clearDrinkCounts()
        dao.clearWeightEntries()
        dao.clearBloodPressureEntries()
        dao.clearEventExceptions()
        dao.clearEvents()
        dao.clearHolidayCancellations()
        dao.clearMeals()
        dao.clearHabitManualMarks()
        dao.clearHabitEntries()
        dao.clearTodos()
        dao.clearHabits()

        dao.insertHabits(snapshot.habits)
        dao.insertTodos(snapshot.todos)
        dao.insertHabitEntries(snapshot.habitEntries)
        dao.insertScheduleBlocks(snapshot.scheduleBlocks)
        dao.insertScheduleBlockHabits(consistentLinks(snapshot.scheduleBlockHabits, snapshot.habits, snapshot.scheduleBlocks))
        dao.insertDaysOff(snapshot.daysOff)
        dao.insertFollowUps(snapshot.followUps)
        dao.insertWorkDays(snapshot.workDays)
        dao.insertDrinkCounts(snapshot.drinkCounts)
        dao.insertWeightEntries(snapshot.weightEntries)
        dao.insertBloodPressureEntries(snapshot.bloodPressureEntries)
        dao.insertEvents(snapshot.events)
        dao.insertEventExceptions(consistentExceptions(snapshot.eventExceptions, snapshot.events))
        dao.insertHolidayCancellations(snapshot.holidayCancellations)
        dao.insertMeals(snapshot.meals)
        dao.insertHabitManualMarks(snapshot.habitManualMarks)
    }
}
