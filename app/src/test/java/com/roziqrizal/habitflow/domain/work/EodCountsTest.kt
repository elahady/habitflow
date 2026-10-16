package com.roziqrizal.habitflow.domain.work

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class EodCountsTest {

    private val zone: ZoneId = ZoneOffset.ofHours(7)
    private val today = LocalDate.of(2026, 10, 15)

    // 06.00 WIB hari ini = 23.00 UTC kemarin.
    private val doneThisMorning = LocalDate.of(2026, 10, 15).atTime(6, 0).atZone(zone).toInstant().toEpochMilli()
    private val doneYesterday = LocalDate.of(2026, 10, 14).atTime(15, 0).atZone(zone).toInstant().toEpochMilli()

    private fun open(id: Long) = FollowUp(id = id, title = "Item $id", status = FollowUpStatus.ACTIVE)

    private fun done(id: Long, at: Long) = FollowUp(id = id, title = "Item $id", status = FollowUpStatus.DONE, doneAt = at)

    private fun counts(all: List<FollowUp>, reviewed: List<FollowUp>, actions: Map<Long, EodAction>) =
        eodCounts(all, reviewed, actions, today, zone)

    @Test
    fun selesaiHanyaYangDipilihSelesaiDariSemuaYangDitinjau() {
        val reviewed = (1L..9L).map(::open)
        val actions = reviewed.associate { it.id to (if (it.id <= 7) EodAction.Done else EodAction.Tomorrow) }

        assertEquals(EodCounts(done = 7, total = 9), counts(reviewed, reviewed, actions))
    }

    @Test
    fun yangSudahSelesaiPagiHariIkutDihitungDiKeduaAngka() {
        val reviewed = listOf(open(1), open(2), open(3))
        val all = reviewed + done(10, doneThisMorning) + done(11, doneThisMorning)
        val actions = mapOf(1L to EodAction.Done, 2L to EodAction.Tomorrow, 3L to EodAction.Wait(today.plusDays(2)))

        // 2 selesai pagi + 1 dipilih Selesai = 3; total = 2 selesai pagi + 3 ditinjau = 5.
        assertEquals(EodCounts(done = 3, total = 5), counts(all, reviewed, actions))
    }

    @Test
    fun yangSelesaiHariLainTidakDihitung() {
        val reviewed = listOf(open(1))
        val all = reviewed + done(10, doneYesterday)

        assertEquals(EodCounts(done = 0, total = 1), counts(all, reviewed, mapOf(1L to EodAction.Tomorrow)))
    }

    @Test
    fun hariTanpaFollowUpMenghasilkanNolDariNol() {
        assertEquals(EodCounts(0, 0), counts(emptyList(), emptyList(), emptyMap()))
    }

    @Test
    fun itemTanpaPilihanTidakDihitungSelesai() {
        val reviewed = listOf(open(1), open(2))
        assertEquals(EodCounts(done = 1, total = 2), counts(reviewed, reviewed, mapOf(1L to EodAction.Done)))
    }

    @Test
    fun itemYangDitinjauTapiSudahTercatatSelesaiHariIniTidakDihitungDua() {
        // Kalau item yang ditinjau ternyata sudah berstatus selesai hari ini, ia tidak boleh masuk dua kali (selesai dan ditinjau).
        val item = done(1, doneThisMorning)
        assertEquals(EodCounts(done = 1, total = 1), counts(listOf(item), listOf(item), mapOf(1L to EodAction.Done)))
    }

    @Test
    fun zonaWaktuMenentukanHariSelesai() {
        // 23.00 UTC tanggal 14 = 06.00 WIB tanggal 15: selesai "hari ini" menurut HP di WIB, "kemarin" menurut UTC.
        val all = listOf(done(10, doneThisMorning))
        assertEquals(1, eodCounts(all, emptyList(), emptyMap(), today, zone).total)
        assertEquals(0, eodCounts(all, emptyList(), emptyMap(), today, ZoneOffset.UTC).total)
    }
}
