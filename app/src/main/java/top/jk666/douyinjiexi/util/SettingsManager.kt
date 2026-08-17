package top.jk666.douyinjiexi.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

object SettingsManager {

    private val API_ONE_ENABLED = booleanPreferencesKey("pref_api_one_enabled")
    private val API_TWO_ENABLED = booleanPreferencesKey("pref_api_two_enabled")
    private val API_THREE_ENABLED = booleanPreferencesKey("pref_api_three_enabled")
    private val API_FOUR_ENABLED = booleanPreferencesKey("pref_api_four_enabled")
    private val API_FIVE_ENABLED = booleanPreferencesKey("pref_api_five_enabled")
    private val API_SIX_ENABLED = booleanPreferencesKey("pref_api_six_enabled")
    private val DOWNLOAD_SUBPATH = stringPreferencesKey("pref_download_subpath")
    private val BACKGROUND_IMAGE = stringPreferencesKey("pref_background_image")

    fun getApiEnabledFlow(context: Context, index: Int): Flow<Boolean> {
        val key = apiKeyForIndex(index)
        return context.dataStore.data.map { prefs -> prefs[key] ?: true }
    }

    fun getAllApiEnabledFlow(context: Context): Flow<List<Boolean>> {
        return context.dataStore.data.map { prefs ->
            (1..6).map { index -> prefs[apiKeyForIndex(index)] ?: true }
        }
    }

    suspend fun setApiEnabled(context: Context, index: Int, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[apiKeyForIndex(index)] = enabled
        }
    }

    fun getDownloadSubpathFlow(context: Context): Flow<String> {
        return context.dataStore.data.map { prefs -> prefs[DOWNLOAD_SUBPATH] ?: "DouyinJieXi" }
    }

    suspend fun setDownloadSubpath(context: Context, subpath: String) {
        context.dataStore.edit { prefs ->
            prefs[DOWNLOAD_SUBPATH] = subpath
        }
    }

    suspend fun getApiEnabledList(context: Context): List<Boolean> {
        val prefs = context.dataStore.data.first()
        return (1..6).map { index -> prefs[apiKeyForIndex(index)] ?: true }
    }

    suspend fun getDownloadSubpath(context: Context): String {
        val prefs = context.dataStore.data.first()
        return prefs[DOWNLOAD_SUBPATH] ?: "DouyinJieXi"
    }

    fun getBackgroundImageFlow(context: Context): Flow<String> {
        return context.dataStore.data.map { prefs -> prefs[BACKGROUND_IMAGE] ?: "bg_main" }
    }

    suspend fun getBackgroundImage(context: Context): String {
        val prefs = context.dataStore.data.first()
        return prefs[BACKGROUND_IMAGE] ?: "bg_main"
    }

    suspend fun setBackgroundImage(context: Context, imageName: String) {
        context.dataStore.edit { prefs ->
            prefs[BACKGROUND_IMAGE] = imageName
        }
    }

    private fun apiKeyForIndex(index: Int): Preferences.Key<Boolean> {
        return when (index) {
            1 -> API_ONE_ENABLED
            2 -> API_TWO_ENABLED
            3 -> API_THREE_ENABLED
            4 -> API_FOUR_ENABLED
            5 -> API_FIVE_ENABLED
            6 -> API_SIX_ENABLED
            else -> API_ONE_ENABLED
        }
    }
}
