package com.roziqrizal.habitflow.domain.schedule

import kotlin.math.abs

/*
 * Pengingat kerja (tahap 20): minum air setiap 60 menit dan break setiap 90 menit di blok yang bertanda
 * `workReminders`. Aturan lengkapnya di docs/rancangan.md bagian Pengingat kerja.
 */

const val WATER_INTERVAL_MINUTES = 60
const val BREAK_INTERVAL_MINUTES = 90

/** Air dan break yang berjarak sampai sebesar ini digabung menjadi satu pengingat. */
const val REMINDER_MERGE_MINUTES = 15

const val GLASSES_TARGET = 8

/** Pengingat air yang tidak dijawab sebanyak ini berturut-turut di satu blok menghentikan pengingat air. */
const val MAX_IGNORED_WATER = 3

enum class WorkReminderKind {
    WATER, BREAK, BREAK_AND_WATER;

    val includesWater: Boolean get() = this != BREAK
}

/** Satu pengingat pada [minute] (menit sejak 00.00) yang berasal dari blok kerja [blockId]. */
data class WorkReminder(val minute: Int, val kind: WorkReminderKind, val blockId: Long)

/**
 * Semua pengingat kerja hari ini, urut menurut waktu. [resolved] sudah memuat blok yang aktif hari itu, jadi
 * "Hari ini libur" yang mematikan blok Kerja otomatis mematikan pengingatnya. Pengingat yang jatuh di blok
 * sholat digeser ke akhir blok sholat itu, dan dibuang kalau hasilnya tidak lagi di dalam blok kerja.
 */
fun workReminders(
    resolved: List<ResolvedBlock>,
    water: Boolean = true,
    breaks: Boolean = true,
): List<WorkReminder> {
    val holds = resolved.filter { !it.isPoint && !it.block.workReminders && it.block.adzanPrayer() != null }
    return resolved
        .filter { it.block.workReminders && !it.isPoint }
        .flatMap { segment -> remindersOf(segment, holds, water, breaks) }
        .sortedWith(compareBy({ it.minute }, { it.blockId }))
}

private fun remindersOf(
    segment: ResolvedBlock,
    holds: List<ResolvedBlock>,
    water: Boolean,
    breaks: Boolean,
): List<WorkReminder> {
    fun times(first: Int, every: Int): List<Int> =
        generateSequence(first) { it + every }
            .takeWhile { it < segment.endMinute }
            .map { shiftOutOfHolds(it, holds) }
            .filter { it < segment.endMinute }
            .distinct()
            .toList()

    val id = segment.block.id
    val waterTimes = if (water) times(segment.startMinute, WATER_INTERVAL_MINUTES) else emptyList()
    val breakTimes = if (breaks) times(segment.startMinute + BREAK_INTERVAL_MINUTES, BREAK_INTERVAL_MINUTES) else emptyList()

    val remainingWater = waterTimes.toMutableList()
    val result = mutableListOf<WorkReminder>()
    for (breakMinute in breakTimes) {
        val partner = remainingWater
            .filter { abs(it - breakMinute) <= REMINDER_MERGE_MINUTES }
            .minByOrNull { abs(it - breakMinute) }
        if (partner != null) {
            remainingWater.remove(partner)
            result += WorkReminder(minOf(partner, breakMinute), WorkReminderKind.BREAK_AND_WATER, id)
        } else {
            result += WorkReminder(breakMinute, WorkReminderKind.BREAK, id)
        }
    }
    remainingWater.forEach { result += WorkReminder(it, WorkReminderKind.WATER, id) }
    return result
}

/** Menit pertama sesudah [minute] yang tidak jatuh di blok sholat mana pun. Selalu berhenti: tiap geser maju. */
private fun shiftOutOfHolds(minute: Int, holds: List<ResolvedBlock>): Int {
    var t = minute
    while (true) {
        val hold = holds.firstOrNull { t >= it.startMinute && t < it.endMinute } ?: return t
        t = hold.endMinute
    }
}

/** Menit pengingat berikutnya setelah [afterMinute] hari ini, atau null kalau sudah lewat semuanya. */
fun nextWorkReminderMinute(reminders: List<WorkReminder>, afterMinute: Int): Int? =
    reminders.firstOrNull { it.minute > afterMinute }?.minute

/** Pengingat di rentang ([sinceMinute], [nowMinute]], seperti [blocksStartedBetween] untuk blok. */
fun workRemindersBetween(reminders: List<WorkReminder>, sinceMinute: Int, nowMinute: Int): List<WorkReminder> =
    reminders.filter { it.minute > sinceMinute && it.minute <= nowMinute }

/** Hitungan gelas melewati target pada perubahan ini. Hanya saat naik melewatinya, supaya centang manual menang. */
fun crossesWaterTarget(before: Int, after: Int): Boolean = before < GLASSES_TARGET && after >= GLASSES_TARGET

/**
 * Ingatan pengingat air terakhir. [segment] mengenali blok dan tanggalnya, [ignored] jumlah pengingat sebelumnya
 * yang tidak dijawab, [glassesAtLast] hitungan gelas saat pengingat terakhir dikirim.
 */
data class WaterReminderState(
    val segment: String? = null,
    val ignored: Int = 0,
    val glassesAtLast: Int? = null,
)

data class WaterDecision(val send: Boolean, val state: WaterReminderState)

/**
 * Menentukan apakah pengingat air di [segment] dikirim. Pengingat sebelumnya dianggap tidak dijawab kalau hitungan
 * gelas tidak bertambah sejak itu. Setelah [MAX_IGNORED_WATER] tak terjawab, pengingat air berhenti sampai blok
 * (segment) berikutnya, juga kalau gelas ditambah belakangan.
 */
fun decideWaterReminder(state: WaterReminderState, segment: String, glassesNow: Int): WaterDecision {
    val sameSegment = state.segment == segment
    val last = state.glassesAtLast
    val ignored = when {
        !sameSegment -> 0
        state.ignored >= MAX_IGNORED_WATER -> state.ignored
        last != null && glassesNow <= last -> state.ignored + 1
        else -> 0
    }
    if (ignored >= MAX_IGNORED_WATER) {
        return WaterDecision(false, WaterReminderState(segment, ignored, state.glassesAtLast))
    }
    return WaterDecision(true, WaterReminderState(segment, ignored, glassesNow))
}
