package com.roziqrizal.habitflow.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Penjaga pemindaian sumber (tahap 31H): setiap komponen yang bisa terpicu sistem tanpa lewat layar harus memeriksa
 * [SessionGate] sebelum bekerja, atau tercatat di bawah sebagai pengecualian beserta alasannya. Komponen baru yang lupa
 * diputuskan nasibnya menggagalkan tes ini, bukan diam-diam berjalan sebelum pengguna masuk.
 */
class BackgroundGuardTest {

    private val root = File("src/main/java")

    private val entryPoint = Regex("""CoroutineWorker\(|BroadcastReceiver\(\)|: Service\(\)""")

    /** File yang berisi komponen tapi sengaja tidak memeriksa [SessionGate] sendiri, dengan alasannya. */
    private val exceptions = mapOf(
        "com/roziqrizal/habitflow/MainActivity.kt" to
            "receiver ini hidup hanya selama Activity tampil dan hanya memuat ulang tanggal, tidak menyentuh data akun",
        "com/roziqrizal/habitflow/notify/AlarmService.kt" to
            "hanya dinyalakan oleh ScheduleReceiver (yang memeriksa sesi), dan finish() memanggil ScheduleNotifier.refresh yang memeriksa sesi",
    )

    private fun kotlinFiles() = root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    private fun relative(file: File) = file.relativeTo(root).path.replace('\\', '/')

    @Test
    fun setiapPekerjaDanReceiverMemeriksaSesiAtauTercatatSebagaiPengecualian() {
        assertTrue("folder sumber tidak ditemukan dari ${File(".").absolutePath}", root.isDirectory)

        val offenders = kotlinFiles()
            .filter { entryPoint.containsMatchIn(it.readText()) }
            .map { relative(it) to it.readText() }
            .filter { (path, text) -> path !in exceptions && "SessionGate.isActive" !in text }
            .map { it.first }

        assertEquals("Komponen latar belakang tanpa pemeriksaan sesi", emptyList<String>(), offenders)
    }

    @Test
    fun pengecualianMasihAdaDanBerisiKomponen() {
        exceptions.keys.forEach { path ->
            val file = File(root, path)
            assertTrue("$path tidak ada lagi: hapus dari daftar pengecualian", file.isFile)
            assertTrue("$path tidak lagi berisi komponen: hapus dari daftar pengecualian", entryPoint.containsMatchIn(file.readText()))
        }
    }

    @Test
    fun jadwalAlarmDanNotifikasiHanyaLewatPemeriksaSesi() {
        val notifier = File(root, "com/roziqrizal/habitflow/notify/ScheduleNotifier.kt").readText()
        val refresh = notifier.substringAfter("suspend fun refresh(").substringBefore("ensureChannels(app)")
        assertTrue("refresh() harus memeriksa sesi sebelum menjadwalkan atau menampilkan apa pun", "SessionGate.isActive" in refresh)
        assertTrue("refresh() tanpa sesi harus membersihkan sisa alarm", "cancelAll(app)" in refresh)
    }

    @Test
    fun pekerjaanTerjadwalHanyaDipasangOlehPenjadwalYangDikenal() {
        // Penjadwal ini hanya dipanggil dari SessionManager (lewat AndroidBackgroundWork), dari pengamat perubahan data (yang
        // memeriksa sesi sendiri lewat isActive), atau dari layar sinkron yang butuh sesi untuk bisa ditekan.
        val allowedToEnqueue = setOf(
            "com/roziqrizal/habitflow/data/health/StepsWorker.kt",
            "com/roziqrizal/habitflow/data/sync/HabitSyncScheduler.kt",
            "com/roziqrizal/habitflow/data/sync/SyncScheduler.kt",
            "com/roziqrizal/habitflow/data/team/TeamPoll.kt",
        )
        val enqueuers = kotlinFiles().filter { "enqueueUnique" in it.readText() }.map { relative(it) }.toSet()
        assertEquals("Ada pemasang pekerjaan terjadwal baru: putuskan bagaimana ia mati saat Keluar", allowedToEnqueue, enqueuers)
    }
}
