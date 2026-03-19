package com.wourd.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.wourd.app.domain.model.ThemeMode
import com.wourd.app.ui.navigation.WourdNavHost
import com.wourd.app.ui.theme.WourdTheme
import com.wourd.app.ui.viewmodel.SettingsViewModel

@Composable
fun WourdRoot() {
    val settingsVm: SettingsViewModel = hiltViewModel()
    val state by settingsVm.state.collectAsState()

    val darkTheme = when (state.ui.themeMode) {
        ThemeMode.Dark -> true
        ThemeMode.Light -> false
        ThemeMode.System -> isSystemInDarkTheme()
    }

    WourdTheme(darkTheme = darkTheme) {
        WourdNavHost(hasApiKey = state.api.apiKey.isNotBlank())
    }
}

