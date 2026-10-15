package com.roziqrizal.habitflow.data.sync

import com.roziqrizal.habitflow.domain.work.DailyBrief
import com.roziqrizal.habitflow.testing.FakeSharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DailyBriefStoreTest {

    private val brief = DailyBrief(LocalDate.of(2026, 10, 15), "3 email belum dibaca\n- Bu Rina — Revisi invoice", 1_760_000_000_000L)

    @Test
    fun kosongSaatBelumPernahDisimpan() {
        assertTrue(DailyBriefStore(FakeSharedPreferences()).briefs.value.isEmpty())
    }

    @Test
    fun tersimpanDanTerbacaLagiSetelahProsesDimulaiUlang() {
        val prefs = FakeSharedPreferences()
        DailyBriefStore(prefs).save(brief)

        val reopened = DailyBriefStore(FakeSharedPreferences(prefs.snapshot()))
        assertEquals(listOf(brief), reopened.briefs.value)
    }

    @Test
    fun menyimpanLagiMenimpaBukanMenambah() {
        val store = DailyBriefStore(FakeSharedPreferences())
        store.save(brief)
        val newer = brief.copy(date = LocalDate.of(2026, 10, 16), emailDigest = "baru")
        store.save(newer)

        assertEquals(listOf(newer), store.briefs.value)
    }

    @Test
    fun menyimpanNullMengosongkan() {
        val prefs = FakeSharedPreferences()
        val store = DailyBriefStore(prefs)
        store.save(brief)
        store.save(null)

        assertTrue(store.briefs.value.isEmpty())
        assertTrue(prefs.all.isEmpty())
    }

    @Test
    fun tanggalRusakDiabaikanBukanCrash() {
        val prefs = FakeSharedPreferences(mapOf("date" to "bukan-tanggal", "email_digest" to "x", "updated_at" to 1L))
        assertTrue(DailyBriefStore(prefs).briefs.value.isEmpty())
    }
}
