package com.roziqrizal.habitflow.data.team

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

/** Server HTTP mini di soket sungguhan, pola sama dengan `HabitTinyServer` di `HabitSyncClientTest.kt`. */
private class TeamTinyServer {
    private val socket = ServerSocket(0, 10, InetAddress.getByName("127.0.0.1"))
    val port: Int get() = socket.localPort

    @Volatile var status = 200
    @Volatile var responseBody = "{}"
    @Volatile var lastMethod = ""
    @Volatile var lastPath = ""
    @Volatile var lastBody = ""

    private val thread = Thread {
        while (!socket.isClosed) {
            try {
                socket.accept().use { handle(it) }
            } catch (e: IOException) {
                // Soket ditutup saat tes selesai.
            }
        }
    }.apply { isDaemon = true }

    fun start() = thread.start()
    fun stop() = socket.close()

    private fun handle(client: Socket) {
        val input = client.getInputStream()
        fun readLine(): String {
            val line = StringBuilder()
            while (true) {
                val b = input.read()
                if (b == -1 || b == '\n'.code) break
                if (b != '\r'.code) line.append(b.toChar())
            }
            return line.toString()
        }
        val request = readLine().split(" ")
        lastMethod = request[0]
        lastPath = request[1]
        var length = 0
        while (true) {
            val header = readLine()
            if (header.isEmpty()) break
            if (header.substringBefore(":").lowercase() == "content-length") length = header.substringAfter(":").trim().toInt()
        }
        lastBody = if (length > 0) input.readNBytes(length).toString(Charsets.UTF_8) else ""

        val bytes = responseBody.toByteArray(Charsets.UTF_8)
        val head = "HTTP/1.1 $status X\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
        client.getOutputStream().apply {
            write(head.toByteArray(Charsets.UTF_8))
            write(bytes)
            flush()
        }
    }
}

class TeamClientTest {

    private val server = TeamTinyServer()
    private var status: Int
        get() = server.status
        set(value) { server.status = value }
    private var responseBody: String
        get() = server.responseBody
        set(value) { server.responseBody = value }
    private val lastMethod get() = server.lastMethod
    private val lastPath get() = server.lastPath
    private val lastBody get() = server.lastBody

    @Before
    fun start() = server.start()

    @After
    fun stop() = server.stop()

    private fun client() = TeamClient("token-uji", "http://127.0.0.1:${server.port}")

    @Test
    fun daftarTimMembacaAnggota() = runBlocking {
        responseBody = """{"teams":[{"id":1,"name":"Keluarga","members":[
            {"id":10,"name":"Roziq","avatar":null},{"id":11,"name":"Istri","avatar":null}
        ]}]}"""
        val result = client().listTeams() as TeamApiResult.Success
        assertEquals("GET", lastMethod)
        assertEquals("/api/v1/teams", lastPath)
        assertEquals(
            listOf(RemoteTeam(1, "Keluarga", listOf(TeamMember(10, "Roziq", null), TeamMember(11, "Istri", null)))),
            result.value,
        )
    }

    @Test
    fun buatUndanganMembacaTokenDanUrl() = runBlocking {
        status = 201
        responseBody = """{"token":"abc123","url":"https://habitflow.roziqrizal.com/invite/abc123","expires_at":"2026-10-12T00:00:00.000000Z"}"""
        val result = client().createInvite(1) as TeamApiResult.Success
        assertEquals("""{"team_id":1}""", lastBody)
        assertEquals("abc123", result.value.token)
        assertEquals("https://habitflow.roziqrizal.com/invite/abc123", result.value.url)
    }

    @Test
    fun terimaUndanganBerhasil() = runBlocking {
        responseBody = """{"team":{"id":1,"name":"Keluarga"}}"""
        val result = client().acceptInvite("abc123") as TeamApiResult.Success
        assertEquals("POST", lastMethod)
        assertEquals("/api/v1/teams/invites/abc123/accept", lastPath)
        assertEquals(1L, result.value.id)
    }

    @Test
    fun terimaUndanganKedaluwarsaMengembalikanInviteInvalid() = runBlocking {
        status = 410
        responseBody = """{"message":"Link undangan sudah tidak berlaku."}"""
        val result = client().acceptInvite("abc123") as TeamApiResult.InviteInvalid
        assertEquals("Link undangan sudah tidak berlaku.", result.detail)
    }

    @Test
    fun daftarTodoMembacaAssignedToNull() = runBlocking {
        responseBody = """{"todos":[{"id":1,"title":"Cuci piring","assigned_to":null,"done":false}]}"""
        val result = client().listTodos(1) as TeamApiResult.Success
        assertEquals("/api/v1/teams/1/todos", lastPath)
        assertNull(result.value.first().assignedTo)
    }

    @Test
    fun tugaskanTodoMengirimAssignedTo() = runBlocking {
        responseBody = """{"todo":{"id":1,"title":"Cuci piring","assigned_to":10,"done":false}}"""
        val result = client().assign(1, 10) as TeamApiResult.Success
        assertEquals("PUT", lastMethod)
        assertEquals("""{"assigned_to":10}""", lastBody)
        assertEquals(10L, result.value.assignedTo)
    }

    @Test
    fun hapusTodoBerhasil() = runBlocking {
        status = 204
        val result = client().deleteTodo(1)
        assertEquals("DELETE", lastMethod)
        assertEquals("/api/v1/teams/todos/1", lastPath)
        assert(result is TeamApiResult.Success)
    }
}
