package com.roziqrizal.habitflow.data.sync

import com.roziqrizal.habitflow.data.BloodPressureEntry
import com.roziqrizal.habitflow.data.WeightEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HealthSyncPayloadTest {

    private fun weight(id: Long, time: Long, kg: Double = 70.0) = WeightEntry(id, time, kg)

    private fun bp(id: Long, time: Long) = BloodPressureEntry(id, time, 120, 80)

    private val focus = HealthFocusPayload(
        rule = "LOW_STEPS",
        walkMinutes = 30,
        title = "Jalan santai 30 menit sore ini",
        sentence = "Langkah baru 2.400 dari 8.000.",
        note = "Dipilih karena langkah di bawah separuh target.",
        reasons = listOf("Berat" to "5,4 kg di atas target", "Tensi" to "128/82 · Normal"),
        computedAtMillis = 1_792_000_000_000,
    )

    private fun build(
        weights: List<WeightEntry> = emptyList(),
        bps: List<BloodPressureEntry> = emptyList(),
        steps: List<Pair<LocalDate, Long>> = emptyList(),
        height: Double? = 170.0,
        target: Double? = null,
        focus: HealthFocusPayload? = null,
    ) = healthSyncPayload(
        timezone = "Asia/Jakarta",
        heightCm = height,
        targetKg = target,
        weights = weights,
        bloodPressures = bps,
        stepDays = steps,
        focus = focus,
        defaultTarget = { 65.0 },
    )

    @Test
    fun bentukJsonSesuaiKontrakServer() {
        val json = build(
            weights = listOf(weight(1, 1_000, 70.4)),
            bps = listOf(BloodPressureEntry(1, 2_000, 128, 82, 72, "pagi")),
            steps = listOf(LocalDate.parse("2026-10-16") to 2_400L),
            focus = focus,
        ).toJson()

        assertEquals("Asia/Jakarta", json.getString("timezone"))
        assertEquals(170.0, json.getJSONObject("profile").getDouble("height_cm"), 0.0)
        val w = json.getJSONArray("weights").getJSONObject(0)
        assertEquals(1_000L, w.getLong("time_millis"))
        assertEquals(70.4, w.getDouble("kg"), 0.0)
        val b = json.getJSONArray("blood_pressures").getJSONObject(0)
        assertEquals(listOf(128, 82, 72, "pagi"), listOf(b.getInt("systolic"), b.getInt("diastolic"), b.getInt("pulse"), b.getString("note")))
        assertEquals("2026-10-16", json.getJSONArray("step_days").getJSONObject(0).getString("date"))
        val f = json.getJSONObject("focus")
        assertEquals("LOW_STEPS", f.getString("rule"))
        assertEquals(30, f.getInt("walk_minutes"))
        assertEquals("Berat", f.getJSONArray("reasons").getJSONObject(0).getString("label"))
        assertTrue(f.getString("computed_at").endsWith("Z"))
    }

    @Test
    fun nadiDanCatatanKosongDikirimSebagaiNull() {
        val b = build(bps = listOf(bp(1, 2_000))).toJson().getJSONArray("blood_pressures").getJSONObject(0)

        assertTrue(b.isNull("pulse"))
        assertTrue(b.isNull("note"))
    }

    @Test
    fun tanpaFokusKunciFokusTidakDikirimSupayaFokusLamaDiServerTidakTersentuh() {
        assertFalse(build(focus = null).toJson().has("focus"))
        assertTrue(build(focus = focus).toJson().has("focus"))
    }

    @Test
    fun waktuHitungFokusDikirimSebagaiIsoUtc() {
        val at = build(focus = focus).toJson().getJSONObject("focus").getString("computed_at")

        assertEquals(java.time.Instant.ofEpochMilli(1_792_000_000_000).toString(), at)
    }

    @Test
    fun walkMinutesKosongDikirimSebagaiNull() {
        assertTrue(build(focus = focus.copy(walkMinutes = null)).toJson().getJSONObject("focus").isNull("walk_minutes"))
    }

    @Test
    fun targetBawaanDihitungDariTinggiKalauBelumDiatur() {
        assertEquals(65.0, build(height = 170.0, target = null).targetKg!!, 0.0)
        assertEquals(60.0, build(height = 170.0, target = 60.0).targetKg!!, 0.0)
        assertNull(build(height = null, target = null).targetKg)
    }

    @Test
    fun tinggiDanTargetKosongDikirimSebagaiNullBukanDihilangkan() {
        val profile = build(height = null, target = null).toJson().getJSONObject("profile")

        assertTrue(profile.has("height_cm") && profile.isNull("height_cm"))
        assertTrue(profile.has("target_kg") && profile.isNull("target_kg"))
    }

    @Test
    fun beratDiurutkanDariYangPalingLamaMeskipunMasukTidakBerurutan() {
        val weights = build(weights = listOf(weight(2, 3_000), weight(1, 1_000), weight(3, 2_000))).weights

        assertEquals(listOf(1_000L, 2_000L, 3_000L), weights.map { it.timeMillis })
    }

    @Test
    fun lebihDariBatasHanyaYangPalingBaruYangDikirim() {
        val many = (1..HEALTH_SYNC_MAX_ENTRIES + 25).map { weight(it.toLong(), it * 1_000L) }

        val sent = build(weights = many, bps = (1..HEALTH_SYNC_MAX_ENTRIES + 25).map { bp(it.toLong(), it * 1_000L) })

        assertEquals(HEALTH_SYNC_MAX_ENTRIES, sent.weights.size)
        assertEquals(26_000L, sent.weights.first().timeMillis)
        assertEquals((HEALTH_SYNC_MAX_ENTRIES + 25) * 1_000L, sent.weights.last().timeMillis)
        assertEquals(HEALTH_SYNC_MAX_ENTRIES, sent.bloodPressures.size)
    }

    @Test
    fun langkahDibatasiTujuhHariTerbaru() {
        val days = (0..9).map { LocalDate.parse("2026-10-16").minusDays(it.toLong()) to 1_000L + it }

        val sent = build(steps = days).stepDays

        assertEquals(HEALTH_SYNC_STEP_DAYS, sent.size)
        assertEquals(LocalDate.parse("2026-10-10"), sent.first().first)
        assertEquals(LocalDate.parse("2026-10-16"), sent.last().first)
    }

    @Test
    fun teksPanjangDipotongSebelumKeBatasServer() {
        val long = focus.copy(
            title = "t".repeat(500),
            sentence = "s".repeat(900),
            note = "n".repeat(900),
            reasons = (1..9).map { "L".repeat(80) to "V".repeat(300) },
        )

        val f = build(focus = long).toJson().getJSONObject("focus")

        assertEquals(200, f.getString("title").length)
        assertEquals(600, f.getString("sentence").length)
        assertEquals(600, f.getString("note").length)
        assertEquals(6, f.getJSONArray("reasons").length())
        assertEquals(40, f.getJSONArray("reasons").getJSONObject(0).getString("label").length)
        assertEquals(120, f.getJSONArray("reasons").getJSONObject(0).getString("value").length)
    }

    @Test
    fun catatanTensiPanjangDipotongLimaRatus() {
        val b = build(bps = listOf(BloodPressureEntry(1, 1_000, 120, 80, null, "x".repeat(800)))).toJson()
            .getJSONArray("blood_pressures").getJSONObject(0)

        assertEquals(500, b.getString("note").length)
    }

    @Test
    fun sidikJariSamaUntukIsiSamaDanBedaKalauIsiBerubah() {
        val a = build(weights = listOf(weight(1, 1_000, 70.0)), focus = focus)
        val same = build(weights = listOf(weight(1, 1_000, 70.0)), focus = focus)
        val otherWeight = build(weights = listOf(weight(1, 1_000, 71.0)), focus = focus)
        val otherFocus = build(weights = listOf(weight(1, 1_000, 70.0)), focus = focus.copy(sentence = "Langkah baru 3.000 dari 8.000."))

        assertEquals(a.fingerprint, same.fingerprint)
        assertNotEquals(a.fingerprint, otherWeight.fingerprint)
        assertNotEquals(a.fingerprint, otherFocus.fingerprint)
    }

    // --- HealthFocusStore ---

    @Test
    fun penyimpanFokusMenerimaFokusPertama() {
        val store = HealthFocusStore()
        assertNull(store.latest.value)

        store.publish(focus)

        assertEquals(focus, store.latest.value)
    }

    @Test
    fun fokusDenganIsiSamaTetapWaktuHitungBedaDiabaikanSupayaTidakMemicuKirimUlang() {
        val store = HealthFocusStore()
        store.publish(focus)

        store.publish(focus.copy(computedAtMillis = focus.computedAtMillis + 60_000))

        assertEquals(focus.computedAtMillis, store.latest.value!!.computedAtMillis)
    }

    @Test
    fun fokusYangIsinyaBerubahMenggantikanYangLama() {
        val store = HealthFocusStore()
        store.publish(focus)
        val next = focus.copy(sentence = "Langkah baru 3.000 dari 8.000.", computedAtMillis = focus.computedAtMillis + 60_000)

        store.publish(next)

        assertEquals(next, store.latest.value)
    }
}
