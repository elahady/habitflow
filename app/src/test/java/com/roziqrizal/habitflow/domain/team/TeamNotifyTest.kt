package com.roziqrizal.habitflow.domain.team

import org.junit.Assert.assertEquals
import org.junit.Test

class TeamNotifyTest {

    private fun todo(id: Long, assignedTo: Long?, done: Boolean = false) =
        TeamTodoAssignment(id, "Tugas $id", assignedTo, done)

    @Test
    fun `tugas yang baru ditugaskan ke saya dianggap baru`() {
        val todos = listOf(todo(1, assignedTo = 10))
        val result = newlyAssignedTodos(todos, myUserId = 10, alreadyNotified = emptySet())
        assertEquals(listOf(1L), result.map { it.id })
    }

    @Test
    fun `tugas yang belum ditugaskan tidak dianggap baru`() {
        val todos = listOf(todo(1, assignedTo = null))
        val result = newlyAssignedTodos(todos, myUserId = 10, alreadyNotified = emptySet())
        assertEquals(emptyList<Long>(), result.map { it.id })
    }

    @Test
    fun `tugas yang ditugaskan ke orang lain tidak dianggap baru`() {
        val todos = listOf(todo(1, assignedTo = 99))
        val result = newlyAssignedTodos(todos, myUserId = 10, alreadyNotified = emptySet())
        assertEquals(emptyList<Long>(), result.map { it.id })
    }

    @Test
    fun `tugas yang sudah selesai tidak dianggap baru walau ditugaskan ke saya`() {
        val todos = listOf(todo(1, assignedTo = 10, done = true))
        val result = newlyAssignedTodos(todos, myUserId = 10, alreadyNotified = emptySet())
        assertEquals(emptyList<Long>(), result.map { it.id })
    }

    @Test
    fun `tugas yang sudah dinotifikasi tidak diulang`() {
        val todos = listOf(todo(1, assignedTo = 10))
        val result = newlyAssignedTodos(todos, myUserId = 10, alreadyNotified = setOf(assignmentKey(1, 10)))
        assertEquals(emptyList<Long>(), result.map { it.id })
    }

    @Test
    fun `ditugaskan ulang ke saya setelah sebelumnya ke orang lain dianggap baru lagi`() {
        // Kunci notifikasi sebelumnya untuk id 1 adalah assignmentKey(1, 99), bukan assignmentKey(1, 10).
        val todos = listOf(todo(1, assignedTo = 10))
        val result = newlyAssignedTodos(todos, myUserId = 10, alreadyNotified = setOf(assignmentKey(1, 99)))
        assertEquals(listOf(1L), result.map { it.id })
    }
}
