package com.Moovie.app

import android.content.Context
import com.Moovie.app.ai.RecommendEngine
import com.Moovie.app.ai.ResumeReminderScheduler
import com.Moovie.app.data.local.PrefsRepository
import com.Moovie.app.data.remote.TmdbApi
import com.Moovie.app.data.remote.TmdbClient
import com.Moovie.app.data.repo.AuthRepository
import com.Moovie.app.data.repo.DownloadFiles
import com.Moovie.app.data.repo.DownloadManager
import com.Moovie.app.data.repo.DownloadsRepository
import com.Moovie.app.data.repo.LocalDataStore
import com.Moovie.app.data.repo.MovieRepository
import com.Moovie.app.data.repo.SocialRepository
import com.Moovie.app.data.repo.WatchPartyRepository
import com.Moovie.app.data.repo.WatchlistRepository

/**
 * Tiny manual dependency container. Kept intentionally dependency-injection-framework-free.
 */
object ServiceLocator {
    lateinit var prefs: PrefsRepository
        private set
    lateinit var tmdb: TmdbApi
        private set
    lateinit var movies: MovieRepository
        private set
    lateinit var auth: AuthRepository
        private set
    lateinit var watchlist: WatchlistRepository
        private set
    lateinit var downloads: DownloadsRepository
        private set
    lateinit var social: SocialRepository
        private set
    lateinit var watchParty: WatchPartyRepository
        private set
    val local = LocalDataStore()
    lateinit var recommend: RecommendEngine
        private set

    /** App-wide coroutine scope for background work (downloads etc.). */
    val appScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.Dispatchers.Default + kotlinx.coroutines.SupervisorJob()
    )

    /** Real file downloader for offline playback. */
    lateinit var downloadManager: DownloadManager
        private set

    /** Gallery publishing + file cleanup for finished downloads. */
    lateinit var downloadFiles: DownloadFiles
        private set

    /** Background update checker (launch + every 5h). */
    lateinit var updatePoller: com.Moovie.app.data.update.UpdatePoller
        private set

    /** Sends resume-reminder notifications for stale Continue Watching items. */
    lateinit var resumeReminder: ResumeReminderScheduler
        private set

    fun init(context: Context) {
        val app = context.applicationContext
        local.attach(app)
        downloadFiles = DownloadFiles(app)
        prefs = PrefsRepository(app)
        tmdb = TmdbClient.create()
        movies = MovieRepository(tmdb)
        auth = AuthRepository()
        watchlist = WatchlistRepository()
        downloads = DownloadsRepository()
        social = SocialRepository()
        watchParty = WatchPartyRepository()
        recommend = RecommendEngine(movies)
        downloadManager = DownloadManager(app)
        updatePoller = com.Moovie.app.data.update.UpdatePoller(appScope)
        updatePoller.start()
        resumeReminder = ResumeReminderScheduler(appScope)
        resumeReminder.start()
    }
}
