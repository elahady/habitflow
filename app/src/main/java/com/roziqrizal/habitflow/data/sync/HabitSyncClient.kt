package com.roziqrizal.habitflow.data.sync

import com.roziqrizal.habitflow.data.FollowUpEntity
import com.roziqrizal.habitflow.data.WorkDayEntity
import com.roziqrizal.habitflow.data.account.AccountClient
import com.roziqrizal.habitflow.data.account.UnauthorizedListener
import com.roziqrizal.habitflow.domain.work.DailyBrief
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDate

/** Habit seperti dikembalikan server (tahap 28 langkah 5). [done] hanya terisi dari `GET habits?date=`. */
data class RemoteHabit(
    val id: Long,
    val name: String,
    val sortOrder: Int,
    val isMandatory: Boolean,
    val updatedAt: String,
    val done: Boolean? = null,
)

/** `date` dari server berbentuk ISO datetime (`2026-10-10T00:00:00.000000Z`) - diambil 10 huruf pertamanya. */
data class RemoteTodo(
    val id: Long,
    val title: String,
    val date: String,
    val done: Boolean,
    val updatedAt: String,
)

sealed interface HabitApiResult<out T> {
    data class Success<T>(val value: T) : HabitApiResult<T>
    /** 409: [server] adalah versi terbaru dari server, dipakai untuk menimpa versi lokal (server menang). */
    data class Conflict<T>(val server: T) : HabitApiResult<T>
    data class Rejected(val detail: String) : HabitApiResult<Nothing>
    data object Unauthorized : HabitApiResult<Nothing>
    data object NotFound : HabitApiResult<Nothing>
    data class ServerError(val code: Int) : HabitApiResult<Nothing>
    data class NetworkError(val detail: String) : HabitApiResult<Nothing>

    val retryable: Boolean get() = this is NetworkError || this is ServerError
}

/**
 * Klien HTTP untuk `/api/v1/habits` dan `/api/v1/todos` (tahap 28 langkah 5). Pola sama dengan
 * [com.roziqrizal.habitflow.data.sync.SyncClient]/[com.roziqrizal.habitflow.data.account.AccountClient]
 * - `HttpURLConnection` bawaan, tanpa dependensi baru. [token] adalah token akun (beda dari token
 * cadangan 19B).
 */
class HabitSyncClient(
    private val token: String,
    private val baseUrl: String = AccountClient.BASE_URL,
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 30_000,
    /** Dilapori token yang ditolak saat server menjawab 401 (lihat [SessionGuard]). */
    private val unauthorized: UnauthorizedListener = UnauthorizedListener.None,
) : DailyBriefSource {
    suspend fun listHabits(date: String): HabitApiResult<List<RemoteHabit>> =
        when (val raw = request("GET", "/api/v1/habits?date=$date")) {
            is Raw.Failed -> HabitApiResult.NetworkError(raw.detail)
            is Raw.Response -> if (raw.code == 200) {
                parseList(raw.body, "habits", ::parseHabit)
            } else {
                failure(raw)
            }
        }

    suspend fun createHabit(name: String, isMandatory: Boolean): HabitApiResult<RemoteHabit> {
        val body = JSONObject().put("name", name).put("is_mandatory", isMandatory).toString()
        return when (val raw = request("POST", "/api/v1/habits", body)) {
            is Raw.Failed -> HabitApiResult.NetworkError(raw.detail)
            is Raw.Response -> if (raw.code == 201) parseSingle(raw.body, "habit", ::parseHabit) else failure(raw)
        }
    }

    suspend fun updateHabit(
        remoteId: Long,
        name: String,
        isMandatory: Boolean,
        sortOrder: Int,
        updatedAt: String?,
    ): HabitApiResult<RemoteHabit> {
        val body = JSONObject()
            .put("name", name)
            .put("is_mandatory", isMandatory)
            .put("sort_order", sortOrder)
            .apply { if (updatedAt != null) put("updated_at", updatedAt) }
            .toString()
        return when (val raw = request("PUT", "/api/v1/habits/$remoteId", body)) {
            is Raw.Failed -> HabitApiResult.NetworkError(raw.detail)
            is Raw.Response -> when (raw.code) {
                200 -> parseSingle(raw.body, "habit", ::parseHabit)
                409 -> parseConflict(raw.body, "habit", ::parseHabit)
                else -> failure(raw)
            }
        }
    }

    suspend fun deleteHabit(remoteId: Long): HabitApiResult<Unit> = when (val raw = request("DELETE", "/api/v1/habits/$remoteId")) {
        is Raw.Failed -> HabitApiResult.NetworkError(raw.detail)
        is Raw.Response -> if (raw.code == 204) HabitApiResult.Success(Unit) else failure(raw)
    }

    suspend fun toggleEntry(remoteHabitId: Long, date: String): HabitApiResult<Unit> {
        val body = JSONObject().put("date", date).toString()
        return when (val raw = request("POST", "/api/v1/habits/$remoteHabitId/entries", body)) {
            is Raw.Failed -> HabitApiResult.NetworkError(raw.detail)
            is Raw.Response -> if (raw.code == 200) HabitApiResult.Success(Unit) else failure(raw)
        }
    }

    suspend fun listTodos(date: String): HabitApiResult<List<RemoteTodo>> =
        when (val raw = request("GET", "/api/v1/todos?date=$date")) {
            is Raw.Failed -> HabitApiResult.NetworkError(raw.detail)
            is Raw.Response -> if (raw.code == 200) parseList(raw.body, "todos", ::parseTodo) else failure(raw)
        }

    suspend fun createTodo(title: String, date: String): HabitApiResult<RemoteTodo> {
        val body = JSONObject().put("title", title).put("date", date).toString()
        return when (val raw = request("POST", "/api/v1/todos", body)) {
            is Raw.Failed -> HabitApiResult.NetworkError(raw.detail)
            is Raw.Response -> when (raw.code) {
                201 -> parseSingle(raw.body, "todo", ::parseTodo)
                422 -> HabitApiResult.Rejected(rejectMessage(raw.body))
                else -> failure(raw)
            }
        }
    }

    suspend fun updateTodo(remoteId: Long, title: String, date: String, done: Boolean, updatedAt: String?): HabitApiResult<RemoteTodo> {
        val body = JSONObject()
            .put("title", title)
            .put("date", date)
            .put("done", done)
            .apply { if (updatedAt != null) put("updated_at", updatedAt) }
            .toString()
        return when (val raw = request("PUT", "/api/v1/todos/$remoteId", body)) {
            is Raw.Failed -> HabitApiResult.NetworkError(raw.detail)
            is Raw.Response -> when (raw.code) {
                200 -> parseSingle(raw.body, "todo", ::parseTodo)
                409 -> parseConflict(raw.body, "todo", ::parseTodo)
                else -> failure(raw)
            }
        }
    }

    suspend fun deleteTodo(remoteId: Long): HabitApiResult<Unit> = when (val raw = request("DELETE", "/api/v1/todos/$remoteId")) {
        is Raw.Failed -> HabitApiResult.NetworkError(raw.detail)
        is Raw.Response -> if (raw.code == 204) HabitApiResult.Success(Unit) else failure(raw)
    }

    // --- Follow-up kerja (tahap 27 langkah 3) ---

    suspend fun listFollowUps(): HabitApiResult<List<RemoteFollowUp>> =
        when (val raw = request("GET", "/api/v1/follow-ups")) {
            is Raw.Failed -> HabitApiResult.NetworkError(raw.detail)
            is Raw.Response -> if (raw.code == 200) {
                parseList(raw.body, "follow_ups", RemoteFollowUp::fromJson)
            } else {
                failure(raw)
            }
        }

    suspend fun createFollowUp(item: FollowUpEntity): HabitApiResult<RemoteFollowUp> =
        when (val raw = request("POST", "/api/v1/follow-ups", item.toRequestJson().toString())) {
            is Raw.Failed -> HabitApiResult.NetworkError(raw.detail)
            is Raw.Response -> if (raw.code == 201) parseSingle(raw.body, "follow_up", RemoteFollowUp::fromJson) else failure(raw)
        }

    /** [item] harus punya `remoteId`. `updated_at` wajib untuk follow-up, jadi tanpa itu server menolak (422). */
    suspend fun updateFollowUp(remoteId: Long, item: FollowUpEntity): HabitApiResult<RemoteFollowUp> {
        val body = item.toRequestJson().apply { item.remoteUpdatedAt?.let { put("updated_at", it) } }.toString()
        return when (val raw = request("PUT", "/api/v1/follow-ups/$remoteId", body)) {
            is Raw.Failed -> HabitApiResult.NetworkError(raw.detail)
            is Raw.Response -> when (raw.code) {
                200 -> parseSingle(raw.body, "follow_up", RemoteFollowUp::fromJson)
                409 -> parseConflict(raw.body, "follow_up", RemoteFollowUp::fromJson)
                else -> failure(raw)
            }
        }
    }

    suspend fun deleteFollowUp(remoteId: Long): HabitApiResult<Unit> =
        when (val raw = request("DELETE", "/api/v1/follow-ups/$remoteId")) {
            is Raw.Failed -> HabitApiResult.NetworkError(raw.detail)
            is Raw.Response -> if (raw.code == 204) HabitApiResult.Success(Unit) else failure(raw)
        }

    // --- Catatan harian kerja (tahap 33 langkah 3c) ---

    /** Sampai 60 hari terbaru milik akun ini, terbaru dulu. */
    suspend fun listWorkDays(): HabitApiResult<List<RemoteWorkDay>> =
        when (val raw = request("GET", "/api/v1/work-days")) {
            is Raw.Failed -> HabitApiResult.NetworkError(raw.detail)
            is Raw.Response -> if (raw.code == 200) {
                val parsed = parseList(raw.body, "work_days", RemoteWorkDay::fromJsonOrNull)
                if (parsed is HabitApiResult.Success) HabitApiResult.Success(parsed.value.filterNotNull()) else HabitApiResult.ServerError(200)
            } else {
                failure(raw)
            }
        }

    /**
     * Upsert satu hari: 201 baris baru, 200 diubah. Kalau baris sudah ada di server, `updated_at` ([WorkDayEntity.remoteUpdatedAt])
     * wajib dan harus sama; kalau tidak server menjawab 409 berisi versi terbarunya ([HabitApiResult.Conflict]).
     */
    suspend fun putWorkDay(item: WorkDayEntity): HabitApiResult<RemoteWorkDay> =
        when (val raw = request("PUT", "/api/v1/work-days/${item.date}", item.toRequestJson().toString())) {
            is Raw.Failed -> HabitApiResult.NetworkError(raw.detail)
            is Raw.Response -> when (raw.code) {
                200, 201 -> parseSingle(raw.body, "work_day", RemoteWorkDay::fromJson)
                409 -> parseConflict(raw.body, "work_day", RemoteWorkDay::fromJson)
                else -> failure(raw)
            }
        }

    /**
     * Mengirim data kesehatan (tahap 30 langkah 4). Satu arah dan idempotent di server: berat dan tensi dikunci waktu catat,
     * langkah per tanggal, fokus satu baris. Tidak ada 409; 422 berarti bentuk kiriman ditolak.
     */
    suspend fun putHealth(payload: HealthSyncPayload): HabitApiResult<Unit> =
        when (val raw = request("PUT", "/api/v1/health", payload.toJson().toString())) {
            is Raw.Failed -> HabitApiResult.NetworkError(raw.detail)
            is Raw.Response -> if (raw.code == 200) HabitApiResult.Success(Unit) else failure(raw)
        }

    /**
     * Mengganti seluruh jadwal di server (tahap 29 langkah 4). Satu arah dan idempotent: HP satu-satunya yang mengubah blok,
     * jadi tidak ada 409; 422 berarti bentuk kiriman ditolak (seluruh jadwal lama di server tetap utuh).
     */
    suspend fun putScheduleBlocks(payload: ScheduleSyncPayload): HabitApiResult<Unit> =
        when (val raw = request("PUT", "/api/v1/schedule-blocks", payload.toJson().toString())) {
            is Raw.Failed -> HabitApiResult.NetworkError(raw.detail)
            is Raw.Response -> if (raw.code == 200) HabitApiResult.Success(Unit) else failure(raw)
        }

    /** Mengirim waktu sholat beberapa hari yang sudah dihitung HP (tahap 29 langkah 4). Idempotent: tanggal yang sama ditimpa. */
    suspend fun putPrayerDays(payload: PrayerDaysPayload): HabitApiResult<Unit> =
        when (val raw = request("PUT", "/api/v1/prayer-days", payload.toJson().toString())) {
            is Raw.Failed -> HabitApiResult.NetworkError(raw.detail)
            is Raw.Response -> if (raw.code == 200) HabitApiResult.Success(Unit) else failure(raw)
        }

    /** Mengirim tanggal libur dalam satu jendela (tahap 29 langkah 5). Idempotent: server mengganti isi jendela itu, tanpa 409. */
    suspend fun putDaysOff(payload: DaysOffPayload): HabitApiResult<Unit> =
        when (val raw = request("PUT", "/api/v1/days-off", payload.toJson().toString())) {
            is Raw.Failed -> HabitApiResult.NetworkError(raw.detail)
            is Raw.Response -> if (raw.code == 200) HabitApiResult.Success(Unit) else failure(raw)
        }

    // --- Ringkasan harian dari Claude (tahap 32): hanya dibaca, ditulis skill di server ---

    /** Sampai 31 ringkasan terbaru milik akun ini, terbaru dulu. Yang tidak berisi email (`email_digest` null) tidak ikut. */
    override suspend fun listDailyBriefs(): HabitApiResult<List<DailyBrief>> =
        when (val raw = request("GET", "/api/v1/daily-briefs")) {
            is Raw.Failed -> HabitApiResult.NetworkError(raw.detail)
            is Raw.Response -> if (raw.code == 200) {
                // parseList hanya menghasilkan Success atau ServerError(200) untuk badan yang bukan JSON.
                val parsed = parseList(raw.body, "daily_briefs", ::parseDailyBrief)
                if (parsed is HabitApiResult.Success) HabitApiResult.Success(parsed.value.filterNotNull()) else HabitApiResult.ServerError(200)
            } else {
                failure(raw)
            }
        }

    /** Null untuk baris yang tidak berisi email atau tanggalnya tidak terbaca, supaya satu baris rusak tidak menggagalkan semuanya. */
    private fun parseDailyBrief(o: JSONObject): DailyBrief? {
        val digest = if (o.isNull("email_digest")) null else o.optString("email_digest").takeIf { it.isNotBlank() }
        val date = runCatching { LocalDate.parse(o.getString("date").take(10)) }.getOrNull()
        val updatedAt = runCatching { Instant.parse(o.getString("updated_at")).toEpochMilli() }.getOrDefault(0L)
        return if (digest != null && date != null) DailyBrief(date, digest, updatedAt) else null
    }

    private fun parseHabit(o: JSONObject) = RemoteHabit(
        id = o.getLong("id"),
        name = o.getString("name"),
        sortOrder = o.getInt("sort_order"),
        isMandatory = o.getBoolean("is_mandatory"),
        updatedAt = o.getString("updated_at"),
        done = if (o.has("done") && !o.isNull("done")) o.getBoolean("done") else null,
    )

    private fun parseTodo(o: JSONObject) = RemoteTodo(
        id = o.getLong("id"),
        title = o.getString("title"),
        date = o.getString("date").take(10),
        done = o.getBoolean("done"),
        updatedAt = o.getString("updated_at"),
    )

    private fun <T> parseList(body: String, key: String, parse: (JSONObject) -> T): HabitApiResult<List<T>> = try {
        val array: JSONArray = JSONObject(body).getJSONArray(key)
        HabitApiResult.Success((0 until array.length()).map { parse(array.getJSONObject(it)) })
    } catch (e: JSONException) {
        HabitApiResult.ServerError(200)
    }

    private fun <T> parseSingle(body: String, key: String, parse: (JSONObject) -> T): HabitApiResult<T> = try {
        HabitApiResult.Success(parse(JSONObject(body).getJSONObject(key)))
    } catch (e: JSONException) {
        HabitApiResult.ServerError(200)
    }

    private fun <T> parseConflict(body: String, key: String, parse: (JSONObject) -> T): HabitApiResult<T> = try {
        HabitApiResult.Conflict(parse(JSONObject(body).getJSONObject(key)))
    } catch (e: JSONException) {
        HabitApiResult.ServerError(409)
    }

    private fun rejectMessage(body: String): String =
        runCatching { JSONObject(body).optString("message") }.getOrNull()?.takeIf { it.isNotBlank() } ?: "ditolak server"

    private fun failure(raw: Raw.Response): HabitApiResult<Nothing> = when (raw.code) {
        401 -> HabitApiResult.Unauthorized
        404 -> HabitApiResult.NotFound
        422 -> HabitApiResult.Rejected(rejectMessage(raw.body))
        else -> HabitApiResult.ServerError(raw.code)
    }

    private sealed interface Raw {
        data class Response(val code: Int, val body: String) : Raw
        data class Failed(val detail: String) : Raw
    }

    private suspend fun request(method: String, path: String, body: String? = null): Raw = withContext(Dispatchers.IO) {
        val connection = try {
            URL(baseUrl + path).openConnection() as HttpURLConnection
        } catch (e: IOException) {
            return@withContext Raw.Failed(e.message ?: "alamat tidak sah")
        }
        try {
            connection.requestMethod = method
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "HabitFlow")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            if (code == 401) unauthorized.onUnauthorized(token)
            val stream = if (code in 200..399) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            Raw.Response(code, text)
        } catch (e: IOException) {
            Raw.Failed(e.message ?: e.javaClass.simpleName)
        } finally {
            connection.disconnect()
        }
    }
}
