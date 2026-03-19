package com.wourd.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wourd.app.domain.model.ThemeMode
import com.wourd.app.domain.repository.ApiSettings
import com.wourd.app.domain.repository.SettingsRepository
import com.wourd.app.domain.repository.UiSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val api: ApiSettings = ApiSettings(
        baseUrl = "https://api.openai.com/v1",
        apiKey = "",
        model = "gpt-4o-mini",
        systemPrompt = "",
        defaultThinkingMode = false,
    ),
    val ui: UiSettings = UiSettings(themeMode = ThemeMode.System),
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val state: StateFlow<SettingsUiState> = combine(
        settingsRepository.apiSettings,
        settingsRepository.uiSettings,
    ) { api, ui -> SettingsUiState(api = api, ui = ui) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setBaseUrl(v: String) = viewModelScope.launch { settingsRepository.setBaseUrl(v) }
    fun setApiKey(v: String) = viewModelScope.launch { settingsRepository.setApiKey(v) }
    fun setModel(v: String) = viewModelScope.launch { settingsRepository.setModel(v) }
    fun setSystemPrompt(v: String) = viewModelScope.launch { settingsRepository.setSystemPrompt(v) }
    fun setThemeMode(v: ThemeMode) = viewModelScope.launch { settingsRepository.setThemeMode(v) }
    fun setDefaultThinkingMode(v: Boolean) = viewModelScope.launch { settingsRepository.setDefaultThinkingMode(v) }
}

