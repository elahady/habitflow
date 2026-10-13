package com.roziqrizal.habitflow.data.account

import com.roziqrizal.habitflow.testing.FakeSharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountSettingsTest {

    private val prefs = FakeSharedPreferences()
    private val ani = Account(id = 7, email = "ani@contoh.com", name = "Ani", avatar = null, token = "token-ani")

    private fun settings() = AccountSettings(prefs)

    /** Proses app yang dimulai ulang: objek baru di atas isi berkas yang sama. */
    private fun restarted() = AccountSettings(FakeSharedPreferences(prefs.snapshot()))

    @Test
    fun awalnyaKeluarTanpaPemilik() {
        val s = settings()
        assertEquals(SessionState.SignedOut, s.session.value)
        assertNull(s.account.value)
        assertNull(s.ownerAccountId)
    }

    @Test
    fun simpanMembuatSesiMasukDanBertahanSetelahAppDimulaiUlang() {
        settings().save(ani)
        val s = restarted()
        assertEquals(SessionState.SignedIn(ani), s.session.value)
        assertEquals(ani, s.account.value)
    }

    @Test
    fun avatarBolehAdaDanTersimpan() {
        val dengan = ani.copy(avatar = "https://contoh.com/a.png")
        settings().save(dengan)
        assertEquals(dengan, restarted().account.value)
    }

    @Test
    fun tokenDitolakMenandaiSesiBerakhirTanpaTokenTetapMembawaEmail() {
        val s = settings()
        s.save(ani)

        assertTrue(s.expire("token-ani"))

        assertEquals(SessionState.Expired("ani@contoh.com", "Ani"), s.session.value)
        assertNull("pekerjaan latar belakang membaca account dan harus berhenti", s.account.value)
    }

    @Test
    fun sesiBerakhirBertahanSetelahAppDimulaiUlangDanTokenSudahDibuang() {
        val s = settings()
        s.save(ani)
        s.expire("token-ani")

        val baru = restarted()

        assertEquals(SessionState.Expired("ani@contoh.com", "Ani"), baru.session.value)
        assertNull(baru.account.value)
        assertFalse("token tidak boleh tersisa di berkas", prefs.snapshot().values.contains("token-ani"))
    }

    @Test
    fun tokenLamaYangTerlambatTidakMematikanSesiBaru() {
        val s = settings()
        s.save(ani)
        val baru = ani.copy(token = "token-baru")
        s.save(baru)

        assertFalse(s.expire("token-ani"))

        assertEquals(SessionState.SignedIn(baru), s.session.value)
    }

    @Test
    fun menandaiBerakhirSaatBelumMasukTidakMelakukanApaApa() {
        val s = settings()
        assertFalse(s.expire("apa-saja"))
        assertEquals(SessionState.SignedOut, s.session.value)
    }

    @Test
    fun masukLagiSetelahBerakhirMemulihkanSesi() {
        val s = settings()
        s.save(ani)
        s.expire("token-ani")

        val baru = ani.copy(token = "token-baru")
        s.save(baru)

        assertEquals(SessionState.SignedIn(baru), s.session.value)
        assertEquals(SessionState.SignedIn(baru), restarted().session.value)
    }

    @Test
    fun keluarMenghapusSesiDanEmailTetapPemilikTidakIkutTerhapus() {
        val s = settings()
        s.save(ani)
        s.setOwnerAccountId(7)

        s.clear()

        assertEquals(SessionState.SignedOut, s.session.value)
        assertEquals(SessionState.SignedOut, restarted().session.value)
        assertFalse(prefs.contains("email"))
        assertEquals(7L, restarted().ownerAccountId)
    }

    @Test
    fun pemilikDataLokalTersimpanDanBertahanSaatSesiBerakhir() {
        val s = settings()
        s.save(ani)
        s.setOwnerAccountId(7)

        s.expire("token-ani")

        assertEquals(7L, s.ownerAccountId)
        assertEquals(7L, restarted().ownerAccountId)
    }

    @Test
    fun hanyaClearOwnerAccountIdYangMenghapusPemilik() {
        val s = settings()
        s.setOwnerAccountId(7)
        s.clearOwnerAccountId()
        assertNull(s.ownerAccountId)
        assertNull(restarted().ownerAccountId)
    }

    @Test
    fun akunDenganIdNolTetapDianggapPemilikSah() {
        val s = settings()
        s.setOwnerAccountId(0)
        assertEquals(0L, s.ownerAccountId)
    }

    @Test
    fun berkasRusakSebagianDianggapBelumMasuk() {
        val rusak = AccountSettings(FakeSharedPreferences(mapOf("token" to "t", "email" to "a@b.c")))
        assertEquals(SessionState.SignedOut, rusak.session.value)
    }

    @Test
    fun berkasLamaTahap25TanpaPenandaBerakhirTetapTerbaca() {
        val lama = FakeSharedPreferences(
            mapOf("id" to 7L, "token" to "token-ani", "email" to "ani@contoh.com", "name" to "Ani"),
        )
        assertEquals(SessionState.SignedIn(ani), AccountSettings(lama).session.value)
    }
}
