package com.roziqrizal.habitflow.session

import com.roziqrizal.habitflow.data.account.Account
import com.roziqrizal.habitflow.data.account.AccountSettings
import com.roziqrizal.habitflow.data.account.SessionState
import com.roziqrizal.habitflow.testing.FakeSharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private class RecordingWork : BackgroundWork {
    val calls = mutableListOf<String>()
    override fun start() { calls += "start" }
    override fun stop() { calls += "stop" }
}

class SessionManagerTest {

    private val prefs = FakeSharedPreferences()
    private val settings = AccountSettings(prefs)
    private val work = RecordingWork()
    private val manager = SessionManager(settings, work)
    private val ani = Account(7, "ani@contoh.com", "Ani", null, "tok-ani")

    @Test
    fun tanpaSesiTidakAdaPekerjaanTerjadwalDanSisaSebelumnyaDibersihkan() {
        manager.apply(settings.session.value)

        assertEquals("tanpa sesi hanya boleh membersihkan, tidak memasang", listOf("stop"), work.calls)
    }

    @Test
    fun masukMenyalakanLatarBelakang() {
        settings.save(ani)

        manager.apply(settings.session.value)

        assertEquals(listOf("start"), work.calls)
    }

    @Test
    fun sesiBerakhirMematikanLatarBelakangTanpaMenghapusData() {
        settings.save(ani)
        manager.apply(settings.session.value)

        settings.expire("tok-ani")
        manager.apply(settings.session.value)

        assertEquals(listOf("start", "stop"), work.calls)
    }

    @Test
    fun keluarMematikanLatarBelakang() {
        settings.save(ani)
        manager.apply(settings.session.value)

        settings.clear()
        manager.apply(settings.session.value)

        assertEquals(listOf("start", "stop"), work.calls)
    }

    @Test
    fun keadaanYangSamaBerulangTidakMenyalakanAtauMematikanUlang() {
        settings.save(ani)
        repeat(3) { manager.apply(settings.session.value) }
        settings.clear()
        repeat(3) { manager.apply(settings.session.value) }

        assertEquals(listOf("start", "stop"), work.calls)
    }

    @Test
    fun masukLagiSetelahBerakhirMenyalakanUlang() {
        settings.save(ani)
        manager.apply(settings.session.value)
        settings.expire("tok-ani")
        manager.apply(settings.session.value)

        settings.save(ani.copy(token = "tok-baru"))
        manager.apply(settings.session.value)

        assertEquals(listOf("start", "stop", "start"), work.calls)
    }

    // --- pemilik data lokal -----------------------------------------------------------------------

    private fun legacyPrefs(expired: Boolean) = FakeSharedPreferences(
        buildMap {
            put("id", 7L)
            put("email", "ani@contoh.com")
            put("name", "Ani")
            if (expired) put("expired", true) else put("token", "tok-lama")
        },
    )

    @Test
    fun sesiLamaDariSebelumTahap31MengisiPemilikDataDariAkunItu() {
        val legacy = AccountSettings(legacyPrefs(expired = false))
        assertNull(legacy.ownerAccountId)

        SessionManager(legacy, work).apply(legacy.session.value)

        assertEquals(7L, legacy.ownerAccountId)
    }

    @Test
    fun sesiLamaYangSudahBerakhirJugaMengisiPemilikSupayaAkunLainTidakMengambilAlihDiamDiam() {
        val legacy = AccountSettings(legacyPrefs(expired = true))
        assertEquals(SessionState.Expired("ani@contoh.com", "Ani"), legacy.session.value)

        SessionManager(legacy, work).apply(legacy.session.value)

        assertEquals(7L, legacy.ownerAccountId)
    }

    @Test
    fun belumAdaAkunSamaSekaliTidakMengisiPemilikSupayaAkunPertamaYangMasukMengambilAlih() {
        manager.apply(settings.session.value)

        assertNull(settings.ownerAccountId)
    }

    @Test
    fun akunPertamaYangMasukMengambilAlihDataYangSudahAda() {
        assertNull(settings.ownerAccountId)

        settings.save(ani)

        assertEquals(7L, settings.ownerAccountId)
    }

    @Test
    fun pemilikYangSudahAdaTidakTertimpaOlehLogin() {
        settings.setOwnerAccountId(7)

        settings.save(Account(9, "budi@contoh.com", "Budi", null, "tok-budi"))

        assertEquals("ganti akun harus ditahan pemanggil, bukan ditimpa diam-diam", 7L, settings.ownerAccountId)
    }

    @Test
    fun klaimDariAkunDikenalTidakMenimpaPemilikYangAda() {
        val legacy = AccountSettings(legacyPrefs(expired = false))
        legacy.setOwnerAccountId(3)

        legacy.claimOwnerFromKnownAccount()

        assertEquals(3L, legacy.ownerAccountId)
    }

    @Test
    fun sesiDibacaTanpaMembuatAccountSettingsUntukReceiverDanPekerja() {
        assertEquals(SessionState.SignedOut, AccountSettings.read(FakeSharedPreferences()))
        assertEquals(
            SessionState.SignedIn(ani),
            AccountSettings.read(
                FakeSharedPreferences(mapOf("id" to 7L, "token" to "tok-ani", "email" to "ani@contoh.com", "name" to "Ani")),
            ),
        )
        assertEquals(
            "token dibuang saat berakhir: pekerja latar belakang tidak boleh menganggapnya aktif",
            SessionState.Expired("ani@contoh.com", "Ani"),
            AccountSettings.read(
                FakeSharedPreferences(mapOf("id" to 7L, "expired" to true, "email" to "ani@contoh.com", "name" to "Ani")),
            ),
        )
    }
}
