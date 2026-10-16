package com.roziqrizal.habitflow.data.health

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class StepHistoryTest {

    private val today = LocalDate.parse("2026-10-16")
    private val zone = ZoneId.of("Asia/Jakarta")

    /** Sumber langkah palsu: [byDate] menjawab per tanggal; tanggal yang tidak ada di sana melempar galat. */
    private class FakeSource(private val byDate: Map<LocalDate, StepsReading>) : StepsSource {
        val asked = mutableListOf<LocalDate>()
        var lastInBackground: Boolean? = null

        override suspend fun read(date: LocalDate, zone: ZoneId, inBackground: Boolean): StepsReading {
            asked += date
            lastInBackground = inBackground
            return byDate[date] ?: throw SecurityException("Health Connect menolak")
        }

        override suspend fun permissionsToRequest(): Set<String> = emptySet()
    }

    private fun available(steps: Long) = StepsReading.Available(steps, background = false, backgroundAvailable = false)

    @Test
    fun membacaHariIniDanHariSebelumnyaTerbaruDulu() = runBlocking {
        val source = FakeSource((0..6).associate { today.minusDays(it.toLong()) to available(1_000L * (it + 1)) })

        val result = readRecentSteps(source, today, zone, 7)

        assertEquals(7, result.size)
        assertEquals(today to 1_000L, result.first())
        assertEquals(today.minusDays(6) to 7_000L, result.last())
    }

    @Test
    fun berhentiDiBacaanPertamaYangTidakTersediaTanpaMembacaSisanya() = runBlocking {
        val source = FakeSource(mapOf(today to StepsReading.NeedsPermission))

        val result = readRecentSteps(source, today, zone, 7)

        assertEquals(emptyList<Pair<LocalDate, Long>>(), result)
        assertEquals(listOf(today), source.asked)
    }

    @Test
    fun galatMembacaDitangkapDanHariYangSudahTerbacaTetapDipakai() = runBlocking {
        val source = FakeSource(mapOf(today to available(2_400), today.minusDays(1) to available(8_100)))

        val result = readRecentSteps(source, today, zone, 7)

        assertEquals(listOf(today to 2_400L, today.minusDays(1) to 8_100L), result)
    }

    @Test
    fun healthConnectBelumTerpasangMenghasilkanDaftarKosong() = runBlocking {
        val source = FakeSource(mapOf(today to StepsReading.NotInstalled))

        assertEquals(emptyList<Pair<LocalDate, Long>>(), readRecentSteps(source, today, zone, 7))
    }

    @Test
    fun dibacaDenganModeLayarBukanLatarBelakang() = runBlocking {
        val source = FakeSource(mapOf(today to available(10)))

        readRecentSteps(source, today, zone, 1)

        assertEquals(false, source.lastInBackground)
    }

    @Test
    fun jumlahHariDibatasi() = runBlocking {
        val source = FakeSource((0..9).associate { today.minusDays(it.toLong()) to available(100) })

        assertEquals(3, readRecentSteps(source, today, zone, 3).size)
        assertEquals(3, source.asked.size)
    }
}
