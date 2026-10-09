package com.roziqrizal.habitflow.data.sync

import com.roziqrizal.habitflow.data.BloodPressureEntry
import com.roziqrizal.habitflow.data.DayOff
import com.roziqrizal.habitflow.data.DrinkCount
import com.roziqrizal.habitflow.data.FollowUpEntity
import com.roziqrizal.habitflow.data.Habit
import com.roziqrizal.habitflow.data.HabitEntry
import com.roziqrizal.habitflow.data.ScheduleBlockEntity
import com.roziqrizal.habitflow.data.ScheduleBlockHabit
import com.roziqrizal.habitflow.data.Todo
import com.roziqrizal.habitflow.data.WeightEntry
import com.roziqrizal.habitflow.data.WorkDayEntity
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SnapshotCodecTest {

    private fun sample() = Snapshot(
        createdAt = 1791350953356,
        deviceId = "perangkat-1",
        appVersion = "1.0",
        habits = listOf(
            Habit(1, "Sholat 5 waktu", "2026-10-07", 0, true),
            Habit(2, "Air putih \"2 liter\"", "2026-10-07", 1, false, "WATER"),
        ),
        habitEntries = listOf(HabitEntry(1, "2026-10-07"), HabitEntry(2, "2026-10-06")),
        todos = listOf(Todo(1, "Tulis laporan\nbaris kedua", "2026-10-07", true, 1791350000000)),
        scheduleBlocks = listOf(
            ScheduleBlockEntity(1, "Bangun", 1, -15, "SUBUH", 15, null, 127, "ALARM", 0, null),
            ScheduleBlockEntity(2, "Aktivitas fisik", 1, 60, "SUBUH", 0, 360, 127, "INFO", 1, "EOD"),
            ScheduleBlockEntity(3, "Kerja pagi", 0, 480, null, 240, null, 31, "REMINDER", 2, "SCRUM", true),
        ),
        scheduleBlockHabits = listOf(ScheduleBlockHabit(1, 1)),
        daysOff = listOf(DayOff("2026-10-07")),
        followUps = listOf(
            FollowUpEntity(1, "Telepon vendor", "ACTIVE", "2026-10-07", "14:00", "Budi", "Bawa kontrak", 5, null, "2026-10-08"),
            FollowUpEntity(2, "Catatan lepas", "INBOX", null, null, null, null, 6, null, null),
            FollowUpEntity(3, "Selesai", "DONE", "2026-10-05", null, "Sari", null, 7, 1791350953356, null),
        ),
        workDays = listOf(
            WorkDayEntity("2026-10-07", "Besok fokus demo", 100L, 200L),
            WorkDayEntity("2026-10-06", null, null, null),
        ),
        drinkCounts = listOf(DrinkCount("2026-10-07", "WATER", 5), DrinkCount("2026-10-06", "WATER", 8)),
        weightEntries = listOf(WeightEntry(1, 1791350000000, 72.4), WeightEntry(2, 1791436400000, 71.9)),
        bloodPressureEntries = listOf(
            BloodPressureEntry(1, 1791350000000, 128, 82, 70, "Setelah ngaji"),
            BloodPressureEntry(2, 1791436400000, 118, 76, null, null),
        ),
        settings = SnapshotSettings(
            locationName = "Surabaya",
            latitude = -7.2575,
            longitude = 112.7521,
            persistentNotification = true,
            adzan = mapOf("SUBUH" to true, "DZUHUR" to false, "ASHAR" to true, "MAGHRIB" to true, "ISYA" to true),
            themeMode = "DARK",
            waterReminders = true,
            breakReminders = false,
            heightCm = 170.0,
            targetKg = 70.5,
            weightReminder = false,
            bpFrequency = "DAILY",
        ),
    )

    @Test
    fun roundTripMengembalikanSemuaDataPersisSama() {
        val original = sample()
        assertEquals(original, SnapshotCodec.decode(SnapshotCodec.encode(original)))
    }

    @Test
    fun jsonMemuatSchemaVersionDanSemuaTabel() {
        val root = JSONObject(SnapshotCodec.encode(sample()))
        assertEquals(1, root.getInt("schemaVersion"))
        val data = root.getJSONObject("data")
        listOf(
            "habits", "habitEntries", "todos", "scheduleBlocks", "scheduleBlockHabits", "daysOff", "followUps",
            "workDays", "drinkCounts", "settings",
        ).forEach { assertTrue("$it hilang", data.has(it)) }
        assertEquals(2, data.getJSONArray("habits").length())
    }

    @Test
    fun bidangTahap20DitulisDenganNamaYangDiHarapkanServerDanApp() {
        val data = JSONObject(SnapshotCodec.encode(sample())).getJSONObject("data")
        assertEquals("WATER", data.getJSONArray("habits").getJSONObject(1).getString("autoSource"))
        assertTrue(data.getJSONArray("habits").getJSONObject(0).isNull("autoSource"))
        assertTrue(data.getJSONArray("scheduleBlocks").getJSONObject(2).getBoolean("workReminders"))
        assertEquals(5, data.getJSONArray("drinkCounts").getJSONObject(0).getInt("count"))
        val reminders = data.getJSONObject("settings").getJSONObject("workReminders")
        assertTrue(reminders.getBoolean("water"))
        assertEquals(false, reminders.getBoolean("break"))
    }

    @Test
    fun bidangTahap21DitulisDenganNamaYangDiHarapkan() {
        val data = JSONObject(SnapshotCodec.encode(sample())).getJSONObject("data")
        assertEquals(72.4, data.getJSONArray("weightEntries").getJSONObject(0).getDouble("kg"), 0.0)
        val bp = data.getJSONArray("bloodPressureEntries")
        assertEquals(128, bp.getJSONObject(0).getInt("systolic"))
        assertEquals("Setelah ngaji", bp.getJSONObject(0).getString("note"))
        assertTrue(bp.getJSONObject(1).isNull("pulse"))
        assertTrue(bp.getJSONObject(1).isNull("note"))
        val health = data.getJSONObject("settings").getJSONObject("health")
        assertEquals(170.0, health.getDouble("heightCm"), 0.0)
        assertEquals(70.5, health.getDouble("targetKg"), 0.0)
        assertEquals(false, health.getBoolean("weightReminder"))
        assertEquals("DAILY", health.getString("bpFrequency"))
    }

    @Test
    fun tinggiDanTargetKosongTetapKosongSetelahRoundTrip() {
        val kosong = sample().copy(settings = sample().settings.copy(heightCm = null, targetKg = null))
        val decoded = SnapshotCodec.decode(SnapshotCodec.encode(kosong))
        assertEquals(null, decoded.settings.heightCm)
        assertEquals(null, decoded.settings.targetKg)
        assertEquals(kosong, decoded)
    }

    @Test
    fun snapshotTahap19BDanTahap20TanpaBidangTahap21TetapBisaDipulihkan() {
        val root = JSONObject(SnapshotCodec.encode(sample()))
        val data = root.getJSONObject("data")
        data.remove("weightEntries")
        data.remove("bloodPressureEntries")
        data.getJSONObject("settings").remove("health")

        val decoded = SnapshotCodec.decode(root.toString())

        assertEquals(emptyList<WeightEntry>(), decoded.weightEntries)
        assertEquals(emptyList<BloodPressureEntry>(), decoded.bloodPressureEntries)
        assertEquals(null, decoded.settings.heightCm)
        assertEquals(null, decoded.settings.targetKg)
        assertTrue(decoded.settings.weightReminder)
        assertEquals("WEEKLY", decoded.settings.bpFrequency)
        // Bidang tahap 20 yang masih ada tetap terbaca.
        assertEquals(sample().drinkCounts, decoded.drinkCounts)
    }

    @Test
    fun snapshotLamaTanpaBidangTahap20TetapBisaDipulihkan() {
        val root = JSONObject(SnapshotCodec.encode(sample()))
        val data = root.getJSONObject("data")
        data.remove("drinkCounts")
        data.getJSONObject("settings").remove("workReminders")
        for (i in 0 until data.getJSONArray("habits").length()) data.getJSONArray("habits").getJSONObject(i).remove("autoSource")
        for (i in 0 until data.getJSONArray("scheduleBlocks").length()) data.getJSONArray("scheduleBlocks").getJSONObject(i).remove("workReminders")

        val decoded = SnapshotCodec.decode(root.toString())

        assertEquals(emptyList<DrinkCount>(), decoded.drinkCounts)
        assertTrue(decoded.settings.waterReminders)
        assertTrue(decoded.settings.breakReminders)
        assertTrue(decoded.habits.all { it.autoSource == null })
        assertTrue(decoded.scheduleBlocks.none { it.workReminders })
        assertEquals(sample().habits.map { it.name }, decoded.habits.map { it.name })
    }

    @Test
    fun nilaiNullDitulisSebagaiJsonNullDanKembaliNull() {
        val block = JSONObject(SnapshotCodec.encode(sample())).getJSONObject("data")
            .getJSONArray("scheduleBlocks").getJSONObject(0)
        assertTrue(block.isNull("endMinuteOfDay"))
        assertTrue(block.isNull("workAction"))

        val decoded = SnapshotCodec.decode(SnapshotCodec.encode(sample()))
        assertEquals(null, decoded.scheduleBlocks[0].endMinuteOfDay)
        assertEquals(null, decoded.followUps[1].date)
        assertEquals(null, decoded.workDays[1].eodNote)
    }

    @Test
    fun dataKosongTetapSah() {
        val empty = sample().copy(
            habits = emptyList(), habitEntries = emptyList(), todos = emptyList(), scheduleBlocks = emptyList(),
            scheduleBlockHabits = emptyList(), daysOff = emptyList(), followUps = emptyList(), workDays = emptyList(),
        )
        assertEquals(empty, SnapshotCodec.decode(SnapshotCodec.encode(empty)))
    }

    @Test
    fun teksBukanJsonDitolakDenganPesanJelas() {
        try {
            SnapshotCodec.decode("ini bukan json")
            fail("seharusnya ditolak")
        } catch (e: SnapshotFormatException) {
            assertTrue(e.message!!.contains("rusak"))
        }
    }

    @Test
    fun tanpaSchemaVersionDitolak() {
        try {
            SnapshotCodec.decode("""{"data": {}}""")
            fail("seharusnya ditolak")
        } catch (e: SnapshotFormatException) {
            assertTrue(e.message!!.contains("schemaVersion"))
        }
    }

    @Test
    fun versiLebihBaruDitolakDenganPesanMemperbaruiApp() {
        val newer = JSONObject(SnapshotCodec.encode(sample())).put("schemaVersion", 2).toString()
        try {
            SnapshotCodec.decode(newer)
            fail("seharusnya ditolak")
        } catch (e: SnapshotFormatException) {
            assertTrue(e.message!!.contains("Perbarui"))
        }
    }

    @Test
    fun tabelHilangDitolakBukanCrash() {
        val broken = JSONObject(SnapshotCodec.encode(sample()))
        broken.getJSONObject("data").remove("habits")
        try {
            SnapshotCodec.decode(broken.toString())
            fail("seharusnya ditolak")
        } catch (e: SnapshotFormatException) {
            assertTrue(e.message!!.contains("rusak"))
        }
    }

    @Test
    fun tautanYatimDibuangDanYangSahDipertahankan() {
        val habits = listOf(Habit(1, "A", "2026-10-07", 0, false), Habit(3, "C", "2026-10-07", 1, false))
        val blocks = listOf(ScheduleBlockEntity(2, "B", 0, 480, null, 60, null, 127, "INFO", 0, null))
        val links = listOf(
            ScheduleBlockHabit(2, 1), // sah
            ScheduleBlockHabit(2, 3), // sah
            ScheduleBlockHabit(17, 3), // blok tidak ada
            ScheduleBlockHabit(2, 99), // habit tidak ada
        )
        assertEquals(listOf(ScheduleBlockHabit(2, 1), ScheduleBlockHabit(2, 3)), consistentLinks(links, habits, blocks))
        assertEquals(emptyList<ScheduleBlockHabit>(), consistentLinks(links, emptyList(), blocks))
    }
}
