package com.Moovie.app.data.model

import com.google.firebase.Timestamp

data class WatchItem(
    val key: String = "",
    val tmdbId: Int = 0,
    val mediaType: String = "movie",
    val titleName: String = "",
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val year: Int? = null,
    val rating: Double = 0.0,
    val genreIds: List<Int> = emptyList(),
    val runtimeMinutes: Int? = null,
    val status: String = STATUS_WANT,
    val progressMinutes: Int? = null,
    /** Last-played episode for TV, used by Continue Watching to resume. */
    val season: Int? = null,
    val episode: Int? = null,
    val addedAt: Timestamp? = null,
    val updatedAt: Timestamp? = null,
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "key" to key,
        "tmdbId" to tmdbId,
        "mediaType" to mediaType,
        "titleName" to titleName,
        "posterPath" to posterPath,
        "backdropPath" to backdropPath,
        "year" to year,
        "rating" to rating,
        "genreIds" to genreIds,
        "runtimeMinutes" to runtimeMinutes,
        "status" to status,
        "progressMinutes" to progressMinutes,
        "season" to season,
        "episode" to episode,
        "addedAt" to (addedAt ?: Timestamp.now()),
        "updatedAt" to Timestamp.now(),
    )

    companion object {
        const val STATUS_WANT = "want"
        const val STATUS_WATCHING = "watching"
        const val STATUS_WATCHED = "watched"

        fun fromMap(id: String, m: Map<String, Any?>): WatchItem = WatchItem(
            key = id,
            tmdbId = (m["tmdbId"] as? Long)?.toInt() ?: (m["tmdbId"] as? Int) ?: 0,
            mediaType = m["mediaType"] as? String ?: "movie",
            titleName = m["titleName"] as? String ?: "",
            posterPath = m["posterPath"] as? String,
            backdropPath = m["backdropPath"] as? String,
            year = (m["year"] as? Long)?.toInt(),
            rating = m["rating"] as? Double ?: 0.0,
            genreIds = (m["genreIds"] as? List<*>)?.mapNotNull { (it as? Long)?.toInt() } ?: emptyList(),
            runtimeMinutes = (m["runtimeMinutes"] as? Long)?.toInt(),
            status = m["status"] as? String ?: STATUS_WANT,
            progressMinutes = (m["progressMinutes"] as? Long)?.toInt(),
            season = (m["season"] as? Long)?.toInt(),
            episode = (m["episode"] as? Long)?.toInt(),
            addedAt = m["addedAt"] as? Timestamp,
            updatedAt = m["updatedAt"] as? Timestamp,
        )
    }
}

/**
 * A title the user has saved for offline use. Moovie does not rip streams, so
 * this tracks intent/progress: QUEUED means "want it offline", READY means the
 * user has it downloaded in their provider app.
 */
data class DownloadItem(
    val key: String = "",
    val tmdbId: Int = 0,
    val mediaType: String = "movie",
    val titleName: String = "",
    val posterPath: String? = null,
    val year: Int? = null,
    val rating: Double = 0.0,
    val season: Int? = null,
    val episode: Int? = null,
    val status: String = STATUS_QUEUED,
    val progressPercent: Int = 0,
    val localPath: String? = null,
    val subtitlePath: String? = null,
    val subtitleLang: String? = null,
    val addedAt: Timestamp? = null,
    val updatedAt: Timestamp? = null,
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "key" to key,
        "tmdbId" to tmdbId,
        "mediaType" to mediaType,
        "titleName" to titleName,
        "posterPath" to posterPath,
        "year" to year,
        "rating" to rating,
        "season" to season,
        "episode" to episode,
        "status" to status,
        "progressPercent" to progressPercent,
        "localPath" to localPath,
        "subtitlePath" to subtitlePath,
        "subtitleLang" to subtitleLang,
        "addedAt" to (addedAt ?: Timestamp.now()),
        "updatedAt" to Timestamp.now(),
    )

    companion object {
        const val STATUS_QUEUED = "queued"
        const val STATUS_DOWNLOADING = "downloading"
        const val STATUS_READY = "ready"
        const val STATUS_FAILED = "failed"

        fun fromMap(id: String, m: Map<String, Any?>): DownloadItem = DownloadItem(
            key = id,
            tmdbId = (m["tmdbId"] as? Long)?.toInt() ?: (m["tmdbId"] as? Int) ?: 0,
            mediaType = m["mediaType"] as? String ?: "movie",
            titleName = m["titleName"] as? String ?: "",
            posterPath = m["posterPath"] as? String,
            year = (m["year"] as? Long)?.toInt(),
            rating = m["rating"] as? Double ?: 0.0,
            season = (m["season"] as? Long)?.toInt(),
            episode = (m["episode"] as? Long)?.toInt(),
            status = m["status"] as? String ?: STATUS_QUEUED,
            progressPercent = (m["progressPercent"] as? Long)?.toInt() ?: 0,
            localPath = m["localPath"] as? String,
            subtitlePath = m["subtitlePath"] as? String,
            subtitleLang = m["subtitleLang"] as? String,
            addedAt = m["addedAt"] as? Timestamp,
            updatedAt = m["updatedAt"] as? Timestamp,
        )
    }
}

data class ReviewPost(
    val id: String = "",
    val key: String = "",
    val mediaType: String = "movie",
    val tmdbId: Int = 0,
    val titleName: String = "",
    val posterPath: String? = null,
    val uid: String = "",
    val authorName: String = "",
    val rating: Int? = null,
    val text: String = "",
    val createdAt: Timestamp? = null,
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "key" to key,
        "mediaType" to mediaType,
        "tmdbId" to tmdbId,
        "titleName" to titleName,
        "posterPath" to posterPath,
        "uid" to uid,
        "authorName" to authorName,
        "rating" to rating,
        "text" to text,
        "createdAt" to Timestamp.now(),
    )

    companion object {
        fun fromMap(id: String, m: Map<String, Any?>): ReviewPost = ReviewPost(
            id = id,
            key = m["key"] as? String ?: "",
            mediaType = m["mediaType"] as? String ?: "movie",
            tmdbId = (m["tmdbId"] as? Long)?.toInt() ?: (m["tmdbId"] as? Int) ?: 0,
            titleName = m["titleName"] as? String ?: "",
            posterPath = m["posterPath"] as? String,
            uid = m["uid"] as? String ?: "",
            authorName = m["authorName"] as? String ?: "",
            rating = (m["rating"] as? Long)?.toInt(),
            text = m["text"] as? String ?: "",
            createdAt = m["createdAt"] as? Timestamp,
        )
    }
}

data class UserProfile(
    val uid: String = "",
    val name: String = "",
    val handle: String = "",
    val bio: String = "",
    val createdAt: Timestamp? = null,
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "uid" to uid,
        "name" to name,
        "handle" to handle,
        "bio" to bio,
        "createdAt" to (createdAt ?: Timestamp.now()),
    )

    companion object {
        fun fromMap(id: String, m: Map<String, Any?>): UserProfile = UserProfile(
            uid = id,
            name = m["name"] as? String ?: "",
            handle = m["handle"] as? String ?: "",
            bio = m["bio"] as? String ?: "",
            createdAt = m["createdAt"] as? Timestamp,
        )
    }
}

data class ChatMessage(
    val id: String = "",
    val uid: String = "",
    val name: String = "",
    val text: String = "",
    val createdAt: Timestamp? = null,
)

data class PartyRoom(
    val code: String = "",
    val hostUid: String = "",
    val hostName: String = "",
    val mediaType: String = "movie",
    val tmdbId: Int = 0,
    val titleName: String = "",
    val posterPath: String? = null,
    val playing: Boolean = false,
    val positionSeconds: Long = 0,
    val stateUpdatedAt: Timestamp? = null,
    val participants: Map<String, String> = emptyMap(),
)

/** Snapshot of a party room as seen by a listener. */
data class PartyRoomState(
    val room: PartyRoom? = null,
)

 data class NotificationItem(
    val id: String = "",
    val type: String = TYPE_GENERIC,
    val text: String = "",
    val uid: String = "",
    val key: String = "",
    val createdAt: Timestamp? = null,
    val read: Boolean = false,
) {
    companion object {
        const val TYPE_GENERIC = "generic"
        const val TYPE_NEW_FOLLOWER = "new_follower"
        const val TYPE_NEW_REVIEW_FROM_FOLLOWEE = "review_followee"
        const val TYPE_RESUME_REMINDER = "resume_reminder"
        const val TYPE_DISCOVERY = "discovery"

        fun fromMap(id: String, m: Map<String, Any?>): NotificationItem = NotificationItem(
            id = id,
            type = m["type"] as? String ?: TYPE_GENERIC,
            text = m["text"] as? String ?: "",
            uid = m["uid"] as? String ?: "",
            key = m["key"] as? String ?: "",
            createdAt = m["createdAt"] as? Timestamp,
            read = m["read"] as? Boolean ?: false,
        )
    }
}
