package com.roziqrizal.habitflow.domain.schedule

import com.roziqrizal.habitflow.domain.prayer.PrayerName
import com.roziqrizal.habitflow.domain.prayer.PrayerTimes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class ScheduleTest {

    private val prayers = PrayerTimes(
        imsak = LocalTime.of(3, 55), subuh = LocalTime.of(4, 5), terbit = LocalTime.of(5, 25),
        dhuha = LocalTime.of(5, 50), dzuhur = LocalTime.of(11, 40), ashar = LocalTime.of(15, 0),
        maghrib = LocalTime.of(17, 35), isya = LocalTime.of(18, 45),
    )

    private fun fixed(h: Int, m: Int) = BlockStart.Fixed(LocalTime.of(h, m))
    private fun min(h: Int, m: Int) = h * 60 + m

    private val blocks = listOf(
        ScheduleBlock(1, "Bangun", BlockStart.Prayer(PrayerName.SUBUH, -15), 15),
        ScheduleBlock(2, "Kerja pagi", fixed(8, 0), 240, activeDays = Days.WEEKDAYS),
        ScheduleBlock(3, "Sholat Dzuhur", BlockStart.Prayer(PrayerName.DZUHUR, 0), 15),
        ScheduleBlock(4, "Project personal", fixed(19, 0), 120),
        ScheduleBlock(5, "Batas tidur", fixed(22, 0), 0),
    )

    // 7 Oktober 2026 hari Rabu, 10 Oktober Sabtu.
    private val rabu = LocalDate.of(2026, 10, 7)
    private val sabtu = LocalDate.of(2026, 10, 10)

    private fun resolve(date: LocalDate, off: Boolean = false) = resolveBlocks(blocks, prayers, date, off)

    @Test
    fun patokanSholatDenganSelisihDihitungDariWaktuSholat() {
        val bangun = resolve(rabu).first { it.block.id == 1L }
        assertEquals(min(3, 50), bangun.startMinute)
        assertEquals(min(4, 5), bangun.endMinute)
    }

    @Test
    fun blokDiurutkanMenurutWaktuMulai() {
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), resolve(rabu).map { it.block.id })
    }

    @Test
    fun akhirPekanMembuangBlokHariKerja() {
        assertEquals(listOf(1L, 3L, 4L, 5L), resolve(sabtu).map { it.block.id })
    }

    @Test
    fun hariLiburMembuangBlokHariKerjaSajaDiHariKerja() {
        assertEquals(listOf(1L, 3L, 4L, 5L), resolve(rabu, off = true).map { it.block.id })
    }

    @Test
    fun blokDenganAkhirTetapMengabaikanDurasi() {
        val fisik = ScheduleBlock(
            9, "Aktivitas fisik", BlockStart.Prayer(PrayerName.SUBUH, 60), 0, endMinuteOfDay = min(6, 0),
        )
        val r = resolveBlocks(listOf(fisik), prayers, rabu, false).single()
        assertEquals(min(5, 5), r.startMinute)
        assertEquals(min(6, 0), r.endMinute)
    }

    @Test
    fun patokanSholatKosongMembuangBlok() {
        val noIsya = prayers.copy(isya = null)
        val b = ScheduleBlock(1, "Isya", BlockStart.Prayer(PrayerName.ISYA, 0), 15)
        assertEquals(emptyList<ResolvedBlock>(), resolveBlocks(listOf(b), noIsya, rabu, false))
    }

    @Test
    fun pagiSebelumBlokPertamaBelumAdaSekarang() {
        val r = nowAndNext(resolve(rabu), min(3, 0))
        assertNull(r.now)
        assertEquals(1L, r.next!!.block.id)
    }

    @Test
    fun jamKerjaSekarangKerjaBerikutnyaSholat() {
        val r = nowAndNext(resolve(rabu), min(9, 0))
        assertEquals(2L, r.now!!.block.id)
        assertEquals(3L, r.next!!.block.id)
    }

    @Test
    fun dzuhurDiTengahKerjaPagiMenggantikanKerjaLaluKembali() {
        val during = nowAndNext(resolve(rabu), min(11, 45))
        assertEquals(3L, during.now!!.block.id)
        val after = nowAndNext(resolve(rabu), min(11, 55))
        assertEquals(2L, after.now!!.block.id)
    }

    @Test
    fun malamBerikutnyaBatasTidur() {
        val r = nowAndNext(resolve(rabu), min(21, 30))
        assertNull(r.now)
        assertEquals(5L, r.next!!.block.id)
    }

    @Test
    fun setelahBatasTidurTidakAdaSekarangDanBerikutnya() {
        val r = nowAndNext(resolve(rabu), min(22, 30))
        assertNull(r.now)
        assertNull(r.next)
    }

    @Test
    fun akhirPekanDiJamKerjaHanyaMenampilkanBlokAkhirPekan() {
        val r = nowAndNext(resolve(sabtu), min(9, 0))
        assertNull(r.now)
        assertEquals(3L, r.next!!.block.id)
    }

    @Test
    fun blokSelesaiTepatDiAkhirTidakLagiSekarang() {
        assertNull(nowAndNext(resolve(rabu), min(12, 0)).now)
    }

    @Test
    fun rentangNotifikasiDariBlokPertamaSampaiBatasTidur() {
        assertEquals(min(3, 50)..min(22, 0), notificationWindow(resolve(rabu)))
        assertNull(notificationWindow(emptyList()))
    }

    @Test
    fun batasBlokTanpaDuplikat() {
        val b = boundaryMinutes(resolve(rabu))
        assertEquals(b.sorted().distinct(), b)
        assertEquals(true, min(22, 0) in b)
    }

    @Test
    fun batasBerikutnyaSetelahMenitTertentu() {
        val r = resolve(rabu)
        assertEquals(min(4, 5), nextBoundaryMinute(r, min(3, 50)))
        assertEquals(min(22, 0), nextBoundaryMinute(r, min(21, 0)))
        assertNull(nextBoundaryMinute(r, min(22, 0)))
    }

    @Test
    fun blokYangMulaiDalamRentangDinotifikasi() {
        val r = resolve(rabu)
        assertEquals(listOf(3L), blocksStartedBetween(r, min(11, 30), min(11, 45)).map { it.block.id })
        assertEquals(emptyList<Long>(), blocksStartedBetween(r, min(11, 40), min(11, 45)).map { it.block.id })
        assertEquals(listOf(1L), blocksStartedBetween(r, min(3, 0), min(3, 50)).map { it.block.id })
    }
}
