package com.roziqrizal.habitflow.domain.schedule

import com.roziqrizal.habitflow.domain.prayer.PrayerName
import com.roziqrizal.habitflow.domain.prayer.PrayerTimes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class WorkRemindersTest {

    private fun min(h: Int, m: Int = 0) = h * 60 + m
    private fun fixed(h: Int, m: Int = 0) = BlockStart.Fixed(LocalTime.of(h, m))

    private val prayers = PrayerTimes(
        imsak = LocalTime.of(3, 55), subuh = LocalTime.of(4, 5), terbit = LocalTime.of(5, 25),
        dhuha = LocalTime.of(5, 50), dzuhur = LocalTime.of(11, 40), ashar = LocalTime.of(15, 0),
        maghrib = LocalTime.of(17, 35), isya = LocalTime.of(18, 45),
    )

    /** Jadwal bawaan yang relevan: Kerja pagi dan sore bertanda, Dzuhur dan Ashar sebagai blok sholat. */
    private val blocks = listOf(
        ScheduleBlock(1, "Kerja pagi", fixed(8), 240, activeDays = Days.WEEKDAYS, workReminders = true),
        ScheduleBlock(2, "Sholat Dzuhur", BlockStart.Prayer(PrayerName.DZUHUR, 0), 15),
        ScheduleBlock(3, "Makan siang dan istirahat", fixed(12), 60, activeDays = Days.WEEKDAYS),
        ScheduleBlock(4, "Kerja sore", fixed(13), 180, activeDays = Days.WEEKDAYS, workReminders = true),
        ScheduleBlock(5, "Sholat Ashar", BlockStart.Prayer(PrayerName.ASHAR, 0), 15),
    )

    // 7 Oktober 2026 hari Rabu.
    private val rabu = LocalDate.of(2026, 10, 7)

    private fun resolve(off: Boolean = false) = resolveBlocks(blocks, prayers, rabu, off)

    private fun reminder(minute: Int, kind: WorkReminderKind, blockId: Long) = WorkReminder(minute, kind, blockId)

    @Test
    fun jadwalBawaanMenghasilkanTujuhPengingatAirDanTigaBreak() {
        val result = workReminders(resolve())

        assertEquals(
            listOf(
                reminder(min(8), WorkReminderKind.WATER, 1),
                reminder(min(9), WorkReminderKind.WATER, 1),
                reminder(min(9, 30), WorkReminderKind.BREAK, 1),
                reminder(min(10), WorkReminderKind.WATER, 1),
                reminder(min(11), WorkReminderKind.BREAK_AND_WATER, 1),
                reminder(min(13), WorkReminderKind.WATER, 4),
                reminder(min(14), WorkReminderKind.WATER, 4),
                // Break 14.30 berjarak 30 menit dari air 14.00 dan 15.00, jadi tidak digabung.
                reminder(min(14, 30), WorkReminderKind.BREAK, 4),
                // Air 15.00 jatuh di Sholat Ashar (15.00 sampai 15.15) lalu digeser ke akhirnya.
                reminder(min(15, 15), WorkReminderKind.WATER, 4),
            ).sortedWith(compareBy({ it.minute }, { it.blockId })),
            result,
        )
        assertEquals(7, result.count { it.kind.includesWater })
        assertEquals(3, result.count { it.kind != WorkReminderKind.WATER })
    }

    @Test
    fun pengingatTidakPernahJatuhTepatDiAkhirBlok() {
        val result = workReminders(resolve())
        assertTrue(result.none { it.minute == min(12) || it.minute == min(16) })
        assertTrue(result.none { it.blockId == 1L && it.minute >= min(12) })
        assertTrue(result.none { it.blockId == 4L && it.minute >= min(16) })
    }

    @Test
    fun airDanBreakBerjarakLimaBelasMenitDigabungDiWaktuLebihAwal() {
        // Sholat 08.50 sampai 09.15 menggeser air 09.00 ke 09.15. Break 09.30 berjarak tepat 15 menit.
        val result = workReminders(
            listOf(kerja(8, 12), sholat(min(8, 50), min(9, 15))),
            water = true, breaks = true,
        ).filter { it.minute in min(9) until min(10) }

        assertEquals(listOf(reminder(min(9, 15), WorkReminderKind.BREAK_AND_WATER, 1)), result)
    }

    @Test
    fun airDanBreakBerjarakEnamBelasMenitTidakDigabung() {
        // Sholat 08.50 sampai 09.14 menggeser air ke 09.14. Break 09.30 berjarak 16 menit.
        val result = workReminders(
            listOf(kerja(8, 12), sholat(min(8, 50), min(9, 14))),
        ).filter { it.minute in min(9) until min(10) }

        assertEquals(
            listOf(
                reminder(min(9, 14), WorkReminderKind.WATER, 1),
                reminder(min(9, 30), WorkReminderKind.BREAK, 1),
            ),
            result,
        )
    }

    @Test
    fun pengingatYangDigeserKeLuarBlokKerjaDibuang() {
        // Sholat 08.30 sampai 10.00 menutup air 09.00 dan break 09.30; keduanya bergeser ke 10.00, di akhir blok.
        val result = workReminders(listOf(kerja(8, 10), sholat(min(8, 30), min(10))))

        assertEquals(listOf(reminder(min(8), WorkReminderKind.WATER, 1)), result)
    }

    @Test
    fun blokTanpaTandaTidakMenghasilkanPengingat() {
        val tanpaTanda = blocks.map { it.copy(workReminders = false) }
        val resolved = resolveBlocks(tanpaTanda, prayers, rabu, false)
        assertTrue(workReminders(resolved).isEmpty())
    }

    @Test
    fun hariLiburMematikanBlokKerjaDanPengingatnya() {
        assertTrue(workReminders(resolve(off = true)).isEmpty())
    }

    @Test
    fun titikWaktuBertandaTidakMenghasilkanPengingat() {
        val titik = ResolvedBlock(ScheduleBlock(9, "Titik", fixed(8), 0, workReminders = true), min(8), min(8))
        assertTrue(workReminders(listOf(titik)).isEmpty())
    }

    @Test
    fun airDanBreakBisaDimatikanTerpisah() {
        val resolved = resolve()
        assertTrue(workReminders(resolved, water = false).all { it.kind == WorkReminderKind.BREAK })
        assertEquals(3, workReminders(resolved, water = false).size)
        assertTrue(workReminders(resolved, breaks = false).all { it.kind == WorkReminderKind.WATER })
        assertEquals(7, workReminders(resolved, breaks = false).size)
        assertTrue(workReminders(resolved, water = false, breaks = false).isEmpty())
    }

    @Test
    fun blokKerjaYangAwalnyaBlokSholatTidakMenahanDirinyaSendiri() {
        val sendiri = ScheduleBlock(
            7, "Kerja", BlockStart.Prayer(PrayerName.DZUHUR, 0), 120, workReminders = true,
        )
        val resolved = resolveBlocks(listOf(sendiri), prayers, rabu, false)
        assertEquals(min(11, 40), workReminders(resolved).first().minute)
    }

    @Test
    fun pengingatBerikutnyaDanRentangMengikutiMenit() {
        val result = workReminders(resolve())
        assertEquals(min(8), nextWorkReminderMinute(result, min(7)))
        assertEquals(min(9), nextWorkReminderMinute(result, min(8)))
        assertEquals(min(13), nextWorkReminderMinute(result, min(11)))
        assertNull(nextWorkReminderMinute(result, min(15, 15)))
        assertEquals(
            listOf(min(9), min(9, 30)),
            workRemindersBetween(result, min(8), min(9, 30)).map { it.minute },
        )
        assertTrue(workRemindersBetween(result, min(9, 30), min(9, 30)).isEmpty())
    }

    @Test
    fun hitunganMelewatiTargetHanyaSaatNaikMelewatinya() {
        assertTrue(crossesWaterTarget(7, 8))
        assertFalse(crossesWaterTarget(8, 9))
        assertFalse(crossesWaterTarget(9, 8))
        assertFalse(crossesWaterTarget(8, 7))
        assertFalse(crossesWaterTarget(0, 1))
    }

    @Test
    fun tigaPengingatAirTakTerjawabMenghentikanPengingatDiBlokItu() {
        var state = WaterReminderState()
        val sent = mutableListOf<Boolean>()
        repeat(5) {
            val decision = decideWaterReminder(state, "2026-10-07:1", glassesNow = 0)
            sent += decision.send
            state = decision.state
        }
        // Tiga pertama dikirim (tiga yang terabaikan baru diketahui di pengingat keempat), sisanya berhenti.
        assertEquals(listOf(true, true, true, false, false), sent)
    }

    @Test
    fun gelasBertambahMengulangHitunganTakTerjawab() {
        var state = WaterReminderState()
        var glasses = 0
        val sent = mutableListOf<Boolean>()
        repeat(6) { index ->
            val decision = decideWaterReminder(state, "2026-10-07:1", glasses)
            sent += decision.send
            state = decision.state
            if (index % 2 == 1) glasses++ // dijawab setiap pengingat kedua
        }
        assertTrue(sent.all { it })
    }

    @Test
    fun setelahBerhentiGelasTambahanTidakMenghidupkanLagiDiBlokYangSama() {
        var state = WaterReminderState()
        repeat(4) { state = decideWaterReminder(state, "2026-10-07:1", 0).state }
        val decision = decideWaterReminder(state, "2026-10-07:1", glassesNow = 3)
        assertFalse(decision.send)
    }

    @Test
    fun blokBerikutnyaMenghidupkanLagiPengingatAir() {
        var state = WaterReminderState()
        repeat(4) { state = decideWaterReminder(state, "2026-10-07:1", 0).state }

        val sore = decideWaterReminder(state, "2026-10-07:4", glassesNow = 0)
        assertTrue(sore.send)
        assertEquals(0, sore.state.ignored)

        val besok = decideWaterReminder(sore.state, "2026-10-08:4", glassesNow = 0)
        assertTrue(besok.send)
    }

    private fun kerja(startHour: Int, endHour: Int) = ResolvedBlock(
        ScheduleBlock(1, "Kerja", fixed(startHour), (endHour - startHour) * 60, workReminders = true),
        min(startHour), min(endHour),
    )

    private fun sholat(start: Int, end: Int) = ResolvedBlock(
        ScheduleBlock(2, "Sholat Dzuhur", BlockStart.Prayer(PrayerName.DZUHUR, 0), end - start),
        start, end,
    )
}
