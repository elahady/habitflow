package com.roziqrizal.habitflow.domain.work

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class EodSummaryTest {

    private val date = LocalDate.of(2026, 10, 7)
    private fun fu(id: Long, title: String) = FollowUp(id, title, FollowUpStatus.ACTIVE)

    @Test
    fun ringkasanMemuatSemuaKelompokDanCatatan() {
        val summary = buildEodSummary(
            date,
            listOf(
                fu(1, "Kirim proposal") to EodAction.Done,
                fu(2, "Review PR") to EodAction.Tomorrow,
                fu(3, "Rapat vendor") to EodAction.Reschedule(LocalDate.of(2026, 10, 20)),
                fu(4, "Balasan klien") to EodAction.Wait(LocalDate.of(2026, 10, 10)),
            ),
            "  Besok fokus ke migrasi.  ",
        )
        val expected = listOf(
            "EOD Rabu, 7 Oktober 2026",
            "",
            "Selesai:",
            "- Kirim proposal",
            "",
            "Lanjut besok:",
            "- Review PR",
            "",
            "Pindah tanggal:",
            "- Rapat vendor (20 Okt)",
            "",
            "Menunggu:",
            "- Balasan klien (cek 10 Okt)",
            "",
            "Catatan:",
            "Besok fokus ke migrasi.",
        ).joinToString("\n")
        assertEquals(expected, summary)
    }

    @Test
    fun kelompokKosongDanCatatanKosongDisembunyikan() {
        val summary = buildEodSummary(date, listOf(fu(1, "Kirim proposal") to EodAction.Done), "   ")
        assertEquals("EOD Rabu, 7 Oktober 2026\n\nSelesai:\n- Kirim proposal", summary)
        assertFalse(summary.contains("Catatan"))
        assertFalse(summary.contains("Menunggu"))
    }

    @Test
    fun tanpaFollowUpHanyaJudulDanCatatan() {
        assertEquals("EOD Rabu, 7 Oktober 2026", buildEodSummary(date, emptyList(), ""))
        assertTrue(buildEodSummary(date, emptyList(), "Hari tenang").endsWith("Catatan:\nHari tenang"))
    }
}
