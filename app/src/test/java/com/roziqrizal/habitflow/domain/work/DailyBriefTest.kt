package com.roziqrizal.habitflow.domain.work

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DailyBriefTest {

    private val today = LocalDate.of(2026, 10, 15)

    private fun brief(date: String, digest: String = "isi $date") = DailyBrief(LocalDate.parse(date), digest, updatedAt = 1L)

    @Test
    fun ringkasanHariIniDipakai() {
        val card = pickDailyBrief(listOf(brief("2026-10-14"), brief("2026-10-15")), today)
        assertEquals(LocalDate.of(2026, 10, 15), card.brief?.date)
        assertTrue(card.isToday)
    }

    @Test
    fun belumAdaHariIniMemakaiYangTerakhirDanMenandainyaBukanHariIni() {
        val card = pickDailyBrief(listOf(brief("2026-10-12"), brief("2026-10-13")), today)
        assertEquals(LocalDate.of(2026, 10, 13), card.brief?.date)
        assertFalse(card.isToday)
    }

    @Test
    fun tanggalDiDepanDiabaikan() {
        val card = pickDailyBrief(listOf(brief("2026-10-20")), today)
        assertNull(card.brief)
        assertFalse(card.isToday)
    }

    @Test
    fun isiKosongTidakDihitung() {
        val card = pickDailyBrief(listOf(brief("2026-10-15", "   "), brief("2026-10-14")), today)
        assertEquals(LocalDate.of(2026, 10, 14), card.brief?.date)
        assertFalse(card.isToday)
    }

    @Test
    fun urutanMasukanTidakMenentukanPilihan() {
        val card = pickDailyBrief(listOf(brief("2026-10-10"), brief("2026-10-15"), brief("2026-10-12")), today)
        assertEquals(LocalDate.of(2026, 10, 15), card.brief?.date)
    }

    @Test
    fun tanpaRingkasanKartuKosong() {
        val card = pickDailyBrief(emptyList(), today)
        assertNull(card.brief)
        assertFalse(card.isToday)
    }
}
