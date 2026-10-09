package com.roziqrizal.habitflow.ui

import com.roziqrizal.habitflow.domain.health.BmiCategory
import com.roziqrizal.habitflow.domain.health.BpCategory
import com.roziqrizal.habitflow.domain.health.TrendDirection
import com.roziqrizal.habitflow.domain.health.WeightTrend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class HealthFormatTest {

    private val zone = ZoneId.of("Asia/Jakarta")

    @Test
    fun langkahDenganTitikRibuan() {
        assertEquals("3.240", formatSteps(3240))
        assertEquals("8.000", formatSteps(8000))
        assertEquals("0", formatSteps(0))
    }

    @Test
    fun angkaDesimalDenganKoma() {
        assertEquals("72,4", formatDecimal1(72.4))
        assertEquals("72,0", formatDecimal1(72.0))
        assertEquals("1.234,5", formatDecimal1(1234.5))
    }

    @Test
    fun selisihBertandaPlusDanMinusUnicode() {
        assertEquals("+0,8", formatSigned1(0.8))
        assertEquals("−1,5", formatSigned1(-1.5))
        assertEquals("0,0", formatSigned1(0.0))
    }

    @Test
    fun isianAngkaMenerimaKomaDanTitik() {
        assertEquals(72.4, parseDecimal("72,4")!!, 0.0)
        assertEquals(72.4, parseDecimal(" 72.4 ")!!, 0.0)
        assertEquals(170.0, parseDecimal("170")!!, 0.0)
        assertNull(parseDecimal("abc"))
        assertNull(parseDecimal(""))
    }

    @Test
    fun hariRelatifUntukKeteranganKartu() {
        val today = LocalDate.of(2026, 10, 9)
        fun millis(date: LocalDate) = date.atTime(8, 0).atZone(zone).toInstant().toEpochMilli()
        assertEquals("Hari ini", relativeDay(millis(today), today, zone))
        assertEquals("Kemarin", relativeDay(millis(today.minusDays(1)), today, zone))
        assertEquals("3 hari lalu", relativeDay(millis(today.minusDays(3)), today, zone))
        // Catatan bertanggal sesudah hari ini (jam HP dimundurkan) tetap ditulis "Hari ini", bukan angka negatif.
        assertEquals("Hari ini", relativeDay(millis(today.plusDays(2)), today, zone))
    }

    @Test
    fun sisaKeTargetDalamKalimat() {
        assertEquals("1,4 kg lagi ke target", describeRemaining(1.4))
        assertEquals("di bawah target 2,3 kg", describeRemaining(-2.3))
        assertEquals("tepat di target", describeRemaining(0.0))
    }

    @Test
    fun trenEmpatMingguDalamKalimat() {
        assertEquals("turun 0,8 kg", WeightTrend(-0.8, TrendDirection.TURUN).describe())
        assertEquals("naik 1,2 kg", WeightTrend(1.2, TrendDirection.NAIK).describe())
        assertEquals("stabil", WeightTrend(0.1, TrendDirection.STABIL).describe())
    }

    @Test
    fun namaKategoriSebagaiTeksBiasa() {
        assertEquals("Kurus", BmiCategory.KURUS.label)
        assertEquals("Obesitas", BmiCategory.OBESITAS.label)
        assertEquals("Normal-tinggi", BpCategory.NORMAL_TINGGI.label)
        assertEquals("Hipertensi derajat 3", BpCategory.HIPERTENSI_3.label)
    }
}
