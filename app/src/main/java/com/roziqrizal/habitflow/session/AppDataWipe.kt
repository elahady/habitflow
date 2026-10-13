package com.roziqrizal.habitflow.session

import android.content.Context
import com.roziqrizal.habitflow.data.HabitDatabase
import com.roziqrizal.habitflow.data.PrefWiper
import com.roziqrizal.habitflow.data.account.AccountSettings
import java.io.File

/**
 * Bagian Android dari penghapusan data HP ini (tahap 31H): database Room (termasuk berkas WAL), semua berkas pengaturan
 * ([PrefWiper], kecuali tema), dan cache. Dijalankan oleh [PendingWipe] di awal proses, sebelum ada yang membuka database.
 */
object AppDataWipe {

    fun wipeAll(context: Context) {
        val app = context.applicationContext
        HabitDatabase.deleteFiles(app)
        PrefWiper.wipe { it.open(app) }
        app.cacheDir.deleteRecursively()
        app.externalCacheDir?.deleteRecursively()
    }

    /** [PendingWipe] untuk app ini. Penanda di `noBackupFilesDir`: tidak ikut cadangan awan dan tidak tersentuh penghapusan. */
    fun pendingWipe(context: Context): PendingWipe {
        val app = context.applicationContext
        return PendingWipe(
            marker = File(app.noBackupFilesDir, "pending_wipe.json"),
            wipe = { wipeAll(app) },
            restoreSession = { AccountSettings(app).save(it) },
        )
    }
}
