package com.Moovie.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.Moovie.app.BuildConfig
import com.Moovie.app.ServiceLocator
import com.Moovie.app.data.local.AccentColor
import com.Moovie.app.data.local.ThemeMode
import com.Moovie.app.ui.navigation.Routes
import com.Moovie.app.ui.theme.accentSwatch
import com.Moovie.app.data.repo.WatchlistRepository
import com.Moovie.app.data.repo.WatchlistRepository.WatchlistExportFormat
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(nav: NavController) {
    val prefs by ServiceLocator.prefs.prefs.collectAsState(initial = null)
    val accountState by ServiceLocator.auth.user.collectAsState()
    val account = accountState
    val scope = rememberCoroutineScope()
    val p = prefs ?: return
    var showRegionPicker by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
            }
            Text("Settings", style = MaterialTheme.typography.headlineSmall)
        }
        Spacer(Modifier.height(8.dp))

        SettingGroup("Theme") {
            Text("Mode", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = p.themeMode == mode,
                        onClick = { scope.launch { ServiceLocator.prefs.setThemeMode(mode) } },
                        label = {
                            Text(
                                when (mode) {
                                    ThemeMode.DARK -> "Dark"
                                    ThemeMode.LIGHT -> "Light"
                                    ThemeMode.SYSTEM -> "System"
                                }
                            )
                        },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("Accent", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AccentColor.entries.forEach { accent ->
                    val selected = p.accent == accent
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(accentSwatch(accent))
                            .border(
                                width = if (selected) 3.dp else 1.dp,
                                color = if (selected) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.outline,
                                shape = CircleShape,
                            )
                            .clickable { scope.launch { ServiceLocator.prefs.setAccent(accent) } },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = accent.label,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }

        SettingGroup("Preferences") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { showRegionPicker = true }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Region", Modifier.weight(1f))
                Text(
                    regionName(p.region),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.Filled.ExpandMore,
                    contentDescription = "Change region",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SettingGroup("Data") {
            OutlinedButton(
                onClick = { scope.launch { ServiceLocator.prefs.clearRecentSearches() } },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Clear recent searches") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { scope.launch { ServiceLocator.watchlist.exportWatchlist(format = com.Moovie.app.data.repo.WatchlistRepository.WatchlistExportFormat.Json) } },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Export watchlist + progress")
            }
        }

        SettingGroup("Account") {
            if (account != null && !account.isAnonymous) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            ServiceLocator.auth.updateDisplayName("")
                            ServiceLocator.watchlist.removeWhere { true }
                            ServiceLocator.downloads.removeWhere { true }
                            ServiceLocator.social.deleteAllMyReviews()
                            ServiceLocator.social.markAllNotificationsRead()
                            ServiceLocator.local.clearAll()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Delete account and all data", color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(8.dp))
            }
            if (account != null && !account.isAnonymous) {
                Text(
                    "Signed in as ${account.name ?: "user"}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                account.email?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { nav.navigate(Routes.AUTH) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Manage account") }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        scope.launch {
                            ServiceLocator.auth.signOut()
                            nav.navigate(Routes.HOME) { popUpTo(0) }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Log out") }
            } else {
                Text(
                    if (!ServiceLocator.auth.isCloud)
                        "Local mode — add app/google-services.json (Firebase) to enable accounts, sync, and watch parties across devices."
                    else if (account == null)
                        "You're browsing as a guest. Sign in so your watchlist, downloads and parties sync across devices."
                    else
                        "You're a guest. Create an account to keep everything you've saved.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { nav.navigate(Routes.AUTH) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (account == null) "Sign in" else "Save your guest account") }
            }
        }

        UpdaterSection()

        if (showRegionPicker) {
            RegionPickerDialog(
                current = p.region,
                onDismiss = { showRegionPicker = false },
                onPick = { code ->
                    scope.launch {
                        ServiceLocator.prefs.setRegion(code)
                        showRegionPicker = false
                    }
                },
            )
        }

        SettingGroup("About") {
            Text("Moovie v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(4.dp))
            Text(
                "Movie data & images by TMDB. This product uses the TMDB API but is not endorsed or certified by TMDB.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun SettingGroup(title: String, content: @Composable () -> Unit) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    HorizontalDivider(Modifier.padding(vertical = 6.dp))
    content()
    Spacer(Modifier.height(16.dp))
}

/** "US" -> "United States"; falls back to the code itself when unknown. */
internal fun regionName(code: String): String =
    if (code.isBlank()) code
    else java.util.Locale("", code).displayCountry.ifBlank { code }

/**
 * Region affects release-date-based rows (Coming Soon, In Theaters) by being
 * sent to TMDB as the `region` query param.
 */
@Composable
internal fun RegionPickerDialog(
    current: String,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val all = remember {
        java.util.Locale.getISOCountries()
            .map { c -> c to regionName(c) }
            .sortedBy { it.second }
    }
    val filtered = remember(query) {
        if (query.isBlank()) all
        else all.filter { it.second.contains(query, ignoreCase = true) || it.first.equals(query, ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose region") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text("Search country") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.fillMaxWidth().height(320.dp)) {
                    items(filtered.size) { i ->
                        val (code, name) = filtered[i]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(code) }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(name, Modifier.weight(1f))
                            if (code == current) {
                                Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
