package com.roziqrizal.habitflow.data.sync

import android.content.Context
import android.content.SharedPreferences
import com.roziqrizal.habitflow.data.PrefFile
import com.roziqrizal.habitflow.domain.work.DailyBrief
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * Salinan ringkasan harian dari server (tahap 32), supaya kartu di tab Kerja tetap tampil tanpa internet. Server tetap
 * sumber kebenaran; ini hanya cache satu ringkasan terakhir dan ditimpa tiap kali berhasil menarik. Disimpan di
 * `daily_brief.xml`, tidak ikut Auto Backup, dan dihapus saat Keluar lewat [PrefFile].
 */
class DailyBriefStore(private val prefs: SharedPreferences) {

    constructor(context: Context) : this(PrefFile.DAILY_BRIEF.open(context))

    private val _briefs = MutableStateFlow(read())

    /** Ringkasan yang tersimpan, paling banyak satu. */
    val briefs: StateFlow<List<DailyBrief>> = _briefs.asStateFlow()

    /** Menyimpan [brief], atau mengosongkan cache kalau null (server tidak punya ringkasan berisi). */
    fun save(brief: DailyBrief?) {
        val editor = prefs.edit()
        if (brief == null) {
            editor.clear()
        } else {
            editor.putString(KEY_DATE, brief.date.toString()).putString(KEY_DIGEST, brief.emailDigest).putLong(KEY_UPDATED, brief.updatedAt)
        }
        editor.apply()
        _briefs.value = listOfNotNull(brief)
    }

    private fun read(): List<DailyBrief> {
        val date = try {
            LocalDate.parse(prefs.getString(KEY_DATE, null) ?: return emptyList())
        } catch (e: DateTimeParseException) {
            return emptyList()
        }
        val digest = prefs.getString(KEY_DIGEST, null) ?: return emptyList()
        return listOf(DailyBrief(date, digest, prefs.getLong(KEY_UPDATED, 0L)))
    }

    private companion object {
        const val KEY_DATE = "date"
        const val KEY_DIGEST = "email_digest"
        const val KEY_UPDATED = "updated_at"
    }
}
