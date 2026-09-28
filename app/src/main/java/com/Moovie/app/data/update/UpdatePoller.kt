package com.Moovie.app.data.update

import com.Moovie.app.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Background update watcher: checks the GitHub Releases feed shortly after
 * launch and then every 5 hours while the app process is alive. When a newer
 * tag is found, [update] emits the info so the UI can show a dialog offering
 * the download (the actual install flow reuses UpdateChecker/ApkInstaller).
 */
class UpdatePoller(
    private val scope: CoroutineScope,
    private val checker: UpdateChecker = UpdateChecker(),
) {

    private val _update = MutableStateFlow<UpdateInfo?>(null)
    val update: StateFlow<UpdateInfo?> = _update.asStateFlow()

    private val _dismissedTag = MutableStateFlow<String?>(null)
    val dismissedTag: StateFlow<String?> = _dismissedTag.asStateFlow()

    private companion object {
        const val FIRST_DELAY_MS = 30_000L          // 30s after launch
        const val INTERVAL_MS = 5 * 60 * 60 * 1000L // 5 hours
    }

    fun start() {
        if (BuildConfig.GITHUB_REPO.isBlank()) return
        scope.launch {
            delay(FIRST_DELAY_MS)
            while (true) {
                val info = checker.latestUpdate(BuildConfig.VERSION_NAME)
                if (info != null) _update.value = info
                delay(INTERVAL_MS)
            }
        }
    }

    /** User tapped "Not now"; stop nagging about this tag until the next release. */
    fun dismiss() {
        _dismissedTag.value = _update.value?.tagName
    }
}
