package com.roziqrizal.habitflow.domain.work

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WorkDaySyncTest {

    private val date = LocalDate.of(2026, 10, 15)

    private fun day(note: String?, scrum: Long? = null, eod: Long? = null, d: LocalDate = date) = WorkDay(d, note, scrum, eod)

    @Test
    fun catatanSamaTetapSatu() {
        assertEquals("Selesai.", mergeWorkDay(day("Selesai."), day("Selesai.")).eodNote)
    }

    @Test
    fun catatanSamaBedaSpasiTidakDigandakan() {
        assertEquals("Selesai.", mergeWorkDay(day("  Selesai.  "), day("Selesai.")).eodNote)
    }

    @Test
    fun serverKosongMemakaiCatatanHp() {
        assertEquals("dari HP", mergeWorkDay(day("dari HP"), day(null)).eodNote)
        assertEquals("dari HP", mergeWorkDay(day("dari HP"), day("   ")).eodNote)
    }

    @Test
    fun hpKosongTidakMenghapusCatatanServer() {
        assertEquals("dari web", mergeWorkDay(day(null), day("dari web")).eodNote)
        assertEquals("dari web", mergeWorkDay(day(""), day("dari web")).eodNote)
    }

    @Test
    fun duaDuanyaKosongTetapNull() {
        assertNull(mergeWorkDay(day(null), day("  ")).eodNote)
    }

    @Test
    fun duaCatatanBedaDipertahankanKeduanyaServerDulu() {
        assertEquals("dari web\n\ndari HP", mergeWorkDay(day("dari HP"), day("dari web")).eodNote)
    }

    @Test
    fun waktuSelesaiMemakaiYangLebihAkhir() {
        val merged = mergeWorkDay(day("x", scrum = 100L, eod = null), day("x", scrum = 50L, eod = 900L))
        assertEquals(100L, merged.scrumDoneAt)
        assertEquals(900L, merged.eodDoneAt)
    }

    @Test
    fun waktuSelesaiNullDiisiDariSisiLain() {
        val merged = mergeWorkDay(day(null, scrum = null, eod = null), day(null, scrum = 7L, eod = 8L))
        assertEquals(7L, merged.scrumDoneAt)
        assertEquals(8L, merged.eodDoneAt)
    }

    @Test
    fun tanggalHarusSama() {
        assertThrows(IllegalArgumentException::class.java) {
            mergeWorkDay(day("a"), day("a", d = date.plusDays(1)))
        }
    }

    @Test
    fun hasilGabunganSamaDenganServerBerartiTidakPerluDikirim() {
        val server = day("dari web", scrum = 100L, eod = 900L)
        // HP hanya punya sebagian dari yang sudah ada di server.
        assertTrue(mergeWorkDay(day("dari web", scrum = 100L), server).sameContentAs(server))
        assertTrue(mergeWorkDay(day(null, eod = 900L), server).sameContentAs(server))
    }

    @Test
    fun hasilGabunganYangBerbedaDariServerPerluDikirim() {
        val server = day("dari web", scrum = 100L)
        assertFalse(mergeWorkDay(day("dari HP", scrum = 100L), server).sameContentAs(server))
        assertFalse(mergeWorkDay(day("dari web", scrum = 100L, eod = 900L), server).sameContentAs(server))
    }

    @Test
    fun sameContentMengabaikanSpasiDiUjungCatatan() {
        assertTrue(day(" a ").sameContentAs(day("a")))
        assertTrue(day(null).sameContentAs(day("  ")))
        assertFalse(day("a").sameContentAs(day("b")))
        assertFalse(day("a", d = date).sameContentAs(day("a", d = date.plusDays(1))))
    }
}
