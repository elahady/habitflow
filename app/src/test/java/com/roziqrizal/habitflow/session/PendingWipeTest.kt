package com.roziqrizal.habitflow.session

import com.roziqrizal.habitflow.data.account.Account
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PendingWipeTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val calls = mutableListOf<String>()
    private var restored: Account? = null
    private var wipeFails = false

    private fun marker() = File(folder.root, "sub/pending_wipe.json")

    private fun wipe() = PendingWipe(
        marker = marker(),
        wipe = {
            calls += "wipe"
            if (wipeFails) error("disk bermasalah")
        },
        restoreSession = { calls += "restore"; restored = it },
    )

    private val budi = Account(9, "budi@contoh.com", "Budi", "https://x/a.png", "tok-budi")

    @Test
    fun tanpaPenandaTidakAdaYangDihapus() {
        assertFalse(wipe().runIfPending())

        assertTrue(calls.isEmpty())
    }

    @Test
    fun penandaMembuatPenghapusanBerjalanLaluPenandaHilang() {
        val w = wipe()
        w.schedule()
        assertTrue(w.isPending())

        assertTrue(w.runIfPending())

        assertEquals(listOf("wipe"), calls)
        assertFalse(w.isPending())
        assertNull(restored)
    }

    @Test
    fun penghapusanHanyaJalanSekali() {
        val w = wipe()
        w.schedule()
        w.runIfPending()
        calls.clear()

        assertFalse(w.runIfPending())

        assertTrue(calls.isEmpty())
    }

    @Test
    fun gantiAkunMemulihkanSesiBaruSesudahPenghapusan() {
        val w = wipe()
        w.schedule(thenSignIn = budi)

        w.runIfPending()

        assertEquals("hapus dulu, baru pulihkan sesi", listOf("wipe", "restore"), calls)
        assertEquals(budi, restored)
        assertFalse(w.isPending())
    }

    @Test
    fun penandaBertahanSetelahProsesDimulaiUlangDenganObjekBaru() {
        wipe().schedule(thenSignIn = budi)

        assertTrue("objek baru di atas berkas yang sama meniru proses baru", wipe().runIfPending())

        assertEquals(budi, restored)
    }

    @Test
    fun penghapusanYangGagalMeninggalkanPenandaSupayaDiulangDiStartBerikutnya() {
        val w = wipe()
        w.schedule(thenSignIn = budi)
        wipeFails = true

        val failure = runCatching { w.runIfPending() }

        assertTrue(failure.isFailure)
        assertTrue("penanda harus tetap ada", w.isPending())
        assertNull("sesi baru tidak dipulihkan sebelum penghapusan selesai", restored)

        wipeFails = false
        calls.clear()
        assertTrue(wipe().runIfPending())
        assertEquals(listOf("wipe", "restore"), calls)
        assertEquals(budi, restored)
    }

    @Test
    fun penandaRusakTetapMenghapusTapiTidakMemulihkanSesi() {
        marker().parentFile!!.mkdirs()
        marker().writeText("{rusak")

        assertTrue(wipe().runIfPending())

        assertEquals(listOf("wipe"), calls)
        assertNull(restored)
        assertFalse(marker().exists())
    }

    @Test
    fun penandaKeluarBiasaTidakMembawaToken() {
        wipe().schedule()

        assertFalse("token tidak boleh ada di penanda Keluar", "token" in marker().readText())
    }

    @Test
    fun penandaTidakMeninggalkanBerkasSementara() {
        wipe().schedule(thenSignIn = budi)

        assertEquals(listOf("pending_wipe.json"), marker().parentFile!!.list()!!.toList())
    }

    @Test
    fun akunTanpaAvatarDipulihkanApaAdanya() {
        val tanpaAvatar = budi.copy(avatar = null)
        val w = wipe()
        w.schedule(thenSignIn = tanpaAvatar)

        w.runIfPending()

        assertEquals(tanpaAvatar, restored)
    }
}
