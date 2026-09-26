package com.Moovie.app.ui.screens.settings

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.Moovie.app.BuildConfig
import com.Moovie.app.data.update.ApkInstaller
import com.Moovie.app.data.update.UpdateChecker
import com.Moovie.app.data.update.UpdateInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.File

enum class UpdateState { IDLE, CHECKING, UP_TO_DATE, AVAILABLE, DOWNLOADING, READY }

class UpdateViewModel : ViewModel() {
    val state = MutableStateFlow(UpdateState.IDLE)
    val update = MutableStateFlow<UpdateInfo?>(null)
    val progress = MutableStateFlow(0)
    val message = MutableStateFlow<String?>(null)

    private val checker = UpdateChecker()
    private var apkFile: File? = null

    fun check() {
        if (state.value == UpdateState.CHECKING) return
        viewModelScope.launch {
            state.value = UpdateState.CHECKING
            message.value = null
            val info = checker.latestUpdate(BuildConfig.VERSION_NAME)
            update.value = info
            state.value = if (info != null) UpdateState.AVAILABLE else UpdateState.UP_TO_DATE
        }
    }

    fun downloadAndInstall(context: Context) {
        val info = update.value ?: return
        if (state.value == UpdateState.DOWNLOADING) return
        viewModelScope.launch {
            state.value = UpdateState.DOWNLOADING
            progress.value = 0
            message.value = null
            val file = checker.downloadApk(context, info.apkUrl) { pct -> progress.value = pct }
            if (file != null && file.exists() && file.length() > 0L) {
                apkFile = file
                state.value = UpdateState.READY
                ApkInstaller.install(context, file)
            } else {
                state.value = UpdateState.AVAILABLE
                message.value = "Download failed — check your connection and try again."
            }
        }
    }

    /** Re-launches the installer if the user dismissed the first prompt. */
    fun installNow(context: Context) {
        apkFile?.let { ApkInstaller.install(context, it) }
    }
}

@Composable
fun UpdaterSection(vm: UpdateViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val update by vm.update.collectAsState()
    val progress by vm.progress.collectAsState()
    val message by vm.message.collectAsState()
    val context = LocalContext.current

    SettingGroup("Updates") {
        Text(
            "Current version ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))

        when (state) {
            UpdateState.IDLE -> Button(onClick = { vm.check() }, modifier = Modifier.fillMaxWidth()) {
                Text("Check for updates")
            }

            UpdateState.CHECKING -> Row(verticalAlignment = Alignment.CenterVertically) {
                LinearProgressIndicator(
                    modifier = Modifier.weight(1f),
                )
            }

            UpdateState.UP_TO_DATE -> Text(
                "You're on the latest version ✓",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )

            UpdateState.AVAILABLE -> {
                val info = update
                Text(
                    "Update available: ${info?.tagName ?: ""}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                info?.notes?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
                Button(
                    onClick = { vm.downloadAndInstall(context) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Download & install") }
            }

            UpdateState.DOWNLOADING -> Column {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Downloading update… $progress%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            UpdateState.READY -> OutlinedButton(
                onClick = { vm.installNow(context) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Install now") }
        }

        message?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (state == UpdateState.UP_TO_DATE || state == UpdateState.AVAILABLE) {
            Spacer(Modifier.height(4.dp))
            OutlinedButton(onClick = { vm.check() }, modifier = Modifier.fillMaxWidth()) {
                Text("Re-check")
            }
        }
    }
}
