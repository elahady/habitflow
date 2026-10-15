package com.roziqrizal.habitflow.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExactAlarmPromptTest {

    private val now = 10_000_000_000L
    private fun prompt(hasAlarm: Boolean = true, granted: Boolean = false, dismissedAt: Long? = null) =
        shouldPromptExactAlarm(hasAlarm, granted, dismissedAt, now)

    @Test
    fun tampilKalauAdaAlarmDanIzinBelumDiberikan() {
        assertTrue(prompt())
    }

    @Test
    fun tidakTampilKalauIzinSudahDiberikan() {
        assertFalse(prompt(granted = true))
    }

    @Test
    fun tidakTampilKalauTidakAdaAlarmYangAkanBerbunyi() {
        assertFalse(prompt(hasAlarm = false))
    }

    @Test
    fun setelahNantiTidakMunculLagiSampaiJedaLewat() {
        assertFalse(prompt(dismissedAt = now - 1_000L))
        assertFalse(prompt(dismissedAt = now - (EXACT_PROMPT_REPEAT_MILLIS - 1)))
        assertTrue(prompt(dismissedAt = now - EXACT_PROMPT_REPEAT_MILLIS))
        assertTrue(prompt(dismissedAt = now - 10 * EXACT_PROMPT_REPEAT_MILLIS))
    }

    @Test
    fun izinDiberikanMengalahkanSemuaKeadaanLain() {
        assertFalse(prompt(granted = true, dismissedAt = null))
        assertFalse(prompt(granted = true, dismissedAt = now - 10 * EXACT_PROMPT_REPEAT_MILLIS))
    }
}
