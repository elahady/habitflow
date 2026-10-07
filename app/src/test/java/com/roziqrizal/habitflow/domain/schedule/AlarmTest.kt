package com.roziqrizal.habitflow.domain.schedule

import com.roziqrizal.habitflow.domain.prayer.PrayerName
import com.roziqrizal.habitflow.domain.prayer.PrayerTimes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class AlarmTest {

    private val prayers = PrayerTimes(
        imsak = LocalTime.of(3, 55), subuh = LocalTime.of(4, 10), terbit = LocalTime.of(5, 25),
        dhuha = LocalTime.of(5, 50), dzuhur = LocalTime.of(11, 40), ashar = LocalTime.of(15, 0),
        maghrib = LocalTime.of(17, 35), isya = LocalTime.of(18, 45),
    )

    private val bangun = ScheduleBlock(
        1, "Bangun", BlockStart.Prayer(PrayerName.SUBUH, -15), 15, level = NotificationLevel.ALARM,
    )
    private val kerja = ScheduleBlock(
        2, "Kerja", BlockStart.Fixed(LocalTime.of(8, 0)), 240, activeDays = Days.WEEKDAYS,
        level = NotificationLevel.REMINDER,
    )

    // Rabu 7 Oktober 2026. Subuh - 15 = 03.55.
    private val rabu = LocalDate.of(2026, 10, 7)
    private fun at(date: LocalDate, h: Int, m: Int) = date.atTime(h, m)
    private fun next(now: LocalDateTime, skipped: Set<LocalDate> = emptySet(), blocks: List<ScheduleBlock> = listOf(bangun, kerja)) =
        nextAlarm(blocks, now, skipped) { prayers }

    @Test
    fun sebelumAlarmHariIniAlarmHariIni() {
        assertEquals(at(rabu, 3, 55), next(at(rabu, 2, 0))?.dateTime)
    }

    @Test
    fun setelahAlarmHariIniAlarmBesok() {
        assertEquals(at(rabu.plusDays(1), 3, 55), next(at(rabu, 4, 0))?.dateTime)
    }

    @Test
    fun tepatDiWaktuAlarmBerartiSudahLewat() {
        assertEquals(at(rabu.plusDays(1), 3, 55), next(at(rabu, 3, 55))?.dateTime)
    }

    @Test
    fun tanggalYangDimatikanDilewati() {
        val skipped = setOf(rabu.plusDays(1))
        assertEquals(at(rabu.plusDays(2), 3, 55), next(at(rabu, 4, 0), skipped)?.dateTime)
    }

    @Test
    fun alarmHariIniBisaDimatikanSendiri() {
        assertEquals(at(rabu.plusDays(1), 3, 55), next(at(rabu, 2, 0), setOf(rabu))?.dateTime)
    }

    @Test
    fun akhirPekanTetapBunyi() {
        val jumat = LocalDate.of(2026, 10, 9)
        assertEquals(at(jumat.plusDays(1), 3, 55), next(at(jumat, 5, 0))?.dateTime)
    }

    @Test
    fun tanpaBlokAlarmTidakAdaAlarm() {
        assertNull(next(at(rabu, 2, 0), blocks = listOf(kerja)))
    }

    @Test
    fun blokHariKerjaBertingkatAlarmTidakBunyiDiAkhirPekan() {
        val alarmKerja = kerja.copy(level = NotificationLevel.ALARM)
        val jumat = LocalDate.of(2026, 10, 9)
        assertEquals(at(jumat.plusDays(3), 8, 0), next(at(jumat, 9, 0), blocks = listOf(alarmKerja))?.dateTime)
    }

    @Test
    fun semuaTanggalDimatikanDalamHorizonTidakAdaAlarm() {
        val skipped = (0 until ALARM_HORIZON_DAYS).map { rabu.plusDays(it.toLong()) }.toSet()
        assertNull(next(at(rabu, 2, 0), skipped))
    }
}
