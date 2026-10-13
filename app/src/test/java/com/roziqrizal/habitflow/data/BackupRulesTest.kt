package com.roziqrizal.habitflow.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Penjaga cadangan (31I): data akun hanya boleh ada di server dan di HP pemiliknya. Database dan semua berkas pengaturan harus
 * dikecualikan dari Auto Backup dan pemindahan perangkat, kalau tidak, data akun lama bisa dipulihkan ke HP lain lalu diambil
 * alih akun lain.
 */
class BackupRulesTest {

    private fun rules(name: String): String {
        val file = File("src/main/res/xml/$name")
        assertTrue("$name tidak ditemukan dari ${File(".").absolutePath}", file.isFile)
        return file.readText()
    }

    private val ruleFiles = listOf("backup_rules.xml", "data_extraction_rules.xml")

    /** Pasangan (domain, path) dari setiap tag `<exclude>` dan `<include>`. */
    private fun entries(xml: String, tag: String): List<Pair<String, String>> =
        Regex("<$tag\\s+domain=\"([^\"]+)\"\\s+path=\"([^\"]+)\"").findAll(xml).map { it.groupValues[1] to it.groupValues[2] }.toList()

    @Test
    fun tidakAdaYangDisertakanSecaraEksplisit() {
        ruleFiles.forEach { assertEquals("$it masih memuat <include>", emptyList<Pair<String, String>>(), entries(rules(it), "include")) }
    }

    @Test
    fun databaseDikecualikan() {
        ruleFiles.forEach { name ->
            val excluded = entries(rules(name), "exclude")
            listOf("habitflow.db", "habitflow.db-wal", "habitflow.db-shm", "habitflow.db-journal").forEach {
                assertTrue("$name tidak mengecualikan $it", "database" to it in excluded)
            }
        }
    }

    @Test
    fun setiapBerkasPengaturanDiRegistriDikecualikan() {
        ruleFiles.forEach { name ->
            val excluded = entries(rules(name), "exclude")
            PrefFile.entries.forEach {
                assertTrue("$name tidak mengecualikan ${it.fileName}.xml", "sharedpref" to "${it.fileName}.xml" in excluded)
            }
        }
    }

    @Test
    fun cadanganAwanDanPemindahanPerangkatSamaDikecualikan() {
        val xml = rules("data_extraction_rules.xml")
        listOf("cloud-backup", "device-transfer").forEach { section ->
            val body = xml.substringAfter("<$section>").substringBefore("</$section>")
            assertEquals("bagian $section", PrefFile.entries.size + 4, entries(body, "exclude").size)
        }
    }
}
