package com.roziqrizal.habitflow.domain.work

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class FollowUpsTest {

    private val today = LocalDate.of(2026, 10, 7)
    private val yesterday = today.minusDays(1)
    private val tomorrow = today.plusDays(1)

    private fun fu(
        id: Long,
        status: FollowUpStatus = FollowUpStatus.ACTIVE,
        date: LocalDate? = null,
        picked: LocalDate? = null,
        person: String? = null,
        time: LocalTime? = null,
    ) = FollowUp(id, "Item $id", status, date, time, person, pickedDate = picked)

    @Test
    fun inboxSelaluMasukInboxMeskiPunyaTanggal() {
        assertEquals(WorkSection.INBOX, fu(1, FollowUpStatus.INBOX).sectionFor(today))
        assertEquals(WorkSection.INBOX, fu(2, FollowUpStatus.INBOX, date = yesterday).sectionFor(today))
    }

    @Test
    fun aktifJatuhTempoHariIniMasukHariIni() {
        assertEquals(WorkSection.TODAY, fu(1, date = today).sectionFor(today))
    }

    @Test
    fun yangDipilihSaatEodKemarinMasukHariIniWalauTanggalLain() {
        assertEquals(WorkSection.TODAY, fu(1, date = tomorrow, picked = today).sectionFor(today))
        assertEquals(WorkSection.TODAY, fu(2, date = null, picked = today).sectionFor(today))
    }

    @Test
    fun lewatTanggalYangTidakDipilihMasukLewatTanggal() {
        assertEquals(WorkSection.OVERDUE, fu(1, date = yesterday).sectionFor(today))
        assertEquals(3, fu(1, date = today.minusDays(3)).daysOverdue(today))
    }

    @Test
    fun lewatTanggalYangDipilihHariIniPindahKeHariIni() {
        assertEquals(WorkSection.TODAY, fu(1, date = yesterday, picked = today).sectionFor(today))
    }

    @Test
    fun menungguDenganCekUlangHariIniMasukHariIni() {
        assertEquals(WorkSection.TODAY, fu(1, FollowUpStatus.WAITING, date = today).sectionFor(today))
    }

    @Test
    fun menungguYangCekUlangnyaNantiTetapMenunggu() {
        assertEquals(WorkSection.WAITING, fu(1, FollowUpStatus.WAITING, date = tomorrow).sectionFor(today))
    }

    @Test
    fun menungguYangLewatCekUlangMasukLewatTanggal() {
        assertEquals(WorkSection.OVERDUE, fu(1, FollowUpStatus.WAITING, date = yesterday).sectionFor(today))
    }

    @Test
    fun aktifBesokAtauTanpaTanggalMasukNanti() {
        assertEquals(WorkSection.LATER, fu(1, date = tomorrow).sectionFor(today))
        assertEquals(WorkSection.LATER, fu(2, date = null).sectionFor(today))
    }

    @Test
    fun yangSelesaiTidakMasukKelompokMana() {
        assertNull(fu(1, FollowUpStatus.DONE, date = today).sectionFor(today))
        assertNull(fu(1, FollowUpStatus.DONE, date = yesterday).daysOverdue(today))
    }

    @Test
    fun dailyScrumMenampilkanLewatTanggalHariIniDipilihDanMenungguHariIni() {
        val items = listOf(
            fu(1, FollowUpStatus.INBOX),
            fu(2, date = yesterday),
            fu(3, date = today),
            fu(4, date = tomorrow, picked = today),
            fu(5, FollowUpStatus.WAITING, date = today),
            fu(6, FollowUpStatus.WAITING, date = tomorrow),
            fu(7, date = tomorrow),
            fu(8, FollowUpStatus.DONE, date = today),
        )
        assertEquals(setOf(2L, 3L, 4L, 5L), scrumCandidates(items, today).map { it.id }.toSet())
    }

    @Test
    fun memilihUntukHariIniMenjadikanItemMasukHariIni() {
        val later = fu(1, date = tomorrow)
        val picked = later.pickedForToday(true, today)
        assertEquals(WorkSection.TODAY, picked.sectionFor(today))
        assertEquals(WorkSection.LATER, picked.pickedForToday(false, today).sectionFor(today))
    }

    @Test
    fun eodSelesaiMenandaiSelesaiDenganWaktu() {
        val done = fu(1, date = today, picked = today).applyEod(EodAction.Done, today, 1234L)
        assertEquals(FollowUpStatus.DONE, done.status)
        assertEquals(1234L, done.doneAt)
        assertNull(done.pickedDate)
    }

    @Test
    fun eodLanjutBesokMemilihItemUntukBesok() {
        val moved = fu(1, date = today).applyEod(EodAction.Tomorrow, today, 0)
        assertEquals(tomorrow, moved.pickedDate)
        // Besok pagi item itu sudah masuk Hari ini, siap dipilih di daily scrum.
        assertEquals(WorkSection.TODAY, moved.sectionFor(tomorrow))
    }

    @Test
    fun eodPindahTanggalMengubahTanggalDanMelepasPilihan() {
        val d = LocalDate.of(2026, 10, 20)
        val moved = fu(1, date = today, picked = today).applyEod(EodAction.Reschedule(d), today, 0)
        assertEquals(d, moved.date)
        assertNull(moved.pickedDate)
        assertEquals(WorkSection.LATER, moved.sectionFor(today))
    }

    @Test
    fun eodMenungguMengubahStatusDanTanggalCekUlang() {
        val d = LocalDate.of(2026, 10, 10)
        val waiting = fu(1, date = today).applyEod(EodAction.Wait(d), today, 0)
        assertEquals(FollowUpStatus.WAITING, waiting.status)
        assertEquals(d, waiting.date)
        assertEquals(WorkSection.WAITING, waiting.sectionFor(today))
    }

    @Test
    fun eodMeninjauSemuaItemHariIniSajaBukanInbox() {
        val items = listOf(
            fu(1, FollowUpStatus.INBOX),
            fu(2, date = today),
            fu(3, date = tomorrow, picked = today),
            fu(4, date = tomorrow),
        )
        assertEquals(listOf(2L, 3L), eodItems(items, today).map { it.id })
    }

    @Test
    fun merapikanInbox() {
        val inbox = fu(1, FollowUpStatus.INBOX)
        assertEquals(WorkSection.LATER, inbox.tidyAsActive(null).sectionFor(today))
        assertEquals(WorkSection.TODAY, inbox.tidyAsActive(today).sectionFor(today))
        assertEquals(WorkSection.WAITING, inbox.tidyAsWaiting(tomorrow).sectionFor(today))
    }

    @Test
    fun kelompokDiurutkanMenurutTanggalLaluJam() {
        val items = listOf(
            fu(1, date = today, time = LocalTime.of(15, 0)),
            fu(2, date = today, time = LocalTime.of(9, 0)),
            fu(3, date = today),
        )
        assertEquals(listOf(2L, 1L, 3L), items.groupedBySection(today).getValue(WorkSection.TODAY).map { it.id })
    }

    @Test
    fun saranNamaOrangDariYangPernahDipakai() {
        val items = listOf(
            fu(1, person = "Budi"), fu(2, person = "budi"), fu(3, person = "Bima"), fu(4, person = "Sari"), fu(5),
        )
        assertEquals(listOf("Budi", "Bima"), personSuggestions(items, "b"))
        assertEquals(listOf("Bima"), personSuggestions(items, "bi"))
        assertEquals(emptyList<String>(), personSuggestions(items, "Sari"))
    }

    @Test
    fun filterPerOrangTidakPekaHurufBesar() {
        val items = listOf(fu(1, person = "Budi"), fu(2, person = "budi "), fu(3, person = "Sari"), fu(4))
        assertEquals(listOf(1L, 2L), items.forPerson("BUDI").map { it.id })
        assertEquals(4, items.forPerson(null).size)
        assertEquals(listOf("Budi", "Sari"), allPeople(items))
    }

    @Test
    fun pengingatJamKhususHanyaUntukItemTerbukaDiTanggalItu() {
        val items = listOf(
            fu(1, date = today, time = LocalTime.of(10, 0)),
            fu(2, date = today),
            fu(3, FollowUpStatus.DONE, date = today, time = LocalTime.of(11, 0)),
            fu(4, date = tomorrow, time = LocalTime.of(9, 0)),
            fu(5, FollowUpStatus.INBOX, date = today, time = LocalTime.of(12, 0)),
        )
        assertEquals(listOf(1L), reminderTimes(items, today).map { it.id })
    }
}
