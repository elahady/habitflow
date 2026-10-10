package com.roziqrizal.habitflow.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Habit::class, HabitEntry::class, Todo::class,
        ScheduleBlockEntity::class, ScheduleBlockHabit::class, DayOff::class,
        FollowUpEntity::class, WorkDayEntity::class, DrinkCount::class,
        WeightEntry::class, BloodPressureEntry::class,
        EventEntity::class, EventExceptionEntity::class, HolidayCancellation::class,
        MealEntity::class, HabitManualMark::class,
        HabitEntryOutbox::class, PendingDelete::class,
    ],
    version = 9,
    exportSchema = false,
)
abstract class HabitDatabase : RoomDatabase() {

    abstract fun habitDao(): HabitDao
    abstract fun habitEntryDao(): HabitEntryDao
    abstract fun todoDao(): TodoDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun followUpDao(): FollowUpDao
    abstract fun workDayDao(): WorkDayDao
    abstract fun syncDao(): SyncDao
    abstract fun drinkDao(): DrinkDao
    abstract fun healthDao(): HealthDao
    abstract fun eventDao(): EventDao
    abstract fun mealDao(): MealDao
    abstract fun habitSyncOutboxDao(): HabitSyncOutboxDao

    companion object {
        private const val NAME = "habitflow.db"

        @Volatile
        private var instance: HabitDatabase? = null

        fun get(context: Context): HabitDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    HabitDatabase::class.java,
                    NAME,
                )
                    .addMigrations(
                        MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8,
                        MIGRATION_8_9,
                    )
                    .addCallback(SeedCallback)
                    .build()
                    .also { instance = it }
            }

        /**
         * Habit awal dari rancangan. Dua yang pertama wajib (`isMandatory = 1`).
         * Dimasukkan sekali, saat database pertama kali dibuat.
         */
        private val SEED_HABITS = listOf(
            "Sholat 5 waktu" to true,
            "Baca Al-Quran" to true,
            "8.000 langkah" to false,
            "Air putih 2 liter" to false,
            "Tanpa minuman manis" to false,
            "Ngopi maksimal 2 gelas (sepulang kerja)" to false,
            "Tidur sebelum 22.00" to false,
            "Makan malam selesai 2-3 jam sebelum tidur" to false,
            "Tanpa gorengan atau camilan manis" to false,
        )

        /** Habit awal yang dicentang otomatis dari sumber tertentu (tahap 20, 21, dan 23). */
        private val SEED_AUTO_SOURCES = mapOf(
            "Air putih 2 liter" to HabitAutoSource.WATER,
            "8.000 langkah" to HabitAutoSource.STEPS,
            "Tanpa minuman manis" to HabitAutoSource.NO_SWEET_DRINK,
            "Ngopi maksimal 2 gelas (sepulang kerja)" to HabitAutoSource.COFFEE,
            "Makan malam selesai 2-3 jam sebelum tidur" to HabitAutoSource.DINNER,
            "Tanpa gorengan atau camilan manis" to HabitAutoSource.NO_FRIED_SWEET,
        )

        private object SeedCallback : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                SEED_HABITS.forEachIndexed { index, (name, mandatory) ->
                    db.execSQL(
                        "INSERT INTO habits (name, createdAt, sortOrder, isMandatory, autoSource) " +
                            "VALUES (?, date('now', 'localtime'), ?, ?, ?)",
                        arrayOf(name, index, if (mandatory) 1 else 0, SEED_AUTO_SOURCES[name]),
                    )
                }
                seedSchedule(db)
            }
        }
    }
}

/**
 * Database versi 8 ke 9 (tahap 28 langkah 5): kolom sinkron server di `habits` dan `todos`
 * (`remoteId`, `remoteUpdatedAt`, `dirty`), serta tabel antrian `habit_entry_outbox` dan
 * `pending_deletes`. Baris lama diberi `dirty = 1` supaya ikut terdorong ke server begitu
 * pengguna login (lihat `HabitSyncManager`). Riwayat habit/todo tidak disentuh.
 */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `habits` ADD COLUMN `remoteId` INTEGER")
        db.execSQL("ALTER TABLE `habits` ADD COLUMN `remoteUpdatedAt` TEXT")
        db.execSQL("ALTER TABLE `habits` ADD COLUMN `dirty` INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE `todos` ADD COLUMN `remoteId` INTEGER")
        db.execSQL("ALTER TABLE `todos` ADD COLUMN `remoteUpdatedAt` TEXT")
        db.execSQL("ALTER TABLE `todos` ADD COLUMN `dirty` INTEGER NOT NULL DEFAULT 1")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `habit_entry_outbox` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`habitId` INTEGER NOT NULL, `date` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `pending_deletes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`entity` TEXT NOT NULL, `remoteId` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)",
        )
    }
}
