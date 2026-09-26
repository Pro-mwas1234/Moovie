package com.Moovie.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "moovie_prefs")

data class AppPrefs(
    val onboarded: Boolean = false,
    val favoriteGenres: Set<String> = emptySet(),
    val darkMode: Boolean = true,
    val region: String = "US",
    val recentSearches: List<String> = emptyList(),
    val trendingSearches: Set<String> = emptySet(),
)

class PrefsRepository(private val context: Context) {

    private object Keys {
        val ONBOARDED = booleanPreferencesKey("onboarded")
        val FAV_GENRES = stringSetPreferencesKey("fav_genres")
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val REGION = stringPreferencesKey("region")
        val RECENTS = stringSetPreferencesKey("recent_searches")
        val TRENDING_SEARCHES = stringSetPreferencesKey("trending_searches")
    }

    val prefs: Flow<AppPrefs> = context.dataStore.data.map { p ->
        AppPrefs(
            onboarded = p[Keys.ONBOARDED] ?: false,
            favoriteGenres = p[Keys.FAV_GENRES] ?: emptySet(),
            darkMode = p[Keys.DARK_MODE] ?: true,
            region = p[Keys.REGION] ?: "US",
            recentSearches = (p[Keys.RECENTS] ?: emptySet()).toList(),
            trendingSearches = p[Keys.TRENDING_SEARCHES] ?: emptySet(),
        )
    }

    suspend fun setOnboarded(genreIds: Set<String>, region: String) {
        context.dataStore.edit { p ->
            p[Keys.ONBOARDED] = true
            p[Keys.FAV_GENRES] = genreIds
            p[Keys.REGION] = region
        }
    }

    suspend fun setDarkMode(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DARK_MODE] = enabled }
    }

    suspend fun setRegion(region: String) {
        context.dataStore.edit { it[Keys.REGION] = region }
    }

    suspend fun addRecentSearch(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        context.dataStore.edit { p ->
            val current = (p[Keys.RECENTS] ?: emptySet()).toMutableSet()
            current.add(q)
            p[Keys.RECENTS] = current
        }
    }

    suspend fun clearRecentSearches() {
        context.dataStore.edit { it.remove(Keys.RECENTS) }
    }

    suspend fun addTrendingSearch(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        context.dataStore.edit { p ->
            val counts = (p[Keys.TRENDING_SEARCHES] ?: emptySet()).toMutableSet()
            counts.add(q)
            p[Keys.TRENDING_SEARCHES] = counts
        }
    }
}
