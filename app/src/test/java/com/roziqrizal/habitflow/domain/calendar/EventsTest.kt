package com.roziqrizal.habitflow.domain.calendar

import com.roziqrizal.habitflow.domain.schedule.Days
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class EventsTest {

    private fun d(year: Int, month: Int, day: Int) = LocalDate.of(year, month, day)
    private fun min(h: Int, m: Int = 0) = h * 60 + m

    private fun event(
        start: LocalDate,
        recurrence: Recurrence = Recurrence(),
        until: LocalDate? = null,
        startMinute: Int? = min(14),
        label: EventLabel = EventLabel.WORK,
        reminder: Int? = 15,
        id: Long = 1,
    ) = CalendarEvent(id, "Meeting", label, start, startMinute, 60, recurrence, until, reminder)

    /** Semua tanggal di rentang yang menjadi kejadian menurut aturan. */
    private fun dates(event: CalendarEvent, from: LocalDate, to: LocalDate): List<LocalDate> =
        generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.filter { event.occursOn(it) }.toList()

    // ---- aturan pengulangan ----

    @Test
    fun acaraSekaliHanyaDiTanggalMulai() {
        val e = event(d(2026, 10, 13))
        assertEquals(listOf(d(2026, 10, 13)), dates(e, d(2026, 10, 1), d(2026, 11, 30)))
    }

    @Test
    fun harianMulaiDariTanggalMulaiDanBerhentiDiTanggalBerakhirYangIkutDihitung() {
        val e = event(d(2026, 10, 13), Recurrence(RecurrenceType.DAILY), until = d(2026, 10, 15))
        assertFalse(e.occursOn(d(2026, 10, 12)))
        assertEquals(listOf(d(2026, 10, 13), d(2026, 10, 14), d(2026, 10, 15)), dates(e, d(2026, 10, 10), d(2026, 10, 20)))
    }

    @Test
    fun meetingRegulerSetiapDuaMingguHariSelasa() {
        // 13 Oktober 2026 hari Selasa.
        val e = event(d(2026, 10, 13), Recurrence(RecurrenceType.WEEKLY, intervalWeeks = 2))
        assertEquals(
            listOf(d(2026, 10, 13), d(2026, 10, 27), d(2026, 11, 10), d(2026, 11, 24)),
            dates(e, d(2026, 10, 1), d(2026, 11, 30)),
        )
        assertFalse(e.occursOn(d(2026, 10, 20)))
    }

    @Test
    fun mingguanTanpaHariTerpilihMemakaiHariTanggalMulai() {
        val e = event(d(2026, 10, 13), Recurrence(RecurrenceType.WEEKLY))
        assertEquals(
            listOf(d(2026, 10, 13), d(2026, 10, 20), d(2026, 10, 27)),
            dates(e, d(2026, 10, 1), d(2026, 10, 31)),
        )
    }

    @Test
    fun mingguanBeberapaHariDenganSelangDuaMinggu() {
        // Mulai Kamis 15 Oktober, hari Senin dan Kamis. Senin sebelum tanggal mulai tidak ikut.
        val mask = Days.bit(java.time.DayOfWeek.MONDAY) or Days.bit(java.time.DayOfWeek.THURSDAY)
        val e = event(d(2026, 10, 15), Recurrence(RecurrenceType.WEEKLY, intervalWeeks = 2, weekDays = mask))
        assertEquals(
            listOf(d(2026, 10, 15), d(2026, 10, 26), d(2026, 10, 29)),
            dates(e, d(2026, 10, 1), d(2026, 11, 1)),
        )
    }

    @Test
    fun mingguDimulaiSeninJadiMingguPertamaAdalahMingguYangMemuatTanggalMulai() {
        // Mulai Minggu 11 Oktober (minggu yang dimulai Senin 5 Oktober), setiap 2 minggu: berikutnya Minggu 25 Oktober.
        val e = event(d(2026, 10, 11), Recurrence(RecurrenceType.WEEKLY, intervalWeeks = 2))
        assertEquals(listOf(d(2026, 10, 11), d(2026, 10, 25)), dates(e, d(2026, 10, 1), d(2026, 10, 31)))
    }

    @Test
    fun bulananPerTanggal() {
        val e = event(d(2026, 10, 12), Recurrence(RecurrenceType.MONTHLY_DATE))
        assertEquals(listOf(d(2026, 10, 12), d(2026, 11, 12), d(2026, 12, 12)), dates(e, d(2026, 10, 1), d(2026, 12, 31)))
        assertFalse(e.occursOn(d(2026, 11, 13)))
    }

    @Test
    fun bulananTanggalTigaPuluhSatuJatuhDiHariTerakhirBulanPendek() {
        val e = event(d(2026, 1, 31), Recurrence(RecurrenceType.MONTHLY_DATE))
        assertTrue(e.occursOn(d(2026, 2, 28)))
        assertFalse(e.occursOn(d(2026, 2, 27)))
        assertTrue(e.occursOn(d(2026, 3, 31)))
        assertTrue(e.occursOn(d(2026, 4, 30)))
        assertFalse(e.occursOn(d(2026, 4, 29)))
    }

    @Test
    fun bulananTanggalTigaPuluhSatuDiFebruariTahunKabisat() {
        val e = event(d(2028, 1, 31), Recurrence(RecurrenceType.MONTHLY_DATE))
        assertTrue(e.occursOn(d(2028, 2, 29)))
        assertFalse(e.occursOn(d(2028, 2, 28)))
    }

    @Test
    fun bulananPerUrutanHariSeninKedua() {
        // 12 Oktober 2026 adalah Senin kedua.
        val e = event(d(2026, 10, 12), Recurrence(RecurrenceType.MONTHLY_WEEKDAY, weekOfMonth = 2))
        assertEquals(listOf(d(2026, 10, 12), d(2026, 11, 9), d(2026, 12, 14)), dates(e, d(2026, 10, 1), d(2026, 12, 31)))
        assertFalse(e.occursOn(d(2026, 11, 2))) // Senin pertama
        assertFalse(e.occursOn(d(2026, 11, 16))) // Senin ketiga
    }

    @Test
    fun bulananJumatTerakhir() {
        // 30 Oktober 2026 adalah Jumat ke-5, jadi "terakhir".
        val e = event(d(2026, 10, 30), Recurrence(RecurrenceType.MONTHLY_WEEKDAY, weekOfMonth = LAST_WEEK))
        assertEquals(listOf(d(2026, 10, 30), d(2026, 11, 27), d(2026, 12, 25)), dates(e, d(2026, 10, 1), d(2026, 12, 31)))
    }

    @Test
    fun urutanHariDalamBulanDariTanggal() {
        assertEquals(1, weekOfMonthFor(d(2026, 10, 1)))
        assertEquals(2, weekOfMonthFor(d(2026, 10, 12)))
        assertEquals(4, weekOfMonthFor(d(2026, 10, 22)))
        assertEquals(LAST_WEEK, weekOfMonthFor(d(2026, 10, 29)))
        assertEquals(LAST_WEEK, weekOfMonthFor(d(2026, 10, 30)))
    }

    @Test
    fun tahunanDiTanggalDanBulanYangSama() {
        val e = event(d(2026, 10, 9), Recurrence(RecurrenceType.YEARLY))
        assertEquals(listOf(d(2026, 10, 9), d(2027, 10, 9), d(2028, 10, 9)), dates(e, d(2026, 1, 1), d(2028, 12, 31)))
    }

    @Test
    fun tahunanDuaPuluhSembilanFebruariJatuhDiDuaPuluhDelapanDiTahunBiasa() {
        val e = event(d(2028, 2, 29), Recurrence(RecurrenceType.YEARLY))
        assertTrue(e.occursOn(d(2029, 2, 28)))
        assertFalse(e.occursOn(d(2029, 3, 1)))
        assertTrue(e.occursOn(d(2031, 2, 28)))
        assertTrue(e.occursOn(d(2032, 2, 29)))
        assertFalse(e.occursOn(d(2032, 2, 28)))
    }

    // ---- kejadian dan pengecualian ----

    private val biweekly = event(d(2026, 10, 13), Recurrence(RecurrenceType.WEEKLY, intervalWeeks = 2))

    @Test
    fun kejadianUrutMenurutTanggalLaluJamDenganSepanjangHariLebihDulu() {
        val a = event(d(2026, 10, 13), startMinute = min(14), id = 1)
        val b = event(d(2026, 10, 13), startMinute = min(9), id = 2)
        val c = event(d(2026, 10, 13), startMinute = null, id = 3)
        val result = occurrencesOn(listOf(a, b, c), emptyList(), d(2026, 10, 13))
        assertEquals(listOf(3L, 2L, 1L), result.map { it.eventId })
        assertTrue(result.first().allDay)
    }

    @Test
    fun melewatiSatuKejadianTidakMengubahYangLain() {
        val skip = EventException(1, d(2026, 10, 27), skipped = true)
        val result = occurrencesBetween(listOf(biweekly), listOf(skip), d(2026, 10, 1), d(2026, 11, 30))
        assertEquals(listOf(d(2026, 10, 13), d(2026, 11, 10), d(2026, 11, 24)), result.map { it.date })
        assertTrue(result.none { it.changed })
    }

    @Test
    fun mengubahSatuKejadianMemindahkannyaDanTidakMengubahYangLain() {
        val change = EventException(1, d(2026, 10, 27), newDate = d(2026, 10, 28), newStartMinute = min(15), newDurationMinutes = 30, newTitle = "Rapat khusus")
        val result = occurrencesBetween(listOf(biweekly), listOf(change), d(2026, 10, 1), d(2026, 11, 30))

        assertEquals(listOf(d(2026, 10, 13), d(2026, 10, 28), d(2026, 11, 10), d(2026, 11, 24)), result.map { it.date })
        val moved = result[1]
        assertTrue(moved.changed)
        assertEquals(d(2026, 10, 27), moved.originalDate)
        assertEquals(min(15), moved.startMinute)
        assertEquals(30, moved.durationMinutes)
        assertEquals("Rapat khusus", moved.title)
        // Kejadian lain tetap memakai nilai acara.
        val untouched = result[0]
        assertFalse(untouched.changed)
        assertEquals(min(14), untouched.startMinute)
        assertEquals("Meeting", untouched.title)
    }

    @Test
    fun kejadianYangDipindahMunculDiRentangTanggalBarunyaWalauTanggalAsliDiLuarRentang() {
        val change = EventException(1, d(2026, 10, 27), newDate = d(2026, 11, 3))
        val inNewRange = occurrencesBetween(listOf(biweekly), listOf(change), d(2026, 11, 1), d(2026, 11, 7))
        assertEquals(listOf(d(2026, 11, 3)), inNewRange.map { it.date })
        // Di rentang tanggal aslinya, kejadian itu sudah tidak ada.
        val inOldRange = occurrencesBetween(listOf(biweekly), listOf(change), d(2026, 10, 26), d(2026, 10, 28))
        assertTrue(inOldRange.isEmpty())
    }

    @Test
    fun pengecualianUntukTanggalYangBukanKejadianDiabaikan() {
        val stray = EventException(1, d(2026, 10, 20), newDate = d(2026, 10, 21))
        val result = occurrencesBetween(listOf(biweekly), listOf(stray), d(2026, 10, 1), d(2026, 10, 31))
        assertEquals(listOf(d(2026, 10, 13), d(2026, 10, 27)), result.map { it.date })
    }

    @Test
    fun pengecualianMilikAcaraLainTidakBerpengaruh() {
        val other = EventException(99, d(2026, 10, 27), skipped = true)
        val result = occurrencesBetween(listOf(biweekly), listOf(other), d(2026, 10, 1), d(2026, 10, 31))
        assertEquals(2, result.size)
    }

    @Test
    fun rentangTerbalikKosong() {
        assertTrue(occurrencesBetween(listOf(biweekly), emptyList(), d(2026, 11, 1), d(2026, 10, 1)).isEmpty())
    }

    // ---- hari libur, pengingat, dan blok ----

    @Test
    fun hariLiburMematikanAcaraKerjaTapiBukanAcaraPribadi() {
        val work = event(d(2026, 12, 25), label = EventLabel.WORK, id = 1)
        val personal = event(d(2026, 12, 25), label = EventLabel.PERSONAL, id = 2)
        val all = occurrencesOn(listOf(work, personal), emptyList(), d(2026, 12, 25))

        assertEquals(listOf(1L, 2L), all.visibleOnDay(false).map { it.eventId })
        assertEquals(listOf(2L), all.visibleOnDay(true).map { it.eventId })
    }

    @Test
    fun pengingatLimaBelasMenitSebelumMulai() {
        val reminders = eventReminders(occurrencesOn(listOf(biweekly), emptyList(), d(2026, 10, 13)))
        assertEquals(1, reminders.size)
        assertEquals(min(13, 45), reminders.single().minute)
    }

    @Test
    fun pengingatMengikutiPilihanDanDilewatiUntukTanpaPengingatSepanjangHariDanDiniHari() {
        fun at(reminder: Int?, start: Int?) =
            eventReminders(occurrencesOn(listOf(event(d(2026, 10, 13), startMinute = start, reminder = reminder)), emptyList(), d(2026, 10, 13)))
        assertEquals(min(13, 30), at(30, min(14)).single().minute)
        assertTrue(at(null, min(14)).isEmpty())
        assertTrue(at(15, null).isEmpty())
        assertTrue(at(15, 10).isEmpty()) // 00.10 dikurangi 15 menit jatuh sebelum 00.00
    }

    @Test
    fun pengingatKejadianYangDiubahMemakaiJamBaru() {
        val change = EventException(1, d(2026, 10, 13), newStartMinute = min(16))
        val reminders = eventReminders(occurrencesOn(listOf(biweekly), listOf(change), d(2026, 10, 13)))
        assertEquals(min(15, 45), reminders.single().minute)
    }

    @Test
    fun kejadianYangDilewatiTidakDiingatkan() {
        val skip = EventException(1, d(2026, 10, 13), skipped = true)
        assertTrue(eventReminders(occurrencesOn(listOf(biweekly), listOf(skip), d(2026, 10, 13))).isEmpty())
    }

    @Test
    fun kejadianBerjamMenjadiBlokTimelineDenganPenandaAcara() {
        val occ = occurrencesOn(listOf(biweekly), emptyList(), d(2026, 10, 13)).single()
        val block = occ.copy(calendarName = "Kalender kantor").toResolvedBlock()!!
        assertEquals(min(14), block.startMinute)
        assertEquals(min(15), block.endMinute)
        assertEquals(EventLabel.WORK, block.event?.label)
        assertEquals("Kalender kantor", block.event?.source)
        assertEquals("Meeting", block.block.name)
    }

    @Test
    fun kejadianSepanjangHariBukanBlokDanJamMelewatiTengahMalamDipotong() {
        val allDay = occurrencesOn(listOf(event(d(2026, 10, 13), startMinute = null)), emptyList(), d(2026, 10, 13)).single()
        assertNull(allDay.toResolvedBlock())
        assertNull(allDay.endMinute)

        val late = event(d(2026, 10, 13), startMinute = min(23)).copy(durationMinutes = 180)
        val occ = occurrencesOn(listOf(late), emptyList(), d(2026, 10, 13)).single()
        assertEquals(24 * 60, occ.endMinute)
        assertEquals(24 * 60, occ.toResolvedBlock()!!.endMinute)
    }

    @Test
    fun acaraDiurutkanSesudahBlokJadwalDiMenitYangSama() {
        val occ = occurrencesOn(listOf(biweekly), emptyList(), d(2026, 10, 13)).single()
        val eventBlock = occ.toResolvedBlock()!!
        val scheduleBlock = eventBlock.copy(
            block = eventBlock.block.copy(id = 7, name = "Kerja", sortOrder = 3),
            event = null,
        )
        val merged = mergeBlocks(listOf(scheduleBlock), listOf(eventBlock))
        assertEquals(listOf("Kerja", "Meeting"), merged.map { it.block.name })
    }
}
