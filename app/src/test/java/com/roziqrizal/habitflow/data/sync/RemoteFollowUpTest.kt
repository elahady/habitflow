package com.roziqrizal.habitflow.data.sync

import com.roziqrizal.habitflow.data.FollowUpEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class RemoteFollowUpTest {

    private val remote = RemoteFollowUp(
        id = 7, title = "Dari web", status = "WAITING", date = "2026-10-12", time = "09:30", person = "Sari", note = "tunggu",
        doneAt = null, pickedDate = null, eventId = null, eventDate = null, eventTitle = null,
        createdAt = 1_791_680_400_000, updatedAt = "2026-10-11T02:00:00.000000Z",
    )

    @Test
    fun jsonBolakBalikMengembalikanDataSama() {
        assertEquals(remote, RemoteFollowUp.fromJson(remote.toJson()))
        val done = remote.copy(doneAt = 1_791_690_000_000, eventId = 4, eventDate = "2026-10-10", eventTitle = "Meeting")
        assertEquals(done, RemoteFollowUp.fromJson(done.toJson()))
    }

    @Test
    fun salinanBaruTidakDirtyDanMembawaIdServer() {
        val entity = remote.toNewEntity()
        assertEquals(7L, entity.remoteId)
        assertEquals("2026-10-11T02:00:00.000000Z", entity.remoteUpdatedAt)
        assertFalse(entity.dirty)
        assertNull(entity.conflictJson)
        assertEquals(remote.createdAt, entity.createdAt)
    }

    @Test
    fun menimpaBarisLokalMempertahankanIdDanWaktuDibuatDiHp() {
        val local = FollowUpEntity(
            id = 3, title = "Lama", status = "INBOX", date = null, time = null, person = null, note = null,
            createdAt = 111, doneAt = null, pickedDate = null, remoteId = 7, remoteUpdatedAt = "lama", dirty = true,
            conflictJson = "{}",
        )
        val result = remote.applyTo(local)
        assertEquals(3L, result.id)
        assertEquals(111L, result.createdAt)
        assertEquals("Dari web", result.title)
        assertEquals("WAITING", result.status)
        assertEquals("2026-10-11T02:00:00.000000Z", result.remoteUpdatedAt)
        assertFalse(result.dirty)
        assertNull(result.conflictJson)
    }

    @Test
    fun versiServerBisaDitampilkanSebagaiFollowUpBiasaDenganIdLokal() {
        val domain = remote.toDomain(localId = 3)
        assertEquals(3L, domain.id)
        assertEquals("Dari web", domain.title)
        assertEquals("Sari", domain.person)
    }
}
