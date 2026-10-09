package com.roziqrizal.habitflow.domain.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BloodPressureTest {

    @Test
    fun kategoriDiSetiapAngkaBatasSistolikDanDiastolik() {
        val cases = listOf(
            Triple(119, 79, BpCategory.OPTIMAL),
            Triple(120, 79, BpCategory.NORMAL),
            Triple(119, 80, BpCategory.NORMAL),
            Triple(129, 84, BpCategory.NORMAL),
            Triple(130, 84, BpCategory.NORMAL_TINGGI),
            Triple(129, 85, BpCategory.NORMAL_TINGGI),
            Triple(139, 89, BpCategory.NORMAL_TINGGI),
            Triple(140, 89, BpCategory.HIPERTENSI_1),
            Triple(139, 90, BpCategory.HIPERTENSI_1),
            Triple(159, 99, BpCategory.HIPERTENSI_1),
            Triple(160, 99, BpCategory.HIPERTENSI_2),
            Triple(159, 100, BpCategory.HIPERTENSI_2),
            Triple(179, 109, BpCategory.HIPERTENSI_2),
            Triple(180, 109, BpCategory.HIPERTENSI_3),
            Triple(179, 110, BpCategory.HIPERTENSI_3),
            Triple(200, 120, BpCategory.HIPERTENSI_3),
        )
        cases.forEach { (sys, dia, expected) ->
            assertEquals("$sys/$dia", expected, bpCategory(sys, dia))
        }
    }

    @Test
    fun kategoriMemakaiTingkatYangLebihTinggi() {
        assertEquals(BpCategory.HIPERTENSI_1, bpCategory(118, 92)) // diastolik yang menentukan
        assertEquals(BpCategory.HIPERTENSI_1, bpCategory(145, 70)) // sistolik yang menentukan
        assertEquals(BpCategory.NORMAL, bpCategory(110, 82))
    }

    @Test
    fun saranHanyaUntukTensiDiAtasBatasSangatTinggi() {
        assertFalse(bpNeedsAdvice(179, 109))
        assertTrue(bpNeedsAdvice(180, 70))
        assertTrue(bpNeedsAdvice(120, 110))
        assertTrue(bpNeedsAdvice(180, 110))
        assertFalse(bpNeedsAdvice(120, 80))
    }

    @Test
    fun rentangDanUrutanTensiYangSah() {
        assertTrue(isValidBloodPressure(120, 80))
        assertFalse(isValidBloodPressure(80, 80)) // sistolik harus lebih besar
        assertFalse(isValidBloodPressure(70, 90))
        assertTrue(isValidBloodPressure(SYSTOLIC_MIN, DIASTOLIC_MIN))
        assertFalse(isValidBloodPressure(SYSTOLIC_MIN - 1, DIASTOLIC_MIN))
        assertFalse(isValidBloodPressure(SYSTOLIC_MIN, DIASTOLIC_MIN - 1))
        assertTrue(isValidBloodPressure(SYSTOLIC_MAX, DIASTOLIC_MAX))
        assertFalse(isValidBloodPressure(SYSTOLIC_MAX + 1, DIASTOLIC_MAX))
        assertFalse(isValidBloodPressure(SYSTOLIC_MAX, DIASTOLIC_MAX + 1))
    }

    @Test
    fun nadiOpsionalTapiBerbatas() {
        assertTrue(isValidBloodPressure(120, 80, null))
        assertTrue(isValidBloodPressure(120, 80, PULSE_MIN))
        assertFalse(isValidBloodPressure(120, 80, PULSE_MIN - 1))
        assertTrue(isValidBloodPressure(120, 80, PULSE_MAX))
        assertFalse(isValidBloodPressure(120, 80, PULSE_MAX + 1))
    }
}
