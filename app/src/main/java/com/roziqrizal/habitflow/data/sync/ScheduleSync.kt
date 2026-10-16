package com.roziqrizal.habitflow.data.sync

import com.roziqrizal.habitflow.data.LocationSettings
import com.roziqrizal.habitflow.data.ScheduleBlockEntity
import com.roziqrizal.habitflow.data.ScheduleBlockHabit
import com.roziqrizal.habitflow.domain.prayer.PrayerName
import com.roziqrizal.habitflow.domain.prayer.PrayerTimes
import com.roziqrizal.habitflow.domain.schedule.Days
import com.roziqrizal.habitflow.domain.schedule.NotificationLevel
import com.roziqrizal.habitflow.domain.schedule.WorkAction
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/*
 * Kirim jadwal harian dan waktu sholat ke server (tahap 29 langkah 4). Satu arah: HP satu-satunya yang mengubah blok
 * (web hanya membaca), jadi tidak ada antrean, id server, atau deteksi konflik. `PUT /api/v1/schedule-blocks` mengganti
 * seluruh jadwal, `PUT /api/v1/prayer-days` menimpa waktu sholat per tanggal. Server tidak pernah menghitung hisab; ia
 * memakai waktu yang sudah dihitung mesin Ephemeris di HP. Setiap kiriman memuat keadaan terbaru, jadi aman diulang.
 */

/** Waktu sholat dikirim untuk hari ini dan enam hari berikutnya, supaya web tetap benar kalau HP beberapa hari tidak sinkron. */
const val SCHEDULE_SYNC_PRAYER_DAYS = 7

/** Batas blok per kiriman di server. */
const val SCHEDULE_SYNC_MAX_BLOCKS = 100

private val HOUR_MINUTE: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/**
 * Satu blok seperti yang diminta server. [habitIds] adalah id habit **di server**: habit yang belum pernah terkirim
 * (belum punya id server) tidak ikut, dan tautannya terkirim begitu habitnya tersinkron, karena isi kiriman berubah.
 */
data class ScheduleSyncBlock(
    val name: String,
    val startType: Int,
    val startValue: Int,
    val prayer: String?,
    val durationMinutes: Int,
    val endMinuteOfDay: Int?,
    val activeDays: Int,
    val level: String,
    val sortOrder: Int,
    val workAction: String?,
    val workReminders: Boolean,
    val habitIds: List<Long>,
)

data class ScheduleSyncPayload(val blocks: List<ScheduleSyncBlock>) {
    fun toJson(): JSONObject = JSONObject().put(
        "blocks",
        JSONArray().apply {
            blocks.forEach { b ->
                put(
                    JSONObject()
                        .put("name", b.name)
                        .put("start_type", b.startType)
                        .put("start_value", b.startValue)
                        .put("prayer", b.prayer ?: JSONObject.NULL)
                        .put("duration_minutes", b.durationMinutes)
                        .put("end_minute_of_day", b.endMinuteOfDay ?: JSONObject.NULL)
                        .put("active_days", b.activeDays)
                        .put("level", b.level)
                        .put("sort_order", b.sortOrder)
                        .put("work_action", b.workAction ?: JSONObject.NULL)
                        .put("work_reminders", b.workReminders)
                        .put("habit_ids", JSONArray(b.habitIds)),
                )
            }
        },
    )

    /** Dua kiriman dengan isi sama menghasilkan teks sama, dipakai untuk melewati kiriman yang tidak ada gunanya. */
    val fingerprint: String get() = toJson().toString()
}

/**
 * Menyusun kiriman dari tabel lokal. Nilai dipaksa masuk ke batas server (nama tidak kosong, jam tetap 0-1439, hari aktif
 * 1-127) supaya satu baris yang ganjil tidak membuat seluruh jadwal ditolak. Blok tanpa hari aktif tidak pernah tampil di
 * HP maupun di web, jadi tidak dikirim. Blok berpatokan sholat tanpa nama sholat dibaca sebagai Subuh, sama dengan
 * `ScheduleBlockEntity.toDomain`.
 *
 * [habitRemoteIds] memetakan id habit lokal ke id habit di server.
 */
fun scheduleSyncPayload(
    blocks: List<ScheduleBlockEntity>,
    links: List<ScheduleBlockHabit>,
    habitRemoteIds: Map<Long, Long>,
): ScheduleSyncPayload {
    val linksByBlock = links.groupBy({ it.blockId }, { it.habitId })
    val validPrayers = PrayerName.entries.map { it.name }.toSet()
    val validLevels = NotificationLevel.entries.map { it.name }.toSet()
    val validActions = WorkAction.entries.map { it.name }.toSet()

    val sent = blocks
        .sortedWith(compareBy({ it.sortOrder }, { it.id }))
        .filter { it.activeDays and Days.ALL != 0 }
        .take(SCHEDULE_SYNC_MAX_BLOCKS)
        .map { b ->
            val byPrayer = b.startType == ScheduleBlockEntity.START_PRAYER
            ScheduleSyncBlock(
                name = b.name.trim().ifEmpty { "Tanpa nama" }.take(255),
                startType = if (byPrayer) ScheduleBlockEntity.START_PRAYER else ScheduleBlockEntity.START_FIXED,
                startValue = if (byPrayer) b.startValue.coerceIn(-1440, 1440) else b.startValue.coerceIn(0, 1439),
                prayer = if (byPrayer) (b.prayer?.takeIf { it in validPrayers } ?: PrayerName.SUBUH.name) else null,
                durationMinutes = b.durationMinutes.coerceIn(0, 1440),
                endMinuteOfDay = b.endMinuteOfDay?.coerceIn(0, 1439),
                activeDays = b.activeDays and Days.ALL,
                level = b.level.takeIf { it in validLevels } ?: NotificationLevel.INFO.name,
                sortOrder = b.sortOrder.coerceAtLeast(0),
                workAction = b.workAction?.takeIf { it in validActions },
                workReminders = b.workReminders,
                habitIds = linksByBlock[b.id].orEmpty().mapNotNull { habitRemoteIds[it] }.distinct().sorted(),
            )
        }
    return ScheduleSyncPayload(sent)
}


/** Waktu sholat beberapa tanggal, sudah dihitung di HP. Waktu null berarti tidak ada (lintang kutub). */
data class PrayerDaysPayload(val timezone: String, val days: List<Pair<LocalDate, PrayerTimes>>) {
    fun toJson(): JSONObject = JSONObject()
        .put("timezone", timezone)
        .put(
            "days",
            JSONArray().apply {
                days.forEach { (date, times) ->
                    put(
                        JSONObject()
                            .put("date", date.toString())
                            .put("subuh", times.subuh.hourMinute())
                            .put("dzuhur", times.dzuhur.hourMinute())
                            .put("ashar", times.ashar.hourMinute())
                            .put("maghrib", times.maghrib.hourMinute())
                            .put("isya", times.isya.hourMinute()),
                    )
                }
            },
        )

    val fingerprint: String get() = toJson().toString()
}

private fun LocalTime?.hourMinute(): Any = this?.format(HOUR_MINUTE) ?: JSONObject.NULL

/**
 * Menghitung waktu sholat [SCHEDULE_SYNC_PRAYER_DAYS] hari mulai [from]. [calculate] dipisah dari mesinnya supaya bisa diuji
 * tanpa hisab sungguhan.
 */
fun prayerDaysPayload(
    timezone: String,
    from: LocalDate,
    calculate: (LocalDate) -> PrayerTimes,
): PrayerDaysPayload = PrayerDaysPayload(
    timezone,
    (0 until SCHEDULE_SYNC_PRAYER_DAYS).map { offset ->
        val date = from.plusDays(offset.toLong())
        date to calculate(date)
    },
)

/** Yang dibutuhkan manajer sinkron untuk mengirim waktu sholat: lokasi yang dipakai kartu Sekarang/Berikutnya di HP. */
class ScheduleSyncInputs(val location: LocationSettings)
