package com.roziqrizal.habitflow.data.account

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Klien HTTP untuk API akun di `/api/v1` (kontrak di docs/concept.md, tahap 31A). Memakai
 * `HttpURLConnection` bawaan, jadi tanpa dependensi tambahan. Alamat server tetap (bukan dikonfigurasi
 * pengguna): fitur akun hanya untuk domain resmi Habitflow, dan alamat per varian build datang dari
 * `BuildConfig.API_BASE_URL`.
 *
 * Panggilan yang membawa token melapor ke [unauthorized] saat server menjawab 401, supaya penanganan sesi
 * berakhir ada di satu tempat ([SessionGuard]).
 */
class AccountClient(
    private val baseUrl: String = BASE_URL,
    private val unauthorized: UnauthorizedListener = UnauthorizedListener.None,
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 30_000,
) : AccountApi {

    override suspend fun register(
        name: String,
        email: String,
        password: String,
        passwordConfirmation: String,
        privacyPolicyVersion: String,
    ): AccountResult<Unit> = call(
        "POST", "/api/v1/auth/register",
        body = JSONObject()
            .put("name", name)
            .put("email", email)
            .put("password", password)
            .put("password_confirmation", passwordConfirmation)
            .put("privacy_policy_version", privacyPolicyVersion),
    ) { }

    override suspend fun verifyEmail(email: String, code: String, deviceName: String): AccountResult<Account> = call(
        "POST", "/api/v1/auth/verify-email",
        body = JSONObject().put("email", email).put("code", code).put("device_name", deviceName),
        parse = ::parseSession,
    )

    override suspend fun resendVerification(email: String): AccountResult<Unit> =
        call("POST", "/api/v1/auth/resend-verification", body = JSONObject().put("email", email)) { }

    override suspend fun login(email: String, password: String, deviceName: String): AccountResult<Account> = call(
        "POST", "/api/v1/auth/login",
        body = JSONObject().put("email", email).put("password", password).put("device_name", deviceName),
        parse = ::parseSession,
    )

    override suspend fun loginWithGoogle(idToken: String, deviceName: String): AccountResult<Account> = call(
        "POST", "/api/v1/auth/google",
        body = JSONObject().put("id_token", idToken).put("device_name", deviceName),
        parse = ::parseSession,
    )

    override suspend fun forgotPassword(email: String): AccountResult<Unit> =
        call("POST", "/api/v1/auth/forgot-password", body = JSONObject().put("email", email)) { }

    override suspend fun resetPassword(
        email: String,
        code: String,
        password: String,
        passwordConfirmation: String,
    ): AccountResult<Unit> = call(
        "POST", "/api/v1/auth/reset-password",
        body = JSONObject()
            .put("email", email)
            .put("code", code)
            .put("password", password)
            .put("password_confirmation", passwordConfirmation),
    ) { }

    override suspend fun logout(token: String): AccountResult<Unit> =
        call("POST", "/api/v1/auth/logout", token = token) { }

    override suspend fun logoutAll(token: String): AccountResult<Unit> =
        call("POST", "/api/v1/auth/logout-all", token = token) { }

    override suspend fun changePassword(
        token: String,
        currentPassword: String?,
        password: String,
        passwordConfirmation: String,
    ): AccountResult<Unit> {
        val body = JSONObject().put("password", password).put("password_confirmation", passwordConfirmation)
        if (currentPassword != null) body.put("current_password", currentPassword)
        return call("PUT", "/api/v1/account/password", body = body, token = token) { }
    }

    override suspend fun deleteAccount(token: String, password: String?, googleIdToken: String?): AccountResult<Unit> {
        val body = JSONObject()
        if (password != null) body.put("password", password)
        if (googleIdToken != null) body.put("id_token", googleIdToken)
        return call("DELETE", "/api/v1/account", body = body, token = token) { }
    }

    override suspend fun me(token: String): AccountResult<MeInfo> = call("GET", "/api/v1/me", token = token) { json ->
        MeInfo(
            account = parseAccount(json.getJSONObject("account"), token),
            hasPassword = json.getBoolean("has_password"),
            googleLinked = json.getBoolean("google_linked"),
            emailVerified = json.getBoolean("email_verified"),
        )
    }

    private fun parseSession(json: JSONObject): Account =
        parseAccount(json.getJSONObject("account"), json.getString("token"))

    private fun parseAccount(json: JSONObject, token: String): Account = Account(
        id = json.getLong("id"),
        email = json.getString("email"),
        name = json.getString("name"),
        avatar = if (json.isNull("avatar")) null else json.getString("avatar"),
        token = token,
    )

    private suspend fun <T> call(
        method: String,
        path: String,
        body: JSONObject? = null,
        token: String? = null,
        parse: (JSONObject) -> T,
    ): AccountResult<T> = withContext(Dispatchers.IO) {
        val connection = try {
            URL(baseUrl + path).openConnection() as? HttpURLConnection
        } catch (e: IOException) {
            null
        } ?: return@withContext AccountResult.Offline // alamat bukan http(s) sama saja dengan tidak terjangkau
        try {
            connection.requestMethod = method
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "HabitFlow")
            if (token != null) connection.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }

            val status = connection.responseCode
            val stream = if (status in 200..399) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()

            when {
                status in 200..299 -> try {
                    AccountResult.Success(parse(parseObject(text) ?: JSONObject()))
                } catch (e: JSONException) {
                    AccountResult.Failed(status, null, "Jawaban server tidak dikenali.")
                }
                status == 401 && token != null -> {
                    unauthorized.onUnauthorized(token)
                    AccountResult.Unauthorized
                }
                else -> failure(status, text, connection.getHeaderField("Retry-After")?.toIntOrNull())
            }
        } catch (e: IOException) {
            AccountResult.Offline
        } finally {
            connection.disconnect()
        }
    }

    private fun parseObject(text: String): JSONObject? =
        if (text.isBlank()) null else runCatching { JSONObject(text) }.getOrNull()

    /**
     * Pesan dipilih dari yang paling spesifik: galat pertama di `errors` (validasi Laravel, field `message`-nya
     * hanya "dan N galat lain"), lalu `message`, lalu kalimat bawaan menurut kode HTTP.
     */
    private fun failure(status: Int, text: String, retryAfter: Int?): AccountResult.Failed {
        val json = parseObject(text)
        val firstError = json?.optJSONObject("errors")?.let { errors ->
            errors.keys().asSequence().mapNotNull { errors.optJSONArray(it)?.optString(0) }.firstOrNull { it.isNotBlank() }
        }
        val code = json?.takeUnless { it.isNull("code") }?.optString("code")?.takeIf { it.isNotBlank() }
            ?: if (firstError != null) AccountErrors.VALIDATION else null
        val message = firstError
            ?: json?.takeUnless { it.isNull("message") }?.optString("message")?.takeIf { it.isNotBlank() }
            ?: if (status >= 500) "Server bermasalah (kode $status)." else "Permintaan ditolak (kode $status)."
        return AccountResult.Failed(status, code, message, retryAfter)
    }

    companion object {
        const val BASE_URL = "https://habitflow.roziqrizal.com"
    }
}
