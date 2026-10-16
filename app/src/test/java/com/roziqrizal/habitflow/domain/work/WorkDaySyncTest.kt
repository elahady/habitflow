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

class WorkDayCountsMergeTest {

    private val date = LocalDate.of(2026, 10, 15)

    private fun day(eod: Long?, done: Int?, total: Int?) = WorkDay(date, null, null, eod, done, total)

    @Test
    fun angkaIkutEodYangLebihAkhir() {
        val merged = mergeWorkDay(local = day(2_000, 7, 9), server = day(1_000, 3, 4))
        assertEquals(7, merged.eodDoneCount)
        assertEquals(9, merged.eodTotalCount)

        val serverNewer = mergeWorkDay(local = day(1_000, 3, 4), server = day(2_000, 7, 9))
        assertEquals(7, serverNewer.eodDoneCount)
        assertEquals(9, serverNewer.eodTotalCount)
    }

    @Test
    fun angkaTidakDigabungPerBidang() {
        val merged = mergeWorkDay(local = day(2_000, 1, 2), server = day(1_000, 8, 9))
        assertEquals(1 to 2, merged.eodDoneCount to merged.eodTotalCount)
    }

    @Test
    fun sisiYangLebihAkhirTanpaAngkaMemakaiAngkaSisiLain() {
        val merged = mergeWorkDay(local = day(2_000, null, null), server = day(1_000, 3, 4))
        assertEquals(3 to 4, merged.eodDoneCount to merged.eodTotalCount)
    }

    @Test
    fun seriJatuhKeHpDanTanpaAngkaDimanapunTetapNull() {
        val tie = mergeWorkDay(local = day(1_000, 5, 6), server = day(1_000, 1, 2))
        assertEquals(5 to 6, tie.eodDoneCount to tie.eodTotalCount)

        val none = mergeWorkDay(local = day(null, null, null), server = day(null, null, null))
        assertNull(none.eodDoneCount)
        assertNull(none.eodTotalCount)
    }

    @Test
    fun isiSamaMemperhatikanAngka() {
        assertTrue(day(1, 7, 9).sameContentAs(day(1, 7, 9)))
        assertFalse(day(1, 7, 9).sameContentAs(day(1, 7, 10)))
        assertFalse(day(1, 7, 9).sameContentAs(day(1, null, null)))
    }
}
