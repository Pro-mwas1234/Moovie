package com.Moovie.app.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.Moovie.app.BuildConfig
import com.Moovie.app.ServiceLocator
import com.Moovie.app.ui.navigation.Routes
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(nav: NavController) {
    val prefs by ServiceLocator.prefs.prefs.collectAsState(initial = null)
    val accountState by ServiceLocator.auth.user.collectAsState()
    val account = accountState
    val scope = rememberCoroutineScope()
    val p = prefs ?: return

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

        SettingGroup("Preferences") {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Dark mode", Modifier.weight(1f))
                Switch(
                    checked = p.darkMode,
                    onCheckedChange = { scope.launch { ServiceLocator.prefs.setDarkMode(it) } },
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Region", Modifier.weight(1f))
                Text(
                    p.region,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SettingGroup("Data") {
            OutlinedButton(
                onClick = { scope.launch { ServiceLocator.prefs.clearRecentSearches() } },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Clear recent searches") }
        }

        SettingGroup("Account") {
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
