package com.roziqrizal.habitflow.data.sync

import com.roziqrizal.habitflow.data.DayOff
import com.roziqrizal.habitflow.data.DrinkCount
import com.roziqrizal.habitflow.data.FollowUpEntity
import com.roziqrizal.habitflow.data.Habit
import com.roziqrizal.habitflow.data.HabitEntry
import com.roziqrizal.habitflow.data.ScheduleBlockEntity
import com.roziqrizal.habitflow.data.ScheduleBlockHabit
import com.roziqrizal.habitflow.data.Todo
import com.roziqrizal.habitflow.data.WorkDayEntity
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Pengaturan yang ikut disnapshot. Nada alarm dan tanggal alarm yang dimatikan sekali sengaja tidak ikut. */
data class SnapshotSettings(
    val locationName: String,
    val latitude: Double,
    val longitude: Double,
    val persistentNotification: Boolean,
    /** Nama `PrayerName` ke status pengingat adzan. */
    val adzan: Map<String, Boolean>,
    /** Nama `ThemeMode`. */
    val themeMode: String,
    /** Pengingat kerja tahap 20. Snapshot lama tanpa bidang ini dibaca sebagai nyala. */
    val waterReminders: Boolean = true,
    val breakReminders: Boolean = true,
)

/** Seluruh data HabitFlow pada satu waktu. Format JSON-nya ada di docs/concept.md tahap 19B. */
data class Snapshot(
    val createdAt: Long,
    val deviceId: String,
    val appVersion: String,
    val habits: List<Habit>,
    val habitEntries: List<HabitEntry>,
    val todos: List<Todo>,
    val scheduleBlocks: List<ScheduleBlockEntity>,
    val scheduleBlockHabits: List<ScheduleBlockHabit>,
    val daysOff: List<DayOff>,
    val followUps: List<FollowUpEntity>,
    val workDays: List<WorkDayEntity>,
    /** Penghitung minuman (tahap 20). Snapshot lama tanpa bidang ini dibaca sebagai kosong. */
    val drinkCounts: List<DrinkCount> = emptyList(),
    val settings: SnapshotSettings,
)

/** Snapshot rusak, bukan JSON, atau dibuat oleh versi app yang lebih baru. [message] aman ditampilkan ke pengguna. */
class SnapshotFormatException(message: String) : Exception(message)

/**
 * Mengubah [Snapshot] dari dan ke JSON (`schemaVersion` 1). Nilai null ditulis sebagai JSON null. Bidang yang
 * ditambah sesudah tahap 19B (tahap 20: `drinkCounts`, `autoSource`, `workReminders`) opsional saat dibaca, jadi
 * snapshot lama tetap bisa dipulihkan tanpa menaikkan `schemaVersion`.
 */
object SnapshotCodec {

    const val SCHEMA_VERSION = 1

    fun encode(s: Snapshot): String = JSONObject().apply {
        put("schemaVersion", SCHEMA_VERSION)
        put("createdAt", s.createdAt)
        put("deviceId", s.deviceId)
        put("appVersion", s.appVersion)
        put("data", JSONObject().apply {
            put("habits", array(s.habits) {
                obj(
                    "id" to it.id, "name" to it.name, "createdAt" to it.createdAt, "sortOrder" to it.sortOrder,
                    "isMandatory" to it.isMandatory, "autoSource" to it.autoSource,
                )
            })
            put("habitEntries", array(s.habitEntries) { obj("habitId" to it.habitId, "date" to it.date) })
            put("todos", array(s.todos) {
                obj("id" to it.id, "title" to it.title, "date" to it.date, "done" to it.done, "createdAt" to it.createdAt)
            })
            put("scheduleBlocks", array(s.scheduleBlocks) {
                obj(
                    "id" to it.id, "name" to it.name, "startType" to it.startType, "startValue" to it.startValue,
                    "prayer" to it.prayer, "durationMinutes" to it.durationMinutes, "endMinuteOfDay" to it.endMinuteOfDay,
                    "activeDays" to it.activeDays, "level" to it.level, "sortOrder" to it.sortOrder, "workAction" to it.workAction,
                    "workReminders" to it.workReminders,
                )
            })
            put("scheduleBlockHabits", array(s.scheduleBlockHabits) { obj("blockId" to it.blockId, "habitId" to it.habitId) })
            put("daysOff", JSONArray(s.daysOff.map { it.date }))
            put("followUps", array(s.followUps) {
                obj(
                    "id" to it.id, "title" to it.title, "status" to it.status, "date" to it.date, "time" to it.time,
                    "person" to it.person, "note" to it.note, "createdAt" to it.createdAt, "doneAt" to it.doneAt,
                    "pickedDate" to it.pickedDate,
                )
            })
            put("workDays", array(s.workDays) {
                obj("date" to it.date, "eodNote" to it.eodNote, "scrumDoneAt" to it.scrumDoneAt, "eodDoneAt" to it.eodDoneAt)
            })
            put("drinkCounts", array(s.drinkCounts) { obj("date" to it.date, "kind" to it.kind, "count" to it.count) })
            put("settings", JSONObject().apply {
                put("location", obj("name" to s.settings.locationName, "latitude" to s.settings.latitude, "longitude" to s.settings.longitude))
                put("persistentNotification", s.settings.persistentNotification)
                put("adzan", JSONObject().apply { s.settings.adzan.forEach { (name, on) -> put(name, on) } })
                put("themeMode", s.settings.themeMode)
                put("workReminders", obj("water" to s.settings.waterReminders, "break" to s.settings.breakReminders))
            })
        })
    }.toString()

    /** @throws SnapshotFormatException kalau bukan snapshot yang sah atau versinya lebih baru dari yang dikenal app. */
    fun decode(json: String): Snapshot {
        try {
            val root = JSONObject(json)
            val version = root.optInt("schemaVersion", -1)
            if (version < 1) throw SnapshotFormatException("Snapshot tidak sah: schemaVersion tidak ada.")
            if (version > SCHEMA_VERSION) {
                throw SnapshotFormatException("Snapshot dibuat oleh versi app yang lebih baru. Perbarui HabitFlow dulu.")
            }
            val data = root.getJSONObject("data")
            val settings = data.getJSONObject("settings")
            val location = settings.getJSONObject("location")
            val adzan = settings.getJSONObject("adzan")
            val reminders = settings.optJSONObject("workReminders")

            return Snapshot(
                createdAt = root.optLong("createdAt", 0),
                deviceId = root.optString("deviceId", ""),
                appVersion = root.optString("appVersion", ""),
                habits = data.getJSONArray("habits").map {
                    Habit(
                        it.getLong("id"), it.getString("name"), it.getString("createdAt"), it.getInt("sortOrder"),
                        it.getBoolean("isMandatory"), it.str("autoSource"),
                    )
                },
                habitEntries = data.getJSONArray("habitEntries").map { HabitEntry(it.getLong("habitId"), it.getString("date")) },
                todos = data.getJSONArray("todos").map {
                    Todo(it.getLong("id"), it.getString("title"), it.getString("date"), it.getBoolean("done"), it.getLong("createdAt"))
                },
                scheduleBlocks = data.getJSONArray("scheduleBlocks").map {
                    ScheduleBlockEntity(
                        id = it.getLong("id"), name = it.getString("name"), startType = it.getInt("startType"),
                        startValue = it.getInt("startValue"), prayer = it.str("prayer"),
                        durationMinutes = it.getInt("durationMinutes"), endMinuteOfDay = it.int("endMinuteOfDay"),
                        activeDays = it.getInt("activeDays"), level = it.getString("level"),
                        sortOrder = it.getInt("sortOrder"), workAction = it.str("workAction"),
                        workReminders = it.optBoolean("workReminders", false),
                    )
                },
                scheduleBlockHabits = data.getJSONArray("scheduleBlockHabits").map {
                    ScheduleBlockHabit(it.getLong("blockId"), it.getLong("habitId"))
                },
                daysOff = data.getJSONArray("daysOff").let { arr -> (0 until arr.length()).map { DayOff(arr.getString(it)) } },
                followUps = data.getJSONArray("followUps").map {
                    FollowUpEntity(
                        id = it.getLong("id"), title = it.getString("title"), status = it.getString("status"),
                        date = it.str("date"), time = it.str("time"), person = it.str("person"), note = it.str("note"),
                        createdAt = it.getLong("createdAt"), doneAt = it.long("doneAt"), pickedDate = it.str("pickedDate"),
                    )
                },
                workDays = data.getJSONArray("workDays").map {
                    WorkDayEntity(it.getString("date"), it.str("eodNote"), it.long("scrumDoneAt"), it.long("eodDoneAt"))
                },
                drinkCounts = data.optJSONArray("drinkCounts")?.map {
                    DrinkCount(it.getString("date"), it.getString("kind"), it.getInt("count"))
                }.orEmpty(),
                settings = SnapshotSettings(
                    locationName = location.getString("name"),
                    latitude = location.getDouble("latitude"),
                    longitude = location.getDouble("longitude"),
                    persistentNotification = settings.getBoolean("persistentNotification"),
                    adzan = adzan.keys().asSequence().associateWith { adzan.getBoolean(it) },
                    themeMode = settings.getString("themeMode"),
                    waterReminders = reminders?.optBoolean("water", true) ?: true,
                    breakReminders = reminders?.optBoolean("break", true) ?: true,
                ),
            )
        } catch (e: SnapshotFormatException) {
            throw e
        } catch (e: JSONException) {
            throw SnapshotFormatException("Snapshot rusak atau bukan snapshot HabitFlow (${e.message}).")
        }
    }

    private fun obj(vararg pairs: Pair<String, Any?>): JSONObject = JSONObject().apply {
        pairs.forEach { (key, value) -> put(key, value ?: JSONObject.NULL) }
    }

    private fun <T> array(items: List<T>, build: (T) -> JSONObject): JSONArray = JSONArray().apply { items.forEach { put(build(it)) } }

    private fun <T> JSONArray.map(transform: (JSONObject) -> T): List<T> = (0 until length()).map { transform(getJSONObject(it)) }

    private fun JSONObject.str(key: String): String? = if (isNull(key)) null else getString(key)

    private fun JSONObject.int(key: String): Int? = if (isNull(key)) null else getInt(key)

    private fun JSONObject.long(key: String): Long? = if (isNull(key)) null else getLong(key)
}

/**
 * Tautan blok ke habit yang kedua ujungnya ada. Tautan yatim (misalnya sisa penghapusan di luar app) dibuang saat
 * ekspor dan pulihkan, supaya satu baris rusak tidak menggagalkan seluruh pemulihan lewat foreign key.
 */
fun consistentLinks(
    links: List<ScheduleBlockHabit>,
    habits: List<Habit>,
    blocks: List<ScheduleBlockEntity>,
): List<ScheduleBlockHabit> {
    val habitIds = habits.mapTo(HashSet()) { it.id }
    val blockIds = blocks.mapTo(HashSet()) { it.id }
    return links.filter { it.habitId in habitIds && it.blockId in blockIds }
}
