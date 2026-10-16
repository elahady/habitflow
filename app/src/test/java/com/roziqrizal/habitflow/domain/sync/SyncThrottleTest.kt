package com.roziqrizal.habitflow.domain.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncThrottleTest {

    private var clock = 1_000_000L
    private val throttle = SyncThrottle(minIntervalMillis = 60_000) { clock }

    @Test
    fun panggilanPertamaDiizinkan() {
        assertTrue(throttle.tryAcquire())
    }

    @Test
    fun panggilanDalamJedaDitolak() {
        throttle.tryAcquire()
        clock += 59_999
        assertFalse(throttle.tryAcquire())
    }

    @Test
    fun panggilanTepatSetelahJedaDiizinkan() {
        throttle.tryAcquire()
        clock += 60_000
        assertTrue(throttle.tryAcquire())
    }

    @Test
    fun penolakanTidakMemperpanjangJeda() {
        throttle.tryAcquire()
        clock += 40_000
        assertFalse(throttle.tryAcquire())
        clock += 20_000
        assertTrue(throttle.tryAcquire())
    }

    @Test
    fun jamMundurTidakMengunciSelamanya() {
        throttle.tryAcquire()
        clock -= 3_600_000
        assertTrue(throttle.tryAcquire())
    }
}
