package com.roziqrizal.habitflow.domain.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class HabitSyncMergeTest {

    @Test
    fun remoteDoneTanpaEntryLokalMenghasilkanInsert() {
        assertEquals(EntryMergeAction.Insert, entryMergeAction(remoteDone = true, hasLocalEntry = false, hasPendingOutbox = false))
    }

    @Test
    fun remoteTidakDoneDenganEntryLokalMenghasilkanDelete() {
        assertEquals(EntryMergeAction.Delete, entryMergeAction(remoteDone = false, hasLocalEntry = true, hasPendingOutbox = false))
    }

    @Test
    fun sudahSelarasTidakMenghasilkanApaApa() {
        assertEquals(EntryMergeAction.None, entryMergeAction(remoteDone = true, hasLocalEntry = true, hasPendingOutbox = false))
        assertEquals(EntryMergeAction.None, entryMergeAction(remoteDone = false, hasLocalEntry = false, hasPendingOutbox = false))
    }

    @Test
    fun remoteDoneNullTidakMenghasilkanApaApa() {
        assertEquals(EntryMergeAction.None, entryMergeAction(remoteDone = null, hasLocalEntry = false, hasPendingOutbox = false))
        assertEquals(EntryMergeAction.None, entryMergeAction(remoteDone = null, hasLocalEntry = true, hasPendingOutbox = false))
    }

    @Test
    fun toggleLokalTertundaMengalahkanStatusServer() {
        // Lokal menang: toggle belum terkirim, jangan ditimpa walau server bilang beda.
        assertEquals(EntryMergeAction.None, entryMergeAction(remoteDone = true, hasLocalEntry = false, hasPendingOutbox = true))
        assertEquals(EntryMergeAction.None, entryMergeAction(remoteDone = false, hasLocalEntry = true, hasPendingOutbox = true))
    }
}
