package com.padanam.psc.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.padanam.psc.AppContainer
import com.padanam.psc.data.auth.AuthSession
import com.padanam.psc.data.remote.AdminSet
import com.padanam.psc.data.remote.AdminUser
import com.padanam.psc.data.remote.ServerException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AdminState(
    val loading: Boolean = true,
    val busy: Boolean = false,
    val error: String = "",
    val message: String = "",
    val users: List<AdminUser> = emptyList(),
    val sets: List<AdminSet> = emptyList(),
    val selectedUserId: String? = null,
    val selectedSetIds: Set<String> = emptySet()
) {
    val selectedUser: AdminUser? get() = users.firstOrNull { it.id == selectedUserId }
}

class AdminViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(AdminState())
    val state = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            val context = context() ?: return@launch
            _state.update { it.copy(loading = true, error = "") }
            try {
                val snapshot = container.serverApi.adminSnapshot(context.first, context.second.token)
                _state.update { current ->
                    val selected = current.selectedUserId
                        ?.takeIf { id -> snapshot.users.any { it.id == id && it.role != "admin" } }
                        ?: snapshot.users.firstOrNull { it.role != "admin" }?.id
                    current.copy(
                        loading = false,
                        busy = false,
                        error = "",
                        users = snapshot.users,
                        sets = snapshot.sets,
                        selectedUserId = selected
                    )
                }
            } catch (error: ServerException) {
                _state.update {
                    it.copy(
                        loading = false,
                        busy = false,
                        error = error.message ?: "Couldn't load the admin console."
                    )
                }
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        loading = false,
                        busy = false,
                        error = error.message ?: "Couldn't load the admin console."
                    )
                }
            }
        }
    }

    fun selectUser(userId: String) {
        _state.update { it.copy(selectedUserId = userId) }
    }

    fun toggleSet(setId: String) {
        _state.update { current ->
            val next = current.selectedSetIds.toMutableSet()
            if (!next.add(setId)) next.remove(setId)
            current.copy(selectedSetIds = next)
        }
    }

    fun createUser(username: String, displayName: String, password: String) {
        if (_state.value.busy) return
        if (username.isBlank() || displayName.isBlank() || password.isBlank()) {
            _state.update { it.copy(error = "Fill in the username, display name and password.") }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val context = context() ?: return@launch
            _state.update { it.copy(busy = true, error = "", message = "") }
            try {
                val user = container.serverApi.createUser(
                    context.first,
                    context.second.token,
                    username,
                    displayName,
                    password
                )
                _state.update {
                    it.copy(
                        busy = false,
                        message = "Created ${user.label} (@${user.username}).",
                        selectedUserId = user.id
                    )
                }
                refresh()
            } catch (error: ServerException) {
                _state.update {
                    it.copy(busy = false, error = error.message ?: "Couldn't create the account.")
                }
            } catch (error: Exception) {
                _state.update {
                    it.copy(busy = false, error = error.message ?: "Couldn't create the account.")
                }
            }
        }
    }

    fun resetPassword(userId: String, password: String) {
        if (_state.value.busy) return
        if (userId.isBlank()) {
            _state.update { it.copy(error = "Choose a learner first.") }
            return
        }
        if (password.length < 8) {
            _state.update { it.copy(error = "The new password must be at least 8 characters.") }
            return
        }
        val user = _state.value.users.firstOrNull { it.id == userId }
        viewModelScope.launch(Dispatchers.IO) {
            val context = context() ?: return@launch
            _state.update { it.copy(busy = true, error = "", message = "") }
            try {
                container.serverApi.resetPassword(
                    context.first,
                    context.second.token,
                    userId,
                    password
                )
                _state.update {
                    it.copy(
                        busy = false,
                        message = "Password updated for ${user?.label ?: "the learner"}. " +
                            "Share it privately; the old session stays valid until it expires."
                    )
                }
            } catch (error: ServerException) {
                _state.update {
                    it.copy(busy = false, error = error.message ?: "Couldn't reset the password.")
                }
            } catch (error: Exception) {
                _state.update {
                    it.copy(busy = false, error = error.message ?: "Couldn't reset the password.")
                }
            }
        }
    }

    fun assignSelectedSets() {
        if (_state.value.busy) return
        val userId = _state.value.selectedUserId
        val setIds = _state.value.selectedSetIds.toList()
        if (userId == null) {
            _state.update { it.copy(error = "Choose a learner first.") }
            return
        }
        if (setIds.isEmpty()) {
            _state.update { it.copy(error = "Select at least one set to assign.") }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val context = context() ?: return@launch
            _state.update { it.copy(busy = true, error = "", message = "") }
            try {
                val assigned = container.serverApi.assignSets(
                    context.first,
                    context.second.token,
                    userId,
                    setIds
                )
                _state.update {
                    it.copy(
                        busy = false,
                        message = "Assigned $assigned set${if (assigned == 1) "" else "s"}.",
                        selectedSetIds = emptySet()
                    )
                }
                refresh()
            } catch (error: ServerException) {
                _state.update {
                    it.copy(
                        busy = false,
                        error = error.message ?: "Couldn't assign those sets."
                    )
                }
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        busy = false,
                        error = error.message ?: "Couldn't assign those sets."
                    )
                }
            }
        }
    }

    fun syncLibrary() {
        if (_state.value.busy) return
        viewModelScope.launch(Dispatchers.IO) {
            val context = context() ?: return@launch
            val settings = container.settingsRepository.current()
            if (settings.serverUrl.isBlank()) {
                _state.update { it.copy(error = "Add the Padanam site address in Settings.") }
                return@launch
            }
            _state.update { it.copy(busy = true, error = "", message = "Syncing the library…") }
            val outcome = container.cloudSyncRepository.sync(
                settings.serverUrl,
                context.second,
                settings
            )
            _state.update {
                it.copy(
                    busy = false,
                    error = if (outcome.ok) "" else outcome.message,
                    message = if (outcome.ok) outcome.message else ""
                )
            }
            if (outcome.ok) {
                container.settingsRepository.markSynced(outcome.message)
                refresh()
            }
        }
    }

    private suspend fun context(): Pair<String, AuthSession>? {
        val session = container.authRepository.current()
        val settings = container.settingsRepository.current()
        if (session == null) {
            _state.update {
                it.copy(loading = false, busy = false, error = "Sign in as the administrator.")
            }
            return null
        }
        if (!session.user.isAdmin) {
            _state.update {
                it.copy(loading = false, busy = false, error = "Admin access is required.")
            }
            return null
        }
        if (settings.serverUrl.isBlank()) {
            _state.update {
                it.copy(loading = false, busy = false, error = "Add the Padanam site address in Settings.")
            }
            return null
        }
        return settings.serverUrl to session
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return AdminViewModel(container) as T
                }
            }
    }
}
