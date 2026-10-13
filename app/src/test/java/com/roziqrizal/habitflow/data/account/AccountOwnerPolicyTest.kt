package com.roziqrizal.habitflow.data.account

import com.roziqrizal.habitflow.testing.FakeSharedPreferences
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Klien palsu untuk aturan pemilik data: yang dijawab hanya login dan logout, sisanya mencatat saja. */
private class OwnerFakeApi : AccountApi {
    val calls = mutableListOf<String>()
    var loggingInAs: Account = Account(9, "budi@contoh.com", "Budi", null, "tok-budi")

    override suspend fun login(email: String, password: String, deviceName: String): AccountResult<Account> =
        AccountResult.Success(loggingInAs)

    override suspend fun verifyEmail(email: String, code: String, deviceName: String): AccountResult<Account> =
        AccountResult.Success(loggingInAs)

    override suspend fun loginWithGoogle(idToken: String, deviceName: String, privacyPolicyVersion: String?): AccountResult<Account> =
        AccountResult.Success(loggingInAs)

    override suspend fun logout(token: String): AccountResult<Unit> {
        calls += "logout $token"
        return AccountResult.Success(Unit)
    }

    override suspend fun register(name: String, email: String, password: String, passwordConfirmation: String, privacyPolicyVersion: String) = TODO()
    override suspend fun resendVerification(email: String) = TODO()
    override suspend fun forgotPassword(email: String) = TODO()
    override suspend fun resetPassword(email: String, code: String, password: String, passwordConfirmation: String) = TODO()
    override suspend fun logoutAll(token: String) = TODO()
    override suspend fun changePassword(token: String, currentPassword: String?, password: String, passwordConfirmation: String) = TODO()
    override suspend fun deleteAccount(token: String, password: String?, googleIdToken: String?) = TODO()
    override suspend fun me(token: String) = TODO()
}

private class OrderedReset : LocalDataReset {
    val calls = mutableListOf<String>()
    override fun scheduleWipe(thenSignIn: Account?) { calls += "wipe ${thenSignIn?.email}:${thenSignIn?.token}" }
    override fun restartProcess() { calls += "restart" }
}

/** Aturan "satu HP, satu pemilik data" (tahap 31H): ambil alih data lama, ganti akun, dan akun yang sama masuk lagi. */
class AccountOwnerPolicyTest {

    private val api = OwnerFakeApi()
    private val reset = OrderedReset()
    private val settings = AccountSettings(FakeSharedPreferences())
    private val manager = AccountManager(settings, GoogleSignIn(""), reset, api, deviceName = { "Pixel 7" })

    private val ani = Account(7, "ani@contoh.com", "Ani", null, "tok-ani")
    private val budi = Account(9, "budi@contoh.com", "Budi", null, "tok-budi")

    private fun phoneBelongsToAni() = settings.setOwnerAccountId(7)

    // --- ambil alih data lama -----------------------------------------------------------------

    @Test
    fun akunPertamaYangMasukDiHpBerisiDataLamaMengambilAlihTanpaPertanyaan() = runBlocking {
        api.loggingInAs = ani
        assertNull(settings.ownerAccountId)

        val result = manager.login("ani@contoh.com", "rahasia123")

        assertEquals(AccountResult.Success(ani), result)
        assertEquals(7L, settings.ownerAccountId)
        assertTrue(reset.calls.isEmpty())
    }

    // --- ganti akun ---------------------------------------------------------------------------

    @Test
    fun akunLainMasukDitahanDanTidakMenyimpanSesiAtauMenyentuhPemilik() = runBlocking {
        phoneBelongsToAni()

        val result = manager.login("budi@contoh.com", "rahasia123")

        assertEquals(AccountResult.Failed(409, AccountErrors.OWNER_MISMATCH, "HP ini masih menyimpan data akun lain."), result)
        assertEquals(SessionState.SignedOut, settings.session.value)
        assertEquals(7L, settings.ownerAccountId)
        assertTrue("belum ada yang dihapus sebelum pengguna memutuskan", reset.calls.isEmpty())
    }

    @Test
    fun akunLainLewatVerifikasiEmailJugaDitahan() = runBlocking {
        phoneBelongsToAni()

        val result = manager.verifyEmail("budi@contoh.com", "123456")

        assertTrue(result is AccountResult.Failed && result.code == AccountErrors.OWNER_MISMATCH)
        assertEquals(SessionState.SignedOut, settings.session.value)
    }

    @Test
    fun akunLainLewatGoogleJugaDitahan() = runBlocking {
        phoneBelongsToAni()

        assertEquals(SignInResult.OwnerConflict, manager.signInWithGoogleToken("id-token"))

        assertEquals(SessionState.SignedOut, settings.session.value)
        assertTrue(reset.calls.isEmpty())
    }

    @Test
    fun konfirmasiMenandaiHapusDenganAkunBaruLaluMemulaiUlangProses() = runBlocking {
        phoneBelongsToAni()
        manager.login("budi@contoh.com", "rahasia123")

        assertTrue(manager.confirmReplaceLocalData())

        assertEquals("tanda hapus lebih dulu, baru mulai ulang", listOf("wipe budi@contoh.com:tok-budi", "restart"), reset.calls)
        assertEquals("sesi baru dipulihkan oleh penghapus di awal proses, bukan di sini", SessionState.SignedOut, settings.session.value)
    }

    @Test
    fun konfirmasiTanpaLoginYangDitahanTidakMenghapusApaApa() = runBlocking {
        phoneBelongsToAni()

        assertFalse(manager.confirmReplaceLocalData())

        assertTrue(reset.calls.isEmpty())
    }

    @Test
    fun konfirmasiHanyaBisaSekali() = runBlocking {
        phoneBelongsToAni()
        manager.login("budi@contoh.com", "rahasia123")
        manager.confirmReplaceLocalData()
        reset.calls.clear()

        assertFalse(manager.confirmReplaceLocalData())

        assertTrue(reset.calls.isEmpty())
    }

    @Test
    fun batalMencabutLoginYangDitahanDanMembiarkanDataDiHp() = runBlocking {
        phoneBelongsToAni()
        manager.login("budi@contoh.com", "rahasia123")

        manager.cancelReplaceLocalData()

        assertEquals(listOf("logout tok-budi"), api.calls)
        assertTrue(reset.calls.isEmpty())
        assertEquals(7L, settings.ownerAccountId)
        assertEquals(SessionState.SignedOut, settings.session.value)
    }

    @Test
    fun batalTanpaLoginYangDitahanTidakMemanggilServer() = runBlocking {
        manager.cancelReplaceLocalData()

        assertTrue(api.calls.isEmpty())
    }

    // --- akun yang sama -----------------------------------------------------------------------

    @Test
    fun akunYangSamaMasukLagiSetelahSesiBerakhirMempertahankanData() = runBlocking {
        settings.save(ani)
        settings.expire("tok-ani")
        api.loggingInAs = ani.copy(token = "tok-baru")

        val result = manager.login("ani@contoh.com", "rahasia123")

        assertTrue(result is AccountResult.Success)
        assertEquals(SessionState.SignedIn(ani.copy(token = "tok-baru")), settings.session.value)
        assertTrue("data tidak boleh dihapus untuk akun yang sama", reset.calls.isEmpty())
    }

    @Test
    fun akunLainSetelahSesiBerakhirTetapDitahan() = runBlocking {
        settings.save(ani)
        settings.expire("tok-ani")

        val result = manager.login("budi@contoh.com", "rahasia123")

        assertTrue(result is AccountResult.Failed && result.code == AccountErrors.OWNER_MISMATCH)
        assertEquals(SessionState.Expired("ani@contoh.com", "Ani"), settings.session.value)
    }

    @Test
    fun loginYangDitahanTergantiLoginBerikutnya() = runBlocking {
        phoneBelongsToAni()
        manager.login("budi@contoh.com", "rahasia123")

        api.loggingInAs = ani
        val result = manager.login("ani@contoh.com", "rahasia123")

        assertTrue(result is AccountResult.Success)
        assertFalse("login Budi yang lama tidak boleh bisa dikonfirmasi lagi", manager.confirmReplaceLocalData())
    }
}
