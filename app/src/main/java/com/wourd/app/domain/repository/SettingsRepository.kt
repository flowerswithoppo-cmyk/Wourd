package com.wourd.app.domain.repository

import com.wourd.app.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow

data class ApiSettings(
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val systemPrompt: String,
    val defaultThinkingMode: Boolean,
)

data class UiSettings(
    val themeMode: ThemeMode,
)

interface SettingsRepository {
    val apiSettings: Flow<ApiSettings>
    val uiSettings: Flow<UiSettings>

    suspend fun setBaseUrl(value: String)
    suspend fun setApiKey(value: String)
    suspend fun setModel(value: String)
    suspend fun setSystemPrompt(value: String)
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setDefaultThinkingMode(enabled: Boolean)
}

