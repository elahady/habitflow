package com.roziqrizal.habitflow.ui.auth

import com.roziqrizal.habitflow.data.account.Account
import com.roziqrizal.habitflow.data.account.AccountErrors
import com.roziqrizal.habitflow.data.account.AccountResult
import com.roziqrizal.habitflow.data.account.AuthOperations
import com.roziqrizal.habitflow.data.account.GoogleIdTokenResult
import com.roziqrizal.habitflow.data.account.SessionState
import com.roziqrizal.habitflow.data.account.SignInResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Operasi palsu: hasil tiap panggilan diatur per tes, dan setiap panggilan dicatat. */
private class FakeOps : AuthOperations {
    val calls = mutableListOf<String>()
    private val ani = Account(7, "ani@contoh.com", "Ani", null, "tok")

    var login: AccountResult<Account> = AccountResult.Success(ani)
    var register: AccountResult<Unit> = AccountResult.Success(Unit)
    var verify: AccountResult<Account> = AccountResult.Success(ani)
    var resend: AccountResult<Unit> = AccountResult.Success(Unit)
    var forgot: AccountResult<Unit> = AccountResult.Success(Unit)
    var reset: AccountResult<Unit> = AccountResult.Success(Unit)
    var google: SignInResult = SignInResult.Success(ani)

    /** Kalau diisi, [login] menunggu sampai selesai: dipakai menguji tombol yang ditekan dua kali. */
    var loginGate: CompletableDeferred<Unit>? = null

    override suspend fun register(name: String, email: String, password: String, passwordConfirmation: String, privacyPolicyVersion: String): AccountResult<Unit> {
        calls += "register $name|$email|$privacyPolicyVersion"
        return register
    }

    override suspend fun verifyEmail(email: String, code: String): AccountResult<Account> {
        calls += "verify $email $code"
        return verify
    }

    override suspend fun resendVerification(email: String): AccountResult<Unit> {
        calls += "resendVerification $email"
        return resend
    }

    override suspend fun login(email: String, password: String): AccountResult<Account> {
        calls += "login $email $password"
        loginGate?.await()
        return login
    }

    override suspend fun signInWithGoogleToken(idToken: String): SignInResult {
        calls += "google $idToken"
        return google
    }

    override suspend fun forgotPassword(email: String): AccountResult<Unit> {
        calls += "forgot $email"
        return forgot
    }

    override suspend fun resetPassword(email: String, code: String, password: String, passwordConfirmation: String): AccountResult<Unit> {
        calls += "reset $email $code $password"
        return reset
    }
}

class AuthStateMachineTest {

    private val ops = FakeOps()
    private var clock = 1_000_000L
    private val machine = AuthStateMachine(ops, now = { clock })
    private val s get() = machine.state.value

    private fun rejected(status: Int, code: String?, message: String) = AccountResult.Failed(status, code, message)

    private fun fillLogin(email: String = "ani@contoh.com", password: String = "rahasia123") {
        machine.onEmail(email)
        machine.onPassword(password)
    }

    private fun fillRegister() {
        machine.go(AuthScreen.REGISTER)
        machine.onName("  Ani  ")
        machine.onEmail("  Ani@Contoh.com ")
        machine.onPassword("rahasia123")
        machine.onPasswordConfirmation("rahasia123")
        machine.onConsent(true)
    }

    // --- masuk ---------------------------------------------------------------------------------

    @Test
    fun masukDenganEmailTidakSahTidakMenghubungiServer() = runBlocking {
        fillLogin(email = "bukan-email")

        machine.submitLogin()

        assertEquals(AuthMessages.EMAIL, s.fieldErrors[AuthField.EMAIL])
        assertTrue(ops.calls.isEmpty())
        assertFalse(s.busy)
    }

    @Test
    fun masukTanpaKataSandiDitolakLokal() = runBlocking {
        fillLogin(password = "")

        machine.submitLogin()

        assertEquals(AuthMessages.PASSWORD_REQUIRED, s.fieldErrors[AuthField.PASSWORD])
        assertTrue(ops.calls.isEmpty())
    }

    @Test
    fun masukBerhasilMengosongkanKataSandiDanTidakBerpindahLayar() = runBlocking {
        fillLogin(email = "  Ani@Contoh.COM ")

        machine.submitLogin()

        assertEquals(listOf("login ani@contoh.com rahasia123"), ops.calls)
        assertEquals("", s.password)
        assertNull(s.error)
        assertEquals(AuthScreen.LOGIN, s.screen)
    }

    @Test
    fun masukSalahMenampilkanPesanServer() = runBlocking {
        ops.login = rejected(422, AccountErrors.INVALID_CREDENTIALS, "Email atau kata sandi salah.")
        fillLogin()

        machine.submitLogin()

        assertEquals("Email atau kata sandi salah.", s.error)
        assertFalse(s.networkProblem)
        assertEquals("kata sandi tidak dihapus supaya bisa dikoreksi", "rahasia123", s.password)
    }

    @Test
    fun masukEmailBelumTerverifikasiMembukaVerifikasiDanMengirimKodeBaru() = runBlocking {
        ops.login = rejected(403, AccountErrors.EMAIL_NOT_VERIFIED, "Email belum diverifikasi.")
        fillLogin()

        machine.submitLogin()

        assertEquals(AuthScreen.VERIFY, s.screen)
        assertEquals("ani@contoh.com", s.email)
        assertEquals(listOf("login ani@contoh.com rahasia123", "resendVerification ani@contoh.com"), ops.calls)
        assertEquals(AuthMessages.VERIFY_RESENT_ON_LOGIN, s.notice)
        assertEquals(60, s.resendSecondsLeft(clock))
        assertEquals(600, s.codeSecondsLeft(clock))
        assertEquals("", s.password)
    }

    @Test
    fun masukBelumTerverifikasiTapiKirimKodeDibatasiTetapDiVerifikasiDenganPesan() = runBlocking {
        ops.login = rejected(403, AccountErrors.EMAIL_NOT_VERIFIED, "Email belum diverifikasi.")
        ops.resend = rejected(429, AccountErrors.TOO_MANY_ATTEMPTS, "Terlalu banyak percobaan. Coba lagi dalam 30 detik.")
        fillLogin()

        machine.submitLogin()

        assertEquals(AuthScreen.VERIFY, s.screen)
        assertEquals("Terlalu banyak percobaan. Coba lagi dalam 30 detik.", s.error)
        assertEquals("kedaluwarsa tidak diketahui kalau kode tidak jadi dikirim", 0, s.codeSecondsLeft(clock))
    }

    @Test
    fun masukTanpaInternetMenampilkanPesanTenangDenganWarnaNetral() = runBlocking {
        ops.login = AccountResult.Offline
        fillLogin()

        machine.submitLogin()

        assertEquals(AuthMessages.OFFLINE, s.error)
        assertTrue(s.networkProblem)
    }

    @Test
    fun masukSaatServerBermasalahTidakMenampilkanKodeHttp() = runBlocking {
        ops.login = rejected(500, null, "Server bermasalah (kode 500).")
        fillLogin()

        machine.submitLogin()

        assertEquals(AuthMessages.SERVER_PROBLEM, s.error)
    }

    @Test
    fun masukTerlaluSeringMenampilkanPesanServer() = runBlocking {
        ops.login = rejected(429, AccountErrors.TOO_MANY_ATTEMPTS, "Terlalu banyak percobaan. Coba lagi dalam 42 detik.")
        fillLogin()

        machine.submitLogin()

        assertEquals("Terlalu banyak percobaan. Coba lagi dalam 42 detik.", s.error)
    }

    @Test
    fun tombolDitekanDuaKaliHanyaMengirimSatuPermintaan() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        ops.loginGate = gate
        fillLogin()

        val first = async { machine.submitLogin() }
        // Yang pertama sudah berjalan dan menunggu server: yang kedua harus diabaikan.
        while (!s.busy) yield()
        machine.submitLogin()
        assertTrue(s.busy)
        gate.complete(Unit)
        first.await()

        assertEquals(1, ops.calls.count { it.startsWith("login") })
        assertFalse(s.busy)
    }

    // --- daftar --------------------------------------------------------------------------------

    @Test
    fun daftarMemeriksaSetiapKolomSebelumMenghubungiServer() = runBlocking {
        machine.go(AuthScreen.REGISTER)

        machine.submitRegister()

        assertEquals(
            setOf(AuthField.NAME, AuthField.EMAIL, AuthField.PASSWORD, AuthField.CONSENT),
            s.fieldErrors.keys,
        )
        assertTrue(ops.calls.isEmpty())
    }

    @Test
    fun daftarKataSandiTidakCocokDanTerlaluPendek() = runBlocking {
        fillRegister()
        machine.onPassword("pendek")
        machine.onPasswordConfirmation("lain")

        machine.submitRegister()

        assertEquals(AuthMessages.PASSWORD_TOO_SHORT, s.fieldErrors[AuthField.PASSWORD])
        assertEquals(AuthMessages.CONFIRMATION, s.fieldErrors[AuthField.CONFIRMATION])
        assertTrue(ops.calls.isEmpty())
    }

    @Test
    fun daftarTanpaPersetujuanKebijakanPrivasiDitolak() = runBlocking {
        fillRegister()
        machine.onConsent(false)

        machine.submitRegister()

        assertEquals(AuthMessages.CONSENT, s.fieldErrors[AuthField.CONSENT])
        assertTrue(ops.calls.isEmpty())
    }

    @Test
    fun daftarBerhasilKeVerifikasiDenganHitungMundurDanKataSandiDikosongkan() = runBlocking {
        fillRegister()

        machine.submitRegister()

        assertEquals(listOf("register Ani|ani@contoh.com|1"), ops.calls)
        assertEquals(AuthScreen.VERIFY, s.screen)
        assertEquals("ani@contoh.com", s.email)
        assertEquals("", s.password)
        assertEquals("", s.passwordConfirmation)
        assertEquals(60, s.resendSecondsLeft(clock))
        assertEquals(600, s.codeSecondsLeft(clock))
    }

    @Test
    fun daftarEmailSudahTerdaftarTetapDiLayarDaftarDenganPesan() = runBlocking {
        ops.register = rejected(422, AccountErrors.EMAIL_TAKEN, "Email ini sudah terdaftar. Silakan masuk.")
        fillRegister()

        machine.submitRegister()

        assertEquals(AuthScreen.REGISTER, s.screen)
        assertEquals("Email ini sudah terdaftar. Silakan masuk.", s.error)
    }

    @Test
    fun daftarKataSandiUmumDitolakServerDenganPesannya() = runBlocking {
        ops.register = rejected(422, AccountErrors.VALIDATION, "Kata sandi terlalu umum.")
        fillRegister()

        machine.submitRegister()

        assertEquals("Kata sandi terlalu umum.", s.error)
        assertEquals(AuthScreen.REGISTER, s.screen)
    }

    @Test
    fun daftarTanpaInternet() = runBlocking {
        ops.register = AccountResult.Offline
        fillRegister()

        machine.submitRegister()

        assertEquals(AuthScreen.REGISTER, s.screen)
        assertTrue(s.networkProblem)
    }

    // --- verifikasi ----------------------------------------------------------------------------

    private fun onVerifyScreen() {
        machine.go(AuthScreen.VERIFY)
        machine.onEmail("ani@contoh.com")
    }

    @Test
    fun kodeYangDitempelDibersihkanDanDipotongEnamAngka() {
        machine.onCode(" 123 456 ")
        assertEquals("123456", s.code)
        machine.onCode("12-34-56-78")
        assertEquals("123456", s.code)
        machine.onCode("ab12c")
        assertEquals("12", s.code)
    }

    @Test
    fun verifikasiKodeBelumLengkapTidakMenghubungiServer() = runBlocking {
        onVerifyScreen()
        machine.onCode("123")

        machine.submitVerify()

        assertEquals(AuthMessages.CODE_INCOMPLETE, s.fieldErrors[AuthField.CODE])
        assertTrue(ops.calls.isEmpty())
    }

    @Test
    fun verifikasiBerhasilMengirimEmailDanKode() = runBlocking {
        onVerifyScreen()
        machine.onCode("123456")

        machine.submitVerify()

        assertEquals(listOf("verify ani@contoh.com 123456"), ops.calls)
        assertNull(s.error)
    }

    @Test
    fun verifikasiKodeSalahMengosongkanKolomDanMenjelaskan() = runBlocking {
        ops.verify = rejected(422, AccountErrors.CODE_INVALID, "Kode salah.")
        onVerifyScreen()
        machine.onCode("000000")

        machine.submitVerify()

        assertEquals(AuthMessages.CODE_WRONG, s.error)
        assertEquals("", s.code)
        assertEquals(AuthScreen.VERIFY, s.screen)
    }

    @Test
    fun verifikasiKodeKedaluwarsaMenampilkanPesanServer() = runBlocking {
        ops.verify = rejected(422, AccountErrors.CODE_EXPIRED, "Kode sudah kedaluwarsa. Minta kode baru.")
        onVerifyScreen()
        machine.onCode("123456")

        machine.submitVerify()

        assertEquals("Kode sudah kedaluwarsa. Minta kode baru.", s.error)
        assertEquals("", s.code)
    }

    @Test
    fun verifikasiTanpaInternetMempertahankanKode() = runBlocking {
        ops.verify = AccountResult.Offline
        onVerifyScreen()
        machine.onCode("123456")

        machine.submitVerify()

        assertTrue(s.networkProblem)
        assertEquals("kode tidak hilang kalau hanya jaringan yang bermasalah", "123456", s.code)
    }

    // --- kirim ulang ---------------------------------------------------------------------------

    @Test
    fun kirimUlangDiabaikanSelamaHitungMundurBerjalan() = runBlocking {
        fillRegister()
        machine.submitRegister()
        ops.calls.clear()
        clock += 30_000

        machine.resendCode()

        assertTrue(ops.calls.isEmpty())
        assertEquals(30, s.resendSecondsLeft(clock))
    }

    @Test
    fun kirimUlangSetelahHitungMundurHabisMemulaiHitunganBaru() = runBlocking {
        fillRegister()
        machine.submitRegister()
        ops.calls.clear()
        clock += 61_000

        machine.resendCode()

        assertEquals(listOf("resendVerification ani@contoh.com"), ops.calls)
        assertEquals(AuthMessages.codeResent("ani@contoh.com"), s.notice)
        assertEquals(60, s.resendSecondsLeft(clock))
        assertEquals(600, s.codeSecondsLeft(clock))
    }

    @Test
    fun kirimUlangDiLayarResetMemintaKodeLupaKataSandi() = runBlocking {
        machine.go(AuthScreen.FORGOT)
        machine.onEmail("ani@contoh.com")
        machine.submitForgot()
        ops.calls.clear()
        clock += 61_000

        machine.resendCode()

        assertEquals(listOf("forgot ani@contoh.com"), ops.calls)
    }

    @Test
    fun kirimUlangDibatasiServerMenampilkanPesannya() = runBlocking {
        onVerifyScreen()
        ops.resend = rejected(429, AccountErrors.TOO_MANY_ATTEMPTS, "Terlalu banyak percobaan. Coba lagi dalam 120 detik.")

        machine.resendCode()

        assertEquals("Terlalu banyak percobaan. Coba lagi dalam 120 detik.", s.error)
    }

    @Test
    fun hitungMundurTidakNegatifSetelahHabis() {
        val state = AuthUiState(resendAvailableAt = 100, codeExpiresAt = 100)
        assertEquals(0, state.resendSecondsLeft(5_000))
        assertEquals(0, state.codeSecondsLeft(5_000))
        assertEquals(1, state.resendSecondsLeft(99))
    }

    // --- lupa dan reset kata sandi --------------------------------------------------------------

    @Test
    fun lupaKataSandiEmailTidakSah() = runBlocking {
        machine.go(AuthScreen.FORGOT)
        machine.onEmail("salah")

        machine.submitForgot()

        assertEquals(AuthMessages.EMAIL, s.fieldErrors[AuthField.EMAIL])
        assertTrue(ops.calls.isEmpty())
    }

    @Test
    fun lupaKataSandiBerhasilKeResetTanpaMembocorkanApakahEmailTerdaftar() = runBlocking {
        machine.go(AuthScreen.FORGOT)
        machine.onEmail(" Ani@Contoh.com ")

        machine.submitForgot()

        assertEquals(listOf("forgot ani@contoh.com"), ops.calls)
        assertEquals(AuthScreen.RESET, s.screen)
        assertEquals(AuthMessages.RESET_SENT, s.notice)
        assertEquals(60, s.resendSecondsLeft(clock))
    }

    @Test
    fun lupaKataSandiTanpaInternetTetapDiLayarLupa() = runBlocking {
        ops.forgot = AccountResult.Offline
        machine.go(AuthScreen.FORGOT)
        machine.onEmail("ani@contoh.com")

        machine.submitForgot()

        assertEquals(AuthScreen.FORGOT, s.screen)
        assertTrue(s.networkProblem)
    }

    private fun onResetScreen() {
        machine.go(AuthScreen.RESET)
        machine.onEmail("ani@contoh.com")
        machine.onCode("654321")
        machine.onPassword("baru12345")
        machine.onPasswordConfirmation("baru12345")
    }

    @Test
    fun resetMemeriksaKodeDanKataSandiSebelumMenghubungiServer() = runBlocking {
        machine.go(AuthScreen.RESET)
        machine.onPassword("pendek")
        machine.onPasswordConfirmation("lain")

        machine.submitReset()

        assertEquals(setOf(AuthField.CODE, AuthField.PASSWORD, AuthField.CONFIRMATION), s.fieldErrors.keys)
        assertTrue(ops.calls.isEmpty())
    }

    @Test
    fun resetBerhasilKembaliKeMasukDenganPesanDanKolomBersih() = runBlocking {
        onResetScreen()

        machine.submitReset()

        assertEquals(listOf("reset ani@contoh.com 654321 baru12345"), ops.calls)
        assertEquals(AuthScreen.LOGIN, s.screen)
        assertEquals(AuthMessages.PASSWORD_CHANGED, s.notice)
        assertEquals("ani@contoh.com", s.email)
        assertEquals("", s.password)
        assertEquals("", s.code)
    }

    @Test
    fun resetKodeSalahMengosongkanKodeTapiMempertahankanKataSandiBaru() = runBlocking {
        ops.reset = rejected(422, AccountErrors.CODE_INVALID, "Kode salah.")
        onResetScreen()

        machine.submitReset()

        assertEquals(AuthMessages.CODE_WRONG, s.error)
        assertEquals("", s.code)
        assertEquals("baru12345", s.password)
        assertEquals(AuthScreen.RESET, s.screen)
    }

    @Test
    fun resetKataSandiUmumDitolakServer() = runBlocking {
        ops.reset = rejected(422, AccountErrors.VALIDATION, "Kata sandi terlalu umum.")
        onResetScreen()

        machine.submitReset()

        assertEquals("Kata sandi terlalu umum.", s.error)
        assertEquals("654321", s.code)
    }

    // --- Google --------------------------------------------------------------------------------

    @Test
    fun googleDibatalkanPenggunaTidakMenampilkanGalat() = runBlocking {
        machine.signInWithGoogle { GoogleIdTokenResult.Cancelled }

        assertNull(s.error)
        assertTrue(ops.calls.isEmpty())
        assertFalse(s.busy)
    }

    @Test
    fun googleGagalMemintaTokenMenampilkanKalimatTenang() = runBlocking {
        machine.signInWithGoogle { GoogleIdTokenResult.Failed("detail teknis") }

        assertEquals(AuthMessages.GOOGLE_FAILED, s.error)
        assertTrue("detail teknis tidak ditampilkan", s.error?.contains("teknis") == false)
        assertTrue(ops.calls.isEmpty())
    }

    @Test
    fun googleBerhasilMengirimTokenKeServer() = runBlocking {
        machine.signInWithGoogle { GoogleIdTokenResult.Success("id-token") }

        assertEquals(listOf("google id-token"), ops.calls)
        assertNull(s.error)
    }

    @Test
    fun googleDitolakServerMenampilkanPesan() = runBlocking {
        ops.google = SignInResult.Failed("Token Google tidak sah.")

        machine.signInWithGoogle { GoogleIdTokenResult.Success("id-token") }

        assertEquals("Token Google tidak sah.", s.error)
    }

    @Test
    fun googleTanpaInternet() = runBlocking {
        ops.google = SignInResult.Offline

        machine.signInWithGoogle { GoogleIdTokenResult.Success("id-token") }

        assertTrue(s.networkProblem)
    }

    // --- navigasi dan sesi ---------------------------------------------------------------------

    @Test
    fun pindahLayarMempertahankanEmailTapiMengosongkanKataSandiDanGalat() = runBlocking {
        fillLogin()
        machine.submitLogin() // sukses: password kosong
        machine.onPassword("rahasia123")
        ops.login = rejected(422, AccountErrors.INVALID_CREDENTIALS, "Email atau kata sandi salah.")
        machine.submitLogin()
        assertTrue(s.error != null)

        machine.go(AuthScreen.FORGOT)

        assertEquals("ani@contoh.com", s.email)
        assertEquals("", s.password)
        assertNull(s.error)
        assertEquals(AuthScreen.FORGOT, s.screen)
    }

    @Test
    fun mengetikMenghapusGalatDanGalatKolomItuSaja() = runBlocking {
        machine.go(AuthScreen.REGISTER)
        machine.submitRegister()
        assertTrue(AuthField.NAME in s.fieldErrors && AuthField.EMAIL in s.fieldErrors)

        machine.onName("Ani")

        assertFalse(AuthField.NAME in s.fieldErrors)
        assertTrue(AuthField.EMAIL in s.fieldErrors)
    }

    @Test
    fun sesiBerakhirMembukaMasukDenganBannerDanEmailTerisi() {
        machine.applySession(SessionState.Expired("ani@contoh.com", "Ani"))

        assertEquals(AuthScreen.LOGIN, s.screen)
        assertTrue(s.sessionExpired)
        assertEquals("ani@contoh.com", s.email)
    }

    @Test
    fun menerapkanSesiYangSamaBerulangTidakMenghapusIsianPengguna() {
        machine.applySession(SessionState.Expired("ani@contoh.com", "Ani"))
        machine.onPassword("sedang-diketik")

        machine.applySession(SessionState.Expired("ani@contoh.com", "Ani"))

        assertEquals("sedang-diketik", s.password)
    }

    @Test
    fun belumMasukDiAwalTidakMengosongkanIsianYangSudahDiketik() {
        machine.onEmail("ani@contoh.com")

        machine.applySession(SessionState.SignedOut)

        assertEquals("ani@contoh.com", s.email)
    }

    @Test
    fun masukKemudianKeluarMengosongkanSemuaIsian() {
        machine.applySession(SessionState.SignedOut)
        machine.onEmail("ani@contoh.com")
        machine.onPassword("rahasia123")

        machine.applySession(SessionState.SignedIn(Account(7, "ani@contoh.com", "Ani", null, "tok")))
        assertEquals("", s.password)
        machine.onPassword("sisa")
        machine.applySession(SessionState.SignedOut)

        assertEquals(AuthUiState(), s)
    }
}
