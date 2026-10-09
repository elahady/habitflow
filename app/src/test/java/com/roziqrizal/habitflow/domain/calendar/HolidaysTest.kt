package com.roziqrizal.habitflow.domain.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

class HolidaysTest {

    private fun d(month: Int, day: Int, year: Int = 2026) = LocalDate.of(year, month, day)

    private val sample = """
        {"year": 2026, "holidays": [
          {"date": "2026-12-24", "name": "Kelahiran Yesus Kristus", "cutiBersama": true},
          {"date": "2026-12-25", "name": "Kelahiran Yesus Kristus", "cutiBersama": false},
          {"date": "tanggal-rusak", "name": "Rusak"},
          {"date": "2026-08-17", "name": "Proklamasi Kemerdekaan"}
        ]}
    """.trimIndent()

    @Test
    fun parserMembacaEntriDanMelewatiYangRusak() {
        val holidays = parseHolidays(sample)
        assertEquals(3, holidays.size)
        assertEquals(Holiday(d(12, 24), "Kelahiran Yesus Kristus", true), holidays.first())
        // cutiBersama yang tidak ditulis dianggap bukan cuti bersama.
        assertFalse(holidays.last().cutiBersama)
    }

    @Test
    fun berkasRusakMenghasilkanDaftarKosong() {
        assertTrue(parseHolidays("bukan json").isEmpty())
        assertTrue(parseHolidays("""{"year": 2026}""").isEmpty())
    }

    @Test
    fun labelMembedakanCutiBersama() {
        assertEquals("Idulfitri 1447 H", Holiday(d(3, 21), "Idulfitri 1447 H").label)
        assertEquals("Cuti bersama: Idulfitri 1447 H", Holiday(d(3, 20), "Idulfitri 1447 H", true).label)
    }

    @Test
    fun kalenderMencariDanMenyaringMenurutRentang() {
        val calendar = HolidayCalendar(parseHolidays(sample))
        assertEquals("Proklamasi Kemerdekaan", calendar.on(d(8, 17))?.name)
        assertNull(calendar.on(d(8, 18)))
        assertEquals(listOf(d(12, 24), d(12, 25)), calendar.between(d(12, 1), d(12, 31)).map { it.date })
        assertEquals(listOf(d(12, 25)), calendar.between(d(12, 25), d(12, 25)).map { it.date })
        assertTrue(calendar.between(d(1, 1), d(1, 31)).isEmpty())
    }

    @Test
    fun liburNasionalMenangDiAtasCutiBersamaDiTanggalYangSama() {
        val calendar = HolidayCalendar(
            listOf(Holiday(d(5, 1), "Cuti", cutiBersama = true), Holiday(d(5, 1), "Hari Buruh Internasional")),
        )
        assertFalse(calendar.on(d(5, 1))!!.cutiBersama)
        assertEquals("Hari Buruh Internasional", calendar.on(d(5, 1))!!.name)
    }

    @Test
    fun tahunTanpaDaftarTidakMenghasilkanLibur() {
        assertNull(HolidayCalendar.EMPTY.on(d(12, 25, 2030)))
    }

    @Test
    fun liburEfektifGabunganManualDanNasionalDikurangiPembatalan() {
        val calendar = HolidayCalendar(parseHolidays(sample))
        val manual = setOf(d(12, 10))
        val cancelled = setOf(d(12, 24))

        val off = effectiveDaysOff(d(12, 1), d(12, 31), manual, calendar, cancelled)

        assertEquals(setOf(d(12, 10), d(12, 25)), off)
    }

    @Test
    fun keadaanLiburSatuTanggal() {
        val calendar = HolidayCalendar(parseHolidays(sample))

        val aktif = dayOffInfo(d(12, 25), emptySet(), calendar, emptySet())
        assertTrue(aktif.holidayActive)
        assertTrue(aktif.isOff)

        val dibatalkan = dayOffInfo(d(12, 25), emptySet(), calendar, setOf(d(12, 25)))
        assertFalse(dibatalkan.holidayActive)
        assertFalse(dibatalkan.isOff)
        assertEquals("Kelahiran Yesus Kristus", dibatalkan.holiday?.name)

        val manualSaja = dayOffInfo(d(12, 11), setOf(d(12, 11)), calendar, emptySet())
        assertTrue(manualSaja.isOff)
        assertNull(manualSaja.holiday)

        // Libur manual tetap berlaku walau libur nasional di tanggal itu dibatalkan.
        val manualDanDibatalkan = dayOffInfo(d(12, 25), setOf(d(12, 25)), calendar, setOf(d(12, 25)))
        assertTrue(manualDanDibatalkan.isOff)
    }

    // --- Berkas data sungguhan di assets/libur (tes JVM berjalan dengan direktori kerja modul app) ---

    private fun asset(year: Int): List<Holiday> =
        parseHolidays(File("src/main/assets/libur/$year.json").readText())

    @Test
    fun data2026MemuatTujuhBelasLiburDanDelapanCutiBersamaSesuaiSkb() {
        val holidays = asset(2026)
        assertEquals(25, holidays.size)
        assertEquals(17, holidays.count { !it.cutiBersama })
        assertEquals(8, holidays.count { it.cutiBersama })
        assertTrue(holidays.all { it.date.year == 2026 })
        assertEquals(25, holidays.map { it.date }.toSet().size)
    }

    @Test
    fun data2026TanggalPentingBenar() {
        val calendar = HolidayCalendar(asset(2026))
        assertEquals("Proklamasi Kemerdekaan", calendar.on(d(8, 17))?.name)
        assertEquals("Kelahiran Yesus Kristus", calendar.on(d(12, 25))?.name)
        assertFalse(calendar.on(d(12, 25))!!.cutiBersama)
        assertTrue(calendar.on(d(12, 24))!!.cutiBersama)
        assertTrue(calendar.on(d(3, 20))!!.cutiBersama)
        assertEquals("Hari Suci Nyepi (Tahun Baru Saka 1948)", calendar.on(d(3, 19))?.name)
        assertNull(calendar.on(d(10, 9)))
    }

    @Test
    fun data2027MemuatDelapanBelasLiburDanDelapanCutiBersamaSesuaiSkb() {
        val holidays = asset(2027)
        assertEquals(26, holidays.size)
        assertEquals(18, holidays.count { !it.cutiBersama })
        assertEquals(8, holidays.count { it.cutiBersama })
        assertTrue(holidays.all { it.date.year == 2027 })
        assertEquals(26, holidays.map { it.date }.toSet().size)
        val calendar = HolidayCalendar(holidays)
        assertEquals("Idulfitri 1448 H", calendar.on(d(3, 10, 2027))?.name)
        assertTrue(calendar.on(d(12, 24, 2027))!!.cutiBersama)
    }
}
