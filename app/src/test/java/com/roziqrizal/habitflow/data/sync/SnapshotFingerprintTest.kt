package com.roziqrizal.habitflow.data.sync

import com.roziqrizal.habitflow.data.WeightEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SnapshotFingerprintTest {

    private fun snapshot(
        createdAt: Long = 1_000,
        deviceId: String = "hp-a",
        appVersion: String = "1.0",
        weights: List<WeightEntry> = emptyList(),
        location: String = "Rumah",
    ) = Snapshot(
        createdAt = createdAt, deviceId = deviceId, appVersion = appVersion,
        habits = emptyList(), habitEntries = emptyList(), todos = emptyList(), scheduleBlocks = emptyList(),
        scheduleBlockHabits = emptyList(), daysOff = emptyList(), followUps = emptyList(), workDays = emptyList(),
        weightEntries = weights,
        settings = SnapshotSettings(
            locationName = location, latitude = -6.2, longitude = 106.8, persistentNotification = true,
            adzan = mapOf("SUBUH" to true), themeMode = "SYSTEM",
        ),
    )

    @Test
    fun waktuEksporIdPerangkatDanVersiAppTidakMengubahSidikJari() {
        assertEquals(
            SnapshotFingerprint.of(snapshot()),
            SnapshotFingerprint.of(snapshot(createdAt = 999_999, deviceId = "hp-b", appVersion = "2.3")),
        )
    }

    @Test
    fun perubahanIsiMengubahSidikJari() {
        val kosong = SnapshotFingerprint.of(snapshot())

        assertNotEquals(kosong, SnapshotFingerprint.of(snapshot(weights = listOf(WeightEntry(timeMillis = 5, kg = 70.5)))))
        assertNotEquals(kosong, SnapshotFingerprint.of(snapshot(location = "Kantor")))
    }

    @Test
    fun isiSamaMenghasilkanSidikJariSamaDanBerbentukHex256() {
        val a = SnapshotFingerprint.of(snapshot())

        assertEquals(a, SnapshotFingerprint.of(snapshot()))
        assertEquals(64, a.length)
        assertEquals(true, a.all { it in "0123456789abcdef" })
    }
}
