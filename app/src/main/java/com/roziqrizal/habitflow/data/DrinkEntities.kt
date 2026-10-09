package com.roziqrizal.habitflow.data

import androidx.room.Entity

/** Penghitung minuman per tanggal dan jenis. Satu baris = jumlah gelas hari itu (tahap 20). */
@Entity(tableName = "drink_counts", primaryKeys = ["date", "kind"])
data class DrinkCount(
    val date: String,
    val kind: String,
    val count: Int,
)

/** Jenis minuman. Tahap 23 menambah kopi dan minuman manis di tabel yang sama. */
object DrinkKind {
    const val WATER = "WATER"
}
