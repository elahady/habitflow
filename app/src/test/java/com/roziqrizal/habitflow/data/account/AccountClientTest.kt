package com.roziqrizal.habitflow.data.account

import com.roziqrizal.habitflow.testing.FakeSharedPreferences
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

/** Server HTTP mini di soket sungguhan: satu permintaan per koneksi, tanpa dependensi. */
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

class AccountClientTest {

    private val server = TinyServer()
    private val rejectedTokens = mutableListOf<String>()

    @Before
    fun start() = server.start()

    @After
    fun stop() = server.stop()

    private fun client(baseUrl: String = "http://127.0.0.1:${server.port}") =
        AccountClient(baseUrl, unauthorized = { rejectedTokens += it })

    private fun reply(status: Int, body: String = "{}", headers: Map<String, String> = emptyMap()) {
        server.status = status
        server.responseBody = body
        server.responseHeaders = headers
    }

    private val sessionBody =
        """{"token":"tok-123","account":{"id":7,"name":"Ani","email":"ani@contoh.com","avatar":null}}"""
    private val ani = Account(7, "ani@contoh.com", "Ani", null, "tok-123")

    private fun sentBody() = JSONObject(server.lastBody)

    // --- tanpa token -----------------------------------------------------------------------------

    @Test
    fun daftarMengirimSemuaIsianKontrak() = runBlocking {
        reply(201, """{"status":"verification_required","email":"ani@contoh.com"}""")

        val result = client().register("Ani", "ani@contoh.com", "rahasia123", "rahasia123", "1")

        assertEquals(AccountResult.Success(Unit), result)
        assertEquals("POST", server.lastMethod)
        assertEquals("/api/v1/auth/register", server.lastPath)
        assertNull("daftar tidak membawa token", server.lastAuth)
        val body = sentBody()
        assertEquals("Ani", body.getString("name"))
        assertEquals("ani@contoh.com", body.getString("email"))
        assertEquals("rahasia123", body.getString("password"))
        assertEquals("rahasia123", body.getString("password_confirmation"))
        assertEquals("1", body.getString("privacy_policy_version"))
    }

    @Test
    fun verifikasiMengembalikanAkunBerikutTokennya() = runBlocking {
        reply(200, sessionBody)

        val result = client().verifyEmail("ani@contoh.com", "123456", "Pixel 7")

        assertEquals(AccountResult.Success(ani), result)
        assertEquals("/api/v1/auth/verify-email", server.lastPath)
        assertEquals("123456", sentBody().getString("code"))
        assertEquals("Pixel 7", sentBody().getString("device_name"))
    }

    @Test
    fun masukEmailMengembalikanAkunBerikutTokennya() = runBlocking {
        reply(200, sessionBody)

        val result = client().login("ani@contoh.com", "rahasia123", "Pixel 7")

        assertEquals(AccountResult.Success(ani), result)
        assertEquals("/api/v1/auth/login", server.lastPath)
        assertEquals("rahasia123", sentBody().getString("password"))
    }

    @Test
    fun masukGoogleMengirimIdTokenDanMembacaAccountBukanUserLama() = runBlocking {
        // Server masih mengirim `user` berdampingan dengan `account` selama masa peralihan; klien hanya membaca `account`.
        reply(
            200,
            """{"token":"tok-123","account":{"id":7,"name":"Ani","email":"ani@contoh.com","avatar":"https://x/a.png"},""" +
                """"user":{"id":99,"name":"Salah","email":"salah@contoh.com","avatar":null}}""",
        )

        val result = client().loginWithGoogle("id-token-google", "Pixel 7") as AccountResult.Success

        assertEquals(ani.copy(avatar = "https://x/a.png"), result.value)
        assertEquals("/api/v1/auth/google", server.lastPath)
        assertEquals("id-token-google", sentBody().getString("id_token"))
    }

    @Test
    fun kirimUlangLupaDanResetMemakaiJalurKontrak() = runBlocking {
        reply(202, """{"status":"accepted"}""")
        assertEquals(AccountResult.Success(Unit), client().resendVerification("ani@contoh.com"))
        assertEquals("/api/v1/auth/resend-verification", server.lastPath)

        assertEquals(AccountResult.Success(Unit), client().forgotPassword("ani@contoh.com"))
        assertEquals("/api/v1/auth/forgot-password", server.lastPath)

        reply(204, "")
        assertEquals(AccountResult.Success(Unit), client().resetPassword("ani@contoh.com", "654321", "baru12345", "baru12345"))
        assertEquals("/api/v1/auth/reset-password", server.lastPath)
        assertEquals("654321", sentBody().getString("code"))
        assertEquals("baru12345", sentBody().getString("password_confirmation"))
    }

    // --- dengan token ----------------------------------------------------------------------------

    @Test
    fun keluarMengirimTokenSebagaiBearer() = runBlocking {
        reply(204, "")

        assertEquals(AccountResult.Success(Unit), client().logout("rahasia"))

        assertEquals("POST", server.lastMethod)
        assertEquals("/api/v1/auth/logout", server.lastPath)
        assertEquals("Bearer rahasia", server.lastAuth)
    }

    @Test
    fun keluarDariSemuaPerangkat() = runBlocking {
        reply(204, "")
        assertEquals(AccountResult.Success(Unit), client().logoutAll("rahasia"))
        assertEquals("/api/v1/auth/logout-all", server.lastPath)
        assertEquals("Bearer rahasia", server.lastAuth)
    }

    @Test
    fun ubahKataSandiMengirimSandiSaatIniHanyaKalauAda() = runBlocking {
        reply(204, "")

        client().changePassword("rahasia", "lama123", "baru12345", "baru12345")
        assertEquals("PUT", server.lastMethod)
        assertEquals("/api/v1/account/password", server.lastPath)
        assertEquals("lama123", sentBody().getString("current_password"))

        client().changePassword("rahasia", null, "baru12345", "baru12345")
        assertTrue("akun Google tanpa sandi tidak mengirim current_password", !sentBody().has("current_password"))
    }

    @Test
    fun hapusAkunMengirimSandiAtauIdTokenGoogle() = runBlocking {
        reply(204, "")

        client().deleteAccount("rahasia", "sandi-saya", null)
        assertEquals("DELETE", server.lastMethod)
        assertEquals("/api/v1/account", server.lastPath)
        assertEquals("sandi-saya", sentBody().getString("password"))
        assertTrue(!sentBody().has("id_token"))

        client().deleteAccount("rahasia", null, "id-token-google")
        assertEquals("id-token-google", sentBody().getString("id_token"))
        assertTrue(!sentBody().has("password"))
    }

    @Test
    fun meMembacaAkunDanStatusDenganTokenYangDikirim() = runBlocking {
        reply(
            200,
            """{"account":{"id":7,"name":"Ani","email":"ani@contoh.com","avatar":null},""" +
                """"has_password":false,"google_linked":true,"email_verified":true}""",
        )

        val result = client().me("tok-123")

        assertEquals(AccountResult.Success(MeInfo(ani, hasPassword = false, googleLinked = true, emailVerified = true)), result)
        assertEquals("GET", server.lastMethod)
        assertEquals("/api/v1/me", server.lastPath)
    }

    // --- galat -----------------------------------------------------------------------------------

    @Test
    fun emailBelumTerverifikasiMembawaKodeSupayaAppBisaMembukaLayarVerifikasi() = runBlocking {
        reply(403, """{"message":"Email belum diverifikasi.","code":"email_not_verified"}""")

        val result = client().login("ani@contoh.com", "rahasia123", "Pixel 7")

        assertEquals(AccountResult.Failed(403, AccountErrors.EMAIL_NOT_VERIFIED, "Email belum diverifikasi."), result)
    }

    @Test
    fun kodeSalahDanKedaluwarsaDibedakan() = runBlocking {
        reply(422, """{"message":"Kode salah.","code":"code_invalid"}""")
        assertEquals(
            AccountResult.Failed(422, AccountErrors.CODE_INVALID, "Kode salah."),
            client().verifyEmail("ani@contoh.com", "000000", "Pixel 7"),
        )

        reply(422, """{"message":"Kode sudah kedaluwarsa. Minta kode baru.","code":"code_expired"}""")
        assertEquals(
            AccountResult.Failed(422, AccountErrors.CODE_EXPIRED, "Kode sudah kedaluwarsa. Minta kode baru."),
            client().verifyEmail("ani@contoh.com", "000000", "Pixel 7"),
        )
    }

    @Test
    fun terlaluSeringMembawaJedaDariHeaderRetryAfter() = runBlocking {
        reply(
            429,
            """{"message":"Terlalu banyak percobaan. Coba lagi dalam 42 detik.","code":"too_many_attempts"}""",
            mapOf("Retry-After" to "42"),
        )

        val result = client().login("ani@contoh.com", "salah", "Pixel 7")

        assertEquals(
            AccountResult.Failed(429, AccountErrors.TOO_MANY_ATTEMPTS, "Terlalu banyak percobaan. Coba lagi dalam 42 detik.", 42),
            result,
        )
    }

    @Test
    fun galatValidasiLaravelMemakaiPesanFieldPertamaBukanRingkasan() = runBlocking {
        reply(
            422,
            """{"message":"The password field must be at least 8 characters. (and 1 more error)",""" +
                """"errors":{"password":["Kata sandi minimal 8 karakter."],"email":["Email tidak sah."]}}""",
        )

        val result = client().register("Ani", "ani@contoh.com", "pendek", "pendek", "1") as AccountResult.Failed

        assertEquals(422, result.status)
        assertEquals(AccountErrors.VALIDATION, result.code)
        assertEquals("Kata sandi minimal 8 karakter.", result.message)
    }

    @Test
    fun emailSudahTerdaftarMembawaKodenya() = runBlocking {
        reply(422, """{"message":"Email ini sudah terdaftar. Silakan masuk.","code":"email_taken"}""")

        val result = client().register("Ani", "ani@contoh.com", "rahasia123", "rahasia123", "1") as AccountResult.Failed

        assertEquals(AccountErrors.EMAIL_TAKEN, result.code)
    }

    @Test
    fun galatServerTanpaIsiMendapatKalimatBawaan() = runBlocking {
        reply(500, "<html>boom</html>")

        val result = client().login("ani@contoh.com", "rahasia123", "Pixel 7")

        assertEquals(AccountResult.Failed(500, null, "Server bermasalah (kode 500)."), result)
    }

    @Test
    fun jawabanSuksesYangBukanJsonSahBukanKeberhasilan() = runBlocking {
        reply(200, "bukan json")

        val result = client().login("ani@contoh.com", "rahasia123", "Pixel 7")

        assertEquals(AccountResult.Failed(200, null, "Jawaban server tidak dikenali."), result)
    }

    @Test
    fun jawabanSuksesTanpaTokenBukanKeberhasilan() = runBlocking {
        reply(200, """{"account":{"id":7,"name":"Ani","email":"ani@contoh.com","avatar":null}}""")

        assertTrue(client().login("ani@contoh.com", "rahasia123", "Pixel 7") is AccountResult.Failed)
    }

    // --- 401 dan tanpa internet -------------------------------------------------------------------

    @Test
    fun tokenDitolakMenghasilkanUnauthorizedDanMelaporKeSatuTempat() = runBlocking {
        reply(401, """{"message":"Unauthenticated."}""")

        assertEquals(AccountResult.Unauthorized, client().me("tok-mati"))
        assertEquals(AccountResult.Unauthorized, client().changePassword("tok-mati", "a", "b", "b"))
        assertEquals(AccountResult.Unauthorized, client().deleteAccount("tok-mati", "a", null))
        assertEquals(AccountResult.Unauthorized, client().logout("tok-mati"))
        assertEquals(AccountResult.Unauthorized, client().logoutAll("tok-mati"))

        assertEquals(List(5) { "tok-mati" }, rejectedTokens)
    }

    @Test
    fun jawaban401TanpaTokenBukanSesiBerakhir() = runBlocking {
        reply(401, """{"message":"Salah."}""")

        val result = client().login("ani@contoh.com", "rahasia123", "Pixel 7")

        assertEquals(AccountResult.Failed(401, null, "Salah."), result)
        assertTrue("tidak ada sesi yang bisa berakhir", rejectedTokens.isEmpty())
    }

    @Test
    fun jawaban401MenjadikanSesiBerakhirLewatSessionGuard() = runBlocking {
        val settings = AccountSettings(FakeSharedPreferences())
        settings.save(ani)
        reply(401, """{"message":"Unauthenticated."}""")

        AccountClient("http://127.0.0.1:${server.port}", SessionGuard(settings)).me("tok-123")

        assertEquals(SessionState.Expired("ani@contoh.com", "Ani"), settings.session.value)
    }

    @Test
    fun jawaban401UntukTokenLamaTidakMematikanSesiBaru() = runBlocking {
        val settings = AccountSettings(FakeSharedPreferences())
        val baru = ani.copy(token = "tok-baru")
        settings.save(baru)
        reply(401, """{"message":"Unauthenticated."}""")

        AccountClient("http://127.0.0.1:${server.port}", SessionGuard(settings)).me("tok-123")

        assertEquals(SessionState.SignedIn(baru), settings.session.value)
    }

    @Test
    fun serverTidakTerjangkauMenjadiOffline() = runBlocking {
        val port = server.port
        server.stop()

        val result = client("http://127.0.0.1:$port").login("ani@contoh.com", "rahasia123", "Pixel 7")

        assertEquals(AccountResult.Offline, result)
    }

    @Test
    fun alamatTidakSahMenjadiOffline() = runBlocking {
        assertEquals(AccountResult.Offline, client("ftp://tidak-sah").me("tok"))
    }
}
