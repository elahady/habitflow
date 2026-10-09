package com.roziqrizal.habitflow.domain.health

import java.time.DayOfWeek
import java.time.LocalDate

/*
 * Langkah dan pengingat kesehatan (tahap 21).
 */

const val STEPS_TARGET = 8000

/**
 * Habit langkah dicentang otomatis sekali per tanggal, saat langkah mencapai target. [lastAutoCheckedDate] adalah
 * tanggal terakhir habit dicentang otomatis, jadi centang yang dibatalkan manual tidak dicentang ulang di hari yang sama.
 */
fun shouldAutoCheckSteps(steps: Long, lastAutoCheckedDate: LocalDate?, today: LocalDate): Boolean =
    steps >= STEPS_TARGET && lastAutoCheckedDate != today

/** Frekuensi pengingat tensi. Mingguan jatuh di hari Senin. */
enum class BpFrequency { DAILY, WEEKLY, OFF }

enum class HealthReminderKind {
    WEIGHT, BLOOD_PRESSURE, BOTH;

    val includesWeight: Boolean get() = this != BLOOD_PRESSURE
    val includesBloodPressure: Boolean get() = this != WEIGHT
}

/** Pengingat kesehatan jatuh pada Subuh + menit ini (akhir blok Jamaah Subuh dan ngaji, sebelum aktivitas pagi). */
const val HEALTH_REMINDER_AFTER_SUBUH_MINUTES = 60

fun healthReminderMinute(subuhMinute: Int): Int = subuhMinute + HEALTH_REMINDER_AFTER_SUBUH_MINUTES

/** Hari timbang dan hari tensi mingguan. */
val HEALTH_WEEKLY_DAY: DayOfWeek = DayOfWeek.MONDAY

/**
 * Jenis pengingat kesehatan untuk [date], atau null kalau tidak ada. Jenis yang hari itu sudah dicatat tidak diingatkan.
 * Berat hanya di hari Senin dan hanya kalau menyala. Tensi harian, atau Senin kalau mingguan.
 */
fun healthReminderKind(
    date: LocalDate,
    weightEnabled: Boolean,
    bpFrequency: BpFrequency,
    weightLoggedToday: Boolean,
    bpLoggedToday: Boolean,
): HealthReminderKind? {
    val isWeeklyDay = date.dayOfWeek == HEALTH_WEEKLY_DAY
    val weightDue = weightEnabled && isWeeklyDay && !weightLoggedToday
    val bpDue = when (bpFrequency) {
        BpFrequency.DAILY -> true
        BpFrequency.WEEKLY -> isWeeklyDay
        BpFrequency.OFF -> false
    } && !bpLoggedToday
    return when {
        weightDue && bpDue -> HealthReminderKind.BOTH
        weightDue -> HealthReminderKind.WEIGHT
        bpDue -> HealthReminderKind.BLOOD_PRESSURE
        else -> null
    }
}

/**
 * Tanggal berikutnya (mulai [from], paling jauh seminggu) yang punya pengingat kesehatan terjadwal, tanpa memperhitungkan
 * apakah sudah dicatat (itu dicek saat alarm berbunyi). Null kalau keduanya mati.
 */
fun nextHealthReminderDate(from: LocalDate, weightEnabled: Boolean, bpFrequency: BpFrequency): LocalDate? =
    (0L..7L).map { from.plusDays(it) }.firstOrNull { date ->
        healthReminderKind(date, weightEnabled, bpFrequency, weightLoggedToday = false, bpLoggedToday = false) != null
    }
