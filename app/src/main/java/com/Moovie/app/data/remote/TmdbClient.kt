package com.Moovie.app.data.remote

import com.Moovie.app.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object TmdbClient {

    const val IMAGE_BASE = "https://image.tmdb.org/t/p/"
    const val YOUTUBE_THUMB = "https://img.youtube.com/vi/%s/hqdefault.jpg"
    const val YOUTUBE_WATCH = "https://www.youtube.com/watch?v=%s"

    fun posterUrl(path: String?, size: String = "w342"): String? =
        path?.let { IMAGE_BASE + size + it }

    fun backdropUrl(path: String?, size: String = "w780"): String? =
        path?.let { IMAGE_BASE + size + it }

    fun profileUrl(path: String?, size: String = "w185"): String? =
        path?.let { IMAGE_BASE + size + it }

    fun youtubeThumbnail(key: String): String = String.format(YOUTUBE_THUMB, key)

    fun youtubeWatchUrl(key: String): String = String.format(YOUTUBE_WATCH, key)

    fun create(): TmdbApi {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
            else HttpLoggingInterceptor.Level.NONE
        }
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val key = BuildConfig.TMDB_API_KEY
                val isV4Token = key.contains(".") // v4 read tokens are JWTs; v3 keys are 32-char hex
                val request = chain.request().newBuilder()
                    .apply {
                        if (isV4Token) {
                            header("Authorization", "Bearer $key")
                        } else {
                            // v3 keys must be passed as the api_key query param
                            header("accept", "application/json")
                            url(chain.request().url.newBuilder()
                                .addQueryParameter("api_key", key)
                                .build())
                        }
                    }
                    .build()
                chain.proceed(request)
            }
            .addInterceptor(logging)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl("https://api.themoviedb.org/3/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TmdbApi::class.java)
    }
}
