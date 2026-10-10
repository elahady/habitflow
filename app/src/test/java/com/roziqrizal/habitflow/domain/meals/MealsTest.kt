package com.roziqrizal.habitflow.domain.meals

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MealsTest {

    private val day = LocalDate.of(2026, 10, 10)
    private val bedtime = 22 * 60

    private fun meal(kind: MealKind, minute: Int, date: LocalDate = day, fried: Boolean = false, sweet: Boolean = false) =
        Meal(date, kind, minute, fried = fried, sweet = sweet)

    private fun fullPlate(kind: MealKind, date: LocalDate = day) =
        Meal(date, kind, 12 * 60, carb = true, protein = true, vegetable = true, fruit = true)

    @Test
    fun makanMalamTepatDuaJamSebelumBatasTidurMasihTepatWaktu() {
        assertTrue(dinnerOnTime(listOf(meal(MealKind.DINNER, 20 * 60)), bedtime))
    }

    @Test
    fun makanMalamSatuMenitSetelahBatasDuaJamTidakTepatWaktu() {
        assertFalse(dinnerOnTime(listOf(meal(MealKind.DINNER, 20 * 60 + 1)), bedtime))
    }

    @Test
    fun tanpaCatatanMalamMakanMalamTidakDicentang() {
        assertFalse(dinnerOnTime(listOf(meal(MealKind.LUNCH, 12 * 60)), bedtime))
        assertFalse(dinnerOnTime(emptyList(), bedtime))
    }

    @Test
    fun hanyaCatatanMalamYangDihitungBukanCamilan() {
        assertFalse(dinnerOnTime(listOf(meal(MealKind.SNACK, 18 * 60)), bedtime))
    }

    @Test
    fun batasTidurLainMenggeserBatasMakanMalam() {
        assertTrue(dinnerOnTime(listOf(meal(MealKind.DINNER, 19 * 60)), 21 * 60))
        assertFalse(dinnerOnTime(listOf(meal(MealKind.DINNER, 19 * 60 + 1)), 21 * 60))
    }

    @Test
    fun tanpaGorenganDanManisButuhMinimalSatuCatatan() {
        assertFalse(noFriedOrSweet(emptyList()))
        assertTrue(noFriedOrSweet(listOf(meal(MealKind.LUNCH, 12 * 60))))
    }

    @Test
    fun satuCatatanBergorenganMenggagalkanHabit() {
        assertFalse(noFriedOrSweet(listOf(meal(MealKind.LUNCH, 12 * 60), meal(MealKind.SNACK, 16 * 60, fried = true))))
    }

    @Test
    fun satuCatatanBermanisMenggagalkanHabit() {
        assertFalse(noFriedOrSweet(listOf(meal(MealKind.SNACK, 16 * 60, sweet = true))))
    }

    @Test
    fun minumanManisHarusNol() {
        assertTrue(noSweetDrink(0))
        assertFalse(noSweetDrink(1))
    }

    @Test
    fun kopiTepatDuaMasihDalamBatasDanTigaTidak() {
        assertTrue(coffeeWithinLimit(0))
        assertTrue(coffeeWithinLimit(2))
        assertFalse(coffeeWithinLimit(3))
        assertFalse(coffeeOverLimit(2))
        assertTrue(coffeeOverLimit(3))
    }

    @Test
    fun hariTanpaCatatanApaPunHanyaKopiDanManisYangTerpenuhi() {
        assertEquals(
            setOf(MealRule.NO_SWEET_DRINK, MealRule.COFFEE_WITHIN_LIMIT),
            satisfiedRules(emptyList(), coffeeCount = 0, sweetCount = 0, bedtimeMinute = bedtime),
        )
    }

    @Test
    fun hariLengkapMemenuhiKeempatAturan() {
        val meals = listOf(meal(MealKind.LUNCH, 12 * 60), meal(MealKind.DINNER, 19 * 60))
        assertEquals(MealRule.entries.toSet(), satisfiedRules(meals, 2, 0, bedtime))
    }

    @Test
    fun kopiKetigaDanManisMenghilangkanDuaAturan() {
        val meals = listOf(meal(MealKind.DINNER, 19 * 60))
        assertEquals(
            setOf(MealRule.DINNER_ON_TIME, MealRule.NO_FRIED_OR_SWEET),
            satisfiedRules(meals, coffeeCount = 3, sweetCount = 1, bedtimeMinute = bedtime),
        )
    }

    @Test
    fun waktuMakanUtamaYangBelumDicatatUrutSarapanSiangMalam() {
        assertEquals(
            listOf(MealKind.LUNCH, MealKind.DINNER),
            missingMeals(listOf(meal(MealKind.BREAKFAST, 7 * 60))),
        )
        assertEquals(
            listOf(MealKind.BREAKFAST, MealKind.LUNCH, MealKind.DINNER),
            missingMeals(listOf(meal(MealKind.SNACK, 16 * 60))),
        )
    }

    @Test
    fun semuaMakanUtamaTercatatTidakAdaYangKurang() {
        val meals = listOf(meal(MealKind.BREAKFAST, 7 * 60), meal(MealKind.LUNCH, 12 * 60), meal(MealKind.DINNER, 19 * 60))
        assertEquals(emptyList<MealKind>(), missingMeals(meals))
    }

    @Test
    fun pengingatCatatanMakanSatuJamSebelumBatasTidur() {
        assertEquals(21 * 60, mealReminderMinute(22 * 60))
        assertEquals(null, mealReminderMinute(30))
    }

    @Test
    fun ringkasanMingguanMenghitungHariPiringLengkapSekaliPerHari() {
        val d1 = day
        val d2 = day.minusDays(1)
        val week = (0..6).map { day.minusDays(it.toLong()) }
        // Siang dan malam sama-sama lengkap di d1 tetap dihitung satu hari. Sarapan lengkap tidak dihitung.
        val meals = listOf(
            fullPlate(MealKind.LUNCH, d1), fullPlate(MealKind.DINNER, d1), fullPlate(MealKind.BREAKFAST, d2),
            meal(MealKind.SNACK, 16 * 60, d2, fried = true), meal(MealKind.DINNER, 19 * 60, d2, sweet = true),
        )
        val summary = weeklyMealSummary(week, meals, mapOf(d1 to 2, d2 to 3))
        assertEquals(1, summary.fullPlateDays)
        assertEquals(1, summary.friedMeals)
        assertEquals(1, summary.sweetMeals)
        assertEquals(5, summary.coffeeCups)
        assertEquals(2, summary.daysLogged)
    }

    @Test
    fun ringkasanMingguanMengabaikanHariDiLuarMinggu() {
        val week = (0..6).map { day.minusDays(it.toLong()) }
        val old = day.minusDays(7)
        val summary = weeklyMealSummary(week, listOf(fullPlate(MealKind.LUNCH, old)), mapOf(old to 4))
        assertEquals(WeeklyMealSummary(0, 0, 0, 0, 0), summary)
    }
}
