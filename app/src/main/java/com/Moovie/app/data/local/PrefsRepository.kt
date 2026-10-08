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

/** Light/Dark/System — stored as a stable string so new options don't break old saves. */
enum class ThemeMode(val storage: String) {
    DARK("dark"), LIGHT("light"), SYSTEM("system");

    companion object {
        fun from(storage: String?): ThemeMode =
            entries.firstOrNull { it.storage == storage } ?: DARK
    }
}

/** Accent colors for the theme, keyed by storage name. */
enum class AccentColor(val storage: String, val label: String) {
    YELLOW("yellow", "Classic"),
    RED("red", "Ruby"),
    PURPLE("purple", "Violet"),
    TEAL("teal", "Teal");

    companion object {
        fun from(storage: String?): AccentColor =
            entries.firstOrNull { it.storage == storage } ?: YELLOW
    }
}

data class AppPrefs(
    val onboarded: Boolean = false,
    val favoriteGenres: Set<String> = emptySet(),
    val darkMode: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val accent: AccentColor = AccentColor.YELLOW,
    val region: String = "US",
    val recentSearches: List<String> = emptyList(),
    val trendingSearches: Set<String> = emptySet(),
    val downloadFolder: String = "",
)

class PrefsRepository(private val context: Context) {

    private object Keys {
        val ONBOARDED = booleanPreferencesKey("onboarded")
        val FAV_GENRES = stringSetPreferencesKey("fav_genres")
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ACCENT = stringPreferencesKey("accent")
        val REGION = stringPreferencesKey("region")
        val RECENTS = stringSetPreferencesKey("recent_searches")
        val TRENDING_SEARCHES = stringSetPreferencesKey("trending_searches")
        val DOWNLOAD_FOLDER = stringPreferencesKey("download_folder")
    }

    val prefs: Flow<AppPrefs> = context.dataStore.data.map { p ->
        // dark_mode was the original switch; honor it for users who only ever
        // had that, until they pick a new mode in Settings.
        val legacyDark = p[Keys.DARK_MODE]
        AppPrefs(
            onboarded = p[Keys.ONBOARDED] ?: false,
            favoriteGenres = p[Keys.FAV_GENRES] ?: emptySet(),
            darkMode = legacyDark ?: true,
            themeMode = p[Keys.THEME_MODE]?.let(ThemeMode::from)
                // First run after this update: derive from the old boolean.
                ?: if (legacyDark == false) ThemeMode.LIGHT else ThemeMode.DARK,
            accent = AccentColor.from(p[Keys.ACCENT]),
            region = p[Keys.REGION] ?: "US",
            recentSearches = (p[Keys.RECENTS] ?: emptySet()).toList(),
            trendingSearches = p[Keys.TRENDING_SEARCHES] ?: emptySet(),
            downloadFolder = p[Keys.DOWNLOAD_FOLDER] ?: "",
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
        context.dataStore.edit {
            it[Keys.DARK_MODE] = enabled
            // Keep the enum in sync so both keys always agree.
            it[Keys.THEME_MODE] = if (enabled) ThemeMode.DARK.storage else ThemeMode.LIGHT.storage
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit {
            it[Keys.THEME_MODE] = mode.storage
            it[Keys.DARK_MODE] = mode != ThemeMode.LIGHT
        }
    }

    suspend fun setAccent(accent: AccentColor) {
        context.dataStore.edit { it[Keys.ACCENT] = accent.storage }
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

    suspend fun setDownloadFolder(folder: String) {
        context.dataStore.edit { it[Keys.DOWNLOAD_FOLDER] = folder }
    }
}
