package com.roziqrizal.habitflow.data.calendar

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.roziqrizal.habitflow.domain.calendar.EventLabel
import com.roziqrizal.habitflow.domain.calendar.EventOccurrence
import com.roziqrizal.habitflow.domain.calendar.phoneInstanceToOccurrences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.time.LocalDate
import java.time.ZoneId

/** Satu kalender di HP, untuk daftar pilihan di Tentang. */
data class PhoneCalendarInfo(val id: Long, val name: String, val account: String)

/**
 * Membaca acara dari kalender HP lewat `CalendarContract.Instances` (pengulangan sudah dijabarkan oleh sistem), hanya baca
 * (tahap 22). Tanpa izin `READ_CALENDAR` semua fungsi mengembalikan kosong dan app tetap jalan.
 */
class PhoneCalendarSource(context: Context) {

    private val appContext = context.applicationContext

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** Kalender yang terlihat di HP, urut menurut akun lalu nama. */
    fun calendars(): List<PhoneCalendarInfo> {
        if (!hasPermission()) return emptyList()
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
        )
        return runCatching {
            appContext.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, projection, null, null, null)?.use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(PhoneCalendarInfo(cursor.getLong(0), cursor.getString(1).orEmpty(), cursor.getString(2).orEmpty()))
                    }
                }
            }.orEmpty().sortedWith(compareBy({ it.account }, { it.name }))
        }.getOrDefault(emptyList())
    }

    /**
     * Kejadian di [from] sampai [to] (keduanya ikut) dari kalender di [selection]. Acara berjam masuk di tanggal mulainya;
     * acara sepanjang hari dipecah satu per tanggal. Acara yang dibatalkan tidak ikut.
     */
    fun occurrencesBetween(
        from: LocalDate,
        to: LocalDate,
        selection: Map<Long, EventLabel>,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<EventOccurrence> {
        if (selection.isEmpty() || !hasPermission() || to.isBefore(from)) return emptyList()
        val names = calendars().associate { it.id to it.name }
        val begin = from.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = to.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, begin)
            ContentUris.appendId(it, end)
        }.build()
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.CALENDAR_ID,
            CalendarContract.Instances.STATUS,
        )
        val ids = selection.keys.joinToString(",")
        return runCatching {
            appContext.contentResolver.query(
                uri, projection, "${CalendarContract.Instances.CALENDAR_ID} IN ($ids)", null,
                "${CalendarContract.Instances.BEGIN} ASC",
            )?.use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        if (cursor.getInt(6) == CalendarContract.Events.STATUS_CANCELED) continue
                        val calendarId = cursor.getLong(5)
                        val label = selection[calendarId] ?: continue
                        addAll(
                            phoneInstanceToOccurrences(
                                eventId = cursor.getLong(0),
                                title = cursor.getString(1).orEmpty().ifBlank { "(Tanpa judul)" },
                                beginMillis = cursor.getLong(2),
                                endMillis = cursor.getLong(3),
                                allDay = cursor.getInt(4) == 1,
                                label = label,
                                calendarName = names[calendarId] ?: "Kalender HP",
                                from = from,
                                to = to,
                                zone = zone,
                            ),
                        )
                    }
                }
            }.orEmpty()
        }.getOrDefault(emptyList()).sortedWith(compareBy({ it.date }, { it.startMinute ?: -1 }, { it.title }))
    }

    /** Memberi sinyal setiap kali isi kalender HP berubah, dan satu kali di awal. */
    fun changes(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        if (hasPermission()) {
            runCatching { appContext.contentResolver.registerContentObserver(CalendarContract.CONTENT_URI, true, observer) }
        }
        trySend(Unit)
        awaitClose { appContext.contentResolver.unregisterContentObserver(observer) }
    }
}
