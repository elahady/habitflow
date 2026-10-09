package com.roziqrizal.habitflow.data

import android.content.Context
import com.roziqrizal.habitflow.domain.calendar.HolidayCalendar
import com.roziqrizal.habitflow.domain.calendar.parseHolidays

/**
 * Daftar libur nasional dan cuti bersama dari `assets/libur/<tahun>.json` (tahap 22). Dibaca sekali lalu disimpan. Tahun tanpa
 * berkas berarti tidak ada libur otomatis; berkas yang rusak dilewati.
 */
object HolidayAssets {

    @Volatile
    private var cached: HolidayCalendar? = null

    fun get(context: Context): HolidayCalendar =
        cached ?: synchronized(this) {
            cached ?: load(context.applicationContext).also { cached = it }
        }

    private fun load(context: Context): HolidayCalendar {
        val files = runCatching { context.assets.list(DIR) }.getOrNull().orEmpty().filter { it.endsWith(".json") }
        val holidays = files.flatMap { name ->
            runCatching { context.assets.open("$DIR/$name").bufferedReader().use { it.readText() } }
                .map(::parseHolidays)
                .getOrDefault(emptyList())
        }
        return HolidayCalendar(holidays)
    }

    private const val DIR = "libur"
}
