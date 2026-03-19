package com.wourd.app.data.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.wourd.app.domain.model.ThemeMode
import com.wourd.app.domain.repository.ApiSettings
import com.wourd.app.domain.repository.SettingsRepository
import com.wourd.app.domain.repository.UiSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "wourd_settings")

class SettingsDataStore(
    private val appContext: Context,
) : SettingsRepository {

    private object Keys {
        val baseUrl = stringPreferencesKey("base_url")
        val apiKey = stringPreferencesKey("api_key")
        val model = stringPreferencesKey("model")
        val systemPrompt = stringPreferencesKey("system_prompt")
        val themeMode = stringPreferencesKey("theme_mode")
        val defaultThinkingMode = booleanPreferencesKey("default_thinking_mode")
    }

    override val apiSettings: Flow<ApiSettings> = appContext.dataStore.data.map { prefs ->
        ApiSettings(
            baseUrl = prefs[Keys.baseUrl] ?: "https://api.openai.com/v1",
            apiKey = prefs[Keys.apiKey] ?: "",
            model = prefs[Keys.model] ?: "gpt-4o-mini",
            systemPrompt = prefs[Keys.systemPrompt] ?: "",
            defaultThinkingMode = prefs[Keys.defaultThinkingMode] ?: false,
        )
    }

    override val uiSettings: Flow<UiSettings> = appContext.dataStore.data.map { prefs ->
        val mode = when (prefs[Keys.themeMode]) {
            "Light" -> ThemeMode.Light
            "Dark" -> ThemeMode.Dark
            else -> ThemeMode.System
        }
        UiSettings(themeMode = mode)
    }

    override suspend fun setBaseUrl(value: String) = set(Keys.baseUrl, value.trim().ifEmpty { "https://api.openai.com/v1" })
    override suspend fun setApiKey(value: String) = set(Keys.apiKey, value.trim())
    override suspend fun setModel(value: String) = set(Keys.model, value.trim().ifEmpty { "gpt-4o-mini" })
    override suspend fun setSystemPrompt(value: String) = set(Keys.systemPrompt, value)
    override suspend fun setThemeMode(mode: ThemeMode) = set(Keys.themeMode, mode.name)
    override suspend fun setDefaultThinkingMode(enabled: Boolean) = set(Keys.defaultThinkingMode, enabled)

    private suspend fun set(key: Preferences.Key<String>, value: String) {
        appContext.dataStore.edit { it[key] = value }
    }

    private suspend fun set(key: Preferences.Key<Boolean>, value: Boolean) {
        appContext.dataStore.edit { it[key] = value }
    }
}

