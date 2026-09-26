package com.Moovie.app.data.remote

import com.Moovie.app.data.model.TmdbCredits
import com.Moovie.app.data.model.TmdbGenre
import com.Moovie.app.data.model.TmdbMovie
import com.Moovie.app.data.model.TmdbMovieDetail
import com.Moovie.app.data.model.TmdbMultiResult
import com.Moovie.app.data.model.TmdbPage
import com.Moovie.app.data.model.TmdbReview
import com.Moovie.app.data.model.TmdbTvDetail
import com.Moovie.app.data.model.TmdbTvShow
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface TmdbApi {

    @GET("movie/popular")
    suspend fun popularMovies(
        @Query("page") page: Int = 1,
        @Query("region") region: String? = null,
    ): TmdbPage<TmdbMovie>

    @GET("movie/now_playing")
    suspend fun nowPlaying(
        @Query("page") page: Int = 1,
        @Query("region") region: String? = null,
    ): TmdbPage<TmdbMovie>

    @GET("trending/movie/day")
    suspend fun trendingMoviesDay(@Query("page") page: Int = 1): TmdbPage<TmdbMovie>

    @GET("trending/movie/week")
    suspend fun trendingMoviesWeek(@Query("page") page: Int = 1): TmdbPage<TmdbMovie>

    @GET("trending/tv/week")
    suspend fun trendingTvWeek(@Query("page") page: Int = 1): TmdbPage<TmdbTvShow>

    @GET("movie/upcoming")
    suspend fun upcoming(
        @Query("page") page: Int = 1,
        @Query("region") region: String? = null,
    ): TmdbPage<TmdbMovie>

    @GET("movie/top_rated")
    suspend fun topRatedMovies(@Query("page") page: Int = 1): TmdbPage<TmdbMovie>

    @GET("tv/top_rated")
    suspend fun topRatedTv(@Query("page") page: Int = 1): TmdbPage<TmdbTvShow>

    @GET("genre/movie/list")
    suspend fun movieGenres(): TmdbGenreListResponse

    @GET("genre/tv/list")
    suspend fun tvGenres(): TmdbGenreListResponse

    @GET("discover/movie")
    suspend fun discoverMovies(
        @Query("with_genres") withGenres: String? = null,
        @Query("with_keywords") withKeywords: String? = null,
        @Query("primary_release_year") releaseYear: Int? = null,
        @Query("vote_average.gte") minRating: Double? = null,
        @Query("vote_count.gte") minVotes: Int? = null,
        @Query("sort_by") sortBy: String? = null,
        @Query("include_adult") includeAdult: Boolean = false,
        @Query("page") page: Int = 1,
    ): TmdbPage<TmdbMovie>

    @GET("discover/tv")
    suspend fun discoverTv(
        @Query("with_genres") withGenres: String? = null,
        @Query("with_keywords") withKeywords: String? = null,
        @Query("first_air_date_year") firstAirYear: Int? = null,
        @Query("vote_average.gte") minRating: Double? = null,
        @Query("vote_count.gte") minVotes: Int? = null,
        @Query("sort_by") sortBy: String? = null,
        @Query("include_adult") includeAdult: Boolean = false,
        @Query("page") page: Int = 1,
    ): TmdbPage<TmdbTvShow>

    @GET("search/multi")
    suspend fun searchMulti(
        @Query("query") query: String,
        @Query("page") page: Int = 1,
        @Query("include_adult") includeAdult: Boolean = false,
    ): TmdbPage<TmdbMultiResult>

    @GET("search/movie")
    suspend fun searchMovies(
        @Query("query") query: String,
        @Query("page") page: Int = 1,
        @Query("include_adult") includeAdult: Boolean = false,
    ): TmdbPage<TmdbMovie>

    @GET("search/tv")
    suspend fun searchTv(
        @Query("query") query: String,
        @Query("page") page: Int = 1,
        @Query("include_adult") includeAdult: Boolean = false,
    ): TmdbPage<TmdbTvShow>

    @GET("search/person")
    suspend fun searchPeople(
        @Query("query") query: String,
        @Query("page") page: Int = 1,
        @Query("include_adult") includeAdult: Boolean = false,
    ): TmdbPage<com.Moovie.app.data.model.TmdbPerson>

    @GET("movie/{id}")
    suspend fun movieDetail(
        @Path("id") id: Int,
        @Query("append_to_response") append: String = "credits,videos,reviews,similar,recommendations,release_dates",
    ): TmdbMovieDetail

    @GET("tv/{id}")
    suspend fun tvDetail(
        @Path("id") id: Int,
        @Query("append_to_response") append: String = "credits,videos,reviews,similar,content_ratings",
    ): TmdbTvDetail

    @GET("tv/{id}/season/{season}")
    suspend fun tvSeason(
        @Path("id") id: Int,
        @Path("season") season: Int,
    ): com.Moovie.app.data.model.TmdbSeasonDetail

    @GET("movie/{id}/recommendations")
    suspend fun movieRecommendations(@Path("id") id: Int, @Query("page") page: Int = 1): TmdbPage<TmdbMovie>

    @GET("movie/{id}/similar")
    suspend fun similarMovies(@Path("id") id: Int, @Query("page") page: Int = 1): TmdbPage<TmdbMovie>
}

data class TmdbGenreListResponse(val genres: List<TmdbGenre> = emptyList())
