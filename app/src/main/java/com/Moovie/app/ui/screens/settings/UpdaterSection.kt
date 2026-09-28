package com.Moovie.app.ui.screens.settings

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.Moovie.app.BuildConfig
import com.Moovie.app.ServiceLocator
import com.Moovie.app.data.update.ApkInstaller
import com.Moovie.app.data.update.UpdateChecker
import com.Moovie.app.data.update.UpdateInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.File

enum class UpdateState { IDLE, CHECKING, UP_TO_DATE, AVAILABLE, DOWNLOADING, READY }

/**
 * One shared instance for the Settings section AND the popup dialog, so a
 * download started from the dialog shows its progress in Settings too (and
 * vice versa) instead of two VMs racing on the same APK file.
 */
val sharedUpdateViewModel = UpdateViewModel()

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

    fun downloadAndInstall(context: Context, info: UpdateInfo? = update.value) {
        val target = info ?: return
        if (state.value == UpdateState.DOWNLOADING) return
        update.value = target
        viewModelScope.launch {
            state.value = UpdateState.DOWNLOADING
            progress.value = 0
            message.value = null
            val result = checker.downloadApk(context, target.apkUrl) { pct -> progress.value = pct }
            val file = result.file
            if (file != null && file.exists() && file.length() > 0L) {
                apkFile = file
                state.value = UpdateState.READY
                if (!ApkInstaller.install(context, file)) {
                    // Download finished fine; the launcher just didn't come up
                    // (usually missing "install unknown apps" consent).
                    message.value = "Download complete — tap Install now to continue."
                }
            } else {
                // The download now retries + resumes internally; if it still
                // failed, tell the user exactly what went wrong.
                state.value = UpdateState.AVAILABLE
                message.value = "Download failed: ${result.error ?: "unknown error"}. Tap Download & install to retry."
            }
        }
    }

    /** Re-launches the installer if the user dismissed the first prompt. */
    fun installNow(context: Context) {
        val f = apkFile ?: return
        if (ApkInstaller.install(context, f)) {
            message.value = null
        } else {
            message.value = "Couldn't open the installer. Check Moovie has 'Install unknown apps' permission, then try again."
        }
    }
}

/**
 * Dialog shown anywhere in the app when the background 5h poller finds a newer
 * release. Reuses UpdateViewModel so "Download & install" shares one flow.
 */
@Composable
fun UpdateAvailableDialog(vm: UpdateViewModel = sharedUpdateViewModel) {
    val poller = ServiceLocator.updatePoller
    val pending by poller.update.collectAsState()
    val dismissed by poller.dismissedTag.collectAsState()
    val state by vm.state.collectAsState()
    val context = LocalContext.current

    val info = pending
    if (info != null && info.tagName != dismissed) {
        AlertDialog(
            onDismissRequest = { poller.dismiss() },
            title = { Text("Update available") },
            text = {
                Text("Moovie ${info.tagName} is out. Install it to get the latest fixes — no need to uninstall, your data stays.")
            },
            confirmButton = {
                Button(onClick = {
                    vm.downloadAndInstall(context, info)
                }) { Text("Update") }
            },
            dismissButton = {
                TextButton(onClick = { poller.dismiss() }) { Text("Not now") }
            },
        )
    }
    // Downloading progress surfaces via the same VM inside Settings; while a
    // dialog-triggered download runs, show a slim progress dialog.
    if (state == UpdateState.DOWNLOADING && info != null && info.tagName != dismissed) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Downloading update…") },
            text = { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) },
            confirmButton = {},
        )
    }
}

@Composable
fun UpdaterSection(vm: UpdateViewModel = sharedUpdateViewModel) {
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
                "You're on the latest version",
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
