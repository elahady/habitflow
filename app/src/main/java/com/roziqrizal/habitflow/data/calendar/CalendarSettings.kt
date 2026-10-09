package com.roziqrizal.habitflow.data.calendar

import android.content.Context
import com.roziqrizal.habitflow.domain.calendar.EventLabel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Kalender HP yang dibaca dan labelnya (tahap 22). Bawaan: tidak ada yang dipilih. Nomor kalender khusus perangkat, jadi pilihan ini
 * sengaja tidak ikut cadangan ke server.
 */
class CalendarSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("calendar", Context.MODE_PRIVATE)

    private val _selection = MutableStateFlow(read())

    /** Nomor kalender yang dipilih ke labelnya. */
    val selection: StateFlow<Map<Long, EventLabel>> = _selection.asStateFlow()

    fun setSelected(calendarId: Long, selected: Boolean) {
        val current = _selection.value
        write(if (selected) current + (calendarId to (current[calendarId] ?: EventLabel.PERSONAL)) else current - calendarId)
    }

    fun setLabel(calendarId: Long, label: EventLabel) {
        val current = _selection.value
        if (calendarId in current) write(current + (calendarId to label))
    }

    private fun write(value: Map<Long, EventLabel>) {
        prefs.edit().putStringSet(KEY, value.map { (id, label) -> "$id|${label.name}" }.toSet()).apply()
        _selection.value = value
    }

    private fun read(): Map<Long, EventLabel> =
        prefs.getStringSet(KEY, emptySet()).orEmpty().mapNotNull { entry ->
            val (id, label) = entry.split('|').takeIf { it.size == 2 } ?: return@mapNotNull null
            val calendarId = id.toLongOrNull() ?: return@mapNotNull null
            calendarId to (EventLabel.entries.firstOrNull { it.name == label } ?: EventLabel.PERSONAL)
        }.toMap()

    private companion object {
        const val KEY = "selection"
    }
}
