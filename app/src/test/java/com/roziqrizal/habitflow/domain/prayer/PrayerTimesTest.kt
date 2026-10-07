package com.roziqrizal.habitflow.domain.prayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.abs

class PrayerTimesTest {

    private val jakarta = ZoneId.of("Asia/Jakarta")

    private fun assertWithin(label: String, expected: String, actual: LocalTime?, toleranceMinutes: Int) {
        assertNotNull("$label null", actual)
        val diff = abs(actual!!.toSecondOfDay() / 60 - LocalTime.parse(expected).toSecondOfDay() / 60)
        assert(diff <= toleranceMinutes) { "$label: harapan $expected, hasil $actual (selisih $diff menit)" }
    }

    /** Contoh perhitungan manual buku "Perhitungan Waktu Sholat": Lamongan, 1 Januari 2009. */
    @Test
    fun lamongan1Januari2009SesuaiBuku() {
        val times = EphemerisPrayerCalculator.calculate(
            LocalDate.of(2009, 1, 1), latitude = -(7 + 8 / 60.0), longitude = 112 + 25 / 60.0, zone = jakarta,
        )
        assertWithin("Dzuhur", "11:35", times.dzuhur, 1)
        assertWithin("Ashar", "15:02", times.ashar, 1)
        assertWithin("Maghrib", "17:52", times.maghrib, 1)
        assertWithin("Isya", "19:08", times.isya, 1)
        assertWithin("Subuh", "03:54", times.subuh, 1)
        assertWithin("Imsak", "03:44", times.imsak, 1)
        // Buku tidak menerapkan ikhtiyat ke Terbit, app mengurangi 2 menit tetap (sama dengan Al-Kaukaba).
        assertWithin("Terbit", "05:26", times.terbit, 2)
        assertWithin("Dhuha", "05:43", times.dhuha, 1)
    }

    @Test
    fun urutanWaktuSholatMenaikDalamSehari() {
        val t = EphemerisPrayerCalculator.calculate(LocalDate.of(2026, 10, 7), -7.25, 112.75, jakarta)
        val order = listOf(t.imsak, t.subuh, t.terbit, t.dhuha, t.dzuhur, t.ashar, t.maghrib, t.isya)
        assertEquals(order.sortedBy { it!! }, order)
    }

    @Test
    fun zonaWaktuLainMenggeserJamLokal() {
        val wib = EphemerisPrayerCalculator.calculate(LocalDate.of(2026, 10, 7), -7.25, 112.75, jakarta)
        val wita = EphemerisPrayerCalculator.calculate(LocalDate.of(2026, 10, 7), -7.25, 112.75, ZoneId.of("Asia/Makassar"))
        assertEquals(wib.dzuhur!!.plusHours(1), wita.dzuhur)
    }

    @Test
    fun lintangKutubMengembalikanNullBukanError() {
        val t = EphemerisPrayerCalculator.calculate(LocalDate.of(2026, 6, 21), 80.0, 15.0, ZoneId.of("UTC"))
        assertNull(t.terbit)
        assertNotNull(t.dzuhur)
    }

    @Test
    fun patokanSholatMengambilWaktuYangBenar() {
        val t = EphemerisPrayerCalculator.calculate(LocalDate.of(2026, 10, 7), -7.25, 112.75, jakarta)
        assertEquals(t.subuh, t.of(PrayerName.SUBUH))
        assertEquals(t.isya, t.of(PrayerName.ISYA))
    }
}
