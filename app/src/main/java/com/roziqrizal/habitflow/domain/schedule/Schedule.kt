package com.roziqrizal.habitflow.domain.schedule

import com.roziqrizal.habitflow.domain.calendar.EventLabel
import com.roziqrizal.habitflow.domain.prayer.PrayerName
import com.roziqrizal.habitflow.domain.prayer.PrayerTimes
import com.roziqrizal.habitflow.domain.prayer.of
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/**
 * Hari aktif disimpan sebagai bitmask: bit 0 = Senin sampai bit 6 = Minggu
 * (`DayOfWeek.value - 1`).
 */
object Days {
    const val WEEKDAYS = 0b0011111
    const val WEEKEND = 0b1100000
    const val ALL = 0b1111111

    fun bit(day: DayOfWeek): Int = 1 shl (day.value - 1)
    fun contains(mask: Int, day: DayOfWeek): Boolean = mask and bit(day) != 0
}

/** Patokan waktu mulai: jam tetap, atau waktu sholat dengan selisih menit (boleh negatif). */
sealed interface BlockStart {
    data class Fixed(val time: LocalTime) : BlockStart
    data class Prayer(val name: PrayerName, val offsetMinutes: Int) : BlockStart
}

/** Notifikasi blok membuka daily scrum atau EOD di tab Kerja (tahap 19). */
enum class WorkAction { SCRUM, EOD }

/** Tingkat notifikasi. [ALARM] berbunyi penuh dan tidak ikut "Hari ini libur" (tahap 18). */
enum class NotificationLevel { INFO, REMINDER, ALARM }

/**
 * Satu blok jadwal. Selesainya ditentukan [endMinuteOfDay] kalau ada, kalau tidak mulai +
 * [durationMinutes]. Durasi 0 berarti titik waktu (misalnya batas tidur).
 */
data class ScheduleBlock(
    val id: Long,
    val name: String,
    val start: BlockStart,
    val durationMinutes: Int,
    val endMinuteOfDay: Int? = null,
    val activeDays: Int = Days.ALL,
    val level: NotificationLevel = NotificationLevel.INFO,
    val sortOrder: Int = 0,
    val habitIds: Set<Long> = emptySet(),
    val workAction: WorkAction? = null,
    /** Blok ini jam kerja: pengingat air dan break dihitung dari jam mulainya (tahap 20). */
    val workReminders: Boolean = false,
)

/** Penanda blok yang berasal dari acara (tahap 22), bukan dari jadwal: labelnya, dan nama kalender HP kalau dari kalender HP. */
data class EventMarker(val label: EventLabel, val source: String? = null)

/**
 * Blok dengan waktu yang sudah dihitung untuk satu tanggal, dalam menit sejak 00.00. [event] terisi kalau blok ini
 * sebenarnya acara (tahap 22): ia ikut Sekarang/Berikutnya dan timeline, tapi tidak diumumkan sebagai blok.
 */
data class ResolvedBlock(
    val block: ScheduleBlock,
    val startMinute: Int,
    val endMinute: Int,
    val event: EventMarker? = null,
) {
    val isPoint: Boolean get() = endMinute <= startMinute
}

const val MINUTES_PER_DAY = 24 * 60

private fun LocalTime.toMinuteOfDay(): Int = hour * 60 + minute

/** Blok yang hanya aktif Senin sampai Jumat dimatikan oleh "Hari ini libur". */
fun ScheduleBlock.isWorkdayOnly(): Boolean = activeDays and Days.WEEKEND == 0

/**
 * Daftar blok untuk [date], urut menurut waktu mulai. Blok yang tidak aktif di hari itu,
 * dimatikan oleh hari libur, atau patokan sholatnya tidak ada (lintang kutub) dibuang.
 */
fun resolveBlocks(
    blocks: List<ScheduleBlock>,
    prayerTimes: PrayerTimes,
    date: LocalDate,
    isDayOff: Boolean,
): List<ResolvedBlock> =
    blocks.asSequence()
        .filter { Days.contains(it.activeDays, date.dayOfWeek) }
        .filterNot { isDayOff && it.isWorkdayOnly() }
        .mapNotNull { block ->
            val start = when (val s = block.start) {
                is BlockStart.Fixed -> s.time.toMinuteOfDay()
                is BlockStart.Prayer ->
                    (prayerTimes.of(s.name)?.toMinuteOfDay() ?: return@mapNotNull null) + s.offsetMinutes
            }.coerceIn(0, MINUTES_PER_DAY - 1)
            val end = (block.endMinuteOfDay ?: (start + block.durationMinutes)).coerceIn(start, MINUTES_PER_DAY)
            ResolvedBlock(block, start, end)
        }
        .sortedWith(compareBy({ it.startMinute }, { it.block.sortOrder }, { it.block.id }))
        .toList()

/**
 * Sekarang = blok aktif yang paling akhir dimulai, jadi blok sholat di tengah blok kerja
 * menggantikan blok kerja selama sholat. Titik waktu tidak pernah jadi Sekarang.
 * Berikutnya = blok pertama yang dimulai setelah [nowMinute].
 */
data class NowAndNext(val now: ResolvedBlock?, val next: ResolvedBlock?)

fun nowAndNext(resolved: List<ResolvedBlock>, nowMinute: Int): NowAndNext {
    val now = resolved.lastOrNull { !it.isPoint && it.startMinute <= nowMinute && nowMinute < it.endMinute }
    val next = resolved.firstOrNull { it.startMinute > nowMinute }
    return NowAndNext(now, next)
}

/** Rentang notifikasi tetap: dari blok pertama sampai akhir blok terakhir (batas tidur). Null kalau tidak ada blok. */
fun notificationWindow(resolved: List<ResolvedBlock>): IntRange? {
    if (resolved.isEmpty()) return null
    return resolved.minOf { it.startMinute }..resolved.maxOf { maxOf(it.startMinute, it.endMinute) }
}

/** Semua menit batas blok (mulai dan selesai), tanpa duplikat, urut. Dipakai menjadwalkan pembaruan notifikasi. */
fun boundaryMinutes(resolved: List<ResolvedBlock>): List<Int> =
    resolved.flatMap { listOf(it.startMinute, it.endMinute) }.filter { it < MINUTES_PER_DAY }.distinct().sorted()

/** Menit batas blok pertama setelah [afterMinute] hari ini, atau null kalau sudah lewat semuanya. */
fun nextBoundaryMinute(resolved: List<ResolvedBlock>, afterMinute: Int): Int? =
    boundaryMinutes(resolved).firstOrNull { it > afterMinute }

/**
 * Blok yang mulai di rentang ([sinceMinute], [nowMinute]], untuk dinotifikasikan. Alarm tidak presisi,
 * jadi rentang dipakai, bukan satu menit tepat. Titik waktu ikut, karena batas tidur juga perlu pengingat.
 */
fun blocksStartedBetween(resolved: List<ResolvedBlock>, sinceMinute: Int, nowMinute: Int): List<ResolvedBlock> =
    resolved.filter { it.startMinute > sinceMinute && it.startMinute <= nowMinute }

/**
 * Waktu sholat yang diumumkan blok ini sebagai adzan: blok yang mulai tepat di waktu sholat
 * (selisih 0). Switch "Pengingat adzan" per waktu sholat mematikan notifikasi blok seperti ini.
 */
fun ScheduleBlock.adzanPrayer(): PrayerName? =
    (start as? BlockStart.Prayer)?.takeIf { it.offsetMinutes == 0 }?.name
