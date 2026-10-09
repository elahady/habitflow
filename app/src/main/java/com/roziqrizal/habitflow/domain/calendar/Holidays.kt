package com.roziqrizal.habitflow.domain.calendar

import org.json.JSONException
import org.json.JSONObject
import java.time.LocalDate

/*
 * Libur nasional dan cuti bersama (tahap 22). Daftar per tahun disimpan di assets/libur/<tahun>.json, bersumber SKB 3 Menteri.
 * Libur dihitung, bukan disimpan di days_off: libur efektif = libur manual ∪ (libur nasional − pembatalan).
 */

/** Satu hari libur. [cutiBersama] benar untuk cuti bersama, yang diperlakukan sama dengan libur nasional. */
data class Holiday(val date: LocalDate, val name: String, val cutiBersama: Boolean = false) {
    /** "Idulfitri 1447 H" atau "Cuti bersama: Idulfitri 1447 H". */
    val label: String get() = if (cutiBersama) "Cuti bersama: $name" else name
}

/** Kumpulan hari libur. Kalau satu tanggal punya libur nasional dan cuti bersama, yang nasional dipakai. */
class HolidayCalendar(holidays: List<Holiday>) {

    // associateBy memakai nilai terakhir, jadi cuti bersama diurutkan lebih dulu supaya libur nasional menang di tanggal yang sama.
    private val byDate: Map<LocalDate, Holiday> =
        holidays.sortedByDescending { it.cutiBersama }.associateBy { it.date }

    fun on(date: LocalDate): Holiday? = byDate[date]

    /** Hari libur di rentang [from] sampai [to] (keduanya ikut), urut menurut tanggal. */
    fun between(from: LocalDate, to: LocalDate): List<Holiday> =
        byDate.values.filter { !it.date.isBefore(from) && !it.date.isAfter(to) }.sortedBy { it.date }

    val all: List<Holiday> get() = byDate.values.sortedBy { it.date }

    companion object {
        val EMPTY = HolidayCalendar(emptyList())

        /** Menggabungkan beberapa daftar tahunan. */
        fun of(vararg years: List<Holiday>) = HolidayCalendar(years.flatMap { it })
    }
}

/** Isi satu berkas `libur/<tahun>.json`. Entri yang rusak dilewati, berkas yang rusak menghasilkan daftar kosong. */
fun parseHolidays(json: String): List<Holiday> = try {
    val array = JSONObject(json).getJSONArray("holidays")
    (0 until array.length()).mapNotNull { index ->
        runCatching {
            val item = array.getJSONObject(index)
            Holiday(LocalDate.parse(item.getString("date")), item.getString("name"), item.optBoolean("cutiBersama", false))
        }.getOrNull()
    }
} catch (e: JSONException) {
    emptyList()
}

/**
 * Keadaan libur satu tanggal: libur manual ("Hari ini libur"), libur nasional (bisa kosong), dan apakah libur nasional itu
 * dibatalkan untuk tanggal ini.
 */
data class DayOffInfo(
    val manual: Boolean = false,
    val holiday: Holiday? = null,
    val holidayCancelled: Boolean = false,
) {
    /** Libur nasional yang berlaku (ada dan tidak dibatalkan). */
    val holidayActive: Boolean get() = holiday != null && !holidayCancelled

    val isOff: Boolean get() = manual || holidayActive
}

fun dayOffInfo(date: LocalDate, manual: Set<LocalDate>, holidays: HolidayCalendar, cancelled: Set<LocalDate>): DayOffInfo =
    DayOffInfo(manual = date in manual, holiday = holidays.on(date), holidayCancelled = date in cancelled)

/** Semua tanggal libur efektif di [from] sampai [to] (keduanya ikut). */
fun effectiveDaysOff(
    from: LocalDate,
    to: LocalDate,
    manual: Set<LocalDate>,
    holidays: HolidayCalendar,
    cancelled: Set<LocalDate>,
): Set<LocalDate> {
    val national = holidays.between(from, to).map { it.date }.filter { it !in cancelled }
    return manual.filter { !it.isBefore(from) && !it.isAfter(to) }.toSet() + national
}
