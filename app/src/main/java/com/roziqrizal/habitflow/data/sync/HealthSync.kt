package com.roziqrizal.habitflow.data.sync

import com.roziqrizal.habitflow.data.BloodPressureEntry
import com.roziqrizal.habitflow.data.HealthSettings
import com.roziqrizal.habitflow.data.WeightEntry
import com.roziqrizal.habitflow.data.health.StepsSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate

/*
 * Kirim data kesehatan ke server (tahap 30 langkah 4). Satu arah: HP menulis lewat `PUT /api/v1/health`, web hanya membaca.
 * Aturan fokus hanya ada di `domain/health/Focus.kt`; yang dikirim adalah hasil dan teksnya, supaya web menampilkan kalimat
 * yang sama persis. Mengirim ulang aman (server idempotent), jadi tidak ada antrean: setiap kiriman memuat jendela terbaru.
 */

/** Berat dan tensi yang dikirim: hanya catatan sejauh ini ke belakang. Server menyimpan yang lebih lama dari kiriman sebelumnya. */
const val HEALTH_SYNC_DAYS = 120L

/** Batas per permintaan di server. */
const val HEALTH_SYNC_MAX_ENTRIES = 500

/** Langkah dikirim untuk hari ini dan enam hari sebelumnya. */
const val HEALTH_SYNC_STEP_DAYS = 7

/** Fokus kesehatan terakhir yang dihitung HP, dengan teks yang tampil di kartu. [computedAtMillis] hanya penanda waktu hitung. */
data class HealthFocusPayload(
    val rule: String,
    val walkMinutes: Int?,
    val title: String,
    val sentence: String,
    val note: String,
    val reasons: List<Pair<String, String>>,
    val computedAtMillis: Long,
)

/**
 * Satu kiriman. [heightCm] dan [targetKg] adalah nilai yang dipakai kartu di HP (target bawaan sudah dihitung dari tinggi),
 * supaya web menampilkan angka yang sama.
 */
data class HealthSyncPayload(
    val timezone: String,
    val heightCm: Double?,
    val targetKg: Double?,
    val weights: List<WeightEntry>,
    val bloodPressures: List<BloodPressureEntry>,
    val stepDays: List<Pair<LocalDate, Long>>,
    val focus: HealthFocusPayload?,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("timezone", timezone)
        put("profile", JSONObject().put("height_cm", heightCm ?: JSONObject.NULL).put("target_kg", targetKg ?: JSONObject.NULL))
        put(
            "weights",
            JSONArray().apply { weights.forEach { put(JSONObject().put("time_millis", it.timeMillis).put("kg", it.kg)) } },
        )
        put(
            "blood_pressures",
            JSONArray().apply {
                bloodPressures.forEach {
                    put(
                        JSONObject()
                            .put("time_millis", it.timeMillis)
                            .put("systolic", it.systolic)
                            .put("diastolic", it.diastolic)
                            .put("pulse", it.pulse ?: JSONObject.NULL)
                            .put("note", it.note?.take(500) ?: JSONObject.NULL),
                    )
                }
            },
        )
        put(
            "step_days",
            JSONArray().apply { stepDays.forEach { (date, steps) -> put(JSONObject().put("date", date.toString()).put("steps", steps)) } },
        )
        // Tanpa fokus (belum pernah dihitung): kuncinya tidak dikirim, jadi fokus lama di server tidak tersentuh.
        focus?.let { f ->
            put(
                "focus",
                JSONObject()
                    .put("rule", f.rule)
                    .put("walk_minutes", f.walkMinutes ?: JSONObject.NULL)
                    .put("title", f.title.take(200))
                    .put("sentence", f.sentence.take(600))
                    .put("note", f.note.take(600))
                    .put(
                        "reasons",
                        JSONArray().apply {
                            f.reasons.take(6).forEach { (label, value) ->
                                put(JSONObject().put("label", label.take(40)).put("value", value.take(120)))
                            }
                        },
                    )
                    .put("computed_at", Instant.ofEpochMilli(f.computedAtMillis).toString()),
            )
        }
    }

    /** Dua kiriman dengan isi sama menghasilkan teks sama, dipakai untuk melewati kiriman yang tidak ada gunanya. */
    val fingerprint: String get() = toJson().toString()
}

/**
 * Menyusun kiriman dari data lokal. [weights] dan [bloodPressures] boleh dalam urutan apa pun dan boleh lebih dari batas;
 * yang dikirim paling baru [HEALTH_SYNC_MAX_ENTRIES], urut dari yang paling lama. Target bawaan dihitung dari tinggi kalau
 * pengguna belum menetapkannya, sama dengan kartu di HP.
 */
fun healthSyncPayload(
    timezone: String,
    heightCm: Double?,
    targetKg: Double?,
    weights: List<WeightEntry>,
    bloodPressures: List<BloodPressureEntry>,
    stepDays: List<Pair<LocalDate, Long>>,
    focus: HealthFocusPayload?,
    defaultTarget: (Double) -> Double,
): HealthSyncPayload = HealthSyncPayload(
    timezone = timezone,
    heightCm = heightCm,
    targetKg = targetKg ?: heightCm?.let(defaultTarget),
    weights = weights.sortedWith(compareBy({ it.timeMillis }, { it.id })).takeLast(HEALTH_SYNC_MAX_ENTRIES),
    bloodPressures = bloodPressures.sortedWith(compareBy({ it.timeMillis }, { it.id })).takeLast(HEALTH_SYNC_MAX_ENTRIES),
    stepDays = stepDays.sortedBy { it.first }.takeLast(HEALTH_SYNC_STEP_DAYS),
    focus = focus,
)

/**
 * Menyimpan fokus terakhir yang dihitung layar Hari ini, di memori. Aturan fokus butuh data yang hidup di UI (langkah,
 * kopi, makan), jadi layar yang menerbitkannya dan sinkron di latar belakang yang membacanya.
 */
class HealthFocusStore {
    private val _latest = MutableStateFlow<HealthFocusPayload?>(null)
    val latest: StateFlow<HealthFocusPayload?> = _latest.asStateFlow()

    /** Fokus yang isinya sama dengan yang sudah ada (selain waktu hitung) diabaikan, supaya tidak memicu kiriman ulang. */
    fun publish(focus: HealthFocusPayload) {
        val current = _latest.value
        if (current != null && current.copy(computedAtMillis = focus.computedAtMillis) == focus) return
        _latest.value = focus
    }
}

/** Yang dibutuhkan manajer sinkron untuk mengirim data kesehatan. */
class HealthSyncInputs(val settings: HealthSettings, val steps: StepsSource, val focus: HealthFocusStore)
