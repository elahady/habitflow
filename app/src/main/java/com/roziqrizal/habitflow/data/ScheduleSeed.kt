package com.roziqrizal.habitflow.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.roziqrizal.habitflow.domain.prayer.PrayerName
import com.roziqrizal.habitflow.domain.schedule.Days
import com.roziqrizal.habitflow.domain.schedule.NotificationLevel

/** Database versi 1 ke 2: tiga tabel jadwal ditambah, habit dan riwayat tidak disentuh. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        createScheduleTables(db)
        seedSchedule(db)
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
)

private fun fixed(hour: Int, minute: Int) = hour * 60 + minute

/** Jadwal awal dari tabel di docs/concept.md tahap 17. */
private val SEED_BLOCKS = listOf(
    SeedBlock("Bangun", ScheduleBlockEntity.START_PRAYER, -15, PrayerName.SUBUH, 15, level = NotificationLevel.REMINDER),
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
        activeDays = Days.WEEKDAYS, level = NotificationLevel.REMINDER,
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
        activeDays = Days.WEEKDAYS, level = NotificationLevel.REMINDER,
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
fun seedSchedule(db: SupportSQLiteDatabase) {
    SEED_BLOCKS.forEachIndexed { index, block ->
        db.execSQL(
            "INSERT INTO schedule_blocks (name, startType, startValue, prayer, durationMinutes, " +
                "endMinuteOfDay, activeDays, level, sortOrder) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
            arrayOf<Any?>(
                block.name, block.startType, block.startValue, block.prayer?.name, block.durationMinutes,
                block.endMinuteOfDay, block.activeDays, block.level.name, index,
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
