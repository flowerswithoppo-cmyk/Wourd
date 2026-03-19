package com.wourd.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wourd.app.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class AppUiState(
    val hasApiKey: Boolean = false,
    val darkTheme: Boolean = false,
)

@HiltViewModel
class AppStateViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
) : ViewModel() {
    val state: StateFlow<AppUiState> = combine(
        settingsRepository.apiSettings,
        settingsRepository.uiSettings,
    ) { api, ui ->
        val isDark = when (ui.themeMode) {
            com.wourd.app.domain.model.ThemeMode.Dark -> true
            com.wourd.app.domain.model.ThemeMode.Light -> false
            com.wourd.app.domain.model.ThemeMode.System -> false // resolved in UI via system setting
        }
        AppUiState(
            hasApiKey = api.apiKey.isNotBlank(),
            darkTheme = isDark,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppUiState())
}

