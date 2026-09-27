package com.Moovie.app.ui.screens.notifications

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Badge
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.Moovie.app.ServiceLocator

@Composable
fun NotificationsScreen(nav: NavController) {
    val uid = ServiceLocator.auth.uid
    val items by (if (uid != null) ServiceLocator.social.notifications(uid) else kotlinx.coroutines.flow.flowOf(emptyList<com.Moovie.app.data.model.NotificationItem>()))
        .collectAsState(initial = emptyList())

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text("Notifications", style = MaterialTheme.typography.headlineSmall)
        }
        if (items.isEmpty()) {
            Text(
                "You're all caught up",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp),
            )
        } else {
            LazyColumn {
                items(items, key = { it.id }) { n ->
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (!n.read) Badge { Text(" ") }
                            Text(
                                n.text,
                                modifier = Modifier.padding(start = if (!n.read) 8.dp else 0.dp),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        HorizontalDivider(Modifier.padding(top = 10.dp))
                    }
                }
            }
        }
    }
}
