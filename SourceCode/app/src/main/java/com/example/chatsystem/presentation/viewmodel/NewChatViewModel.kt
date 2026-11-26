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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NewChatUiState(
    val query: String = "",
    val results: List<UserDto>? = null, // null = lan tim kiem dau tien van dang chay
    val loading: Boolean = false,
    val error: String? = null,
    val openingUserId: Long? = null,
)

/** Chon nguoi dung de bat dau chat 1-1. Tim kiem co debounce de tranh goi request lien tuc theo tung phim go. */
@OptIn(FlowPreview::class)
class NewChatViewModel(private val repo: ChatRepository) : ViewModel() {
    private val _rawState = MutableStateFlow(NewChatUiState())

    val state: StateFlow<NewChatUiState> = combine(_rawState, repo.onlineUserIds()) { s, onlineIds ->
        val onlineSet = onlineIds.toSet()
        val mappedResults = s.results?.map { user ->
            user.copy(online = user.id in onlineSet) // khong dung user.online cua lan tai REST: event presence offline phai ghi de duoc
        }
        s.copy(results = mappedResults)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NewChatUiState())

    init {
        viewModelScope.launch {
            // distinctUntilChanged: cap nhat loading/results cung phat ra state nhung khong duoc kich hoat tim kiem lai
            _rawState.map { it.query.trim() }.distinctUntilChanged().debounce(300).collectLatest { q ->
                _rawState.update { it.copy(loading = true) }
                val r = repo.searchUsers(q)
                _rawState.update {
                    it.copy(loading = false, results = r.getOrNull() ?: it.results, error = r.exceptionOrNull()?.toUserMessage())
                }
            }
        }
    }

    fun onQuery(q: String) = _rawState.update { it.copy(query = q) }

    fun open(userId: Long, onOpened: (conversationId: Long) -> Unit) {
        if (_rawState.value.openingUserId != null) return
        _rawState.update { it.copy(openingUserId = userId, error = null) }
        viewModelScope.launch {
            val r = repo.openDirect(userId)
            _rawState.update { it.copy(openingUserId = null, error = r.exceptionOrNull()?.toUserMessage()) }
            r.onSuccess(onOpened)
        }
    }
}
