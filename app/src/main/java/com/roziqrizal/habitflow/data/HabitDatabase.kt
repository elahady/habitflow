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
    ],
    version = 2,
    exportSchema = false,
)
abstract class HabitDatabase : RoomDatabase() {

    abstract fun habitDao(): HabitDao
    abstract fun habitEntryDao(): HabitEntryDao
    abstract fun todoDao(): TodoDao
    abstract fun scheduleDao(): ScheduleDao

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
                    .addMigrations(MIGRATION_1_2)
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

        private object SeedCallback : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                SEED_HABITS.forEachIndexed { index, (name, mandatory) ->
                    db.execSQL(
                        "INSERT INTO habits (name, createdAt, sortOrder, isMandatory) " +
                            "VALUES (?, date('now', 'localtime'), ?, ?)",
                        arrayOf(name, index, if (mandatory) 1 else 0),
                    )
                }
                seedSchedule(db)
            }
        }
    }
}
