package com.roziqrizal.habitflow.domain.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusTest {

    // Tinggi 200 cm: BMI = kg / 4. Target 100 kg, jadi 104 kg adalah 4 kg di atas target (BMI 26,0).
    private fun input(
        weightKg: Double? = 104.0,
        heightCm: Double? = 200.0,
        targetKg: Double? = 100.0,
        weightAgeDays: Int? = 1,
        trend: WeightTrend? = null,
        latestBp: FocusBp? = null,
        previousBp: FocusBp? = null,
        steps: Long? = 8000,
        coffee: Int = 0,
        sweet: Int = 0,
        mealLoggedToday: Boolean = true,
        minuteOfDay: Int = 12 * 60,
    ) = FocusInput(weightKg, heightCm, targetKg, weightAgeDays, trend, latestBp, previousBp, steps, coffee, sweet, mealLoggedToday, minuteOfDay)

    private fun rule(input: FocusInput) = computeFocus(input).rule

    @Test
    fun tensiSangatTinggiSelaluMenangAtasSemuaFokusLain() {
        val everything = input(
            weightKg = null,
            latestBp = FocusBp(180, 100),
            previousBp = FocusBp(150, 95),
            coffee = 5,
            steps = 0,
        )
        assertEquals(FocusRule.BP_ADVICE, rule(everything))
        assertEquals(FocusRule.BP_ADVICE, rule(input(latestBp = FocusBp(150, 110))))
    }

    @Test
    fun tensiTinggiDuaKaliBerturutanMemintaUkurUlang() {
        assertEquals(FocusRule.BP_RECHECK, rule(input(latestBp = FocusBp(140, 80), previousBp = FocusBp(120, 90))))
        assertEquals(FocusRule.BP_RECHECK, rule(input(weightKg = null, latestBp = FocusBp(145, 85), previousBp = FocusBp(150, 95))))
    }

    @Test
    fun tensiTinggiSekaliAtauSalahSatuNormalTidakMemicuUkurUlang() {
        assertEquals(FocusRule.ON_TRACK, rule(input(latestBp = FocusBp(150, 95))))
        assertEquals(FocusRule.ON_TRACK, rule(input(latestBp = FocusBp(150, 95), previousBp = FocusBp(139, 89))))
        assertEquals(FocusRule.ON_TRACK, rule(input(latestBp = FocusBp(139, 89), previousBp = FocusBp(150, 95))))
    }

    @Test
    fun tanpaBeratAtauTinggiFokusnyaCatatBeratPertama() {
        assertEquals(FocusRule.FIRST_WEIGHT, rule(input(weightKg = null, coffee = 5)))
        assertEquals(FocusRule.FIRST_WEIGHT, rule(input(heightCm = null, coffee = 5)))
    }

    @Test
    fun beratLebihDariSepuluhHariLaluMemintaTimbangUlang() {
        assertEquals(FocusRule.ON_TRACK, rule(input(weightAgeDays = 10)))
        assertEquals(FocusRule.STALE_WEIGHT, rule(input(weightAgeDays = 11, coffee = 5)))
    }

    @Test
    fun turunDuaKgAtauLebihDalamEmpatMingguMemintaPertahankanPorsi() {
        assertEquals(FocusRule.FAST_LOSS, rule(input(trend = WeightTrend(-2.0, TrendDirection.TURUN), coffee = 5)))
        assertEquals(FocusRule.ON_TRACK, rule(input(trend = WeightTrend(-1.9, TrendDirection.TURUN))))
    }

    @Test
    fun kopiTigaAtauLebihAtauSatuMinumanManisMemintaGantiAir() {
        assertEquals(FocusRule.ON_TRACK, rule(input(coffee = 2)))
        assertEquals(FocusRule.DRINKS, rule(input(coffee = 3, steps = 0)))
        assertEquals(FocusRule.DRINKS, rule(input(sweet = 1, steps = 0)))
    }

    @Test
    fun langkahDiBawahSeparuhTargetSetelahJamSepuluhMemintaJalan() {
        val focus = computeFocus(input(steps = 3200, minuteOfDay = 11 * 60 + 28))
        assertEquals(FocusRule.LOW_STEPS, focus.rule)
        assertEquals(40, focus.walkMinutes) // sisa 4.800 / 120 = 40
    }

    @Test
    fun batasJamDanSeparuhTargetPersis() {
        assertEquals(FocusRule.ON_TRACK, rule(input(steps = 3999, minuteOfDay = 9 * 60 + 59)))
        assertEquals(FocusRule.LOW_STEPS, rule(input(steps = 3999, minuteOfDay = 10 * 60)))
        assertEquals(FocusRule.ON_TRACK, rule(input(steps = 4000, minuteOfDay = 10 * 60)))
    }

    @Test
    fun langkahBelumTerbacaDilewatiTanpaMenebak() {
        assertEquals(FocusRule.ON_TRACK, rule(input(steps = null)))
    }

    @Test
    fun lamaJalanDibulatkanKeAtasKeKelipatanLimaDanDibatasiEnamPuluh() {
        assertEquals(40, walkMinutesFor(4800))
        assertEquals(45, walkMinutesFor(4801)) // 40,008 menit -> 41 -> 45
        assertEquals(55, walkMinutesFor(6500)) // 54,2 -> 55
        assertEquals(60, walkMinutesFor(8000)) // 66,7 -> 70 -> dibatasi 60
        assertEquals(5, walkMinutesFor(1))
        assertEquals(60, computeFocus(input(steps = 0)).walkMinutes)
    }

    @Test
    fun catatMakanButuhBeratDiAtasTargetTrenNaikJamSatuDanBelumAdaCatatan() {
        val naik = WeightTrend(0.3, TrendDirection.NAIK)
        val base = input(trend = naik, mealLoggedToday = false, minuteOfDay = 13 * 60)
        assertEquals(FocusRule.LOG_MEAL, rule(base))
        assertEquals(FocusRule.ON_TRACK, rule(base.copy(minuteOfDay = 13 * 60 - 1)))
        assertEquals(FocusRule.ON_TRACK, rule(base.copy(mealLoggedToday = true)))
        assertEquals(FocusRule.ON_TRACK, rule(base.copy(trend = WeightTrend(0.0, TrendDirection.STABIL))))
        assertEquals(FocusRule.ON_TRACK, rule(base.copy(trend = null)))
    }

    @Test
    fun urutanAturanYangPertamaCocokMenang() {
        // Langkah rendah dan kopi banyak sama-sama cocok: minuman (5) mengalahkan langkah (6).
        assertEquals(FocusRule.DRINKS, rule(input(steps = 100, coffee = 3)))
        // Berat lama dan minuman: berat lama (3) menang.
        assertEquals(FocusRule.STALE_WEIGHT, rule(input(weightAgeDays = 20, coffee = 3)))
        // Langkah rendah mengalahkan catat makan (7).
        assertEquals(
            FocusRule.LOW_STEPS,
            rule(input(steps = 100, trend = WeightTrend(0.5, TrendDirection.NAIK), mealLoggedToday = false, minuteOfDay = 14 * 60)),
        )
    }

    // Pagar keselamatan.

    @Test
    fun bmiKurusTidakPernahMemicuSaranMenurunkanBerat() {
        // 70 kg di tinggi 200 cm = BMI 17,5 (kurus), walau target yang tersimpan 60 kg.
        val kurus = input(weightKg = 70.0, targetKg = 60.0, trend = WeightTrend(0.5, TrendDirection.NAIK), mealLoggedToday = false, minuteOfDay = 14 * 60)
        assertFalse(mayAdviseLosingWeight(kurus.weightKg, kurus.heightCm, kurus.targetKg))
        assertEquals(FocusRule.ON_TRACK, rule(kurus))
    }

    @Test
    fun beratDiBawahAtauSamaDenganTargetTidakMemicuSaranMenurunkan() {
        val trend = WeightTrend(0.5, TrendDirection.NAIK)
        assertEquals(FocusRule.ON_TRACK, rule(input(weightKg = 100.0, trend = trend, mealLoggedToday = false, minuteOfDay = 14 * 60)))
        assertEquals(FocusRule.ON_TRACK, rule(input(weightKg = 90.0, trend = trend, mealLoggedToday = false, minuteOfDay = 14 * 60)))
        assertFalse(mayAdviseLosingWeight(100.0, 200.0, 100.0))
        assertTrue(mayAdviseLosingWeight(100.1, 200.0, 100.0))
    }

    @Test
    fun tanpaTargetTidakAdaSaranMenurunkan() {
        assertFalse(mayAdviseLosingWeight(104.0, 200.0, null))
        assertFalse(mayAdviseLosingWeight(null, 200.0, 100.0))
        assertFalse(mayAdviseLosingWeight(104.0, null, 100.0))
    }

    @Test
    fun fokusLangkahTidakPernahLebihDariEnamPuluhMenit() {
        for (steps in 0L..3999L step 37L) {
            val minutes = computeFocus(input(steps = steps)).walkMinutes
            assertTrue("steps=$steps menit=$minutes", minutes != null && minutes in 5..60)
        }
    }

    @Test
    fun hanyaAturanLangkahMengisiLamaJalan() {
        assertNull(computeFocus(input()).walkMinutes)
        assertNotEquals(null, computeFocus(input(steps = 0)).walkMinutes)
    }
}
