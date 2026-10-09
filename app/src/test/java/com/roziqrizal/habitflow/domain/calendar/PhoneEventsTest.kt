package com.roziqrizal.habitflow.domain.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class PhoneEventsTest {

    private val jakarta = ZoneId.of("Asia/Jakarta") // UTC+7, tanpa waktu musim panas
    private fun d(day: Int, month: Int = 10) = LocalDate.of(2026, month, day)

    private fun utcMillis(date: LocalDate) = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    private fun localMillis(date: LocalDate, hour: Int, minute: Int = 0) =
        date.atTime(hour, minute).atZone(jakarta).toInstant().toEpochMilli()

    private fun convert(
        begin: Long,
        end: Long,
        allDay: Boolean,
        from: LocalDate = d(1),
        to: LocalDate = d(31),
    ) = phoneInstanceToOccurrences(
        eventId = 42, title = "Rapat kantor", beginMillis = begin, endMillis = end, allDay = allDay,
        label = EventLabel.WORK, calendarName = "Kantor", from = from, to = to, zone = jakarta,
    )

    @Test
    fun acaraBerjamMasukDiTanggalMulaiDenganJamLokal() {
        val result = convert(localMillis(d(13), 14, 30), localMillis(d(13), 15, 45), allDay = false)
        val occ = result.single()
        assertEquals(d(13), occ.date)
        assertEquals(14 * 60 + 30, occ.startMinute)
        assertEquals(75, occ.durationMinutes)
        assertEquals("Kantor", occ.calendarName)
        assertTrue(occ.fromPhone)
        assertNull(occ.reminderMinutes)
        assertEquals(EventLabel.WORK, occ.label)
    }

    @Test
    fun acaraBerjamYangMulaiSebelumRentangAtauSesudahnyaTidakIkut() {
        val begin = localMillis(d(13), 14)
        val end = localMillis(d(13), 15)
        assertTrue(convert(begin, end, false, from = d(14), to = d(20)).isEmpty())
        assertTrue(convert(begin, end, false, from = d(1), to = d(12)).isEmpty())
        assertEquals(1, convert(begin, end, false, from = d(13), to = d(13)).size)
    }

    @Test
    fun acaraBerjamYangMelewatiTengahMalamHanyaMasukDiTanggalMulaiDanDipotongDiTimeline() {
        val result = convert(localMillis(d(13), 23), localMillis(d(14), 1), allDay = false)
        val occ = result.single()
        assertEquals(d(13), occ.date)
        assertEquals(120, occ.durationMinutes)
        assertEquals(24 * 60, occ.endMinute)
    }

    @Test
    fun acaraSepanjangHariSatuHariMemakaiTanggalUtcDenganAkhirEksklusif() {
        // Kalender Android menyimpan 13 Oktober sepanjang hari sebagai 00.00 UTC 13 Oktober sampai 00.00 UTC 14 Oktober.
        val result = convert(utcMillis(d(13)), utcMillis(d(14)), allDay = true)
        assertEquals(listOf(d(13)), result.map { it.date })
        assertTrue(result.single().allDay)
        assertNull(result.single().startMinute)
    }

    @Test
    fun acaraSepanjangHariBeberapaHariDipecahSatuPerTanggal() {
        val result = convert(utcMillis(d(13)), utcMillis(d(16)), allDay = true)
        assertEquals(listOf(d(13), d(14), d(15)), result.map { it.date })
    }

    @Test
    fun acaraSepanjangHariDipotongDiRentang() {
        val result = convert(utcMillis(d(13)), utcMillis(d(17)), allDay = true, from = d(15), to = d(15))
        assertEquals(listOf(d(15)), result.map { it.date })
    }

    @Test
    fun acaraSepanjangHariTidakBergeserKeHariLainWalauZonaLokalMendahuluiUtc() {
        // 13 Oktober 00.00 UTC adalah 07.00 WIB tanggal yang sama; tanggalnya tetap 13, bukan 12 atau 14.
        val result = convert(utcMillis(d(13)), utcMillis(d(14)), allDay = true, from = d(12), to = d(14))
        assertEquals(listOf(d(13)), result.map { it.date })
        assertFalse(result.any { it.date == d(12) || it.date == d(14) })
    }

    @Test
    fun acaraDenganAkhirSamaDenganMulaiTetapPunyaDurasiMinimalSatuMenit() {
        val millis = localMillis(d(13), 9)
        assertEquals(1, convert(millis, millis, allDay = false).single().durationMinutes)
    }

    @Test
    fun acaraHpBisaDiubahJadiBlokTimelineDenganNamaKalender() {
        val occ = convert(localMillis(d(13), 14), localMillis(d(13), 15), allDay = false).single()
        val block = occ.toResolvedBlock()!!
        assertEquals("Kantor", block.event?.source)
        assertEquals(EventLabel.WORK, block.event?.label)
        // Id blok acara HP tidak bertabrakan dengan acara HabitFlow bernomor sama.
        val own = occ.copy(calendarName = null).toResolvedBlock()!!
        assertTrue(block.block.id != own.block.id)
    }
}
