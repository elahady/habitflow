package com.roziqrizal.habitflow.domain.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AgendaTest {

    private fun d(month: Int, day: Int, year: Int = 2026) = LocalDate.of(year, month, day)
    private fun min(h: Int, m: Int = 0) = h * 60 + m

    private val holidays = HolidayCalendar(
        listOf(
            Holiday(d(12, 24), "Kelahiran Yesus Kristus", cutiBersama = true),
            Holiday(d(12, 25), "Kelahiran Yesus Kristus"),
        ),
    )

    private fun event(
        id: Long,
        title: String,
        date: LocalDate,
        label: EventLabel,
        startMinute: Int? = min(10),
        recurrence: Recurrence = Recurrence(),
    ) = CalendarEvent(id, title, label, date, startMinute, 60, recurrence)

    private fun agenda(
        events: List<CalendarEvent>,
        from: LocalDate = d(12, 23),
        to: LocalDate = d(12, 26),
        exceptions: List<EventException> = emptyList(),
        phone: List<EventOccurrence> = emptyList(),
        manual: Set<LocalDate> = emptySet(),
        cancelled: Set<LocalDate> = emptySet(),
    ) = buildAgenda(from, to, events, exceptions, phone, holidays, manual, cancelled)

    @Test
    fun setiapTanggalDiRentangAdaSatuHari() {
        val days = agenda(emptyList())
        assertEquals(listOf(d(12, 23), d(12, 24), d(12, 25), d(12, 26)), days.map { it.date })
        assertTrue(days.all { it.items.isEmpty() })
    }

    @Test
    fun rentangTerbalikKosong() {
        assertTrue(agenda(emptyList(), from = d(12, 26), to = d(12, 23)).isEmpty())
    }

    @Test
    fun liburNasionalMematikanAcaraKerjaTapiAcaraPribadiTetap() {
        val work = event(1, "Meeting", d(12, 25), EventLabel.WORK)
        val personal = event(2, "Makan keluarga", d(12, 25), EventLabel.PERSONAL, startMinute = min(12))

        val day = agenda(listOf(work, personal)).first { it.date == d(12, 25) }

        assertTrue(day.dayOff)
        assertEquals("Kelahiran Yesus Kristus", day.holiday?.name)
        assertEquals(listOf("Makan keluarga"), day.items.map { it.title })
    }

    @Test
    fun cutiBersamaDiperlakukanSamaDenganLiburNasional() {
        val work = event(1, "Meeting", d(12, 24), EventLabel.WORK)
        val day = agenda(listOf(work)).first { it.date == d(12, 24) }
        assertTrue(day.dayOff)
        assertTrue(day.holiday!!.cutiBersama)
        assertTrue(day.items.isEmpty())
    }

    @Test
    fun membatalkanLiburMengembalikanAcaraKerjaTapiHariLiburTetapDisebut() {
        val work = event(1, "Meeting", d(12, 25), EventLabel.WORK)

        val day = agenda(listOf(work), cancelled = setOf(d(12, 25))).first { it.date == d(12, 25) }

        assertFalse(day.dayOff)
        assertTrue(day.holidayCancelled)
        assertEquals("Kelahiran Yesus Kristus", day.holiday?.name)
        assertEquals(listOf("Meeting"), day.items.map { it.title })
    }

    @Test
    fun liburManualJugaMematikanAcaraKerja() {
        val work = event(1, "Meeting", d(12, 23), EventLabel.WORK)
        val day = agenda(listOf(work), manual = setOf(d(12, 23))).first { it.date == d(12, 23) }
        assertTrue(day.dayOff)
        assertNull(day.holiday)
        assertTrue(day.items.isEmpty())
    }

    @Test
    fun hariBiasaMemuatSemuaAcara() {
        val work = event(1, "Meeting", d(12, 23), EventLabel.WORK)
        val day = agenda(listOf(work)).first { it.date == d(12, 23) }
        assertFalse(day.dayOff)
        assertEquals(listOf("Meeting"), day.items.map { it.title })
    }

    @Test
    fun acaraHpIkutDigabungDanDipengaruhiLiburSesuaiLabelnya() {
        fun phoneEvent(title: String, label: EventLabel) = EventOccurrence(
            eventId = 77, originalDate = d(12, 25), date = d(12, 25), startMinute = min(9), durationMinutes = 60,
            title = title, label = label, recurring = false, changed = false, reminderMinutes = null, note = null,
            calendarName = "Kalender",
        )
        val day = agenda(
            emptyList(),
            phone = listOf(phoneEvent("Rapat HP", EventLabel.WORK), phoneEvent("Arisan", EventLabel.PERSONAL)),
        ).first { it.date == d(12, 25) }
        assertEquals(listOf("Arisan"), day.items.map { it.title })
    }

    @Test
    fun acaraHpDiLuarRentangDiabaikan() {
        val outside = EventOccurrence(
            eventId = 1, originalDate = d(11, 1), date = d(11, 1), startMinute = min(9), durationMinutes = 60,
            title = "Lama", label = EventLabel.PERSONAL, recurring = false, changed = false, reminderMinutes = null,
            note = null, calendarName = "K",
        )
        assertTrue(agenda(emptyList(), phone = listOf(outside)).all { it.items.isEmpty() })
    }

    @Test
    fun itemDiurutkanSepanjangHariDuluLaluJam() {
        val late = event(1, "Sore", d(12, 23), EventLabel.PERSONAL, startMinute = min(16))
        val early = event(2, "Pagi", d(12, 23), EventLabel.PERSONAL, startMinute = min(8))
        val allDay = event(3, "Ulang tahun", d(12, 23), EventLabel.PERSONAL, startMinute = null)
        val day = agenda(listOf(late, early, allDay)).first { it.date == d(12, 23) }
        assertEquals(listOf("Ulang tahun", "Pagi", "Sore"), day.items.map { it.title })
    }

    @Test
    fun acaraBerulangDanPengecualianIkutTerhitung() {
        val daily = event(1, "Standup", d(12, 23), EventLabel.PERSONAL, recurrence = Recurrence(RecurrenceType.DAILY))
        val skip = EventException(1, d(12, 24), skipped = true)
        val days = agenda(listOf(daily), exceptions = listOf(skip))
        assertEquals(listOf(1, 0, 1, 1), days.map { it.items.size })
    }

    @Test
    fun selBulanDimulaiHariSeninDenganSelKosongDiAwal() {
        // Oktober 2026 dimulai hari Kamis: tiga sel kosong (Senin, Selasa, Rabu), lalu tanggal 1.
        val cells = monthCells(2026, 10)
        assertEquals(3, cells.takeWhile { it == null }.size)
        assertEquals(d(10, 1), cells[3])
        assertEquals(3 + 31, cells.size)
        assertEquals(d(10, 31), cells.last())
        // Bulan yang dimulai hari Senin tidak punya sel kosong (Juni 2026 dimulai Senin).
        assertEquals(d(6, 1), monthCells(2026, 6).first())
    }
}
