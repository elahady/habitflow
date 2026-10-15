package com.roziqrizal.habitflow.domain.work

/**
 * Menggabungkan catatan harian kerja di HP ([local]) dengan versi server ([server]) saat server menjawab 409
 * (tahap 33 langkah 3c). Catatan EOD ditulis dari dua tempat (HP dan web), jadi tidak ada sisi yang menang diam-diam:
 *
 * - **Catatan**: sama atau salah satunya kosong, pakai yang berisi. Dua-duanya berisi dan beda, keduanya dipertahankan
 *   (server dulu, lalu milik HP, dipisah satu baris kosong), jadi tidak ada tulisan yang hilang. Catatan kosong di HP
 *   tidak menghapus catatan di server: menghapus lewat bentrok terlalu mudah merusak, dan bisa dilakukan dari web.
 * - **Waktu daily scrum dan EOD selesai**: yang lebih akhir. Menandai selesai hanya pernah bertambah.
 */
fun mergeWorkDay(local: WorkDay, server: WorkDay): WorkDay {
    require(local.date == server.date) { "Tanggal harus sama: ${local.date} vs ${server.date}" }
    return WorkDay(
        date = local.date,
        eodNote = mergeNotes(server.eodNote, local.eodNote),
        scrumDoneAt = latest(local.scrumDoneAt, server.scrumDoneAt),
        eodDoneAt = latest(local.eodDoneAt, server.eodDoneAt),
    )
}

private fun mergeNotes(server: String?, local: String?): String? {
    val s = server?.trim().orEmpty()
    val l = local?.trim().orEmpty()
    return when {
        s.isEmpty() && l.isEmpty() -> null
        s.isEmpty() -> l
        l.isEmpty() || s == l -> s
        else -> "$s\n\n$l"
    }
}

private fun latest(a: Long?, b: Long?): Long? = when {
    a == null -> b
    b == null -> a
    else -> maxOf(a, b)
}

/** Isi [this] dan [other] sama (tanpa melihat penanda sinkron), dipakai untuk melewati pengiriman yang tidak ada gunanya. */
fun WorkDay.sameContentAs(other: WorkDay): Boolean =
    date == other.date && eodNote?.trim().orEmpty() == other.eodNote?.trim().orEmpty() &&
        scrumDoneAt == other.scrumDoneAt && eodDoneAt == other.eodDoneAt
