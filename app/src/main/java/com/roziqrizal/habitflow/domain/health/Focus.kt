package com.roziqrizal.habitflow.domain.health

/*
 * Fokus kesehatan harian (tahap 30). Aturan lengkapnya di docs/rancangan.md bagian Fokus kesehatan harian: satu fokus per
 * hari, dipilih berurutan oleh aturan (yang pertama cocok menang), tanpa AI dan tanpa internet. Kategori adalah informasi,
 * bukan diagnosis. Teks yang tampil ada di lapisan UI; di sini hanya kode aturan dan angka yang dipakai.
 */

/** Tensi dua catatan terakhir sama-sama sampai di sini dianggap perlu diukur ulang: sistolik ke atas atau diastolik ke atas. */
const val FOCUS_BP_RECHECK_SYSTOLIC = 140
const val FOCUS_BP_RECHECK_DIASTOLIC = 90

/** Berat terakhir yang lebih lama dari ini dianggap basi. */
const val FOCUS_WEIGHT_STALE_DAYS = 10

/** Turun [FOCUS_FAST_LOSS_KG] atau lebih dalam 4 minggu lebih cepat dari 0,5 kg per minggu. */
const val FOCUS_FAST_LOSS_KG = 2.0

/** Kopi sebanyak ini atau lebih, atau minuman manis sebanyak [FOCUS_SWEET_MIN], memunculkan fokus minuman. */
const val FOCUS_COFFEE_MIN = 3
const val FOCUS_SWEET_MIN = 1

/** Fokus langkah baru berlaku mulai jam ini (menit sejak 00.00) dan kalau langkah di bawah separuh target. */
const val FOCUS_STEPS_FROM_MINUTE = 10 * 60
const val FOCUS_STEPS_FRACTION = 0.5

/** Jalan santai sekitar 120 langkah per menit, dibulatkan ke atas ke kelipatan 5, paling banyak 60 menit. */
const val FOCUS_STEPS_PER_MINUTE = 120
const val FOCUS_WALK_ROUND_MINUTES = 5
const val FOCUS_WALK_MAX_MINUTES = 60

/** Pengingat catat makan baru muncul mulai jam ini. */
const val FOCUS_MEAL_FROM_MINUTE = 13 * 60

/** Satu pengukuran tensi untuk menilai fokus. */
data class FocusBp(val systolic: Int, val diastolic: Int) {
    internal val high: Boolean get() = systolic >= FOCUS_BP_RECHECK_SYSTOLIC || diastolic >= FOCUS_BP_RECHECK_DIASTOLIC
}

/**
 * Bahan penilaian. Yang null berarti belum ada datanya, dan aturan yang membutuhkannya dilewati (tidak menebak).
 * [weightAgeDays] umur berat terakhir dalam hari. [steps] null kalau langkah belum terbaca.
 */
data class FocusInput(
    val weightKg: Double?,
    val heightCm: Double?,
    val targetKg: Double?,
    val weightAgeDays: Int?,
    val trend: WeightTrend?,
    val latestBp: FocusBp?,
    val previousBp: FocusBp?,
    val steps: Long?,
    val coffee: Int,
    val sweet: Int,
    val mealLoggedToday: Boolean,
    val minuteOfDay: Int,
)

enum class FocusRule {
    /** Tensi ≥ 180/110: saran tenang yang sudah ada, tanpa fokus gaya hidup. */
    BP_ADVICE,
    BP_RECHECK,
    FIRST_WEIGHT,
    STALE_WEIGHT,
    FAST_LOSS,
    DRINKS,
    LOW_STEPS,
    LOG_MEAL,
    ON_TRACK,
}

/** Hasil: aturan yang menang, dan lama jalan dalam menit kalau aturannya [FocusRule.LOW_STEPS]. */
data class Focus(val rule: FocusRule, val walkMinutes: Int? = null)

/** Lama jalan santai untuk menutup sisa langkah: dibulatkan ke atas ke kelipatan 5 menit, paling banyak 60. */
fun walkMinutesFor(remainingSteps: Long): Int {
    val raw = (remainingSteps + FOCUS_STEPS_PER_MINUTE - 1) / FOCUS_STEPS_PER_MINUTE
    val rounded = ((raw + FOCUS_WALK_ROUND_MINUTES - 1) / FOCUS_WALK_ROUND_MINUTES) * FOCUS_WALK_ROUND_MINUTES
    return rounded.coerceIn(FOCUS_WALK_ROUND_MINUTES.toLong(), FOCUS_WALK_MAX_MINUTES.toLong()).toInt()
}

/**
 * Berat boleh disarankan turun hanya kalau BMI tidak kurus dan berat masih di atas target. Tanpa tinggi atau target, tidak
 * ada saran turun.
 */
fun mayAdviseLosingWeight(weightKg: Double?, heightCm: Double?, targetKg: Double?): Boolean {
    if (weightKg == null || heightCm == null || targetKg == null) return false
    if (bmiCategory(bmi(weightKg, heightCm)) == BmiCategory.KURUS) return false
    return remainingToTarget(weightKg, targetKg) > 0.0
}

/** Memilih satu fokus hari ini. Aturan dicek berurutan; yang pertama cocok menang. */
fun computeFocus(input: FocusInput): Focus {
    val latest = input.latestBp
    val previous = input.previousBp

    // 1. Tensi. ≥ 180/110 selalu menang.
    if (latest != null && bpNeedsAdvice(latest.systolic, latest.diastolic)) return Focus(FocusRule.BP_ADVICE)
    if (latest != null && previous != null && latest.high && previous.high) return Focus(FocusRule.BP_RECHECK)

    // 2. Data awal: tanpa berat atau tinggi tidak ada rekomendasi lain.
    if (input.weightKg == null || input.heightCm == null) return Focus(FocusRule.FIRST_WEIGHT)

    // 3. Data lama.
    if (input.weightAgeDays != null && input.weightAgeDays > FOCUS_WEIGHT_STALE_DAYS) return Focus(FocusRule.STALE_WEIGHT)

    // 4. Turun terlalu cepat. Tidak pernah menyarankan menambah usaha menurun.
    if (input.trend != null && input.trend.deltaKg <= -FOCUS_FAST_LOSS_KG) return Focus(FocusRule.FAST_LOSS)

    // 5. Minuman.
    if (input.coffee >= FOCUS_COFFEE_MIN || input.sweet >= FOCUS_SWEET_MIN) return Focus(FocusRule.DRINKS)

    // 6. Langkah rendah: jalan santai, paling lama 60 menit.
    val steps = input.steps
    val half = (STEPS_TARGET * FOCUS_STEPS_FRACTION).toLong()
    if (steps != null && input.minuteOfDay >= FOCUS_STEPS_FROM_MINUTE && steps < half) {
        return Focus(FocusRule.LOW_STEPS, walkMinutesFor(STEPS_TARGET - steps))
    }

    // 7. Catat makan, hanya kalau menurunkan berat memang masuk akal.
    if (mayAdviseLosingWeight(input.weightKg, input.heightCm, input.targetKg) &&
        input.trend?.direction == TrendDirection.NAIK &&
        input.minuteOfDay >= FOCUS_MEAL_FROM_MINUTE &&
        !input.mealLoggedToday
    ) {
        return Focus(FocusRule.LOG_MEAL)
    }

    // 8. Bawaan.
    return Focus(FocusRule.ON_TRACK)
}
