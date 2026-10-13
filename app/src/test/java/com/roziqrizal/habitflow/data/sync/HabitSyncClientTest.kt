package com.roziqrizal.habitflow.data.sync

import com.roziqrizal.habitflow.data.FollowUpEntity
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
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
    fun jawaban401MelaporTokenYangDitolakKeSatuPenangan() = runBlocking {
        val rejected = mutableListOf<String>()
        val client = HabitSyncClient("tok-mati", "http://127.0.0.1:${server.port}", unauthorized = { rejected += it })

        status = 200
        client.listHabits("2026-10-10")
        assertTrue("jawaban sukses tidak boleh melapor", rejected.isEmpty())

        status = 401
        assertEquals(HabitApiResult.Unauthorized, client.listHabits("2026-10-10"))
        assertEquals(HabitApiResult.Unauthorized, client.createHabit("Baca", true))
        assertEquals(HabitApiResult.Unauthorized, client.deleteTodo(1))

        assertEquals(List(3) { "tok-mati" }, rejected)
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

private const val SERVER_FOLLOW_UP = """{"id":7,"title":"Tanya Budi","status":"ACTIVE","date":"2026-10-12","time":"09:30:00",
    "person":"Budi","note":null,"done_at":null,"picked_date":null,"event_id":null,"event_date":null,"event_title":null,
    "created_at":"2026-10-11T01:00:00.000000Z","updated_at":"2026-10-11T02:00:00.000000Z"}"""

/** Follow-up kerja (tahap 27 langkah 3). Memakai server mini file ini. */
class FollowUpSyncClientTest {

    private val server = HabitTinyServer()

    @Before
    fun start() = server.start()

    @After
    fun stop() = server.stop()

    private fun client() = HabitSyncClient("token-uji", "http://127.0.0.1:${server.port}")

    private fun entity(remoteUpdatedAt: String? = null) = FollowUpEntity(
        id = 3, title = "Tanya Budi", status = "ACTIVE", date = "2026-10-12", time = "09:30", person = "Budi", note = null,
        createdAt = 1_000, doneAt = null, pickedDate = null, remoteId = 7, remoteUpdatedAt = remoteUpdatedAt,
    )

    @Test
    fun daftarMembacaSemuaFieldDanMengubahWaktuServerJadiMilidetik() = runBlocking {
        server.responseBody = """{"follow_ups":[$SERVER_FOLLOW_UP]}"""
        val result = client().listFollowUps() as HabitApiResult.Success
        assertEquals("GET", server.lastMethod)
        assertEquals("/api/v1/follow-ups", server.lastPath)
        val item = result.value.single()
        assertEquals(7L, item.id)
        assertEquals("09:30", item.time)
        assertEquals("2026-10-12", item.date)
        assertEquals(null, item.note)
        assertEquals(1_791_680_400_000L, item.createdAt)
        assertEquals("2026-10-11T02:00:00.000000Z", item.updatedAt)
    }

    @Test
    fun jawaban401FollowUpMelaporTokenYangDitolakKeSatuPenangan() = runBlocking {
        val rejected = mutableListOf<String>()
        val client = HabitSyncClient("tok-mati", "http://127.0.0.1:${server.port}", unauthorized = { rejected += it })

        server.status = 401
        assertEquals(HabitApiResult.Unauthorized, client.listFollowUps())
        assertEquals(HabitApiResult.Unauthorized, client.createFollowUp(entity()))
        assertEquals(HabitApiResult.Unauthorized, client.deleteFollowUp(7))

        assertEquals(List(3) { "tok-mati" }, rejected)
    }

    @Test
    fun buatMengirimNilaiKosongSebagaiNullDanMembacaIdServer() = runBlocking {
        server.status = 201
        server.responseBody = """{"follow_up":$SERVER_FOLLOW_UP}"""
        val result = client().createFollowUp(entity().copy(date = null, time = null, person = null)) as HabitApiResult.Success
        assertEquals("POST", server.lastMethod)
        assertEquals("/api/v1/follow-ups", server.lastPath)
        val body = JSONObject(server.lastBody)
        assertEquals("Tanya Budi", body.getString("title"))
        assertEquals("ACTIVE", body.getString("status"))
        assertTrue(body.isNull("date") && body.isNull("time") && body.isNull("person") && body.isNull("done_at"))
        assertEquals(7L, result.value.id)
    }

    @Test
    fun ubahMengirimUpdatedAtDanJamHanyaLimaHuruf() = runBlocking {
        server.responseBody = """{"follow_up":$SERVER_FOLLOW_UP}"""
        val result = client().updateFollowUp(7, entity("2026-10-11T02:00:00.000000Z").copy(time = "09:30")) as HabitApiResult.Success
        assertEquals("PUT", server.lastMethod)
        assertEquals("/api/v1/follow-ups/7", server.lastPath)
        val body = JSONObject(server.lastBody)
        assertEquals("2026-10-11T02:00:00.000000Z", body.getString("updated_at"))
        assertEquals("09:30", body.getString("time"))
        assertEquals(7L, result.value.id)
    }

    @Test
    fun ubahBentrokMengembalikanVersiServer() = runBlocking {
        server.status = 409
        server.responseBody = """{"message":"Berubah di tempat lain.","follow_up":$SERVER_FOLLOW_UP}"""
        val result = client().updateFollowUp(7, entity("2026-10-11T01:00:00.000000Z"))
        assertEquals("Tanya Budi", (result as HabitApiResult.Conflict).server.title)
    }

    @Test
    fun hapusBerhasilDanTidakDitemukan() = runBlocking {
        server.status = 204
        server.responseBody = ""
        assertTrue(client().deleteFollowUp(7) is HabitApiResult.Success)
        assertEquals("DELETE", server.lastMethod)
        assertEquals("/api/v1/follow-ups/7", server.lastPath)
        server.status = 404
        assertEquals(HabitApiResult.NotFound, client().deleteFollowUp(7))
    }
}
