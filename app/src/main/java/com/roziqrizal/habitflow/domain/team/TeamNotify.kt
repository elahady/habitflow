package com.roziqrizal.habitflow.domain.team

/** Bagian dari to-do tim yang relevan untuk keputusan notifikasi (tahap 26 langkah 4). */
data class TeamTodoAssignment(val id: Long, val title: String, val assignedTo: Long?, val done: Boolean)

/** Kunci dipakai untuk menandai "sudah dinotifikasi" - berubah kalau tugas yang sama ditugaskan ulang ke orang yang sama. */
fun assignmentKey(id: Long, assignedTo: Long): String = "$id:$assignedTo"

/**
 * To-do yang baru ditugaskan ke [myUserId] dan belum pernah dinotifikasi ([alreadyNotified]), dipakai
 * [com.roziqrizal.habitflow.data.team.TeamPollWorker] untuk memutuskan notifikasi senyap mana yang
 * perlu ditampilkan. To-do yang sudah selesai, belum ditugaskan, atau ditugaskan ke orang lain tidak
 * pernah dianggap baru.
 */
fun newlyAssignedTodos(
    todos: List<TeamTodoAssignment>,
    myUserId: Long,
    alreadyNotified: Set<String>,
): List<TeamTodoAssignment> = todos.filter { todo ->
    todo.assignedTo == myUserId && !todo.done && assignmentKey(todo.id, myUserId) !in alreadyNotified
}
