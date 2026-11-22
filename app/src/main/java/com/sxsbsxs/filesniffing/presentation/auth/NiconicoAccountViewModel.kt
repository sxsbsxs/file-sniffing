package com.sxsbsxs.filesniffing.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sxsbsxs.filesniffing.data.auth.NiconicoCredentialStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class NiconicoAccountUiState(
    val accountName: String? = null,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null
) {
    val hasCredentials: Boolean
        get() = !accountName.isNullOrBlank()
}

@HiltViewModel
class NiconicoAccountViewModel @Inject constructor(
    private val credentialStore: NiconicoCredentialStore
) : ViewModel() {
    private val _uiState = MutableStateFlow(NiconicoAccountUiState())
    val uiState = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val accountName = withContext(Dispatchers.IO) {
                credentialStore.load()?.username
            }
            _uiState.value = _uiState.value.copy(
                accountName = accountName,
                isLoading = false
            )
        }
    }

    fun save(username: String, password: String) {
        if (username.isBlank() || password.isEmpty()) {
            _uiState.value = _uiState.value.copy(errorMessage = "请填写 Niconico 账号和密码")
            return
        }
        if (_uiState.value.isSaving) return

        _uiState.value = _uiState.value.copy(
            isSaving = true,
            statusMessage = null,
            errorMessage = null
        )
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    credentialStore.save(username, password)
                    credentialStore.load()?.username
                }
            }.onSuccess { accountName ->
                _uiState.value = NiconicoAccountUiState(
                    accountName = accountName,
                    isLoading = false,
                    statusMessage = "账号已加密保存在本机。现在可以重新解析 Niconico 链接。"
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    errorMessage = error.message ?: "保存账号失败，请重试"
                )
            }
        }
    }

    fun remove() {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { credentialStore.clear() }
            }.onSuccess {
                _uiState.value = NiconicoAccountUiState(
                    isLoading = false,
                    statusMessage = "已移除本机保存的 Niconico 账号"
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    errorMessage = error.message ?: "移除账号失败，请重试"
                )
            }
        }
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(
            statusMessage = null,
            errorMessage = null
        )
    }
}
