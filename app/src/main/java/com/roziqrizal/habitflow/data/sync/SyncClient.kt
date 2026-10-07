package com.roziqrizal.habitflow.data.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Hasil satu permintaan ke server cadangan. [message] adalah teks untuk pengguna. */
sealed interface SyncResult {
    /** Ping berhasil. */
    data object Ok : SyncResult
    data class Uploaded(val id: Long, val bytes: Long, val unchanged: Boolean) : SyncResult
    data class Downloaded(val body: String, val receivedAt: Long) : SyncResult
    data object NoSnapshot : SyncResult
    data object NotConfigured : SyncResult
    data object Unauthorized : SyncResult
    data object TooLarge : SyncResult
    data object RateLimited : SyncResult
    data class Rejected(val detail: String) : SyncResult
    data class ServerError(val code: Int) : SyncResult
    data class NetworkError(val detail: String) : SyncResult

    /** Gagal yang masih masuk akal dicoba lagi nanti (jaringan atau server sibuk). */
    val retryable: Boolean get() = this is NetworkError || this is ServerError || this is RateLimited

    val isSuccess: Boolean get() = this is Ok || this is Uploaded || this is Downloaded
}

fun SyncResult.message(): String = when (this) {
    SyncResult.Ok -> "Terhubung ke server."
    is SyncResult.Uploaded -> if (unchanged) "Data sudah sama dengan di server." else "Cadangan terkirim."
    is SyncResult.Downloaded -> "Snapshot diunduh."
    SyncResult.NoSnapshot -> "Belum ada cadangan di server."
    SyncResult.NotConfigured -> "Isi alamat server dan token dulu."
    SyncResult.Unauthorized -> "Token ditolak server. Periksa token di pengaturan."
    SyncResult.TooLarge -> "Data terlalu besar untuk server (maksimal 5 MB)."
    SyncResult.RateLimited -> "Terlalu sering. Coba lagi sebentar."
    is SyncResult.Rejected -> "Server menolak data: $detail"
    is SyncResult.ServerError -> "Server bermasalah (kode $code)."
    is SyncResult.NetworkError -> "Tidak bisa menjangkau server: $detail"
}

/**
 * Klien HTTP untuk server cadangan (kontrak di docs/concept.md tahap 19B). Memakai `HttpURLConnection`
 * bawaan Android, jadi tanpa dependensi tambahan. [baseUrl] sudah dinormalkan oleh [SyncUrl].
 */
class SyncClient(
    private val baseUrl: String,
    private val token: String,
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 30_000,
) {

    suspend fun ping(): SyncResult = request("GET", "/api/v1/ping").let { raw ->
        when (raw) {
            is Raw.Response -> if (raw.code == 200) SyncResult.Ok else failure(raw)
            is Raw.Failed -> SyncResult.NetworkError(raw.detail)
        }
    }

    suspend fun upload(json: String): SyncResult = when (val raw = request("PUT", "/api/v1/snapshot", json)) {
        is Raw.Failed -> SyncResult.NetworkError(raw.detail)
        is Raw.Response -> if (raw.code == 200) parseUpload(raw.body) else failure(raw)
    }

    suspend fun downloadLatest(): SyncResult = when (val raw = request("GET", "/api/v1/snapshot/latest")) {
        is Raw.Failed -> SyncResult.NetworkError(raw.detail)
        is Raw.Response -> when (raw.code) {
            200 -> SyncResult.Downloaded(raw.body, raw.receivedAt ?: 0L)
            404 -> SyncResult.NoSnapshot
            else -> failure(raw)
        }
    }

    private fun parseUpload(body: String): SyncResult = try {
        val json = JSONObject(body)
        SyncResult.Uploaded(json.optLong("id"), json.optLong("bytes"), json.optBoolean("unchanged"))
    } catch (e: JSONException) {
        SyncResult.ServerError(200)
    }

    private fun failure(raw: Raw.Response): SyncResult = when (raw.code) {
        401 -> SyncResult.Unauthorized
        413 -> SyncResult.TooLarge
        429 -> SyncResult.RateLimited
        422 -> SyncResult.Rejected(
            runCatching { JSONObject(raw.body).optString("message") }.getOrNull()?.takeIf { it.isNotBlank() } ?: "isi tidak sah",
        )
        else -> SyncResult.ServerError(raw.code)
    }

    private sealed interface Raw {
        data class Response(val code: Int, val body: String, val receivedAt: Long?) : Raw
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
            val stream = if (code in 200..399) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            Raw.Response(code, text, connection.getHeaderField("X-Received-At")?.toLongOrNull())
        } catch (e: IOException) {
            Raw.Failed(e.message ?: e.javaClass.simpleName)
        } finally {
            connection.disconnect()
        }
    }
}

/** Memeriksa dan merapikan alamat server yang diketik pengguna. */
object SyncUrl {

    private val LOCAL_HOSTS = setOf("10.0.2.2", "localhost", "127.0.0.1")

    /**
     * Mengembalikan alamat tanpa garis miring di akhir, atau null kalau tidak boleh dipakai. Hanya `https://` yang
     * diterima. `http://` hanya untuk emulator dan localhost, dan hanya kalau [allowLocalCleartext] (build debug).
     */
    fun normalize(input: String, allowLocalCleartext: Boolean): String? {
        val text = input.trim().trimEnd('/')
        if (text.isEmpty() || text.any { it.isWhitespace() }) return null
        val url = runCatching { URL(text) }.getOrNull() ?: return null
        val host = url.host.takeIf { it.isNotEmpty() } ?: return null
        return when (url.protocol) {
            "https" -> text
            "http" -> if (allowLocalCleartext && host in LOCAL_HOSTS) text else null
            else -> null
        }
    }
}
