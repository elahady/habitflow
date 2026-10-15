package com.roziqrizal.habitflow.ui

import com.roziqrizal.habitflow.domain.work.DailyBrief
import com.roziqrizal.habitflow.domain.work.DailyBriefCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class DailyBriefCaptionTest {

    private val jakarta = ZoneId.of("Asia/Jakarta")

    @Test
    fun hariIniMenyebutJamDiperbaruiDiZonaPerangkat() {
        // 2026-10-15 00:05 UTC = 07.05 WIB.
        val updated = Instant.parse("2026-10-15T00:05:00Z").toEpochMilli()
        val card = DailyBriefCard(DailyBrief(LocalDate.of(2026, 10, 15), "x", updated), isToday = true)

        assertEquals("Hari ini · diperbarui 07.05", dailyBriefCaption(card, jakarta))
    }

    @Test
    fun bukanHariIniMenyebutTanggalTerakhir() {
        val card = DailyBriefCard(DailyBrief(LocalDate.of(2026, 10, 13), "x", 0L), isToday = false)

        assertEquals(
            "Belum ada ringkasan hari ini. Terakhir: ${formatShortDate(LocalDate.of(2026, 10, 13))}",
            dailyBriefCaption(card, jakarta),
        )
    }

    @Test
    fun tanpaRingkasanTidakAdaKeterangan() {
        assertNull(dailyBriefCaption(DailyBriefCard(null, isToday = false), jakarta))
    }
}
