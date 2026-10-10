package com.roziqrizal.habitflow.domain.sync

/** Apa yang harus terjadi pada `habit_entries` hari ini saat pull dari server (tahap 28 langkah 5). */
sealed interface EntryMergeAction {
    data object None : EntryMergeAction
    data object Insert : EntryMergeAction
    data object Delete : EntryMergeAction
}

/**
 * Kotlin murni, testable tanpa Room: entry hari ini disamakan dengan status `done` dari server,
 * kecuali ada toggle lokal yang belum terkirim ([hasPendingOutbox]) - state lokal menang karena
 * belum sempat dikirim, akan disamakan sendiri oleh push berikutnya. [remoteDone] null berarti
 * server tidak mengirim status (habit baru ditemukan di pull yang sama, belum relevan untuk tanggal ini).
 */
fun entryMergeAction(remoteDone: Boolean?, hasLocalEntry: Boolean, hasPendingOutbox: Boolean): EntryMergeAction = when {
    remoteDone == null || hasPendingOutbox -> EntryMergeAction.None
    remoteDone && !hasLocalEntry -> EntryMergeAction.Insert
    !remoteDone && hasLocalEntry -> EntryMergeAction.Delete
    else -> EntryMergeAction.None
}
