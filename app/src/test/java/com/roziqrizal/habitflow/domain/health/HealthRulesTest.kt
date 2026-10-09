package com.roziqrizal.habitflow.domain.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HealthRulesTest {

    // 5 Oktober 2026 hari Senin, 6 Oktober Selasa.
    private val senin = LocalDate.of(2026, 10, 5)
    private val selasa = LocalDate.of(2026, 10, 6)

    @Test
    fun habitLangkahDicentangTepatDiTarget() {
        assertFalse(shouldAutoCheckSteps(7999, null, senin))
        assertTrue(shouldAutoCheckSteps(8000, null, senin))
        assertTrue(shouldAutoCheckSteps(12345, null, senin))
    }

    @Test
    fun habitLangkahHanyaDicentangOtomatisSekaliPerTanggal() {
        assertFalse(shouldAutoCheckSteps(9000, senin, senin))
        assertTrue(shouldAutoCheckSteps(9000, senin.minusDays(1), senin))
    }

    private fun kind(
        date: LocalDate,
        weight: Boolean = true,
        bp: BpFrequency = BpFrequency.WEEKLY,
        weightLogged: Boolean = false,
        bpLogged: Boolean = false,
    ) = healthReminderKind(date, weight, bp, weightLogged, bpLogged)

    @Test
    fun seninBeratDanTensiMingguanDigabung() {
        assertEquals(HealthReminderKind.BOTH, kind(senin))
    }

    @Test
    fun selasaTanpaTensiHarianTidakAdaPengingat() {
        assertNull(kind(selasa))
        assertEquals(HealthReminderKind.BLOOD_PRESSURE, kind(selasa, bp = BpFrequency.DAILY))
    }

    @Test
    fun switchMenentukanJenisYangDiingatkan() {
        assertEquals(HealthReminderKind.WEIGHT, kind(senin, bp = BpFrequency.OFF))
        assertEquals(HealthReminderKind.BLOOD_PRESSURE, kind(senin, weight = false))
        assertNull(kind(senin, weight = false, bp = BpFrequency.OFF))
    }

    @Test
    fun jenisYangSudahDicatatHariItuTidakDiingatkan() {
        assertEquals(HealthReminderKind.BLOOD_PRESSURE, kind(senin, weightLogged = true))
        assertEquals(HealthReminderKind.WEIGHT, kind(senin, bpLogged = true))
        assertNull(kind(senin, weightLogged = true, bpLogged = true))
        assertNull(kind(selasa, bp = BpFrequency.DAILY, bpLogged = true))
    }

    @Test
    fun jenisMenyebutApaYangDisertakan() {
        assertTrue(HealthReminderKind.BOTH.includesWeight && HealthReminderKind.BOTH.includesBloodPressure)
        assertTrue(HealthReminderKind.WEIGHT.includesWeight && !HealthReminderKind.WEIGHT.includesBloodPressure)
        assertTrue(!HealthReminderKind.BLOOD_PRESSURE.includesWeight && HealthReminderKind.BLOOD_PRESSURE.includesBloodPressure)
    }

    @Test
    fun pengingatJatuhSatuJamSetelahSubuh() {
        assertEquals(4 * 60 + 5 + 60, healthReminderMinute(4 * 60 + 5))
    }

    @Test
    fun tanggalPengingatBerikutnyaMelompatKeSeninKalauMingguan() {
        assertEquals(senin.plusDays(7), nextHealthReminderDate(selasa, true, BpFrequency.WEEKLY))
        assertEquals(senin, nextHealthReminderDate(senin, true, BpFrequency.WEEKLY))
        assertEquals(selasa, nextHealthReminderDate(selasa, false, BpFrequency.DAILY))
        assertNull(nextHealthReminderDate(selasa, false, BpFrequency.OFF))
        // Hanya berat menyala: tetap hari Senin.
        assertEquals(senin.plusDays(7), nextHealthReminderDate(selasa, true, BpFrequency.OFF))
    }
}
