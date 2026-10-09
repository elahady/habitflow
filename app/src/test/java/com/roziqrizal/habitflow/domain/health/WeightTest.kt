package com.roziqrizal.habitflow.domain.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WeightTest {

    // Tinggi 200 cm membuat m² tepat 4,0, jadi BMI = kg / 4 dan angka batasnya bulat.
    private fun category(kg: Double) = bmiCategory(bmi(kg, 200.0))

    @Test
    fun bmiDibulatkanSatuDesimal() {
        assertEquals(24.2, bmi(70.0, 170.0), 0.0)
        assertEquals(25.0, bmi(100.0, 200.0), 0.0)
    }

    @Test
    fun kategoriBmiMengikutiKemenkesDiSetiapAngkaBatas() {
        assertEquals(BmiCategory.KURUS, category(73.6)) // 18,4
        assertEquals(BmiCategory.NORMAL, category(74.0)) // 18,5 masuk normal
        assertEquals(BmiCategory.NORMAL, category(100.0)) // 25,0 masih normal
        assertEquals(BmiCategory.GEMUK, category(100.4)) // 25,1
        assertEquals(BmiCategory.GEMUK, category(108.0)) // 27,0 masih gemuk
        assertEquals(BmiCategory.OBESITAS, category(108.4)) // 27,1
    }

    @Test
    fun kategoriDihitungDariAngkaYangTampil() {
        // 100,19 / 4 = 25,0475 tampil 25,0 sehingga masih normal, walau secara mentah sedikit di atas 25.
        assertEquals(25.0, bmi(100.19, 200.0), 0.0)
        assertEquals(BmiCategory.NORMAL, category(100.19))
    }

    @Test
    fun targetBawaanAdalahBatasAtasBmiNormal() {
        assertEquals(100.0, defaultTargetKg(200.0), 0.0)
        assertEquals(72.3, defaultTargetKg(170.0), 0.0) // 72,25 dibulatkan satu desimal
    }

    @Test
    fun sisaKeTargetBertandaPositifDiAtasDanNegatifDiBawah() {
        assertEquals(1.4, remainingToTarget(72.4, 71.0), 0.0)
        assertEquals(-2.3, remainingToTarget(70.0, 72.3), 0.0)
        assertEquals(0.0, remainingToTarget(71.0, 71.0), 0.0)
    }

    @Test
    fun batasBeratDanTinggiBadan() {
        assertTrue(isValidWeight(20.0))
        assertFalse(isValidWeight(19.9))
        assertTrue(isValidWeight(300.0))
        assertFalse(isValidWeight(300.1))
        assertTrue(isValidHeight(100.0))
        assertFalse(isValidHeight(99.9))
        assertTrue(isValidHeight(250.0))
        assertFalse(isValidHeight(250.1))
    }

    private val today = LocalDate.of(2026, 10, 9)

    private fun point(daysAgo: Long, kg: Double, offset: Long = 0) =
        WeightPoint(today.minusDays(daysAgo), today.minusDays(daysAgo).toEpochDay() * 1000 + offset, kg)

    @Test
    fun trenButuhDuaCatatanDiJendela() {
        assertNull(weightTrend(emptyList(), today))
        assertNull(weightTrend(listOf(point(1, 70.0)), today))
        // Satu catatan di jendela dan satu terlalu lama: tetap belum cukup.
        assertNull(weightTrend(listOf(point(1, 70.0), point(40, 75.0)), today))
    }

    @Test
    fun trenMemakaiBeratTerbaruDikurangiYangPalingAwalDiDuaPuluhDelapanHari() {
        val trend = weightTrend(listOf(point(28, 72.0), point(14, 71.2), point(0, 70.5)), today)!!
        assertEquals(-1.5, trend.deltaKg, 0.0)
        assertEquals(TrendDirection.TURUN, trend.direction)
    }

    @Test
    fun catatanTepatDuaPuluhDelapanHariLaluMasukDanDuaPuluhSembilanHariKeluar() {
        val masuk = weightTrend(listOf(point(28, 72.0), point(0, 71.0)), today)!!
        assertEquals(-1.0, masuk.deltaKg, 0.0)
        assertNull(weightTrend(listOf(point(29, 72.0), point(0, 71.0)), today))
    }

    @Test
    fun trenNaikDanStabil() {
        assertEquals(TrendDirection.NAIK, weightTrend(listOf(point(20, 70.0), point(0, 71.0)), today)!!.direction)
        assertEquals(TrendDirection.STABIL, weightTrend(listOf(point(20, 70.0), point(0, 70.1)), today)!!.direction)
        assertEquals(TrendDirection.STABIL, weightTrend(listOf(point(20, 70.0), point(0, 69.9)), today)!!.direction)
        // Tepat 0,2 kg sudah bukan stabil.
        assertEquals(TrendDirection.NAIK, weightTrend(listOf(point(20, 70.0), point(0, 70.2)), today)!!.direction)
        assertEquals(TrendDirection.TURUN, weightTrend(listOf(point(20, 70.0), point(0, 69.8)), today)!!.direction)
    }

    @Test
    fun urutanDiHariYangSamaMengikutiWaktuCatat() {
        // Dua catatan di hari ini: yang kedua (waktu lebih besar) adalah yang terbaru, urutan di list tidak berpengaruh.
        val trend = weightTrend(listOf(point(0, 70.4, offset = 5), point(0, 70.0, offset = 1), point(10, 71.0)), today)!!
        assertEquals(-0.6, trend.deltaKg, 0.0)
    }

    @Test
    fun catatanSesudahHariIniDiabaikan() {
        val future = WeightPoint(today.plusDays(1), 0L, 90.0)
        assertNull(weightTrend(listOf(point(3, 70.0), future), today))
    }
}
