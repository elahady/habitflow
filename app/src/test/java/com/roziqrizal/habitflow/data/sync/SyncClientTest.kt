package com.roziqrizal.habitflow.data.sync

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import org.junit.Assert.assertNotNull

/** Server HTTP mini di soket sungguhan: cukup untuk satu permintaan per koneksi, tanpa dependensi. */
private class TinyServer {
    private val socket = ServerSocket(0, 10, InetAddress.getByName("127.0.0.1"))
    val port: Int get() = socket.localPort

    @Volatile var status = 200
    @Volatile var responseBody = "{}"
    @Volatile var responseHeaders = mapOf<String, String>()
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
        val head = StringBuilder("HTTP/1.1 $status X\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n")
        responseHeaders.forEach { (k, v) -> head.append("$k: $v\r\n") }
        head.append("\r\n")
        client.getOutputStream().apply {
            write(head.toString().toByteArray(Charsets.UTF_8))
            write(bytes)
            flush()
        }
    }
}

/** Menguji klien terhadap server HTTP sungguhan di JVM, tanpa mock. */
class SyncClientTest {

    private val server = TinyServer()
    private var status: Int
        get() = server.status
        set(value) { server.status = value }
    private var responseBody: String
        get() = server.responseBody
        set(value) { server.responseBody = value }
    private var responseHeaders: Map<String, String>
        get() = server.responseHeaders
        set(value) { server.responseHeaders = value }
    private val lastMethod get() = server.lastMethod
    private val lastPath get() = server.lastPath
    private val lastAuth get() = server.lastAuth
    private val lastBody get() = server.lastBody

    @Before
    fun start() = server.start()

    @After
    fun stop() = server.stop()

    private fun client(token: String = "token-uji") = SyncClient("http://127.0.0.1:${server.port}", token)

    @Test
    fun pingMengirimTokenSebagaiBearer() = runBlocking {
        responseBody = """{"ok":true}"""
        assertEquals(SyncResult.Ok, client("rahasia").ping())
        assertEquals("GET", lastMethod)
        assertEquals("/api/v1/ping", lastPath)
        assertEquals("Bearer rahasia", lastAuth)
    }

    @Test
    fun unggahMengirimJsonApaAdanyaDanMembacaRingkasan() = runBlocking {
        responseBody = """{"id":7,"receivedAt":1,"bytes":42,"unchanged":false}"""
        val result = client().upload("""{"schemaVersion":1,"data":{"a":"é"}}""")
        assertEquals(SyncResult.Uploaded(7, 42, false), result)
        assertEquals("PUT", lastMethod)
        assertEquals("/api/v1/snapshot", lastPath)
        assertEquals("""{"schemaVersion":1,"data":{"a":"é"}}""", lastBody)
    }

    @Test
    fun unggahYangSamaDilaporkanTidakBerubah() = runBlocking {
        responseBody = """{"id":7,"receivedAt":1,"bytes":42,"unchanged":true}"""
        val result = client().upload("{}") as SyncResult.Uploaded
        assertTrue(result.unchanged)
    }

    @Test
    fun unduhMengembalikanIsiPersisDanWaktuTerima() = runBlocking {
        responseBody = """{"schemaVersion":1,"data":{}}"""
        responseHeaders = mapOf("X-Received-At" to "1791350953000")
        val result = client().downloadLatest() as SyncResult.Downloaded
        assertEquals("""{"schemaVersion":1,"data":{}}""", result.body)
        assertEquals(1791350953000L, result.receivedAt)
        assertEquals("/api/v1/snapshot/latest", lastPath)
    }

    @Test
    fun belumAdaSnapshotMenjadiNoSnapshot() = runBlocking {
        status = 404
        responseBody = """{"message":"Belum ada snapshot."}"""
        assertEquals(SyncResult.NoSnapshot, client().downloadLatest())
    }

    @Test
    fun kodeGalatDipetakanKeHasilYangTepat() = runBlocking {
        status = 401; assertEquals(SyncResult.Unauthorized, client().ping())
        status = 413; assertEquals(SyncResult.TooLarge, client().upload("{}"))
        status = 429; assertEquals(SyncResult.RateLimited, client().ping())
        status = 500; assertEquals(SyncResult.ServerError(500), client().ping())
        status = 422; responseBody = """{"message":"Snapshot tidak sah."}"""
        assertEquals(SyncResult.Rejected("Snapshot tidak sah."), client().upload("{}"))
    }

    @Test
    fun serverTidakTerjangkauMenjadiNetworkError() = runBlocking {
        val port = server.port
        server.stop()
        val result = SyncClient("http://127.0.0.1:$port", "t", connectTimeoutMs = 1_000).ping()
        assertTrue(result is SyncResult.NetworkError)
        assertTrue(result.retryable)
    }

    @Test
    fun hanyaGalatJaringanDanServerYangBisaDiulang() {
        assertTrue(SyncResult.NetworkError("x").retryable)
        assertTrue(SyncResult.ServerError(503).retryable)
        assertTrue(SyncResult.RateLimited.retryable)
        assertFalse(SyncResult.Unauthorized.retryable)
        assertFalse(SyncResult.TooLarge.retryable)
        assertFalse(SyncResult.Rejected("x").retryable)
    }

    @Test
    fun urlHanyaHttpsKecualiLocalSaatDebug() {
        assertEquals("https://habitflow.contoh.com", SyncUrl.normalize(" https://habitflow.contoh.com/ ", false))
        assertNull(SyncUrl.normalize("http://habitflow.contoh.com", false))
        assertNull(SyncUrl.normalize("http://habitflow.contoh.com", true))
        assertNull(SyncUrl.normalize("http://10.0.2.2:8000", false))
        assertEquals("http://10.0.2.2:8000", SyncUrl.normalize("http://10.0.2.2:8000/", true))
        assertEquals("http://localhost:8000", SyncUrl.normalize("http://localhost:8000", true))
        assertNull(SyncUrl.normalize("", true))
        assertNull(SyncUrl.normalize("habitflow.contoh.com", true))
        assertNull(SyncUrl.normalize("ftp://habitflow.contoh.com", true))
        assertNull(SyncUrl.normalize("https://habit flow.com", true))
    }

    @Test
    fun pesanUntukPenggunaAdaUntukSetiapHasil() {
        listOf(
            SyncResult.Ok, SyncResult.Uploaded(1, 1, false), SyncResult.Uploaded(1, 1, true), SyncResult.Downloaded("", 0),
            SyncResult.NoSnapshot, SyncResult.NotConfigured, SyncResult.Unauthorized, SyncResult.TooLarge,
            SyncResult.RateLimited, SyncResult.Rejected("x"), SyncResult.ServerError(500), SyncResult.NetworkError("x"),
        ).forEach { assertTrue(it.message().isNotBlank()) }
    }
}
