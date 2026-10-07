package com.roziqrizal.habitflow.domain.schedule

import com.roziqrizal.habitflow.domain.prayer.PrayerTimes
import java.time.LocalDate
import java.time.LocalDateTime

/** Satu kejadian alarm: [block] berbunyi pada [dateTime]. */
data class AlarmTime(val dateTime: LocalDateTime, val block: ScheduleBlock) {
    val date: LocalDate get() = dateTime.toLocalDate()
}

/** Seberapa jauh ke depan alarm dicari. Cukup untuk melewati akhir pekan atau beberapa tanggal yang dimatikan. */
const val ALARM_HORIZON_DAYS = 8

/**
 * Alarm berikutnya setelah [now]. Hanya blok bertingkat [NotificationLevel.ALARM] yang dihitung, dan
 * "Hari ini libur" tidak berpengaruh: alarm tetap bunyi di hari libur. Tanggal di [skipped] dilewati
 * (alarm dimatikan sekali). Null kalau tidak ada alarm dalam [ALARM_HORIZON_DAYS] hari.
 */
fun nextAlarm(
    blocks: List<ScheduleBlock>,
    now: LocalDateTime,
    skipped: Set<LocalDate>,
    prayerTimesFor: (LocalDate) -> PrayerTimes,
): AlarmTime? {
    val alarmBlocks = blocks.filter { it.level == NotificationLevel.ALARM }
    if (alarmBlocks.isEmpty()) return null

    for (offset in 0 until ALARM_HORIZON_DAYS) {
        val date = now.toLocalDate().plusDays(offset.toLong())
        if (date in skipped) continue
        val candidate = resolveBlocks(alarmBlocks, prayerTimesFor(date), date, isDayOff = false)
            .map { AlarmTime(date.atStartOfDay().plusMinutes(it.startMinute.toLong()), it.block) }
            .firstOrNull { it.dateTime.isAfter(now) }
        if (candidate != null) return candidate
    }
    return null
}
