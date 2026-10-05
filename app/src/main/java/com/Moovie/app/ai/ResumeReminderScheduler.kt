package com.Moovie.app.ai

import com.Moovie.app.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Background coroutine that periodically looks for Continue Watching titles that
 * have been sitting unfinished for too long and nudges the user with a
 * resume-reminder notification.
 *
 * This is intentionally light: it only runs while the app process is alive,
 * it only nudges a small number of titles per cycle, and it skips users with no
 * Continue Watching items. It does not attempt to send notifications when the
 * app isn't running.
 */
class ResumeReminderScheduler(
    private val scope: CoroutineScope,
    private val checkIntervalMinutes: Long = 60,
    private val staleThresholdHours: Long = 48,
) {
    private var started = false

    /** Starts the background loop. Safe to call multiple times; only starts once. */
    fun start() {
        if (started) return
        started = true
        scope.launch {
            while (true) {
                try {
                    runCatching {
                        scanAndNotify()
                    }.onFailure {
                        android.util.Log.w("ResumeReminder", "Resume reminder scan failed: ${it.message}")
                    }
                } catch (_: Throwable) {
                    // Protect the background loop; one bad scan shouldn't stop future scans.
                }
                delay(checkIntervalMinutes * 60_000L)
            }
        }
    }

    private suspend fun scanAndNotify() {
        val uid = ServiceLocator.auth.uid ?: return
        val items = ServiceLocator.watchlist.watchlistOnce(uid)
            .filter { it.status != com.Moovie.app.data.model.WatchItem.STATUS_WATCHED
                && (it.status == com.Moovie.app.data.model.WatchItem.STATUS_WATCHING
                    || it.progressMinutes != null) }
        if (items.isEmpty()) return

        val stale = System.currentTimeMillis() - staleThresholdHours * 3600_000L
        val toNudge = items.filter { item ->
            val updated = item.updatedAt?.seconds?.let { TimeUnit.SECONDS.toMillis(it.toLong()) } ?: 0L
            updated < stale
        }
            .sortedBy { it.addedAt?.seconds ?: 0 }
            .take(3)

        toNudge.forEach { item ->
            runCatching {
                ServiceLocator.social.sendResumeReminder(
                    uid = uid,
                    watchItemKey = item.key,
                    titleName = item.titleName,
                )
            }.onFailure {
                android.util.Log.w("ResumeReminder", "Failed to enqueue resume reminder: ${it.message}")
            }
        }
    }
}
