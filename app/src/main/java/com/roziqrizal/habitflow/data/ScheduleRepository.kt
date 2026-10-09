package com.roziqrizal.habitflow.data

import androidx.room.withTransaction
import com.roziqrizal.habitflow.domain.calendar.HolidayCalendar
import com.roziqrizal.habitflow.domain.schedule.ScheduleBlock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/** Pintu ke tabel jadwal. Blok dikembalikan sebagai model domain lengkap dengan tautan habitnya. */
class ScheduleRepository(
    private val db: HabitDatabase,
    /** Libur nasional dan cuti bersama (tahap 22). Kosong berarti tidak ada libur otomatis. */
    val holidays: HolidayCalendar = HolidayCalendar.EMPTY,
) {

    private val schedule = db.scheduleDao()
    private val entries = db.habitEntryDao()

    fun observeBlocks(): Flow<List<ScheduleBlock>> =
        combine(schedule.observeBlocks(), schedule.observeLinks()) { blocks, links ->
            val byBlock = links.groupBy({ it.blockId }, { it.habitId })
            blocks.map { it.toDomain(byBlock[it.id].orEmpty().toSet()) }
        }

    /** Hanya libur manual ("Hari ini libur"). */
    fun observeManualDaysOff(): Flow<Set<LocalDate>> =
        schedule.observeDaysOff().map { dates -> dates.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.toSet() }

    /** Tanggal libur nasional yang dibatalkan. */
    fun observeHolidayCancellations(): Flow<Set<LocalDate>> =
        schedule.observeHolidayCancellations().map { dates ->
            dates.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.toSet()
        }

    /** Semua tanggal libur efektif: libur manual digabung libur nasional yang tidak dibatalkan (tahap 22). */
    fun observeDaysOff(): Flow<Set<LocalDate>> =
        combine(observeManualDaysOff(), observeHolidayCancellations()) { manual, cancelled ->
            manual + holidays.all.map { it.date }.filter { it !in cancelled }
        }

    /** Untuk penerima alarm dan notifikasi, yang tidak memakai Flow. */
    suspend fun getBlocks(): List<ScheduleBlock> {
        val byBlock = schedule.getLinks().groupBy({ it.blockId }, { it.habitId })
        return schedule.getBlocks().map { it.toDomain(byBlock[it.id].orEmpty().toSet()) }
    }

    /** Untuk penerima alarm dan notifikasi, yang tidak memakai Flow. */
    suspend fun getManualDaysOff(): Set<LocalDate> =
        schedule.getDaysOff().mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.toSet()

    suspend fun getHolidayCancellations(): Set<LocalDate> =
        schedule.getHolidayCancellations().mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.toSet()

    /** Libur efektif: manual, atau libur nasional yang tidak dibatalkan. */
    suspend fun isDayOff(date: LocalDate): Boolean =
        schedule.countDayOff(date.toString()) > 0 ||
            (holidays.on(date) != null && schedule.countHolidayCancellation(date.toString()) == 0)

    /** Menyalakan atau mematikan libur manual. Tidak menyentuh libur nasional. */
    suspend fun setDayOff(date: LocalDate, off: Boolean) {
        if (off) schedule.insertDayOff(DayOff(date.toString())) else schedule.deleteDayOff(date.toString())
    }

    /** Membatalkan (atau mengaktifkan lagi) libur nasional untuk satu tanggal. */
    suspend fun setHolidayCancelled(date: LocalDate, cancelled: Boolean) {
        if (cancelled) {
            schedule.insertHolidayCancellation(HolidayCancellation(date.toString()))
        } else {
            schedule.deleteHolidayCancellation(date.toString())
        }
    }

    /** Simpan blok baru (id 0) atau ubah yang ada, beserta tautan habitnya. Mengembalikan id blok. */
    suspend fun saveBlock(block: ScheduleBlock): Long = db.withTransaction {
        val id = if (block.id == 0L) {
            schedule.insertBlock(block.copy(sortOrder = schedule.nextSortOrder()).toEntity())
        } else {
            schedule.updateBlock(block.toEntity())
            block.id
        }
        schedule.clearLinks(id)
        schedule.insertLinks(block.habitIds.map { ScheduleBlockHabit(id, it) })
        id
    }

    suspend fun deleteBlock(blockId: Long) {
        schedule.deleteBlock(blockId)
    }

    /** Habit yang ditautkan ke blok dicentang untuk [date] (tombol "Sudah" di notifikasi). Tidak membatalkan centang. */
    suspend fun markDone(blockId: Long, date: LocalDate) {
        schedule.habitIdsOf(blockId)
            .forEach { entries.insert(HabitEntry(it, date.toString())) }
    }
}
