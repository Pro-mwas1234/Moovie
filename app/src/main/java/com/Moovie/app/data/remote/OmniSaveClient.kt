package com.Moovie.app.data.remote

import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

/**
 * Client for the OmniSave public BFF (the API behind videodownloader.site).
 *
 * Flow mirrors the web app:
 *  1. POST /subject/search-suggest anonymously — the server mints a guest JWT
 *     and returns it in the `x-user` response header.
 *  2. POST /subject/search with the guest token — finds titles by keyword.
 *  3. GET  /subject/download — returns signed, direct MP4 URLs (360/480/720P)
 *     plus subtitle URLs. Links expire (~1h), so they are resolved on demand.
 *
 * The signed CDN URLs can be played directly by ExoPlayer — no embed WebView,
 * no Node proxy needed.
 */
object OmniSaveClient {

    private const val BASE_URL = "https://h5-api.aoneroom.com/wefeed-h5api-bff/"
    private const val WEB_ORIGIN = "https://videodownloader.site"

    @Volatile
    private var token: String? = null
    private val tokenMutex = Mutex()

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header("accept", "application/json")
                .header("content-type", "application/json")
                .header("x-source", "downloader")
                .header("x-request-lang", "en")
                .header("origin", WEB_ORIGIN)
                .header("referer", "$WEB_ORIGIN/")
                .apply { token?.let { header("Authorization", "Bearer $it") } }
                .build()
            val resp = chain.proceed(req)
            // The server attaches a fresh guest token to any response via `x-user`.
            resp.header("x-user")?.let { raw ->
                runCatching {
                    com.google.gson.Gson().fromJson(raw, TokenEnvelope::class.java).token
                }.getOrNull()?.let { token = it }
            }
            resp
        }
        .build()

    private val api: OmniSaveApi = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(http)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(OmniSaveApi::class.java)

    /** Ensures we hold a valid guest token, minting one if needed. */
    private suspend fun ensureToken() {
        token?.let { return }
        tokenMutex.withLock {
            if (token != null) return
            // Anonymous suggest call; the interceptor harvests the token from
            // the `x-user` response header.
            withContext(Dispatchers.IO) {
                runCatching { api.suggest(SuggestReq(keyword = "a")) }
            }
            if (token == null) error("OmniSave: could not mint guest token")
        }
    }    /**
     * Resolves a direct, playable MP4 URL for the given title.
     * Picks the highest resolution non-VIP stream.
     * For series episodes pass [season]/[episode] (1-based) and [preferTv] = true
     * so the search prefers TV matches and the download call targets that episode.
     * Returns null when the title can't be found (caller should fall back).
     */
    suspend fun resolveStream(
        title: String,
        season: Int = 0,
        episode: Int = 0,
        preferTv: Boolean = false,
    ): String? = withContext(Dispatchers.IO) {
        if (title.isBlank()) return@withContext null
        runCatching {
            ensureToken()
            val search = api.search(SearchReq(keyword = title))
            val item = pickItem(search.body()?.data?.items.orEmpty(), preferTv)
                ?: return@withContext null
            val dl = api.download(
                subjectId = item.subjectId,
                detailPath = item.detailPath!!, 
                season = season,
                episode = episode,
            )
            dl.body()?.data?.downloads
                ?.filter { !it.vipLocked && !it.url.isNullOrBlank() }
                ?.maxByOrNull { it.resolution ?: 0 }
                ?.url
        }.getOrNull()
    }

    /**
     * All qualities incl. VIP-locked tiers (url null, vipLocked true) so the UI
     * can show *why* 720p isn't downloadable instead of hiding it.
     */
    suspend fun resolveQualitiesDetailed(
        title: String,
        season: Int = 0,
        episode: Int = 0,
        preferTv: Boolean = false,
    ): List<StreamQuality> =
        withContext(Dispatchers.IO) {
            val item = runCatching {
                ensureToken()
                api.search(SearchReq(keyword = title)).body()?.data?.items
                    .orEmpty().let { pickItem(it, preferTv) }
            }.getOrNull() ?: return@withContext emptyList()
            runCatching {
                api.download(
                    subjectId = item.subjectId,
                    detailPath = item.detailPath!!, 
                    season = season,
                    episode = episode,
                ).body()?.data?.downloads
                    ?.filter { !it.url.isNullOrBlank() }
                    ?.sortedByDescending { it.resolution ?: 0 }
                    ?.map {
                        StreamQuality(
                            url = if (it.vipLocked) "" else it.url!!,
                            resolution = it.resolution ?: 0,
                            format = it.format ?: "MP4",
                            vipLocked = it.vipLocked,
                            size = it.size,
                        )
                    }
                    ?: emptyList()
            }.getOrDefault(emptyList())
        }

    /**
     * OmniSave search hits, choosing among entries that actually have media.
     * [preferTv] biases towards subjectType != 1 (the CLI maps 1 = Movie,
     * anything else = TV Series) but still falls back to the best hit.
     */
    private fun pickItem(items: List<SearchItem>, preferTv: Boolean): SearchItem? {
        val usable = items.filter { it.hasResource && !it.detailPath.isNullOrBlank() }
        if (!preferTv) return usable.firstOrNull()
        return usable.firstOrNull { it.subjectType != 1 } ?: usable.firstOrNull()
    }

    /**
     * All playable (non-VIP) qualities for a title, best first. Empty when
     * unavailable. For series episodes pass [season]/[episode] (1-based) and
     * [preferTv] = true.
     */
    suspend fun resolveQualities(
        title: String,
        season: Int = 0,
        episode: Int = 0,
        preferTv: Boolean = false,
    ): List<StreamQuality> =
        resolveQualitiesDetailed(title, season, episode, preferTv).filter { !it.vipLocked }
}

data class StreamQuality(
    val url: String,
    val resolution: Int,
    val format: String,
    val vipLocked: Boolean = false,
    val size: String? = null,
)

// ---------- Retrofit interface ----------

interface OmniSaveApi {
    @POST("subject/search-suggest")
    suspend fun suggest(@Body body: SuggestReq): Response<Unit>

    @POST("subject/search")
    suspend fun search(@Body body: SearchReq): Response<SearchResp>

    @GET("subject/download")
    suspend fun download(
        @Query("subjectId") subjectId: String,
        @Query("detailPath") detailPath: String,
        @Query("se") season: Int = 0,
        @Query("ep") episode: Int = 0,
        // Retrofit encodes the bracketed key as supportCodecs%5Bh264%5D=1.
        @Query("supportCodecs[h264]") supportH264: Int = 1,
    ): Response<DownloadResp>
}

// ---------- DTOs ----------

data class TokenEnvelope(val token: String? = null)

data class SuggestReq(val keyword: String, val perPage: Int = 5)

data class SearchReq(
    val keyword: String,
    val page: Int = 1,
    val perPage: Int = 20,
    val subjectType: Int = 0,
)

data class SearchResp(val code: Int = 0, val message: String? = null, val data: SearchData? = null)

data class SearchData(val pager: Pager? = null, val items: List<SearchItem> = emptyList())

data class Pager(
    val page: String? = null,
    val perPage: String? = null,
    val hasMore: Boolean = false,
    @SerializedName("totalCount") val totalCount: Int = 0,
)

data class SearchItem(
    val subjectId: String,
    val subjectType: Int = 0,
    val title: String? = null,
    @SerializedName("releaseDate") val releaseDate: String? = null,
    val genre: String? = null,
    val duration: Long = 0,
    @SerializedName("imdbRatingValue") val imdbRating: String? = null,
    @SerializedName("detailPath") val detailPath: String? = null,
    @SerializedName("hasResource") val hasResource: Boolean = false,
)

data class DownloadResp(val code: Int = 0, val message: String? = null, val data: DownloadData? = null)

data class DownloadData(
    val downloads: List<DownloadEntry> = emptyList(),
    val captions: List<Caption> = emptyList(),
)

data class DownloadEntry(
    val id: String? = null,
    val url: String? = null,
    val format: String? = null,
    val resolution: Int? = null,
    val size: String? = null,
    val duration: Long? = null,
    @SerializedName("vipLocked") val vipLocked: Boolean = false,
)

data class Caption(
    val id: String? = null,
    val lan: String? = null,
    val lanName: String? = null,
    val url: String? = null,
    val size: String? = null,
)
