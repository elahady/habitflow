package com.roziqrizal.habitflow.domain.meals

import java.time.LocalDate

/*
 * Asupan makan (tahap 23): catatan Isi Piringku per waktu makan dan aturan centang otomatis habit makan. Aturan lengkapnya
 * di docs/rancangan.md bagian Asupan makan dan docs/concept.md tahap 23.
 */

enum class MealKind { BREAKFAST, LUNCH, DINNER, SNACK }

/**
 * Satu catatan makan: komponen Isi Piringku yang ada di piring, tanda gorengan dan manis, dan jam makan ([minute], menit sejak
 * 00.00 di [date]). Satu catatan per tanggal dan jenis.
 */
data class Meal(
    val date: LocalDate,
    val kind: MealKind,
    val minute: Int,
    val carb: Boolean = false,
    val protein: Boolean = false,
    val vegetable: Boolean = false,
    val fruit: Boolean = false,
    val fried: Boolean = false,
    val sweet: Boolean = false,
    val note: String? = null,
) {
    /** Keempat komponen Isi Piringku ada. */
    val fullPlate: Boolean get() = carb && protein && vegetable && fruit
}

/** Jenis minuman yang dihitung selain air. */
enum class CountedDrink { COFFEE, SWEET }

/** Batas tidur bawaan kalau blok "Batas tidur" tidak ada. */
const val DEFAULT_BEDTIME_MINUTE = 22 * 60

/** Makan malam dihitung tepat waktu kalau paling lambat sekian menit sebelum batas tidur. */
const val DINNER_BEFORE_BEDTIME_MINUTES = 120

/** Kopi paling banyak sekian gelas per hari. */
const val COFFEE_LIMIT = 2

/** Pengingat catatan makan muncul sekian menit sebelum batas tidur. */
const val MEAL_REMINDER_BEFORE_BEDTIME_MINUTES = 60

/** Habit makan yang dicentang otomatis. */
enum class MealRule { DINNER_ON_TIME, NO_FRIED_OR_SWEET, NO_SWEET_DRINK, COFFEE_WITHIN_LIMIT }

/** Makan malam dicatat paling lambat [DINNER_BEFORE_BEDTIME_MINUTES] menit sebelum [bedtimeMinute]. Tanpa catatan malam: tidak. */
fun dinnerOnTime(meals: List<Meal>, bedtimeMinute: Int): Boolean =
    meals.any { it.kind == MealKind.DINNER && it.minute <= bedtimeMinute - DINNER_BEFORE_BEDTIME_MINUTES }

/** Tidak ada catatan bergorengan atau bermanis. Hari tanpa catatan makan sama sekali tidak dihitung (tidak ada data). */
fun noFriedOrSweet(meals: List<Meal>): Boolean = meals.isNotEmpty() && meals.none { it.fried || it.sweet }

fun noSweetDrink(sweetCount: Int): Boolean = sweetCount == 0

/** Kopi paling banyak [COFFEE_LIMIT] gelas. */
fun coffeeWithinLimit(coffeeCount: Int): Boolean = coffeeCount <= COFFEE_LIMIT

/** Kopi di atas batas, untuk pesan tenang di kartu air. */
fun coffeeOverLimit(coffeeCount: Int): Boolean = coffeeCount > COFFEE_LIMIT

/** Aturan yang terpenuhi pada satu hari. [meals] hanya catatan hari itu. */
fun satisfiedRules(meals: List<Meal>, coffeeCount: Int, sweetCount: Int, bedtimeMinute: Int): Set<MealRule> = buildSet {
    if (dinnerOnTime(meals, bedtimeMinute)) add(MealRule.DINNER_ON_TIME)
    if (noFriedOrSweet(meals)) add(MealRule.NO_FRIED_OR_SWEET)
    if (noSweetDrink(sweetCount)) add(MealRule.NO_SWEET_DRINK)
    if (coffeeWithinLimit(coffeeCount)) add(MealRule.COFFEE_WITHIN_LIMIT)
}

/** Waktu makan utama yang diingatkan kalau belum dicatat. Camilan tidak wajib. */
val REQUIRED_MEALS = listOf(MealKind.BREAKFAST, MealKind.LUNCH, MealKind.DINNER)

/** Waktu makan utama yang belum dicatat hari itu, urut sarapan, siang, malam. */
fun missingMeals(meals: List<Meal>): List<MealKind> = REQUIRED_MEALS.filter { kind -> meals.none { it.kind == kind } }

/** Menit pengingat catatan makan, atau null kalau jatuhnya sebelum 00.00. */
fun mealReminderMinute(bedtimeMinute: Int): Int? =
    (bedtimeMinute - MEAL_REMINDER_BEFORE_BEDTIME_MINUTES).takeIf { it >= 0 }

/** Ringkasan tujuh hari untuk tab Progres. */
data class WeeklyMealSummary(
    /** Hari ketika makan siang atau malam memuat keempat komponen Isi Piringku. */
    val fullPlateDays: Int,
    /** Jumlah catatan makan bertanda gorengan. */
    val friedMeals: Int,
    /** Jumlah catatan makan bertanda manis. */
    val sweetMeals: Int,
    /** Total gelas kopi. */
    val coffeeCups: Int,
    /** Jumlah hari yang punya catatan makan, untuk menandai minggu tanpa data. */
    val daysLogged: Int,
)

/** Ringkasan dari [days] (tanggal-tanggal yang masuk minggu itu), catatan [meals], dan kopi per tanggal. */
fun weeklyMealSummary(days: List<LocalDate>, meals: List<Meal>, coffeeByDate: Map<LocalDate, Int>): WeeklyMealSummary {
    val inWeek = meals.filter { it.date in days }
    return WeeklyMealSummary(
        fullPlateDays = inWeek.filter { it.kind == MealKind.LUNCH || it.kind == MealKind.DINNER }
            .filter { it.fullPlate }.map { it.date }.distinct().size,
        friedMeals = inWeek.count { it.fried },
        sweetMeals = inWeek.count { it.sweet },
        coffeeCups = days.sumOf { coffeeByDate[it] ?: 0 },
        daysLogged = inWeek.map { it.date }.distinct().size,
    )
}
