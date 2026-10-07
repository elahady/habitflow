package com.roziqrizal.habitflow.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.roziqrizal.habitflow.domain.prayer.PrayerName
import com.roziqrizal.habitflow.domain.schedule.Days
import com.roziqrizal.habitflow.domain.schedule.NotificationLevel
import com.roziqrizal.habitflow.domain.schedule.WorkAction

/** Database versi 1 ke 2: tiga tabel jadwal ditambah, habit dan riwayat tidak disentuh. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        createScheduleTables(db)
        seedSchedule(db, withWorkAction = false)
    }
}

/** SQL sama dengan skema yang dihasilkan Room untuk versi 2. */
private fun createScheduleTables(db: SupportSQLiteDatabase) {
    db.execSQL(
        "CREATE TABLE IF NOT EXISTS `schedule_blocks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`name` TEXT NOT NULL, `startType` INTEGER NOT NULL, `startValue` INTEGER NOT NULL, `prayer` TEXT, " +
            "`durationMinutes` INTEGER NOT NULL, `endMinuteOfDay` INTEGER, `activeDays` INTEGER NOT NULL, " +
            "`level` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL)"
    )
    db.execSQL(
        "CREATE TABLE IF NOT EXISTS `schedule_block_habits` (`blockId` INTEGER NOT NULL, `habitId` INTEGER NOT NULL, " +
            "PRIMARY KEY(`blockId`, `habitId`), " +
            "FOREIGN KEY(`blockId`) REFERENCES `schedule_blocks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
            "FOREIGN KEY(`habitId`) REFERENCES `habits`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
    )
    db.execSQL(
        "CREATE INDEX IF NOT EXISTS `index_schedule_block_habits_habitId` ON `schedule_block_habits` (`habitId`)"
    )
    db.execSQL("CREATE TABLE IF NOT EXISTS `days_off` (`date` TEXT NOT NULL, PRIMARY KEY(`date`))")
}

private class SeedBlock(
    val name: String,
    val startType: Int,
    val startValue: Int,
    val prayer: PrayerName? = null,
    val durationMinutes: Int,
    val endMinuteOfDay: Int? = null,
    val activeDays: Int = Days.ALL,
    val level: NotificationLevel,
    val habitName: String? = null,
    val workAction: WorkAction? = null,
)

private fun fixed(hour: Int, minute: Int) = hour * 60 + minute

/** Jadwal awal dari tabel di docs/concept.md tahap 17. */
private val SEED_BLOCKS = listOf(
    SeedBlock("Bangun", ScheduleBlockEntity.START_PRAYER, -15, PrayerName.SUBUH, 15, level = NotificationLevel.ALARM),
    SeedBlock(
        "Jamaah Subuh dan ngaji", ScheduleBlockEntity.START_PRAYER, 0, PrayerName.SUBUH, 60,
        level = NotificationLevel.REMINDER, habitName = "Baca Al-Quran",
    ),
    SeedBlock(
        "Aktivitas fisik", ScheduleBlockEntity.START_PRAYER, 60, PrayerName.SUBUH, 0, endMinuteOfDay = fixed(6, 0),
        level = NotificationLevel.INFO, habitName = "Jalan kaki 20 menit",
    ),
    SeedBlock(
        "Mandi dan prepare", ScheduleBlockEntity.START_FIXED, fixed(6, 15), durationMinutes = 20,
        activeDays = Days.WEEKDAYS, level = NotificationLevel.INFO,
    ),
    SeedBlock(
        "Berangkat", ScheduleBlockEntity.START_FIXED, fixed(6, 35), durationMinutes = 85,
        activeDays = Days.WEEKDAYS, level = NotificationLevel.REMINDER,
    ),
    SeedBlock(
        "Kerja pagi", ScheduleBlockEntity.START_FIXED, fixed(8, 0), durationMinutes = 240,
        activeDays = Days.WEEKDAYS, level = NotificationLevel.REMINDER, workAction = WorkAction.SCRUM,
    ),
    SeedBlock("Sholat Dzuhur", ScheduleBlockEntity.START_PRAYER, 0, PrayerName.DZUHUR, 15, level = NotificationLevel.REMINDER),
    SeedBlock(
        "Makan siang dan istirahat", ScheduleBlockEntity.START_FIXED, fixed(12, 0), durationMinutes = 60,
        activeDays = Days.WEEKDAYS, level = NotificationLevel.INFO,
    ),
    SeedBlock(
        "Kerja sore", ScheduleBlockEntity.START_FIXED, fixed(13, 0), durationMinutes = 180,
        activeDays = Days.WEEKDAYS, level = NotificationLevel.REMINDER,
    ),
    SeedBlock("Sholat Ashar", ScheduleBlockEntity.START_PRAYER, 0, PrayerName.ASHAR, 15, level = NotificationLevel.REMINDER),
    SeedBlock(
        "EOD", ScheduleBlockEntity.START_FIXED, fixed(16, 0), durationMinutes = 60,
        activeDays = Days.WEEKDAYS, level = NotificationLevel.REMINDER, workAction = WorkAction.EOD,
    ),
    SeedBlock(
        "Pulang", ScheduleBlockEntity.START_FIXED, fixed(17, 0), durationMinutes = 60,
        activeDays = Days.WEEKDAYS, level = NotificationLevel.INFO,
    ),
    SeedBlock("Sholat Maghrib", ScheduleBlockEntity.START_PRAYER, 0, PrayerName.MAGHRIB, 15, level = NotificationLevel.REMINDER),
    SeedBlock(
        "Sholat Isya", ScheduleBlockEntity.START_PRAYER, 0, PrayerName.ISYA, 15,
        level = NotificationLevel.REMINDER, habitName = "Sholat 5 waktu",
    ),
    SeedBlock(
        "Project personal", ScheduleBlockEntity.START_FIXED, fixed(19, 0), durationMinutes = 120,
        level = NotificationLevel.INFO,
    ),
    SeedBlock(
        "Batas tidur", ScheduleBlockEntity.START_FIXED, fixed(22, 0), durationMinutes = 0,
        level = NotificationLevel.REMINDER, habitName = "Tidur sebelum 22.00",
    ),
)

/**
 * Mengisi jadwal awal. Tautan ke habit dicari lewat nama habit; kalau habitnya sudah diganti
 * namanya atau dihapus (pada pembaruan dari versi 1), blok dibuat tanpa tautan.
 */
fun seedSchedule(db: SupportSQLiteDatabase, withWorkAction: Boolean = true) {
    SEED_BLOCKS.forEachIndexed { index, block ->
        db.execSQL(
            if (withWorkAction) {
                "INSERT INTO schedule_blocks (name, startType, startValue, prayer, durationMinutes, " +
                    "endMinuteOfDay, activeDays, level, sortOrder, workAction) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
            } else {
                // Skema versi 2 belum punya kolom workAction; diisi migrasi 3 ke 4.
                "INSERT INTO schedule_blocks (name, startType, startValue, prayer, durationMinutes, " +
                    "endMinuteOfDay, activeDays, level, sortOrder) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"
            },
            arrayOf<Any?>(
                block.name, block.startType, block.startValue, block.prayer?.name, block.durationMinutes,
                block.endMinuteOfDay, block.activeDays, block.level.name, index,
                *(if (withWorkAction) arrayOf<Any?>(block.workAction?.name) else emptyArray()),
            ),
        )
        if (block.habitName != null) {
            db.execSQL(
                "INSERT OR IGNORE INTO schedule_block_habits (blockId, habitId) " +
                    "SELECT (SELECT MAX(id) FROM schedule_blocks), id FROM habits WHERE name = ?",
                arrayOf<Any?>(block.habitName),
            )
        }
    }
}

/**
 * Database versi 2 ke 3: blok Bangun bawaan naik dari Pengingat ke Alarm. Tidak ada perubahan
 * skema. Blok yang sudah diubah pengguna (nama, patokan, atau tingkatnya) tidak disentuh.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "UPDATE schedule_blocks SET level = 'ALARM' WHERE name = 'Bangun' AND startType = " +
                "${ScheduleBlockEntity.START_PRAYER} AND prayer = 'SUBUH' AND startValue = -15 AND level = 'REMINDER'"
        )
    }
}

/**
 * Database versi 3 ke 4: tabel follow-up kerja dan catatan harian kerja, serta kolom `workAction`
 * pada blok jadwal. Blok Kerja pagi dan EOD bawaan menjadi pembuka daily scrum dan EOD; blok yang
 * sudah diubah pengguna (nama atau waktunya) tidak disentuh. Data lama tidak dihapus.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `schedule_blocks` ADD COLUMN `workAction` TEXT")
        db.execSQL(
            "UPDATE schedule_blocks SET workAction = 'SCRUM' WHERE name = 'Kerja pagi' AND startType = " +
                "${ScheduleBlockEntity.START_FIXED} AND startValue = 480",
        )
        db.execSQL(
            "UPDATE schedule_blocks SET workAction = 'EOD' WHERE name = 'EOD' AND startType = " +
                "${ScheduleBlockEntity.START_FIXED} AND startValue = 960",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `follow_ups` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`title` TEXT NOT NULL, `status` TEXT NOT NULL, `date` TEXT, `time` TEXT, `person` TEXT, " +
                "`note` TEXT, `createdAt` INTEGER NOT NULL, `doneAt` INTEGER, `pickedDate` TEXT)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_follow_ups_status` ON `follow_ups` (`status`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_follow_ups_date` ON `follow_ups` (`date`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `work_days` (`date` TEXT NOT NULL, `eodNote` TEXT, " +
                "`scrumDoneAt` INTEGER, `eodDoneAt` INTEGER, PRIMARY KEY(`date`))",
        )
    }
}
