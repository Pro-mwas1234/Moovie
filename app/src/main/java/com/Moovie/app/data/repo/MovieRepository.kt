package com.Moovie.app.data.repo

import com.Moovie.app.data.model.CastMemberUi
import com.Moovie.app.data.model.Title
import com.Moovie.app.data.model.TmdbGenre
import com.Moovie.app.data.model.TmdbMovie
import com.Moovie.app.data.model.TmdbMovieDetail
import com.Moovie.app.data.model.TmdbMultiResult
import com.Moovie.app.data.model.TmdbPerson
import com.Moovie.app.data.model.TmdbReview
import com.Moovie.app.data.model.TmdbTvDetail
import com.Moovie.app.data.model.TmdbTvShow
import com.Moovie.app.data.model.VideoUi
import com.Moovie.app.data.remote.TmdbApi
import java.util.concurrent.ConcurrentHashMap

data class DetailBundle(
    val title: Title,
    val tagline: String? = null,
    val runtimeMinutes: Int? = null,
    val seasons: Int? = null,
    val episodes: Int? = null,
    val status: String? = null,
    val certification: String? = null,
    val genres: List<TmdbGenre> = emptyList(),
    val cast: List<CastMemberUi> = emptyList(),
    val directors: List<String> = emptyList(),
    val trailerKey: String? = null,
    val videos: List<VideoUi> = emptyList(),
    val reviews: List<TmdbReview> = emptyList(),
    val similar: List<Title> = emptyList(),
)

/**
 * Repository over TMDB with a simple in-memory TTL cache (30 min) to save quota.
 */
class MovieRepository(private val api: TmdbApi) {

    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private val cacheTtlMs = 30 * 60 * 1000L

    private var genresCache: List<TmdbGenre>? = null

    /**
     * ISO-3166 country code ("US", "KE", …) sent to TMDB's region-aware
     * endpoints so "new releases"/"coming soon" match the user's country.
     * Set from prefs at startup and whenever the user changes it in Settings.
     */
    @Volatile
    var region: String = "US"
        private set

    /** Updates the region and drops cached region-aware rows so they reload. */
    fun setRegion(code: String) {
        val next = code.trim().uppercase().ifBlank { "US" }
        if (next == region) return
        region = next
        listOf("now_playing", "popular", "upcoming").forEach { cache.remove(it) }
    }

    private data class CacheEntry(val at: Long, val value: Any?)

    private suspend fun <T> cached(key: String, loader: suspend () -> T): T {
        val hit = cache[key]
        if (hit != null && System.currentTimeMillis() - hit.at < cacheTtlMs) {
            @Suppress("UNCHECKED_CAST")
            return hit.value as T
        }
        val value = loader()
        cache[key] = CacheEntry(System.currentTimeMillis(), value)
        return value
    }

    private fun movieToTitle(m: TmdbMovie) = Title(
        id = m.id,
        mediaType = "movie",
        name = m.title ?: m.originalTitle ?: "Untitled",
        overview = m.overview,
        posterPath = m.posterPath,
        backdropPath = m.backdropPath,
        year = m.year,
        rating = m.voteAverage,
        genreIds = m.genreIds,
    )

    private fun tvToTitle(t: TmdbTvShow) = Title(
        id = t.id,
        mediaType = "tv",
        name = t.name ?: t.originalName ?: "Untitled",
        overview = t.overview,
        posterPath = t.posterPath,
        backdropPath = t.backdropPath,
        year = t.year,
        rating = t.voteAverage,
        genreIds = t.genreIds,
    )

    private fun multiToTitle(r: TmdbMultiResult): Title? {
        if (r.mediaType != "movie" && r.mediaType != "tv") return null
        return Title(
            id = r.id,
            mediaType = r.mediaType,
            name = r.displayTitle ?: "Untitled",
            overview = r.overview,
            posterPath = r.posterPath,
            backdropPath = null,
            year = r.displayDate?.takeIf { it.length >= 4 }?.substring(0, 4)?.toIntOrNull(),
            rating = r.voteAverage,
        )
    }

    // ---------- Home rows ----------

    suspend fun trendingToday(): List<Title> = cached("trending_day") {
        api.trendingMoviesDay().results.map { movieToTitle(it) }
    }

    suspend fun trendingWeek(): List<Title> = cached("trending_week") {
        api.trendingMoviesWeek().results.map { movieToTitle(it) }
    }

    suspend fun trendingTvWeek(): List<Title> = cached("trending_tv_week") {
        api.trendingTvWeek().results.map { tvToTitle(it) }
    }

    suspend fun newReleases(): List<Title> = cached("now_playing") {
        api.nowPlaying(region = region).results.map { movieToTitle(it) }
    }

    suspend fun popular(): List<Title> = cached("popular") {
        api.popularMovies(region = region).results.map { movieToTitle(it) }
    }

    suspend fun upcoming(): List<Title> = cached("upcoming") {
        api.upcoming(region = region).results.map { movieToTitle(it) }
    }

    suspend fun topRatedMovies(): List<Title> = cached("top_rated_movies") {
        api.topRatedMovies().results.map { movieToTitle(it) }
    }

    suspend fun topRatedTv(): List<Title> = cached("top_rated_tv") {
        api.topRatedTv().results.map { tvToTitle(it) }
    }

    suspend fun hero(): Title? = cached("hero") {
        api.trendingMoviesWeek().results.firstOrNull()?.let { movieToTitle(it) }
    }

    // ---------- Genres / Discover ----------

    suspend fun genres(): List<TmdbGenre> {
        genresCache?.let { return it }
        val merged = (api.movieGenres().genres + api.tvGenres().genres)
            .distinctBy { it.id }
        genresCache = merged
        return merged
    }

    suspend fun byGenre(genreId: Int, page: Int = 1): List<Title> =
        cached("genre_$genreId:$page") {
            val movies = api.discoverMovies(withGenres = genreId.toString(), page = page).results
            val tv = api.discoverTv(withGenres = genreId.toString(), page = page).results
            (movies.map { movieToTitle(it) } + tv.map { tvToTitle(it) })
                .sortedByDescending { it.rating }
        }

    suspend fun byMood(moodId: String, page: Int = 1): List<Title> =
        cached("mood_$moodId:$page") {
            val mood = Moods.ALL.firstOrNull { it.id == moodId } ?: Moods.ALL.first()
            val movies = api.discoverMovies(
                withGenres = mood.genreIds.joinToString("|"),
                withKeywords = mood.keywordIds.joinToString("|"),
                minVotes = 50,
                page = page,
            ).results
            val tv = api.discoverTv(
                withGenres = mood.genreIds.joinToString("|"),
                withKeywords = mood.keywordIds.joinToString("|"),
                minVotes = 50,
                page = page,
            ).results
            (movies.map { movieToTitle(it) } + tv.map { tvToTitle(it) })
                .sortedByDescending { it.rating }
                .shuffled()
        }

    /** Mood/vibe discover for the AI Recommend engine (movies+TV, adult filtered). */
    suspend fun discoverByVibe(genreIds: List<Int>, keywordIds: List<Int>, page: Int = 1): List<Title> {
        val g = genreIds.joinToString("|")
        val k = keywordIds.joinToString("|").ifBlank { null }
        val movies = runCatching {
            api.discoverMovies(withGenres = g, withKeywords = k, minVotes = 100, page = page).results
        }.getOrDefault(emptyList())
        val tv = runCatching {
            api.discoverTv(withGenres = g, withKeywords = k, minVotes = 100, page = page).results
        }.getOrDefault(emptyList())
        return (movies.map { movieToTitle(it) } + tv.map { tvToTitle(it) })
            .filter { !it.name.equals("Untitled", true) }
            .sortedByDescending { it.rating }
    }

    suspend fun surpriseMe(): Title {
        val page = (1..8).random()
        val results = api.discoverMovies(
            minVotes = 200,
            sortBy = "popularity.desc",
            page = page,
        ).results
        val pick = results.filter { !it.adult }.randomOrNull()
            ?: results.firstOrNull()
            ?: return popular().first()
        return movieToTitle(pick)
    }

    // ---------- Search ----------

    suspend fun search(query: String, page: Int = 1): List<Title> =
        cached("search:${query.lowercase()}:$page") {
            val multi = api.searchMulti(query, page = page).results.mapNotNull { multiToTitle(it) }
            // Belt-and-braces: if multi-search came back thin (some proxies strip
            // media_type), merge in movie+TV results so "All" is never empty.
            if (multi.size >= 5 || query.isBlank()) multi
            else (
                multi +
                    api.searchMovies(query, page = page).results.map { movieToTitle(it) } +
                    api.searchTv(query, page = page).results.map { tvToTitle(it) }
                ).distinctBy { "${it.mediaType}-${it.id}" }
        }

    suspend fun searchMoviesOnly(query: String, page: Int = 1): List<Title> =
        cached("search_m:${query.lowercase()}:$page") {
            api.searchMovies(query, page = page).results.map { movieToTitle(it) }
        }

    suspend fun searchTvOnly(query: String, page: Int = 1): List<Title> =
        cached("search_t:${query.lowercase()}:$page") {
            api.searchTv(query, page = page).results.map { tvToTitle(it) }
        }

    suspend fun searchPeopleOnly(query: String, page: Int = 1): List<TmdbPerson> =
        cached("search_p:${query.lowercase()}:$page") {
            api.searchPeople(query, page = page).results
        }

    suspend fun filterTitles(
        genreId: Int?,
        year: Int?,
        minRating: Double?,
        page: Int = 1,
    ): List<Title> = cached("filter:$genreId:$year:$minRating:$page") {
        val movies = api.discoverMovies(
            withGenres = genreId?.toString(),
            releaseYear = year,
            minRating = minRating,
            minVotes = 30,
            page = page,
        ).results
        movies.map { movieToTitle(it) }
    }

    // ---------- Because You Liked ----------

    suspend fun becauseYouLiked(seed: Title): List<Title> =
        if (seed.mediaType == "movie") {
            cached("reco:${seed.id}") {
                val recs = api.movieRecommendations(seed.id).results
                val similar = if (recs.size < 6) {
                    recs.map { movieToTitle(it) } + api.similarMovies(seed.id).results.map { movieToTitle(it) }
                } else recs.map { movieToTitle(it) }
                similar.distinctBy { it.id }.take(20)
            }
        } else {
            cached("reco_tv:${seed.id}") {
                api.discoverMovies(minVotes = 100, sortBy = "popularity.desc").results
                    .map { movieToTitle(it) }
                    .shuffled()
                    .take(20)
            }
        }

    // ---------- Detail ----------

    suspend fun movieDetail(id: Int): DetailBundle = cached("detail_movie_$id") {
        val d = api.movieDetail(id)
        DetailBundle(
            title = Title(
                id = d.id,
                mediaType = "movie",
                name = d.title ?: d.originalTitle ?: "Untitled",
                overview = d.overview,
                posterPath = d.posterPath,
                backdropPath = d.backdropPath,
                year = d.year,
                rating = d.voteAverage,
                genreIds = d.genres.map { it.id },
            ),
            tagline = d.tagline,
            runtimeMinutes = d.runtime,
            status = d.status,
            certification = d.releaseDates?.results
                ?.find { it.iso31661 == "US" }
                ?.releaseDates
                ?.firstOrNull { !it.certification.isNullOrBlank() }
                ?.certification,
            genres = d.genres,
            cast = d.credits?.cast?.take(15)?.map {
                CastMemberUi(it.id, it.name ?: "", it.character, it.profilePath)
            } ?: emptyList(),
            directors = d.credits?.crew
                ?.filter { it.job == "Director" }
                ?.map { it.name ?: "" }
                .orEmpty(),
            trailerKey = d.videos?.results
                ?.filter { it.site == "YouTube" && it.type == "Trailer" }
                ?.maxByOrNull { it.official.toString().toIntOrNull() ?: 0 }
                ?.key
                ?: d.videos?.results?.firstOrNull { it.site == "YouTube" }?.key,
            videos = d.videos?.results
                ?.filter { it.site == "YouTube" }
                ?.take(8)
                ?.map { VideoUi(it.key ?: "", it.name, it.site, it.type) }
                .orEmpty(),
            reviews = d.reviews?.results.orEmpty(),
            similar = (d.recommendations?.results ?: d.similar?.results ?: emptyList())
                .map { movieToTitle(it) },
        )
    }

    suspend fun tvDetail(id: Int): DetailBundle = cached("detail_tv_$id") {
        val d = api.tvDetail(id)
        DetailBundle(
            title = Title(
                id = d.id,
                mediaType = "tv",
                name = d.name ?: d.originalName ?: "Untitled",
                overview = d.overview,
                posterPath = d.posterPath,
                backdropPath = d.backdropPath,
                year = d.year,
                rating = d.voteAverage,
                genreIds = d.genres.map { it.id },
            ),
            tagline = d.tagline,
            runtimeMinutes = d.episodeRunTime.firstOrNull(),
            seasons = d.numberOfSeasons,
            episodes = d.numberOfEpisodes,
            status = null,
            certification = d.contentRatings?.results
                ?.firstOrNull { it.iso31661 == "US" }?.rating,
            genres = d.genres,
            cast = d.credits?.cast?.take(15)?.map {
                CastMemberUi(it.id, it.name ?: "", it.character, it.profilePath)
            } ?: emptyList(),
            directors = d.credits?.crew
                ?.filter { it.department == "Directing" }
                ?.distinctBy { it.id }
                ?.take(3)
                ?.map { it.name ?: "" }
                .orEmpty(),
            trailerKey = d.videos?.results
                ?.filter { it.site == "YouTube" && it.type == "Trailer" }
                ?.firstOrNull()?.key
                ?: d.videos?.results?.firstOrNull { it.site == "YouTube" }?.key,
            videos = d.videos?.results
                ?.filter { it.site == "YouTube" }
                ?.take(8)
                ?.map { VideoUi(it.key ?: "", it.name, it.site, it.type) }
                .orEmpty(),
            reviews = d.reviews?.results.orEmpty(),
            similar = d.similar?.results?.map { tvToTitle(it) } ?: emptyList(),
        )
    }

    suspend fun detail(id: Int, mediaType: String): DetailBundle =
        if (mediaType == "tv") tvDetail(id) else movieDetail(id)
}
