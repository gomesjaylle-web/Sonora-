package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.SortOrder
import com.example.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.sonoraDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "sonora_preferences"
)

data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val includeShortAudio: Boolean = false,
    val sortOrder: SortOrder = SortOrder.TITLE_ASC,
    val hasRequestedAudioPermission: Boolean = false,
    val isSupporter: Boolean = false
)

class PreferencesRepository(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val INCLUDE_SHORT_AUDIO = booleanPreferencesKey("include_short_audio")
        val SORT_ORDER = stringPreferencesKey("sort_order")
        val HAS_REQUESTED_AUDIO_PERMISSION = booleanPreferencesKey("has_requested_audio_permission")
        val IS_SUPPORTER = booleanPreferencesKey("is_supporter")
    }

    val preferences: Flow<UserPreferences> = context.sonoraDataStore.data.map { prefs ->
        val themeMode = prefs[Keys.THEME_MODE]?.let { name ->
            ThemeMode.entries.firstOrNull { it.name == name }
        } ?: ThemeMode.DARK

        val sortOrder = prefs[Keys.SORT_ORDER]?.let { name ->
            SortOrder.entries.firstOrNull { it.name == name }
        } ?: SortOrder.TITLE_ASC

        val includeShortAudio = prefs[Keys.INCLUDE_SHORT_AUDIO] ?: false
        val hasRequestedAudioPermission = prefs[Keys.HAS_REQUESTED_AUDIO_PERMISSION] ?: false
        val isSupporter = prefs[Keys.IS_SUPPORTER] ?: false

        UserPreferences(
            themeMode = themeMode,
            includeShortAudio = includeShortAudio,
            sortOrder = sortOrder,
            hasRequestedAudioPermission = hasRequestedAudioPermission,
            isSupporter = isSupporter
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.sonoraDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setIncludeShortAudio(include: Boolean) {
        context.sonoraDataStore.edit { it[Keys.INCLUDE_SHORT_AUDIO] = include }
    }

    suspend fun setSortOrder(order: SortOrder) {
        context.sonoraDataStore.edit { it[Keys.SORT_ORDER] = order.name }
    }

    suspend fun setHasRequestedAudioPermission(requested: Boolean) {
        context.sonoraDataStore.edit { it[Keys.HAS_REQUESTED_AUDIO_PERMISSION] = requested }
    }

    suspend fun setSupporter(isSupporter: Boolean) {
        context.sonoraDataStore.edit { it[Keys.IS_SUPPORTER] = isSupporter }
    }
}
