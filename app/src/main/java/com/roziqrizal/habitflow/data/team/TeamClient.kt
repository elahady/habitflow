package com.roziqrizal.habitflow.data.team

import com.roziqrizal.habitflow.data.account.AccountClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Klien HTTP untuk `/api/v1/teams` dan `/api/v1/teams/{team}/todos` (tahap 26 langkah 2 dan 4).
 * Pola sama dengan [com.roziqrizal.habitflow.data.sync.HabitSyncClient] - `HttpURLConnection`
 * bawaan, token akun (tahap 25) di header `Authorization`. Beda dari habit/to-do harian: tidak ada
 * deteksi konflik 409, sesuai keputusan "realtime-ish lewat polling" di tahap 26.
 */
class TeamClient(
    private val token: String,
    private val baseUrl: String = AccountClient.BASE_URL,
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 30_000,
) {
    suspend fun listTeams(): TeamApiResult<List<RemoteTeam>> =
        when (val raw = request("GET", "/api/v1/teams")) {
            is Raw.Failed -> TeamApiResult.NetworkError(raw.detail)
            is Raw.Response -> if (raw.code == 200) parseTeams(raw.body) else failure(raw)
        }

    suspend fun createTeam(name: String): TeamApiResult<RemoteTeam> {
        val body = JSONObject().put("name", name).toString()
        return when (val raw = request("POST", "/api/v1/teams", body)) {
            is Raw.Failed -> TeamApiResult.NetworkError(raw.detail)
            is Raw.Response -> when (raw.code) {
                201 -> parseSingleTeam(raw.body)
                422 -> TeamApiResult.Rejected(rejectMessage(raw.body))
                else -> failure(raw)
            }
        }
    }

    suspend fun createInvite(teamId: Long): TeamApiResult<TeamInvite> {
        val body = JSONObject().put("team_id", teamId).toString()
        return when (val raw = request("POST", "/api/v1/teams/invites", body)) {
            is Raw.Failed -> TeamApiResult.NetworkError(raw.detail)
            is Raw.Response -> if (raw.code == 201) {
                try {
                    val json = JSONObject(raw.body)
                    TeamApiResult.Success(TeamInvite(json.getString("token"), json.getString("url"), json.getString("expires_at")))
                } catch (e: JSONException) {
                    TeamApiResult.ServerError(201)
                }
            } else {
                failure(raw)
            }
        }
    }

    /** Terima undangan (tahap 26 langkah 2). 410/404 berarti link sudah tidak berlaku atau tidak ditemukan. */
    suspend fun acceptInvite(inviteToken: String): TeamApiResult<RemoteTeam> =
        when (val raw = request("POST", "/api/v1/teams/invites/$inviteToken/accept")) {
            is Raw.Failed -> TeamApiResult.NetworkError(raw.detail)
            is Raw.Response -> when (raw.code) {
                200 -> try {
                    TeamApiResult.Success(parseTeam(JSONObject(raw.body).getJSONObject("team")))
                } catch (e: JSONException) {
                    TeamApiResult.ServerError(200)
                }
                410 -> TeamApiResult.InviteInvalid(rejectMessage(raw.body))
                404 -> TeamApiResult.InviteInvalid(rejectMessage(raw.body).ifBlank { "Link undangan tidak ditemukan." })
                else -> failure(raw)
            }
        }

    suspend fun listTodos(teamId: Long): TeamApiResult<List<RemoteTeamTodo>> =
        when (val raw = request("GET", "/api/v1/teams/$teamId/todos")) {
            is Raw.Failed -> TeamApiResult.NetworkError(raw.detail)
            is Raw.Response -> if (raw.code == 200) parseTodos(raw.body) else failure(raw)
        }

    suspend fun createTodo(teamId: Long, title: String, assignedTo: Long?): TeamApiResult<RemoteTeamTodo> {
        val body = JSONObject().put("title", title).apply {
            if (assignedTo != null) put("assigned_to", assignedTo) else put("assigned_to", JSONObject.NULL)
        }.toString()
        return when (val raw = request("POST", "/api/v1/teams/$teamId/todos", body)) {
            is Raw.Failed -> TeamApiResult.NetworkError(raw.detail)
            is Raw.Response -> when (raw.code) {
                201 -> parseSingleTodo(raw.body)
                422 -> TeamApiResult.Rejected(rejectMessage(raw.body))
                else -> failure(raw)
            }
        }
    }

    suspend fun setDone(todoId: Long, done: Boolean): TeamApiResult<RemoteTeamTodo> =
        updateTodo(todoId, JSONObject().put("done", done))

    suspend fun assign(todoId: Long, assignedTo: Long?): TeamApiResult<RemoteTeamTodo> =
        updateTodo(todoId, JSONObject().put("assigned_to", assignedTo ?: JSONObject.NULL))

    private suspend fun updateTodo(todoId: Long, body: JSONObject): TeamApiResult<RemoteTeamTodo> =
        when (val raw = request("PUT", "/api/v1/teams/todos/$todoId", body.toString())) {
            is Raw.Failed -> TeamApiResult.NetworkError(raw.detail)
            is Raw.Response -> when (raw.code) {
                200 -> parseSingleTodo(raw.body)
                422 -> TeamApiResult.Rejected(rejectMessage(raw.body))
                else -> failure(raw)
            }
        }

    suspend fun deleteTodo(todoId: Long): TeamApiResult<Unit> =
        when (val raw = request("DELETE", "/api/v1/teams/todos/$todoId")) {
            is Raw.Failed -> TeamApiResult.NetworkError(raw.detail)
            is Raw.Response -> if (raw.code == 204) TeamApiResult.Success(Unit) else failure(raw)
        }

    private fun parseTeams(body: String): TeamApiResult<List<RemoteTeam>> = try {
        val array: JSONArray = JSONObject(body).getJSONArray("teams")
        TeamApiResult.Success((0 until array.length()).map { parseTeam(array.getJSONObject(it)) })
    } catch (e: JSONException) {
        TeamApiResult.ServerError(200)
    }

    private fun parseSingleTeam(body: String): TeamApiResult<RemoteTeam> = try {
        TeamApiResult.Success(parseTeam(JSONObject(body).getJSONObject("team")))
    } catch (e: JSONException) {
        TeamApiResult.ServerError(200)
    }

    private fun parseTeam(o: JSONObject): RemoteTeam {
        val members = if (o.has("members") && !o.isNull("members")) {
            val array = o.getJSONArray("members")
            (0 until array.length()).map { i ->
                val m = array.getJSONObject(i)
                TeamMember(m.getLong("id"), m.getString("name"), if (m.isNull("avatar")) null else m.getString("avatar"))
            }
        } else {
            emptyList()
        }
        return RemoteTeam(o.getLong("id"), o.getString("name"), members)
    }

    private fun parseTodos(body: String): TeamApiResult<List<RemoteTeamTodo>> = try {
        val array: JSONArray = JSONObject(body).getJSONArray("todos")
        TeamApiResult.Success((0 until array.length()).map { parseTodo(array.getJSONObject(it)) })
    } catch (e: JSONException) {
        TeamApiResult.ServerError(200)
    }

    private fun parseSingleTodo(body: String): TeamApiResult<RemoteTeamTodo> = try {
        TeamApiResult.Success(parseTodo(JSONObject(body).getJSONObject("todo")))
    } catch (e: JSONException) {
        TeamApiResult.ServerError(200)
    }

    private fun parseTodo(o: JSONObject) = RemoteTeamTodo(
        id = o.getLong("id"),
        title = o.getString("title"),
        assignedTo = if (o.has("assigned_to") && !o.isNull("assigned_to")) o.getLong("assigned_to") else null,
        done = o.getBoolean("done"),
    )

    private fun rejectMessage(body: String): String =
        runCatching { JSONObject(body).optString("message") }.getOrNull()?.takeIf { it.isNotBlank() } ?: ""

    private fun failure(raw: Raw.Response): TeamApiResult<Nothing> = when (raw.code) {
        401 -> TeamApiResult.Unauthorized
        404 -> TeamApiResult.NotFound
        422 -> TeamApiResult.Rejected(rejectMessage(raw.body))
        else -> TeamApiResult.ServerError(raw.code)
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
