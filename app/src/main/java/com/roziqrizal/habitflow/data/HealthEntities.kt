package com.roziqrizal.habitflow.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Satu catatan berat badan. [timeMillis] adalah waktu catat (epoch milidetik), [kg] satu desimal (tahap 21). */
@Entity(tableName = "weight_entries", indices = [Index("timeMillis")])
data class WeightEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timeMillis: Long,
    val kg: Double,
)

/** Satu catatan tensi. Nadi dan catatan opsional. */
@Entity(tableName = "blood_pressure_entries", indices = [Index("timeMillis")])
data class BloodPressureEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timeMillis: Long,
    val systolic: Int,
    val diastolic: Int,
    val pulse: Int? = null,
    val note: String? = null,
)
