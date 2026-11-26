package com.example.chatsystem.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chat.protocol.UserDto
import com.example.chatsystem.data.remote.toUserMessage
import com.example.chatsystem.data.repository.ChatRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

const val MAX_GROUP_MEMBERS = 100 // theo sach: nhom chat nho toi da 100 nguoi (server cung kiem tra)

data class GroupEditorUiState(
    val creating: Boolean,                 // true = tao nhom moi, false = them thanh vien vao [conversationId]
    val name: String = "",
    val query: String = "",
    val results: List<UserDto>? = null,    // ket qua tim kiem loai tru nhung nguoi da o trong nhom
    val selected: List<UserDto> = emptyList(),
    val existingCount: Int = 0,
    val saving: Boolean = false,
    val error: String? = null,
) {
    val total: Int get() = existingCount + selected.size + if (creating) 1 else 0 // +1: chinh minh do server tu dong them
    val canSave: Boolean
        get() = !saving && selected.isNotEmpty() && total <= MAX_GROUP_MEMBERS
}

/** Tao nhom moi hoac them thanh vien vao nhom co san ([conversationId] != null). */
@OptIn(FlowPreview::class)
class GroupEditorViewModel(private val repo: ChatRepository, private val conversationId: Long?) : ViewModel() {
    private val form = MutableStateFlow(GroupEditorUiState(creating = conversationId == null))
    private val existingIds = conversationId?.let { id -> repo.members(id).map { m -> m.map { it.userId }.toSet() } } ?: flowOf(emptySet())

    val state: StateFlow<GroupEditorUiState> = combine(form, existingIds) { f, existing ->
        f.copy(results = f.results?.filter { it.id !in existing }, existingCount = existing.size)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), form.value)

    init {
        viewModelScope.launch {
            form.map { it.query.trim() }.distinctUntilChanged().debounce(300).collectLatest { q ->
                val r = repo.searchUsers(q)
                form.update { it.copy(results = r.getOrNull() ?: it.results, error = r.exceptionOrNull()?.toUserMessage()) }
            }
        }
    }

    fun onName(v: String) = form.update { it.copy(name = v.take(64), error = null) }
    fun onQuery(v: String) = form.update { it.copy(query = v) }

    fun toggle(user: UserDto) = form.update { s ->
        s.copy(selected = if (s.selected.any { it.id == user.id }) s.selected.filter { it.id != user.id } else s.selected + user)
    }

    fun save(onDone: (conversationId: Long) -> Unit) {
        val s = state.value
        if (!s.canSave) return
        form.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            val ids = s.selected.map { it.id }
            val fallbackName = s.selected.joinToString(", ") { it.displayName }.take(64)
            val groupName = s.name.trim().ifBlank { fallbackName.ifBlank { "Nhóm mới" } }
            val result = if (conversationId == null) repo.createGroup(groupName, ids)
            else repo.addMembers(conversationId, ids).map { conversationId }
            form.update { it.copy(saving = false, error = result.exceptionOrNull()?.toUserMessage()) }
            result.onSuccess(onDone)
        }
    }
}
