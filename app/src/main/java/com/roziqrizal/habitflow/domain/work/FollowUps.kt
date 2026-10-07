package com.roziqrizal.habitflow.domain.work

import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/**
 * Status follow-up kerja. [INBOX] baru dicatat dan belum dirapikan, [ACTIVE] akan dikerjakan,
 * [WAITING] menunggu orang lain (tanggalnya adalah tanggal cek ulang), [DONE] selesai.
 */
enum class FollowUpStatus { INBOX, ACTIVE, WAITING, DONE }

/**
 * Satu follow-up. [pickedDate] adalah tanggal follow-up ini dipilih untuk dikerjakan (di daily scrum,
 * atau lewat "Lanjut besok" saat EOD). Tidak ada batas jumlah dan tidak memengaruhi level hari.
 */
data class FollowUp(
    val id: Long,
    val title: String,
    val status: FollowUpStatus = FollowUpStatus.INBOX,
    val date: LocalDate? = null,
    val time: LocalTime? = null,
    val person: String? = null,
    val note: String? = null,
    val createdAt: Long = 0,
    val doneAt: Long? = null,
    val pickedDate: LocalDate? = null,
)

/** Kelompok di tab Kerja. Selesai tidak ditampilkan di daftar. */
enum class WorkSection { INBOX, OVERDUE, TODAY, WAITING, LATER }

private fun FollowUp.isOpen() = status != FollowUpStatus.DONE

private fun FollowUp.isPickedFor(day: LocalDate) = pickedDate == day

/** Dikerjakan hari ini: dipilih untuk hari ini, atau aktif dan jatuh tempo hari ini, atau menunggu dan dicek ulang hari ini. */
private fun FollowUp.isToday(today: LocalDate) =
    isOpen() && status != FollowUpStatus.INBOX && (isPickedFor(today) || date == today)

private fun FollowUp.isOverdue(today: LocalDate) =
    isOpen() && status != FollowUpStatus.INBOX && date != null && date.isBefore(today) && !isPickedFor(today)

/**
 * Kelompok follow-up untuk [today]. Urutan prioritas: Inbox, Hari ini, Lewat tanggal, Menunggu, Nanti.
 * Follow-up yang sudah selesai tidak masuk kelompok mana pun (null).
 */
fun FollowUp.sectionFor(today: LocalDate): WorkSection? = when {
    !isOpen() -> null
    status == FollowUpStatus.INBOX -> WorkSection.INBOX
    isToday(today) -> WorkSection.TODAY
    isOverdue(today) -> WorkSection.OVERDUE
    status == FollowUpStatus.WAITING -> WorkSection.WAITING
    else -> WorkSection.LATER
}

/** Jumlah hari lewat dari tanggal tindak lanjut, atau null kalau belum lewat atau tanpa tanggal. */
fun FollowUp.daysOverdue(today: LocalDate): Int? =
    date?.takeIf { isOpen() && it.isBefore(today) }?.let { ChronoUnit.DAYS.between(it, today).toInt() }

fun List<FollowUp>.groupedBySection(today: LocalDate): Map<WorkSection, List<FollowUp>> {
    val order = compareBy<FollowUp>({ it.date ?: LocalDate.MAX }, { it.time ?: LocalTime.MAX }, { it.id })
    return mapNotNull { item -> item.sectionFor(today)?.let { it to item } }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, items) -> items.sortedWith(order) }
}

/**
 * Daftar yang ditampilkan di daily scrum: yang lewat tanggal, jatuh tempo hari ini, dipilih saat EOD
 * kemarin, dan Menunggu yang cek ulangnya hari ini. Inbox dan yang jatuh tempo nanti tidak ikut.
 */
fun scrumCandidates(items: List<FollowUp>, today: LocalDate): List<FollowUp> =
    items.filter { it.sectionFor(today).let { s -> s == WorkSection.TODAY || s == WorkSection.OVERDUE } }
        .sortedWith(compareBy({ it.date ?: LocalDate.MAX }, { it.time ?: LocalTime.MAX }, { it.id }))

/** Pilih atau batalkan pilihan follow-up untuk dikerjakan hari ini. */
fun FollowUp.pickedForToday(picked: Boolean, today: LocalDate): FollowUp =
    copy(pickedDate = if (picked) today else null)

/** Yang perlu ditinjau saat EOD: semua follow-up yang dikerjakan hari ini. */
fun eodItems(items: List<FollowUp>, today: LocalDate): List<FollowUp> =
    items.filter { it.sectionFor(today) == WorkSection.TODAY }.sortedBy { it.id }

/** Pilihan status di EOD untuk follow-up hari ini. */
sealed interface EodAction {
    data object Done : EodAction
    /** Lanjut besok: dipilih otomatis di daily scrum besok. */
    data object Tomorrow : EodAction
    data class Reschedule(val date: LocalDate) : EodAction
    data class Wait(val recheckDate: LocalDate) : EodAction
}

fun FollowUp.applyEod(action: EodAction, today: LocalDate, nowMillis: Long): FollowUp = when (action) {
    EodAction.Done -> markDone(nowMillis)
    EodAction.Tomorrow -> copy(pickedDate = today.plusDays(1))
    is EodAction.Reschedule -> copy(status = FollowUpStatus.ACTIVE, date = action.date, pickedDate = null)
    is EodAction.Wait -> copy(status = FollowUpStatus.WAITING, date = action.recheckDate, pickedDate = null)
}

/**
 * Merapikan item Inbox: jadi Aktif (tanggal boleh kosong, berarti Nanti) atau Menunggu (tanggal cek ulang).
 */
fun FollowUp.tidyAsActive(date: LocalDate?): FollowUp = copy(status = FollowUpStatus.ACTIVE, date = date)

fun FollowUp.tidyAsWaiting(recheckDate: LocalDate): FollowUp = copy(status = FollowUpStatus.WAITING, date = recheckDate)

/** Menandai selesai tanpa melalui EOD (dari tab Kerja). */
fun FollowUp.markDone(nowMillis: Long): FollowUp =
    copy(status = FollowUpStatus.DONE, doneAt = nowMillis, pickedDate = null)

/** Saran nama orang: nama yang pernah dipakai, tanpa duplikat (tidak peka huruf besar), diawali [query]. */
fun personSuggestions(items: List<FollowUp>, query: String, limit: Int = 5): List<String> {
    val q = query.trim()
    val byName = items.mapNotNull { it.person?.trim()?.takeIf(String::isNotEmpty) }.groupBy { it.lowercase() }
    return byName.values.sortedByDescending { it.size }.map { it.first() }
        .filter { q.isEmpty() || it.startsWith(q, ignoreCase = true) }
        .filterNot { it.equals(q, ignoreCase = true) }
        .take(limit)
}

/** Semua nama orang yang pernah dipakai, urut abjad, untuk filter. */
fun allPeople(items: List<FollowUp>): List<String> =
    items.mapNotNull { it.person?.trim()?.takeIf(String::isNotEmpty) }
        .distinctBy { it.lowercase() }
        .sortedBy { it.lowercase() }

fun List<FollowUp>.forPerson(person: String?): List<FollowUp> =
    if (person == null) this else filter { it.person?.trim().equals(person, ignoreCase = true) }

/** Follow-up bertanda jam khusus pada [date] yang masih terbuka, untuk pengingat tingkat Pengingat. */
fun reminderTimes(items: List<FollowUp>, date: LocalDate): List<FollowUp> =
    items.filter { it.isOpen() && it.status != FollowUpStatus.INBOX && it.date == date && it.time != null }
        .sortedBy { it.time }

/** Catatan harian kerja. [eodNote] adalah catatan EOD; waktu selesai dalam milidetik, null kalau belum. */
data class WorkDay(
    val date: LocalDate,
    val eodNote: String? = null,
    val scrumDoneAt: Long? = null,
    val eodDoneAt: Long? = null,
)
