package com.roziqrizal.habitflow.data

import androidx.room.Entity

/** Penghitung minuman per tanggal dan jenis. Satu baris = jumlah gelas hari itu (tahap 20). */
@Entity(tableName = "drink_counts", primaryKeys = ["date", "kind"])
data class DrinkCount(
    val date: String,
    val kind: String,
    val count: Int,
)

/** Jenis minuman: air (tahap 20), kopi dan minuman manis (tahap 23) di tabel yang sama. */
object DrinkKind {
    const val WATER = "WATER"
    const val COFFEE = "COFFEE"
    const val SWEET = "SWEET"
}
