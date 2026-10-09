package com.roziqrizal.habitflow.data

import com.roziqrizal.habitflow.domain.calendar.CalendarEvent
import com.roziqrizal.habitflow.domain.calendar.EventException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/** Pintu ke acara HabitFlow dan pengecualian kejadiannya (tahap 22). Aturan pengulangan ada di `domain/calendar`. */
class EventRepository(private val db: HabitDatabase) {

    private val events = db.eventDao()

    fun observeEvents(): Flow<List<CalendarEvent>> =
        events.observeEvents().map { list -> list.mapNotNull { it.toDomain() } }

    fun observeExceptions(): Flow<List<EventException>> =
        events.observeExceptions().map { list -> list.mapNotNull { it.toDomain() } }

    /** Untuk penerima alarm dan notifikasi, yang tidak memakai Flow. */
    suspend fun getEvents(): List<CalendarEvent> = events.getEvents().mapNotNull { it.toDomain() }

    suspend fun getExceptions(): List<EventException> = events.getExceptions().mapNotNull { it.toDomain() }

    /** Simpan acara baru (id 0) atau ubah yang ada. Mengembalikan id. Judul kosong ditolak oleh pemanggil. */
    suspend fun save(event: CalendarEvent): Long =
        if (event.id == 0L) {
            events.insert(event.toEntity())
        } else {
            events.update(event.toEntity())
            event.id
        }

    /** Menghapus acara beserta semua pengecualiannya (cascade). */
    suspend fun delete(id: Long) {
        events.delete(id)
    }

    /** Melewati satu kejadian. */
    suspend fun skip(eventId: Long, originalDate: LocalDate) {
        events.upsertException(EventException(eventId, originalDate, skipped = true).toEntity())
    }

    /** Mengubah satu kejadian (menggantikan pengecualian sebelumnya untuk tanggal itu). */
    suspend fun change(exception: EventException) {
        events.upsertException(exception.toEntity())
    }

    /** Mengembalikan satu kejadian ke nilai acaranya (membuang pengecualian). */
    suspend fun restore(eventId: Long, originalDate: LocalDate) {
        events.deleteException(eventId, originalDate.toString())
    }
}
