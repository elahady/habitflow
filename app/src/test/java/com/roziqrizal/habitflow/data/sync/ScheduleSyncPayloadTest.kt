package com.roziqrizal.habitflow.data.sync

import com.roziqrizal.habitflow.data.ScheduleBlockEntity
import com.roziqrizal.habitflow.data.ScheduleBlockHabit
import com.roziqrizal.habitflow.domain.prayer.PrayerTimes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class ScheduleSyncPayloadTest {

    private fun block(
        id: Long,
        name: String = "Kerja pagi",
        startType: Int = ScheduleBlockEntity.START_FIXED,
        startValue: Int = 480,
        prayer: String? = null,
        duration: Int = 240,
        end: Int? = null,
        days: Int = 31,
        level: String = "REMINDER",
        sort: Int = 0,
        action: String? = null,
        reminders: Boolean = false,
    ) = ScheduleBlockEntity(id, name, startType, startValue, prayer, duration, end, days, level, sort, action, reminders)

    private fun build(
        blocks: List<ScheduleBlockEntity>,
        links: List<ScheduleBlockHabit> = emptyList(),
        remote: Map<Long, Long> = emptyMap(),
    ) = scheduleSyncPayload(blocks, links, remote)

    @Test
    fun bentukJsonSesuaiKontrakServer() {
        val json = build(
            listOf(block(1, "Subuh", ScheduleBlockEntity.START_PRAYER, -15, "SUBUH", 15, 60, 127, "ALARM", 2, "EOD", true)),
            listOf(ScheduleBlockHabit(1, 10)),
            mapOf(10L to 77L),
        ).toJson()

        val b = json.getJSONArray("blocks").getJSONObject(0)
        assertEquals("Subuh", b.getString("name"))
        assertEquals(1, b.getInt("start_type"))
        assertEquals(-15, b.getInt("start_value"))
        assertEquals("SUBUH", b.getString("prayer"))
        assertEquals(15, b.getInt("duration_minutes"))
        assertEquals(60, b.getInt("end_minute_of_day"))
        assertEquals(127, b.getInt("active_days"))
        assertEquals("ALARM", b.getString("level"))
        assertEquals(2, b.getInt("sort_order"))
        assertEquals("EOD", b.getString("work_action"))
        assertTrue(b.getBoolean("work_reminders"))
        assertEquals(77L, b.getJSONArray("habit_ids").getLong(0))
    }

    @Test
    fun jamTetapTanpaPrayerDanFieldOpsionalNull() {
        val b = build(listOf(block(1))).toJson().getJSONArray("blocks").getJSONObject(0)
        assertTrue(b.isNull("prayer"))
        assertTrue(b.isNull("end_minute_of_day"))
        assertTrue(b.isNull("work_action"))
        assertEquals(0, b.getJSONArray("habit_ids").length())
    }

    @Test
    fun tautanHabitMemakaiIdServerDanHabitYangBelumTerkirimDilewati() {
        val payload = build(
            listOf(block(1), block(2, sort = 1)),
            listOf(ScheduleBlockHabit(1, 10), ScheduleBlockHabit(1, 11), ScheduleBlockHabit(2, 10)),
            mapOf(10L to 500L), // habit 11 belum punya id server
        )
        assertEquals(listOf(500L), payload.blocks[0].habitIds)
        assertEquals(listOf(500L), payload.blocks[1].habitIds)
    }

    @Test
    fun tautanBerubahBegitHabitPunyaIdServerJadiKirimanBerbeda() {
        val before = build(listOf(block(1)), listOf(ScheduleBlockHabit(1, 10)))
        val after = build(listOf(block(1)), listOf(ScheduleBlockHabit(1, 10)), mapOf(10L to 5L))
        assertNotEquals(before.fingerprint, after.fingerprint)
    }

    @Test
    fun urutSortOrderLaluIdTanpaMemedulikanUrutanMasukan() {
        val payload = build(listOf(block(3, "c", sort = 1), block(2, "b", sort = 0), block(1, "a", sort = 0)))
        assertEquals(listOf("a", "b", "c"), payload.blocks.map { it.name })
    }

    @Test
    fun blokTanpaHariAktifTidakDikirim() {
        val payload = build(listOf(block(1, days = 0), block(2, name = "ada", days = 1)))
        assertEquals(listOf("ada"), payload.blocks.map { it.name })
    }

    @Test
    fun nilaiGanjilDipaksaMasukBatasServer() {
        val b = build(
            listOf(block(1, name = "  ", startValue = 5000, duration = 9999, end = 3000, level = "ANEH", action = "ANEH", days = 255, sort = -4)),
        ).blocks[0]
        assertEquals("Tanpa nama", b.name)
        assertEquals(1439, b.startValue)
        assertEquals(1440, b.durationMinutes)
        assertEquals(1439, b.endMinuteOfDay)
        assertEquals("INFO", b.level)
        assertNull(b.workAction)
        assertEquals(127, b.activeDays)
        assertEquals(0, b.sortOrder)
    }

    @Test
    fun blokSholatTanpaNamaSholatDibacaSubuhDanJamTetapMembuangPrayer() {
        val prayer = build(listOf(block(1, startType = ScheduleBlockEntity.START_PRAYER, startValue = -5000, prayer = null))).blocks[0]
        assertEquals("SUBUH", prayer.prayer)
        assertEquals(-1440, prayer.startValue)

        val fixed = build(listOf(block(2, prayer = "ISYA"))).blocks[0]
        assertNull(fixed.prayer)
    }

    @Test
    fun namaDipotongKeBatasServerDanJumlahBlokDibatasi() {
        val payload = build((1L..120L).map { block(it, name = "x".repeat(300), sort = it.toInt()) })
        assertEquals(SCHEDULE_SYNC_MAX_BLOCKS, payload.blocks.size)
        assertEquals(255, payload.blocks[0].name.length)
    }

    @Test
    fun jadwalKosongTetapDikirimSupayaServerDikosongkan() {
        assertEquals(0, build(emptyList()).toJson().getJSONArray("blocks").length())
    }

    @Test
    fun kirimanDenganIsiSamaPunyaSidikJariSama() {
        assertEquals(build(listOf(block(1))).fingerprint, build(listOf(block(1))).fingerprint)
        assertNotEquals(build(listOf(block(1))).fingerprint, build(listOf(block(1, name = "Lain"))).fingerprint)
    }

    // --- Libur ---

    private fun date(text: String) = LocalDate.parse(text)

    @Test
    fun liburJendelaEmpatBelasHariMulaiDariTanggalYangDiminta() {
        val payload = daysOffPayload(date("2026-10-16"), emptyList())
        assertEquals(date("2026-10-16"), payload.from)
        assertEquals(date("2026-10-29"), payload.to)
        assertEquals(DAYS_OFF_SYNC_DAYS, payload.from.until(payload.to, java.time.temporal.ChronoUnit.DAYS).toInt() + 1)
    }

    @Test
    fun liburUrutTanpaDuplikatDanDiluarJendelaDibuang() {
        val payload = daysOffPayload(
            date("2026-10-16"),
            listOf(date("2026-10-18"), date("2026-10-17"), date("2026-10-18"), date("2026-10-15"), date("2026-10-30"), date("2026-10-29")),
        )
        assertEquals(listOf(date("2026-10-17"), date("2026-10-18"), date("2026-10-29")), payload.dates)
    }

    @Test
    fun liburJsonSesuaiKontrakServer() {
        val json = daysOffPayload(date("2026-10-16"), listOf(date("2026-10-17"))).toJson()
        assertEquals("2026-10-16", json.getString("from"))
        assertEquals("2026-10-29", json.getString("to"))
        assertEquals("2026-10-17", json.getJSONArray("dates").getString(0))
    }

    @Test
    fun tanpaLiburTetapDikirimSupayaLiburYangDimatikanSampaiKeWeb() {
        assertEquals(0, daysOffPayload(date("2026-10-16"), emptyList()).toJson().getJSONArray("dates").length())
    }

    @Test
    fun liburBerubahJadiKirimanBerbedaDanHariBergantiJugaBerbeda() {
        val off = daysOffPayload(date("2026-10-16"), listOf(date("2026-10-17")))
        assertEquals(off.fingerprint, daysOffPayload(date("2026-10-16"), listOf(date("2026-10-17"))).fingerprint)
        assertNotEquals(off.fingerprint, daysOffPayload(date("2026-10-16"), emptyList()).fingerprint)
        assertNotEquals(off.fingerprint, daysOffPayload(date("2026-10-17"), listOf(date("2026-10-17"))).fingerprint)
    }

    // --- Waktu sholat ---

    private fun times(subuh: LocalTime? = LocalTime.of(4, 15)) = PrayerTimes(
        imsak = null, subuh = subuh, terbit = null, dhuha = null,
        dzuhur = LocalTime.of(11, 40), ashar = LocalTime.of(15, 0), maghrib = LocalTime.of(17, 45), isya = LocalTime.of(19, 0),
    )

    @Test
    fun waktuSholatTujuhHariMulaiDariTanggalYangDiminta() {
        val asked = mutableListOf<LocalDate>()
        val payload = prayerDaysPayload("Asia/Jakarta", LocalDate.parse("2026-10-16")) { date ->
            asked += date
            times()
        }

        assertEquals(SCHEDULE_SYNC_PRAYER_DAYS, payload.days.size)
        assertEquals(LocalDate.parse("2026-10-16"), asked.first())
        assertEquals(LocalDate.parse("2026-10-22"), asked.last())
    }

    @Test
    fun waktuSholatJsonJamMenitDanNullUntukYangTidakAda() {
        val json = prayerDaysPayload("Asia/Jakarta", LocalDate.parse("2026-10-16")) { times(subuh = null) }.toJson()

        assertEquals("Asia/Jakarta", json.getString("timezone"))
        val day = json.getJSONArray("days").getJSONObject(0)
        assertEquals("2026-10-16", day.getString("date"))
        assertTrue("kunci tetap ada dengan nilai null (server mewajibkan kunci)", day.has("subuh") && day.isNull("subuh"))
        assertEquals("11:40", day.getString("dzuhur"))
        assertEquals("15:00", day.getString("ashar"))
        assertEquals("17:45", day.getString("maghrib"))
        assertEquals("19:00", day.getString("isya"))
    }

    @Test
    fun waktuSholatBerubahKetikaHariBergantiAtauWaktuBerubah() {
        val today = prayerDaysPayload("Asia/Jakarta", LocalDate.parse("2026-10-16")) { times() }
        val tomorrow = prayerDaysPayload("Asia/Jakarta", LocalDate.parse("2026-10-17")) { times() }
        val moved = prayerDaysPayload("Asia/Jakarta", LocalDate.parse("2026-10-16")) { times(LocalTime.of(4, 20)) }
        assertNotEquals(today.fingerprint, tomorrow.fingerprint)
        assertNotEquals(today.fingerprint, moved.fingerprint)
    }
}
