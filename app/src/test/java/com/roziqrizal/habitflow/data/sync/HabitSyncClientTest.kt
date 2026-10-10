package com.roziqrizal.habitflow.data.sync

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * Server HTTP mini di soket sungguhan, sama pola dengan `TinyServer` di `SyncClientTest.kt` (tanpa
 * dependensi mock). Nama beda supaya tidak bentrok - top-level `private` di Kotlin tetap satu
 * namespace per paket, bukan per file.
 */
private class HabitTinyServer {
    private val socket = ServerSocket(0, 10, InetAddress.getByName("127.0.0.1"))
    val port: Int get() = socket.localPort

    @Volatile var status = 200
    @Volatile var responseBody = "{}"
    @Volatile var lastMethod = ""
    @Volatile var lastPath = ""
    @Volatile var lastAuth: String? = null
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
        lastAuth = null
        while (true) {
            val header = readLine()
            if (header.isEmpty()) break
            val name = header.substringBefore(":").lowercase()
            val value = header.substringAfter(":").trim()
            if (name == "content-length") length = value.toInt()
            if (name == "authorization") lastAuth = value
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

class HabitSyncClientTest {

    private val server = HabitTinyServer()
    private var status: Int
        get() = server.status
        set(value) { server.status = value }
    private var responseBody: String
        get() = server.responseBody
        set(value) { server.responseBody = value }
    private val lastMethod get() = server.lastMethod
    private val lastPath get() = server.lastPath
    private val lastAuth get() = server.lastAuth
    private val lastBody get() = server.lastBody

    @Before
    fun start() = server.start()

    @After
    fun stop() = server.stop()

    private fun client(token: String = "token-uji") = HabitSyncClient(token, "http://127.0.0.1:${server.port}")

    @Test
    fun daftarHabitMengirimTanggalDanTokenSertaMembacaFieldDone() = runBlocking {
        responseBody = """{"habits":[
            {"id":1,"name":"Sholat","sort_order":0,"is_mandatory":true,"updated_at":"2026-10-10T01:00:00.000000Z","done":true},
            {"id":2,"name":"Baca","sort_order":1,"is_mandatory":false,"updated_at":"2026-10-10T01:00:00.000000Z","done":false}
        ]}"""
        val result = client("rahasia").listHabits("2026-10-10") as HabitApiResult.Success
        assertEquals("GET", lastMethod)
        assertEquals("/api/v1/habits?date=2026-10-10", lastPath)
        assertEquals("Bearer rahasia", lastAuth)
        assertEquals(
            listOf(
                RemoteHabit(1, "Sholat", 0, true, "2026-10-10T01:00:00.000000Z", true),
                RemoteHabit(2, "Baca", 1, false, "2026-10-10T01:00:00.000000Z", false),
            ),
            result.value,
        )
    }

    @Test
    fun buatHabitMengirimNamaDanWajibLaluMembacaHasilnya() = runBlocking {
        responseBody = """{"habit":{"id":9,"name":"Baru","sort_order":2,"is_mandatory":false,"updated_at":"2026-10-10T02:00:00.000000Z"}}"""
        status = 201
        val result = client().createHabit("Baru", false) as HabitApiResult.Success
        assertEquals("POST", lastMethod)
        assertEquals("/api/v1/habits", lastPath)
        assertEquals("""{"name":"Baru","is_mandatory":false}""", lastBody)
        assertEquals(RemoteHabit(9, "Baru", 2, false, "2026-10-10T02:00:00.000000Z"), result.value)
    }

    @Test
    fun ubahHabitBerhasil() = runBlocking {
        responseBody = """{"habit":{"id":1,"name":"Baru","sort_order":0,"is_mandatory":true,"updated_at":"2026-10-10T03:00:00.000000Z"}}"""
        val result = client().updateHabit(1, "Baru", true, 0, "2026-10-10T01:00:00.000000Z") as HabitApiResult.Success
        assertEquals("PUT", lastMethod)
        assertEquals("/api/v1/habits/1", lastPath)
        assertTrue(lastBody.contains(""""updated_at":"2026-10-10T01:00:00.000000Z""""))
        assertEquals("Baru", result.value.name)
    }

    @Test
    fun ubahHabitBentrokMengembalikanVersiServer() = runBlocking {
        status = 409
        responseBody = """{"message":"Berubah di tempat lain.","habit":{"id":1,"name":"Punya Orang Lain","sort_order":0,"is_mandatory":false,"updated_at":"2026-10-10T04:00:00.000000Z"}}"""
        val result = client().updateHabit(1, "Punyaku", false, 0, "lama") as HabitApiResult.Conflict
        assertEquals("Punya Orang Lain", result.server.name)
    }

    @Test
    fun hapusHabitBerhasilDanTidakDitemukan() = runBlocking {
        status = 204
        assertTrue(client().deleteHabit(1) is HabitApiResult.Success)
        assertEquals("DELETE", lastMethod)
        assertEquals("/api/v1/habits/1", lastPath)

        status = 404
        assertEquals(HabitApiResult.NotFound, client().deleteHabit(1))
    }

    @Test
    fun togglEntryMengirimTanggal() = runBlocking {
        val result = client().toggleEntry(1, "2026-10-10")
        assertTrue(result is HabitApiResult.Success)
        assertEquals("POST", lastMethod)
        assertEquals("/api/v1/habits/1/entries", lastPath)
        assertEquals("""{"date":"2026-10-10"}""", lastBody)
    }

    @Test
    fun daftarTodoMengambilSepuluhHurufPertamaTanggal() = runBlocking {
        responseBody = """{"todos":[{"id":1,"title":"Beli susu","date":"2026-10-10T00:00:00.000000Z","done":false,"updated_at":"2026-10-10T05:00:00.000000Z"}]}"""
        val result = client().listTodos("2026-10-10") as HabitApiResult.Success
        assertEquals("2026-10-10", result.value.single().date)
    }

    @Test
    fun buatTodoDitolakKarenaSudahLimaMenjadiRejected() = runBlocking {
        status = 422
        responseBody = """{"message":"Sudah 5 to-do untuk tanggal ini."}"""
        val result = client().createTodo("Baru", "2026-10-10") as HabitApiResult.Rejected
        assertEquals("Sudah 5 to-do untuk tanggal ini.", result.detail)
    }

    @Test
    fun kodeGalatDipetakanKeHasilYangTepat() = runBlocking {
        status = 401; assertEquals(HabitApiResult.Unauthorized, client().listHabits("2026-10-10"))
        status = 500; assertEquals(HabitApiResult.ServerError(500), client().deleteTodo(1))
    }

    @Test
    fun serverTidakTerjangkauMenjadiNetworkErrorDanRetryable() = runBlocking {
        val port = server.port
        server.stop()
        val result = HabitSyncClient("token", "http://127.0.0.1:$port", connectTimeoutMs = 1_000).listHabits("2026-10-10")
        assertTrue(result is HabitApiResult.NetworkError)
        assertTrue(result.retryable)
    }

    @Test
    fun hanyaGalatJaringanDanServerYangBisaDiulang() {
        assertTrue(HabitApiResult.NetworkError("x").retryable)
        assertTrue(HabitApiResult.ServerError(503).retryable)
        assertTrue(!HabitApiResult.Unauthorized.retryable)
        assertTrue(!HabitApiResult.NotFound.retryable)
        assertTrue(!HabitApiResult.Rejected("x").retryable)
    }
}
