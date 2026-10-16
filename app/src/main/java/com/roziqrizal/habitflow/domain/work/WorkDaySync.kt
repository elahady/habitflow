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
    val counts = mergeCounts(local, server)
    return WorkDay(
        date = local.date,
        eodNote = mergeNotes(server.eodNote, local.eodNote),
        scrumDoneAt = latest(local.scrumDoneAt, server.scrumDoneAt),
        eodDoneAt = latest(local.eodDoneAt, server.eodDoneAt),
        eodDoneCount = counts.first,
        eodTotalCount = counts.second,
    )
}

/**
 * Angka ringkasan EOD ikut EOD yang lebih akhir (angka itu milik satu kali EOD, jadi tidak digabung per bidang). Seri jatuh ke HP.
 * Kalau yang terpilih tidak punya angka, dipakai milik sisi lain.
 */
private fun mergeCounts(local: WorkDay, server: WorkDay): Pair<Int?, Int?> {
    val serverNewer = (server.eodDoneAt ?: Long.MIN_VALUE) > (local.eodDoneAt ?: Long.MIN_VALUE)
    val (first, second) = if (serverNewer) server to local else local to server
    val chosen = first.takeIf { it.eodTotalCount != null } ?: second
    return chosen.eodDoneCount to chosen.eodTotalCount
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
        scrumDoneAt == other.scrumDoneAt && eodDoneAt == other.eodDoneAt &&
        eodDoneCount == other.eodDoneCount && eodTotalCount == other.eodTotalCount
