package com.Moovie.app.data.model

import com.google.gson.annotations.SerializedName

// ---------- TMDB DTOs ----------

data class TmdbPage<T>(
    val page: Int = 0,
    val results: List<T> = emptyList(),
    @SerializedName("total_pages") val totalPages: Int = 0,
    @SerializedName("total_results") val totalResults: Int = 0,
)

data class TmdbMovie(
    val id: Int,
    val title: String? = null,
    @SerializedName("original_title") val originalTitle: String? = null,
    val overview: String? = null,
    @SerializedName("poster_path") val posterPath: String? = null,
    @SerializedName("backdrop_path") val backdropPath: String? = null,
    @SerializedName("release_date") val releaseDate: String? = null,
    val runtime: Int? = null,
    @SerializedName("vote_average") val voteAverage: Double = 0.0,
    @SerializedName("vote_count") val voteCount: Int = 0,
    val genres: List<TmdbGenre> = emptyList(),
    @SerializedName("genre_ids") val genreIds: List<Int> = emptyList(),
    val tagline: String? = null,
    val status: String? = null,
    val popularity: Double = 0.0,
    val adult: Boolean = false,
    @SerializedName("original_language") val originalLanguage: String? = null,
) {
    val year: Int?
        get() = releaseDate?.takeIf { it.length >= 4 }?.substring(0, 4)?.toIntOrNull()
}

data class TmdbTvShow(
    val id: Int,
    val name: String? = null,
    @SerializedName("original_name") val originalName: String? = null,
    val overview: String? = null,
    @SerializedName("poster_path") val posterPath: String? = null,
    @SerializedName("backdrop_path") val backdropPath: String? = null,
    @SerializedName("first_air_date") val firstAirDate: String? = null,
    @SerializedName("vote_average") val voteAverage: Double = 0.0,
    @SerializedName("vote_count") val voteCount: Int = 0,
    @SerializedName("genre_ids") val genreIds: List<Int> = emptyList(),
    val originCountry: List<String> = emptyList(),
    val popularity: Double = 0.0,
) {
    val year: Int?
        get() = firstAirDate?.takeIf { it.length >= 4 }?.substring(0, 4)?.toIntOrNull()
}

data class TmdbPerson(
    val id: Int,
    val name: String? = null,
    val knownForDepartment: String? = null,
    @SerializedName("profile_path") val profilePath: String? = null,
) {
    @SerializedName("known_for")
    val knownFor: List<TmdbMultiResult> = emptyList()
}

data class TmdbMultiResult(
    val id: Int,
    @SerializedName("media_type") val mediaType: String? = null,
    val title: String? = null,
    val name: String? = null,
    @SerializedName("poster_path") val posterPath: String? = null,
    @SerializedName("profile_path") val profilePath: String? = null,
    @SerializedName("release_date") val releaseDate: String? = null,
    @SerializedName("first_air_date") val firstAirDate: String? = null,
    @SerializedName("vote_average") val voteAverage: Double = 0.0,
    val overview: String? = null,
) {
    val displayTitle: String?
        get() = title ?: name
    val displayDate: String?
        get() = releaseDate ?: firstAirDate
}

data class TmdbGenre(val id: Int, val name: String? = null)

data class TmdbCredits(
    val cast: List<TmdbCastMember> = emptyList(),
    val crew: List<TmdbCrewMember> = emptyList(),
)

data class TmdbCastMember(
    val id: Int,
    val name: String? = null,
    val character: String? = null,
    @SerializedName("profile_path") val profilePath: String? = null,
    val order: Int = 0,
)

data class TmdbCrewMember(
    val id: Int,
    val name: String? = null,
    val job: String? = null,
    val department: String? = null,
    @SerializedName("profile_path") val profilePath: String? = null,
)

data class TmdbVideo(
    val id: String? = null,
    val key: String? = null,
    val site: String? = null,
    val type: String? = null,
    val official: Boolean = false,
    val name: String? = null,
)

data class TmdbReview(
    val id: String? = null,
    val author: String? = null,
    val content: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("author_details") val authorDetails: TmdbReviewAuthorDetails? = null,
)

data class TmdbReviewAuthorDetails(
    val name: String? = null,
    val username: String? = null,
    @SerializedName("avatar_path") val avatarPath: String? = null,
    val rating: Double? = null,
)

data class TmdbMovieDetail(
    @SerializedName("append_to_response") val appendToResponse: String? = null,
    val id: Int,
    val title: String? = null,
    @SerializedName("original_title") val originalTitle: String? = null,
    val overview: String? = null,
    @SerializedName("poster_path") val posterPath: String? = null,
    @SerializedName("backdrop_path") val backdropPath: String? = null,
    @SerializedName("release_date") val releaseDate: String? = null,
    val runtime: Int? = null,
    @SerializedName("vote_average") val voteAverage: Double = 0.0,
    @SerializedName("vote_count") val voteCount: Int = 0,
    val genres: List<TmdbGenre> = emptyList(),
    val tagline: String? = null,
    val status: String? = null,
    val budget: Long = 0,
    val revenue: Long = 0,
    val credits: TmdbCredits? = null,
    val videos: TmdbVideoListResponse? = null,
    val reviews: TmdbPage<TmdbReview>? = null,
    val similar: TmdbPage<TmdbMovie>? = null,
    val recommendations: TmdbPage<TmdbMovie>? = null,
    @SerializedName("release_dates") val releaseDates: TmdbReleaseDatesResponse? = null,
) {
    val year: Int?
        get() = releaseDate?.takeIf { it.length >= 4 }?.substring(0, 4)?.toIntOrNull()
}

data class TmdbVideoListResponse(val results: List<TmdbVideo> = emptyList())

data class TmdbReleaseDatesResponse(val results: List<TmdbReleaseDatesResult> = emptyList())

data class TmdbReleaseDatesResult(
    @SerializedName("iso_3166_1") val iso31661: String? = null,
    @SerializedName("release_dates") val releaseDates: List<TmdbReleaseDateInfo> = emptyList(),
)

data class TmdbReleaseDateInfo(
    val certification: String? = null,
    @SerializedName("release_date") val releaseDate: String? = null,
)

data class TmdbContentRatingsResponse(val results: List<TmdbContentRating> = emptyList())

data class TmdbSeasonDetail(
    val id: Int = 0,
    val name: String? = null,
    val episodes: List<TmdbEpisode> = emptyList(),
)

data class TmdbEpisode(
    val id: Int = 0,
    @SerializedName("episode_number") val episodeNumber: Int = 0,
    @SerializedName("season_number") val seasonNumber: Int = 0,
    val name: String? = null,
    val overview: String? = null,
    @SerializedName("still_path") val stillPath: String? = null,
    @SerializedName("air_date") val airDate: String? = null,
    @SerializedName("vote_average") val voteAverage: Double = 0.0,
)

data class TmdbContentRating(
    @SerializedName("iso_3166_1") val iso31661: String? = null,
    val rating: String? = null,
)

data class TmdbTvDetail(
    val id: Int,
    val name: String? = null,
    @SerializedName("original_name") val originalName: String? = null,
    val overview: String? = null,
    @SerializedName("poster_path") val posterPath: String? = null,
    @SerializedName("backdrop_path") val backdropPath: String? = null,
    @SerializedName("first_air_date") val firstAirDate: String? = null,
    @SerializedName("vote_average") val voteAverage: Double = 0.0,
    @SerializedName("vote_count") val voteCount: Int = 0,
    @SerializedName("episode_run_time") val episodeRunTime: List<Int> = emptyList(),
    @SerializedName("number_of_seasons") val numberOfSeasons: Int = 0,
    @SerializedName("number_of_episodes") val numberOfEpisodes: Int = 0,
    val genres: List<TmdbGenre> = emptyList(),
    val tagline: String? = null,
    val credits: TmdbCredits? = null,
    val videos: TmdbVideoListResponse? = null,
    val reviews: TmdbPage<TmdbReview>? = null,
    val similar: TmdbPage<TmdbTvShow>? = null,
    @SerializedName("content_ratings") val contentRatings: TmdbContentRatingsResponse? = null,
) {
    val year: Int?
        get() = firstAirDate?.takeIf { it.length >= 4 }?.substring(0, 4)?.toIntOrNull()
}

// ---------- App-domain UI models (unified across movie/tv) ----------

/**
 * A normalized title the whole UI renders. `mediaType` is "movie" or "tv".
 */
data class Title(
    val id: Int,
    val mediaType: String,
    val name: String,
    val overview: String?,
    val posterPath: String?,
    val backdropPath: String?,
    val year: Int?,
    val rating: Double,
    val genreIds: List<Int> = emptyList(),
)

data class CastMemberUi(val id: Int, val name: String, val character: String?, val profilePath: String?)

data class ReviewUi(
    val id: String,
    val author: String,
    val content: String,
    val rating: Double?,
    val createdAt: String?,
)

data class VideoUi(val key: String, val name: String?, val site: String?, val type: String?)
