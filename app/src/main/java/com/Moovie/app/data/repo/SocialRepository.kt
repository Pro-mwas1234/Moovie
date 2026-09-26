package com.Moovie.app.data.repo

import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import com.Moovie.app.ServiceLocator
import com.Moovie.app.data.model.NotificationItem
import com.Moovie.app.data.model.ReviewPost
import com.Moovie.app.data.model.UserProfile
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class SocialRepository {

    private val local get() = ServiceLocator.local

    private fun reviewsCol() = FirestoreGate.db()?.collection("reviews")
    private fun profilesCol() = FirestoreGate.db()?.collection("profiles")
    private fun followsCol(uid: String) = FirestoreGate.db()?.collection("users/$uid/following")
    private fun notifsCol(uid: String) = FirestoreGate.db()?.collection("users/$uid/notifications")

    // ---------- Reviews ----------

    fun reviewsForTitle(key: String): Flow<List<ReviewPost>> {
        val c = reviewsCol()
        if (c == null) return local.reviewFlow(key)
        return callbackFlow {
            val reg = c.whereEqualTo("key", key)
                .addSnapshotListener { snap, err ->
                    if (err != null) { close(err); return@addSnapshotListener }
                    val list = snap?.documents?.mapNotNull { ReviewPost.fromMap(it.id, it.data ?: emptyMap()) } ?: emptyList()
                    trySend(list)
                }
            awaitClose { reg.remove() }
        }
    }

    fun myReviews(uid: String): Flow<List<ReviewPost>> {
        val c = reviewsCol()
        if (c == null) {
            return local.allReviews.map { list -> list.filter { it.uid == uid } }
        }
        return callbackFlow {
            val reg = c.whereEqualTo("uid", uid)
                .addSnapshotListener { snap, err ->
                    if (err != null) { close(err); return@addSnapshotListener }
                    val list = snap?.documents?.mapNotNull { ReviewPost.fromMap(it.id, it.data ?: emptyMap()) } ?: emptyList()
                    trySend(list)
                }
            awaitClose { reg.remove() }
        }
    }

    suspend fun postReview(review: ReviewPost): Result<Unit> = runCatching {
        val c = reviewsCol() ?: run {
            local.reviewFlow(review.key).value = listOf(review) + local.reviewFlow(review.key).value
            local.allReviews.value = listOf(review) + local.allReviews.value
            return Result.success(Unit)
        }
        c.add(review.toMap()).await()
        Unit
    }

    suspend fun deleteReview(id: String): Result<Unit> = runCatching {
        val c = reviewsCol() ?: return Result.success(Unit)
        c.document(id).delete().await()
    }

    // ---------- Feed ----------

    fun feed(uid: String): Flow<List<ReviewPost>> {
        val c = reviewsCol()
        if (c == null) {
            return local.allReviews
        }
        return callbackFlow {
            val followees = (followingOnce(uid) + uid).distinct().take(30)
            val reg = c.whereIn("uid", followees)
                .addSnapshotListener { snap, err ->
                    if (err != null) { close(err); return@addSnapshotListener }
                    val list = snap?.documents?.mapNotNull { ReviewPost.fromMap(it.id, it.data ?: emptyMap()) } ?: emptyList()
                    trySend(list)
                }
            awaitClose { reg.remove() }
        }
    }

    suspend fun feedOnce(uid: String): List<ReviewPost> {
        val c = reviewsCol() ?: return local.reviews.values.flatMap { it.value }
        val followees = followingOnce(uid) + uid
        return try {
            // Firestore whereIn supports up to 30 values
            c.whereIn("uid", followees.take(30)).get().await()
                .documents.mapNotNull { ReviewPost.fromMap(it.id, it.data ?: emptyMap()) }
                .sortedByDescending { it.createdAt?.seconds ?: 0 }
        } catch (_: Exception) {
            emptyList()
        }
    }

    // ---------- Follows ----------

    fun following(uid: String): Flow<List<String>> {
        val c = followsCol(uid)
        if (c == null) return local.follows.map { it.toList() }
        return callbackFlow {
            val reg = c.addSnapshotListener { snap, err ->
                if (err != null) { close(err); return@addSnapshotListener }
                trySend(snap?.documents?.map { it.id } ?: emptyList())
            }
            awaitClose { reg.remove() }
        }
    }

    suspend fun followingOnce(uid: String): List<String> {
        val c = followsCol(uid) ?: return local.follows.value.toList()
        return try { c.get().await().documents.map { it.id } } catch (_: Exception) { emptyList() }
    }

    suspend fun setFollowing(uid: String, targetUid: String, follow: Boolean): Result<Unit> = runCatching {
        val c = followsCol(uid) ?: run {
            local.follows.value = if (follow) local.follows.value + targetUid
            else local.follows.value - targetUid
            return Result.success(Unit)
        }
        if (follow) {
            c.document(targetUid).set(mapOf("since" to System.currentTimeMillis())).await()
            // Notify the followed user
            notifsCol(targetUid)?.add(
                NotificationItem(
                    type = NotificationItem.TYPE_NEW_FOLLOWER,
                    uid = uid,
                    text = "Someone new started following you",
                ).let { mapOf("type" to it.type, "uid" to it.uid, "text" to it.text, "read" to false, "createdAt" to com.google.firebase.Timestamp.now()) }
            )?.await()
        } else {
            c.document(targetUid).delete().await()
        }
    }

    // ---------- Profiles ----------

    suspend fun profile(uid: String): UserProfile? {
        @Suppress("NAME_SHADOWING") val uid = uid
        val c = profilesCol() ?: return UserProfile(uid = uid, name = ServiceLocator.auth.name ?: "You")
        return try {
            c.document(uid).get().await().data?.let { UserProfile.fromMap(uid, it) }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun updateProfile(uid: String, name: String, bio: String): Result<Unit> = runCatching {
        local.setProfileName(name)
        val c = profilesCol() ?: return Result.success(Unit)
        c.document(uid).set(
            mapOf("uid" to uid, "name" to name, "bio" to bio),
            com.google.firebase.firestore.SetOptions.merge(),
        ).await()
    }

    suspend fun searchProfiles(query: String): List<UserProfile> {
        val c = profilesCol() ?: return emptyList()
        return try {
            val q = query.trim()
            if (q.isEmpty()) return emptyList()
            val byName = c.whereGreaterThanOrEqualTo("name", q)
                .whereLessThanOrEqualTo("name", q + "\uf8ff").get().await()
            byName.documents.mapNotNull { UserProfile.fromMap(it.id, it.data ?: emptyMap()) }
        } catch (_: Exception) { emptyList() }
    }

    // ---------- Notifications ----------

    fun notifications(uid: String): Flow<List<NotificationItem>> {
        val c = notifsCol(uid)
        if (c == null) return local.notifications
        return callbackFlow {
            val reg = c.addSnapshotListener { snap, err ->
                if (err != null) { close(err); return@addSnapshotListener }
                val list = snap?.documents?.mapNotNull { NotificationItem.fromMap(it.id, it.data ?: emptyMap()) } ?: emptyList()
                trySend(list.sortedByDescending { it.createdAt?.seconds ?: 0 })
            }
            awaitClose { reg.remove() }
        }
    }

    suspend fun markNotificationsRead(uid: String): Result<Unit> = runCatching {
        local.notifications.value = local.notifications.value.map { it.copy(read = true) }
        val c = notifsCol(uid) ?: return Result.success(Unit)
        val unread = try { c.whereEqualTo("read", false).get().await() } catch (_: Exception) { return Result.success(Unit) }
        val batch = FirestoreGate.db()?.batch()
        unread.documents.forEach { batch?.update(it.reference, "read", true) }
        batch?.commit()?.await()
    }
}
