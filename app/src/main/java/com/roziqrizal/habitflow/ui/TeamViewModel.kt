package com.roziqrizal.habitflow.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roziqrizal.habitflow.data.account.Account
import com.roziqrizal.habitflow.data.account.AccountSettings
import com.roziqrizal.habitflow.data.team.RemoteTeam
import com.roziqrizal.habitflow.data.team.RemoteTeamTodo
import com.roziqrizal.habitflow.data.team.TeamApiResult
import com.roziqrizal.habitflow.data.team.TeamClient
import com.roziqrizal.habitflow.data.team.TeamSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TeamUiState(
    val account: Account?,
    val loading: Boolean = false,
    val team: RemoteTeam? = null,
    val todos: List<RemoteTeamTodo> = emptyList(),
    val busy: Boolean = false,
    val error: String? = null,
    val inviteUrl: String? = null,
)

sealed interface JoinInviteState {
    data object InProgress : JoinInviteState
    data class Success(val teamName: String) : JoinInviteState
    data class Failed(val message: String) : JoinInviteState
}

/** Tab "Tugas Rumah" (tahap 26): to-do tim dengan polling, bukan snapshot. Butuh login Google dulu. */
class TeamViewModel(
    private val accountSettings: AccountSettings,
    private val teamSettings: TeamSettings,
) : ViewModel() {

    private val _state = MutableStateFlow(TeamUiState(account = accountSettings.account.value))
    val state: StateFlow<TeamUiState> = _state.asStateFlow()

    private val _joinState = MutableStateFlow<JoinInviteState?>(null)
    val joinState: StateFlow<JoinInviteState?> = _joinState.asStateFlow()

    init {
        viewModelScope.launch {
            accountSettings.account.collectLatest { account ->
                _state.update { it.copy(account = account) }
                if (account != null) refresh() else _state.update { it.copy(team = null, todos = emptyList()) }
            }
        }
    }

    private fun client(): TeamClient? = accountSettings.account.value?.let { TeamClient(it.token) }

    fun refresh() {
        val client = client() ?: return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            when (val result = client.listTeams()) {
                is TeamApiResult.Success -> {
                    val team = result.value.firstOrNull()
                    team?.let { teamSettings.setActiveTeam(it.id) }
                    val todos = team?.let { loadTodos(client, it.id) } ?: emptyList()
                    _state.update { it.copy(loading = false, team = team, todos = todos) }
                }
                else -> _state.update { it.copy(loading = false, error = describeError(result)) }
            }
        }
    }

    private suspend fun loadTodos(client: TeamClient, teamId: Long): List<RemoteTeamTodo> =
        when (val result = client.listTodos(teamId)) {
            is TeamApiResult.Success -> result.value
            else -> emptyList()
        }

    private fun refreshTodosQuiet() {
        val client = client() ?: return
        val teamId = _state.value.team?.id ?: return
        viewModelScope.launch {
            val todos = loadTodos(client, teamId)
            _state.update { it.copy(todos = todos, error = null) }
        }
    }

    fun createTeam(name: String) {
        val client = client() ?: return
        if (name.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            when (val result = client.createTeam(name.trim())) {
                is TeamApiResult.Success -> {
                    teamSettings.setActiveTeam(result.value.id)
                    _state.update { it.copy(busy = false, team = result.value, todos = emptyList()) }
                }
                else -> _state.update { it.copy(busy = false, error = describeError(result)) }
            }
        }
    }

    fun createInvite() {
        val client = client() ?: return
        val teamId = _state.value.team?.id ?: return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            when (val result = client.createInvite(teamId)) {
                is TeamApiResult.Success -> _state.update { it.copy(busy = false, inviteUrl = result.value.url) }
                else -> _state.update { it.copy(busy = false, error = describeError(result)) }
            }
        }
    }

    fun clearInviteUrl() = _state.update { it.copy(inviteUrl = null) }

    fun addTodo(title: String) {
        val client = client() ?: return
        val teamId = _state.value.team?.id ?: return
        if (title.isBlank()) return
        viewModelScope.launch {
            when (val result = client.createTodo(teamId, title.trim(), assignedTo = null)) {
                is TeamApiResult.Success -> refreshTodosQuiet()
                else -> _state.update { it.copy(error = describeError(result)) }
            }
        }
    }

    fun setDone(todo: RemoteTeamTodo, done: Boolean) {
        val client = client() ?: return
        viewModelScope.launch {
            when (val result = client.setDone(todo.id, done)) {
                is TeamApiResult.Success -> refreshTodosQuiet()
                else -> _state.update { it.copy(error = describeError(result)) }
            }
        }
    }

    fun assign(todo: RemoteTeamTodo, memberId: Long?) {
        val client = client() ?: return
        viewModelScope.launch {
            when (val result = client.assign(todo.id, memberId)) {
                is TeamApiResult.Success -> refreshTodosQuiet()
                else -> _state.update { it.copy(error = describeError(result)) }
            }
        }
    }

    fun deleteTodo(todo: RemoteTeamTodo) {
        val client = client() ?: return
        viewModelScope.launch {
            when (val result = client.deleteTodo(todo.id)) {
                is TeamApiResult.Success -> refreshTodosQuiet()
                else -> _state.update { it.copy(error = describeError(result)) }
            }
        }
    }

    /** Terima undangan dari deep link (tahap 26 langkah 2). Butuh sudah login - panggilan dari UI menunggu akun ada. */
    fun acceptInvite(token: String) {
        val account = accountSettings.account.value
        if (account == null) {
            val message = "Masuk dengan Google dulu untuk bisa bergabung ke tim."
            _joinState.value = JoinInviteState.Failed(message)
            _state.update { it.copy(error = message) }
            return
        }
        _joinState.value = JoinInviteState.InProgress
        viewModelScope.launch {
            when (val result = TeamClient(account.token).acceptInvite(token)) {
                is TeamApiResult.Success -> {
                    teamSettings.setActiveTeam(result.value.id)
                    _joinState.value = JoinInviteState.Success(result.value.name)
                    refresh()
                }
                is TeamApiResult.InviteInvalid -> {
                    val message = result.detail.ifBlank { "Link undangan sudah tidak berlaku." }
                    _joinState.value = JoinInviteState.Failed(message)
                    _state.update { it.copy(error = message) }
                }
                else -> {
                    val message = describeError(result)
                    _joinState.value = JoinInviteState.Failed(message)
                    _state.update { it.copy(error = message) }
                }
            }
        }
    }

    fun dismissJoin() {
        _joinState.value = null
    }

    private fun describeError(result: TeamApiResult<*>): String = when (result) {
        is TeamApiResult.Rejected -> result.detail.ifBlank { "Ditolak server." }
        is TeamApiResult.InviteInvalid -> result.detail.ifBlank { "Link undangan sudah tidak berlaku." }
        TeamApiResult.Unauthorized -> "Sesi berakhir, masuk lagi."
        TeamApiResult.NotFound -> "Tidak ditemukan."
        is TeamApiResult.ServerError -> "Server bermasalah (kode ${result.code})."
        is TeamApiResult.NetworkError -> "Tidak bisa menjangkau server: ${result.detail}"
        is TeamApiResult.Success -> ""
    }
}
