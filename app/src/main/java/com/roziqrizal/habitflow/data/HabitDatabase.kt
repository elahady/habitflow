package com.roziqrizal.habitflow.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Habit::class, HabitEntry::class, Todo::class,
        ScheduleBlockEntity::class, ScheduleBlockHabit::class, DayOff::class,
        FollowUpEntity::class, WorkDayEntity::class, DrinkCount::class,
    ],
    version = 5,
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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
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
            "Jalan kaki 20 menit" to false,
            "Air putih 2 liter" to false,
            "Tanpa minuman manis" to false,
            "Ngopi maksimal 2 gelas (sepulang kerja)" to false,
            "Tidur sebelum 22.00" to false,
            "Makan malam selesai 2-3 jam sebelum tidur" to false,
            "Tanpa gorengan atau camilan manis" to false,
        )

        /** Habit awal yang dicentang otomatis dari sumber tertentu (tahap 20). */
        private val SEED_AUTO_SOURCES = mapOf("Air putih 2 liter" to HabitAutoSource.WATER)

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
