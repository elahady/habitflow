package com.roziqrizal.habitflow.data.team

/** Anggota tim seperti dikembalikan server (tahap 26). */
data class TeamMember(val id: Long, val name: String, val avatar: String?)

data class RemoteTeam(val id: Long, val name: String, val members: List<TeamMember> = emptyList())

data class RemoteTeamTodo(
    val id: Long,
    val title: String,
    val assignedTo: Long?,
    val done: Boolean,
)

data class TeamInvite(val token: String, val url: String, val expiresAt: String)

sealed interface TeamApiResult<out T> {
    data class Success<T>(val value: T) : TeamApiResult<T>
    data class Rejected(val detail: String) : TeamApiResult<Nothing>
    /** Link undangan kedaluwarsa, sudah dipakai, atau tidak ditemukan (410/404 dari `acceptInvite`). */
    data class InviteInvalid(val detail: String) : TeamApiResult<Nothing>
    data object Unauthorized : TeamApiResult<Nothing>
    data object NotFound : TeamApiResult<Nothing>
    data class ServerError(val code: Int) : TeamApiResult<Nothing>
    data class NetworkError(val detail: String) : TeamApiResult<Nothing>

    val retryable: Boolean get() = this is NetworkError || this is ServerError
}
