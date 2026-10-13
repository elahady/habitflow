package com.roziqrizal.habitflow.data

import com.roziqrizal.habitflow.testing.FakeSharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PrefFileRegistryTest {

    private val files = PrefFile.entries.associateWith { FakeSharedPreferences() }

    private fun wipe() = PrefWiper.wipe { files.getValue(it) }

    @Test
    fun setiapBerkasDiDaftarDihapusSaatKeluar() {
        files.forEach { (file, prefs) ->
            prefs.edit().putString("rahasia", "x").putBoolean("flag", true).putLong("angka", 7L).commit()
            assertTrue("${file.fileName} harus terisi sebelum dihapus", prefs.all.isNotEmpty())
        }

        wipe()

        files.forEach { (file, prefs) ->
            assertEquals("${file.fileName} masih berisi ${prefs.all.keys}", emptySet<String>(), prefs.all.keys)
        }
    }

    @Test
    fun hanyaTemaTampilanYangDibiarkan() {
        files.getValue(PrefFile.SETTINGS).edit()
            .putString(ThemeSettings.KEY_MODE, "DARK")
            .putString("location_name", "Rumah")
            .putBoolean("water_reminders", true)
            .commit()

        wipe()

        assertEquals(mapOf<String, Any?>(ThemeSettings.KEY_MODE to "DARK"), files.getValue(PrefFile.SETTINGS).all.toMap())
    }

    @Test
    fun hanyaSatuKunciYangDikecualikanDiSeluruhRegistri() {
        val preserved = PrefFile.entries.flatMap { f -> f.preservedKeys.map { f to it } }
        assertEquals(listOf(PrefFile.SETTINGS to ThemeSettings.KEY_MODE), preserved)
    }

    @Test
    fun namaBerkasUnik() {
        val names = PrefFile.entries.map { it.fileName }
        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun berkasKosongTidakMenimbulkanMasalah() {
        wipe()
        files.values.forEach { assertTrue(it.all.isEmpty()) }
    }

    /**
     * Penjaga: pengaturan baru yang dibuka langsung lewat `getSharedPreferences` tidak terdaftar di [PrefFile], sehingga lolos dari
     * penghapusan saat Keluar dan data akun lama bisa tertinggal di HP. Semua harus lewat `PrefFile.X.open(context)`.
     */
    @Test
    fun tidakAdaPemanggilanLangsungGetSharedPreferencesDiLuarRegistri() {
        val root = File("src/main/java")
        assertTrue("folder sumber tidak ditemukan dari ${File(".").absolutePath}", root.isDirectory)

        val offenders = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.name != "PrefFile.kt" }
            .filter { file -> file.readLines().any { line -> "getSharedPreferences(" in line && !line.trimStart().startsWith("*") } }
            .map { it.relativeTo(root).path }
            .toList()

        assertEquals("Buka berkas lewat PrefFile, bukan getSharedPreferences langsung", emptyList<String>(), offenders)
    }

    /** Setiap berkas di registri dipakai sungguhan; nama yang tak lagi dipakai harus dihapus dari daftar. */
    @Test
    fun setiapEntriRegistriDipakaiDiKode() {
        val root = File("src/main/java")
        val text = root.walkTopDown().filter { it.isFile && it.extension == "kt" && it.name != "PrefFile.kt" }
            .joinToString("\n") { it.readText() }

        PrefFile.entries.forEach { file ->
            assertTrue("PrefFile.${file.name} tidak dipakai di mana pun", "PrefFile.${file.name}" in text)
        }
    }
}
