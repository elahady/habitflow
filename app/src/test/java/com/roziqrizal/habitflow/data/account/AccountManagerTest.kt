package com.roziqrizal.habitflow.data.account

import com.roziqrizal.habitflow.testing.FakeSharedPreferences
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Klien palsu: hasil tiap panggilan diatur per tes, dan setiap panggilan dicatat. */
private class FakeAccountApi : AccountApi {
    val calls = mutableListOf<String>()
    var next: AccountResult<*> = AccountResult.Success(Unit)

    @Suppress("UNCHECKED_CAST")
    private fun <T> answer(call: String): AccountResult<T> {
        calls += call
        return next as AccountResult<T>
    }

    override suspend fun register(name: String, email: String, password: String, passwordConfirmation: String, privacyPolicyVersion: String) =
        answer<Unit>("register $name $email $privacyPolicyVersion")

    override suspend fun verifyEmail(email: String, code: String, deviceName: String) =
        answer<Account>("verifyEmail $email $code $deviceName")

    override suspend fun resendVerification(email: String) = answer<Unit>("resendVerification $email")

    override suspend fun login(email: String, password: String, deviceName: String) =
        answer<Account>("login $email $deviceName")

    override suspend fun loginWithGoogle(idToken: String, deviceName: String, privacyPolicyVersion: String?) =
        answer<Account>("google $idToken $deviceName $privacyPolicyVersion")

    override suspend fun forgotPassword(email: String) = answer<Unit>("forgotPassword $email")

    override suspend fun resetPassword(email: String, code: String, password: String, passwordConfirmation: String) =
        answer<Unit>("resetPassword $email $code")

    override suspend fun logout(token: String) = answer<Unit>("logout $token")

    override suspend fun logoutAll(token: String) = answer<Unit>("logoutAll $token")

    override suspend fun changePassword(token: String, currentPassword: String?, password: String, passwordConfirmation: String) =
        answer<Unit>("changePassword $token $currentPassword")

    override suspend fun deleteAccount(token: String, password: String?, googleIdToken: String?) =
        answer<Unit>("deleteAccount $token $password $googleIdToken")

    override suspend fun me(token: String) = answer<MeInfo>("me $token")
}

class AccountManagerTest {

    private val api = FakeAccountApi()
    private val settings = AccountSettings(FakeSharedPreferences())
    private val manager = AccountManager(settings, GoogleSignIn(""), api, deviceName = { "Pixel 7" })

    private val ani = Account(7, "ani@contoh.com", "Ani", null, "tok-ani")
    private val failed = AccountResult.Failed(422, AccountErrors.INVALID_CREDENTIALS, "Email atau kata sandi salah.")

    private fun signedIn() = settings.save(ani)

    // --- masuk ---------------------------------------------------------------------------------

    @Test
    fun masukEmailBerhasilMenyimpanSesiDanMengirimNamaPerangkat() = runBlocking {
        api.next = AccountResult.Success(ani)

        val result = manager.login("ani@contoh.com", "rahasia123")

        assertEquals(AccountResult.Success(ani), result)
        assertEquals(SessionState.SignedIn(ani), settings.session.value)
        assertEquals(listOf("login ani@contoh.com Pixel 7"), api.calls)
    }

    @Test
    fun masukEmailDitolakTidakMembuatSesi() = runBlocking {
        api.next = failed

        assertEquals(failed, manager.login("ani@contoh.com", "salah"))

        assertEquals(SessionState.SignedOut, settings.session.value)
    }

    @Test
    fun masukEmailBelumTerverifikasiMeneruskanKodeSupayaAppBisaMembukaVerifikasi() = runBlocking {
        val belum = AccountResult.Failed(403, AccountErrors.EMAIL_NOT_VERIFIED, "Email belum diverifikasi.")
        api.next = belum

        assertEquals(belum, manager.login("ani@contoh.com", "rahasia123"))
        assertEquals(SessionState.SignedOut, settings.session.value)
    }

    @Test
    fun masukTanpaInternetTidakMembuatSesi() = runBlocking {
        api.next = AccountResult.Offline

        assertEquals(AccountResult.Offline, manager.login("ani@contoh.com", "rahasia123"))
        assertEquals(SessionState.SignedOut, settings.session.value)
    }

    @Test
    fun masukGoogleBerhasilMenyimpanSesi() = runBlocking {
        api.next = AccountResult.Success(ani)

        assertEquals(SignInResult.Success(ani), manager.signInWithGoogleToken("id-token"))

        assertEquals(SessionState.SignedIn(ani), settings.session.value)
        assertEquals(listOf("google id-token Pixel 7 null"), api.calls)
    }

    @Test
    fun masukGoogleDitolakMembawaPesanServer() = runBlocking {
        api.next = AccountResult.Failed(422, null, "Token Google tidak sah.")

        assertEquals(SignInResult.Failed("Token Google tidak sah."), manager.signInWithGoogleToken("id-token"))
        assertEquals(SessionState.SignedOut, settings.session.value)
    }

    @Test
    fun masukGoogleTanpaInternetDibedakanDariDitolak() = runBlocking {
        api.next = AccountResult.Offline

        assertEquals(SignInResult.Offline, manager.signInWithGoogleToken("id-token"))
    }

    // --- daftar dan verifikasi -----------------------------------------------------------------

    @Test
    fun daftarBerhasilBelumMembuatSesiKarenaEmailBelumTerverifikasi() = runBlocking {
        api.next = AccountResult.Success(Unit)

        assertEquals(AccountResult.Success(Unit), manager.register("Ani", "ani@contoh.com", "rahasia123", "rahasia123", "1"))

        assertEquals(SessionState.SignedOut, settings.session.value)
        assertEquals(listOf("register Ani ani@contoh.com 1"), api.calls)
    }

    @Test
    fun verifikasiBerhasilLangsungMasuk() = runBlocking {
        api.next = AccountResult.Success(ani)

        assertEquals(AccountResult.Success(ani), manager.verifyEmail("ani@contoh.com", "123456"))

        assertEquals(SessionState.SignedIn(ani), settings.session.value)
        assertEquals(listOf("verifyEmail ani@contoh.com 123456 Pixel 7"), api.calls)
    }

    @Test
    fun verifikasiKodeSalahTidakMembuatSesi() = runBlocking {
        api.next = AccountResult.Failed(422, AccountErrors.CODE_INVALID, "Kode salah.")

        assertTrue(manager.verifyEmail("ani@contoh.com", "000000") is AccountResult.Failed)
        assertEquals(SessionState.SignedOut, settings.session.value)
    }

    @Test
    fun kirimUlangDanLupaKataSandiTidakMenyentuhSesi() = runBlocking {
        api.next = AccountResult.Success(Unit)

        assertEquals(AccountResult.Success(Unit), manager.resendVerification("ani@contoh.com"))
        assertEquals(AccountResult.Success(Unit), manager.forgotPassword("ani@contoh.com"))

        assertEquals(SessionState.SignedOut, settings.session.value)
        assertEquals(listOf("resendVerification ani@contoh.com", "forgotPassword ani@contoh.com"), api.calls)
    }

    // --- reset kata sandi ----------------------------------------------------------------------

    @Test
    fun resetKataSandiBerhasilMenutupSesiYangMasihTercatat() = runBlocking {
        signedIn()
        api.next = AccountResult.Success(Unit)

        manager.resetPassword("ani@contoh.com", "654321", "baru12345", "baru12345")

        assertEquals(SessionState.SignedOut, settings.session.value)
    }

    @Test
    fun resetKataSandiTidakMenghapusEmailSesiYangSudahBerakhir() = runBlocking {
        signedIn()
        settings.expire("tok-ani")
        api.next = AccountResult.Success(Unit)

        manager.resetPassword("ani@contoh.com", "654321", "baru12345", "baru12345")

        assertEquals(SessionState.Expired("ani@contoh.com", "Ani"), settings.session.value)
    }

    @Test
    fun resetKataSandiGagalTidakMenutupSesi() = runBlocking {
        signedIn()
        api.next = AccountResult.Failed(422, AccountErrors.CODE_INVALID, "Kode salah.")

        manager.resetPassword("ani@contoh.com", "000000", "baru12345", "baru12345")

        assertEquals(SessionState.SignedIn(ani), settings.session.value)
    }

    // --- keluar --------------------------------------------------------------------------------

    @Test
    fun keluarMencabutTokenDiServerLaluMenutupSesi() = runBlocking {
        signedIn()
        api.next = AccountResult.Success(Unit)

        assertEquals(AccountResult.Success(Unit), manager.revokeAndClear())

        assertEquals(listOf("logout tok-ani"), api.calls)
        assertEquals(SessionState.SignedOut, settings.session.value)
    }

    @Test
    fun keluarTanpaInternetTetapMenutupSesiDiHpDanMemberiTahuToken() = runBlocking {
        signedIn()
        api.next = AccountResult.Offline

        assertEquals(AccountResult.Offline, manager.revokeAndClear())

        assertEquals(SessionState.SignedOut, settings.session.value)
    }

    @Test
    fun keluarSaatTokenSudahMatiDianggapBerhasil() = runBlocking {
        signedIn()
        api.next = AccountResult.Unauthorized

        assertEquals(AccountResult.Success(Unit), manager.revokeAndClear())

        assertEquals(SessionState.SignedOut, settings.session.value)
    }

    @Test
    fun keluarSaatSesiSudahBerakhirTidakMemanggilServer() = runBlocking {
        signedIn()
        settings.expire("tok-ani")

        assertEquals(AccountResult.Success(Unit), manager.revokeAndClear())

        assertTrue(api.calls.isEmpty())
        assertEquals(SessionState.SignedOut, settings.session.value)
    }

    @Test
    fun keluarTidakMenyentuhPemilikDataLokal() = runBlocking {
        signedIn()
        settings.setOwnerAccountId(7)

        manager.revokeAndClear()

        assertEquals(7L, settings.ownerAccountId)
    }

    // --- keluar dari semua perangkat -----------------------------------------------------------

    @Test
    fun keluarDariSemuaPerangkatBerhasilMenutupSesi() = runBlocking {
        signedIn()
        api.next = AccountResult.Success(Unit)

        assertEquals(AccountResult.Success(Unit), manager.signOutAllDevices())

        assertEquals(listOf("logoutAll tok-ani"), api.calls)
        assertEquals(SessionState.SignedOut, settings.session.value)
    }

    @Test
    fun keluarDariSemuaPerangkatTanpaInternetTidakMenutupSesiKarenaBelumTerlaksana() = runBlocking {
        signedIn()
        api.next = AccountResult.Offline

        assertEquals(AccountResult.Offline, manager.signOutAllDevices())

        assertEquals(SessionState.SignedIn(ani), settings.session.value)
    }

    @Test
    fun keluarDariSemuaPerangkatTanpaSesiTidakMemanggilServer() = runBlocking {
        assertEquals(AccountResult.Unauthorized, manager.signOutAllDevices())
        assertTrue(api.calls.isEmpty())
    }

    // --- ubah kata sandi, hapus akun, me --------------------------------------------------------

    @Test
    fun ubahKataSandiMemakaiTokenSesiDanSesiTetapHidup() = runBlocking {
        signedIn()
        api.next = AccountResult.Success(Unit)

        assertEquals(AccountResult.Success(Unit), manager.changePassword("lama123", "baru12345", "baru12345"))

        assertEquals(listOf("changePassword tok-ani lama123"), api.calls)
        assertEquals(SessionState.SignedIn(ani), settings.session.value)
    }

    @Test
    fun ubahKataSandiSalahMeneruskanGalatDanSesiTetapHidup() = runBlocking {
        signedIn()
        api.next = failed

        assertEquals(failed, manager.changePassword("salah", "baru12345", "baru12345"))
        assertEquals(SessionState.SignedIn(ani), settings.session.value)
    }

    @Test
    fun ubahKataSandiTanpaSesiTidakMemanggilServer() = runBlocking {
        assertEquals(AccountResult.Unauthorized, manager.changePassword("a", "b", "b"))
        assertTrue(api.calls.isEmpty())
    }

    @Test
    fun hapusAkunBerhasilMenutupSesiTanpaMenyentuhPemilikDataLokal() = runBlocking {
        signedIn()
        settings.setOwnerAccountId(7)
        api.next = AccountResult.Success(Unit)

        assertEquals(AccountResult.Success(Unit), manager.deleteAccount("rahasia123", null))

        assertEquals(listOf("deleteAccount tok-ani rahasia123 null"), api.calls)
        assertEquals(SessionState.SignedOut, settings.session.value)
        assertEquals(7L, settings.ownerAccountId)
    }

    @Test
    fun hapusAkunDitolakTidakMenutupSesi() = runBlocking {
        signedIn()
        api.next = failed

        assertEquals(failed, manager.deleteAccount("salah", null))
        assertEquals(SessionState.SignedIn(ani), settings.session.value)
    }

    @Test
    fun hapusAkunGoogleMengirimIdToken() = runBlocking {
        signedIn()

        manager.deleteAccount(null, "id-token-google")

        assertEquals(listOf("deleteAccount tok-ani null id-token-google"), api.calls)
    }

    @Test
    fun meMemakaiTokenSesi() = runBlocking {
        signedIn()
        val info = MeInfo(ani, hasPassword = true, googleLinked = false, emailVerified = true)
        api.next = AccountResult.Success(info)

        assertEquals(AccountResult.Success(info), manager.me())
        assertEquals(listOf("me tok-ani"), api.calls)
    }

    @Test
    fun meTanpaSesiTidakMemanggilServer() = runBlocking {
        assertEquals(AccountResult.Unauthorized, manager.me())
        assertNull(settings.account.value)
        assertTrue(api.calls.isEmpty())
    }
}
