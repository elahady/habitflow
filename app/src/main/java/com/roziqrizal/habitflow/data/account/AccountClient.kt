package com.roziqrizal.habitflow.data.account

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

sealed interface LoginResult {
    data class Success(val account: Account) : LoginResult
    data object Rejected : LoginResult
    data class ServerError(val code: Int) : LoginResult
    data class NetworkError(val detail: String) : LoginResult
}

fun LoginResult.message(): String = when (this) {
    is LoginResult.Success -> "Masuk sebagai ${this.account.name}."
    LoginResult.Rejected -> "Google menolak token ini. Coba lagi."
    is LoginResult.ServerError -> "Server bermasalah (kode $code)."
    is LoginResult.NetworkError -> "Tidak bisa menjangkau server: $detail"
}

/**
 * Tukar ID token Google ke token API lewat `POST /api/v1/auth/google` (tahap 25). Alamat server
 * tetap (bukan dikonfigurasi pengguna seperti cadangan 19B), karena fitur akun hanya untuk domain
 * resmi Habitflow.
 */
class AccountClient(
    private val baseUrl: String = BASE_URL,
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 30_000,
) {
    suspend fun login(idToken: String): LoginResult = withContext(Dispatchers.IO) {
        val connection = try {
            URL("$baseUrl/api/v1/auth/google").openConnection() as HttpURLConnection
        } catch (e: IOException) {
            return@withContext LoginResult.NetworkError(e.message ?: "alamat tidak sah")
        }
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "HabitFlow")
            val body = JSONObject().put("id_token", idToken).toString()
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            val code = connection.responseCode
            val stream = if (code in 200..399) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()

            when (code) {
                200 -> parseSuccess(text)
                422 -> LoginResult.Rejected
                else -> LoginResult.ServerError(code)
            }
        } catch (e: IOException) {
            LoginResult.NetworkError(e.message ?: e.javaClass.simpleName)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseSuccess(body: String): LoginResult = try {
        val json = JSONObject(body)
        val token = json.getString("token")
        val user = json.getJSONObject("user")
        LoginResult.Success(
            Account(
                id = user.getLong("id"),
                email = user.getString("email"),
                name = user.getString("name"),
                avatar = if (user.isNull("avatar")) null else user.getString("avatar"),
                token = token,
            ),
        )
    } catch (e: JSONException) {
        LoginResult.ServerError(200)
    }

    companion object {
        const val BASE_URL = "https://habitflow.roziqrizal.com"
    }
}
