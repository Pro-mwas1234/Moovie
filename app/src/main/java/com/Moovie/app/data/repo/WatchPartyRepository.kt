package com.Moovie.app.data.repo

import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import com.Moovie.app.ServiceLocator
import com.Moovie.app.data.model.ChatMessage
import com.Moovie.app.data.model.PartyRoom
import com.Moovie.app.data.model.PartyRoomState
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.security.SecureRandom

/**
 * Watch Party: Firestore-backed rooms that sync playback *state* (play/pause +
 * timestamp) and chat. Actual video playback happens in the provider app; this
 * syncs what everyone should be doing.
 */
class WatchPartyRepository {

    private val local get() = ServiceLocator.local

    private fun roomsCol() = FirestoreGate.db()?.collection("watch_parties")
    private fun room(code: String) = roomsCol()?.document(code)
    private fun chatCol(code: String) = room(code)?.collection("messages")

    fun generateCode(): String {
        val alphabet = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
        val rnd = SecureRandom()
        return buildString { repeat(6) { append(alphabet[rnd.nextInt(alphabet.length)]) } }
    }

    suspend fun createRoom(
        hostUid: String,
        hostName: String,
        mediaType: String,
        tmdbId: Int,
        titleName: String,
        posterPath: String?,
    ): Result<String> = runCatching {
        val code = generateCode()
        val r = PartyRoom(
            code = code,
            hostUid = hostUid,
            hostName = hostName,
            mediaType = mediaType,
            tmdbId = tmdbId,
            titleName = titleName,
            posterPath = posterPath,
            participants = mapOf(hostUid to hostName),
        )
        @Suppress("UNUSED_VARIABLE")
        val c = roomsCol()
        if (c == null) {
            local.roomFlow(code).value = PartyRoomState(r)
        } else {
            c.document(code).set(r.toMap()).await()
        }
        code
    }

    suspend fun joinRoom(code: String, uid: String, name: String): Result<PartyRoom> = runCatching {
        val cc = roomsCol()
        val normalized = code.trim().uppercase()
        val r: PartyRoom = if (cc == null) {
            local.roomFlow(normalized).value.room
                ?: error("Room not found. Check the code.")
        } else {
            val snap = cc.document(normalized).get().await()
            snap.data?.let { roomFromMap(snap.id, it) }
                ?: error("Room not found. Check the code.")
        }
        if (cc != null) {
            cc.document(normalized).set(
                mapOf("participants" to mapOf(uid to name)),
                com.google.firebase.firestore.SetOptions.merge(),
            ).await()
        } else {
            val s = local.roomFlow(normalized).value
            s.room?.let {
                local.roomFlow(normalized).value =
                    s.copy(room = it.copy(participants = it.participants + (uid to name)))
            }
        }
        r
    }

    fun roomState(code: String): Flow<PartyRoomState> {
        val c = roomsCol()
        if (c == null) return local.roomFlow(code.uppercase())
        return callbackFlow {
            val reg = c.document(code.uppercase())
                .addSnapshotListener { snap, err ->
                    if (err != null) { close(err); return@addSnapshotListener }
                    val r = snap?.data?.let { roomFromMap(snap.id, it) }
                    trySend(PartyRoomState(r))
                }
            awaitClose { reg.remove() }
        }
    }

    /** Host-only: broadcast new playback state. */
    suspend fun setPlaybackState(code: String, playing: Boolean, positionSeconds: Long): Result<Unit> = runCatching {
        val c = roomsCol() ?: run {
            val s = local.roomFlow(code.uppercase()).value
            s.room?.let { local.roomFlow(code.uppercase()).value = s.copy(room = it.copy(playing = playing, positionSeconds = positionSeconds)) }
            return Result.success(Unit)
        }
        c.document(code.uppercase()).update(
            mapOf(
                "playing" to playing,
                "positionSeconds" to positionSeconds,
                "stateUpdatedAt" to com.google.firebase.Timestamp.now(),
            )
        ).await()
    }

    fun chat(code: String): Flow<List<ChatMessage>> {
        val c = chatCol(code.uppercase())
        if (c == null) return local.chatFlow(code.uppercase())
        return callbackFlow {
            val reg = c.addSnapshotListener { snap, err ->
                if (err != null) { close(err); return@addSnapshotListener }
                val list = snap?.documents?.mapNotNull { doc ->
                    val d = doc.data ?: return@mapNotNull null
                    ChatMessage(
                        id = doc.id,
                        uid = d["uid"] as? String ?: "",
                        name = d["name"] as? String ?: "",
                        text = d["text"] as? String ?: "",
                        createdAt = d["createdAt"] as? com.google.firebase.Timestamp,
                    )
                }?.sortedBy { it.createdAt?.seconds ?: 0 } ?: emptyList()
                trySend(list)
            }
            awaitClose { reg.remove() }
        }
    }

    suspend fun sendChat(code: String, uid: String, name: String, text: String): Result<Unit> = runCatching {
        val c = chatCol(code.uppercase()) ?: run {
            val flow = local.chatFlow(code.uppercase())
            flow.value = flow.value + ChatMessage(
                uid = uid, name = name, text = text,
                createdAt = com.google.firebase.Timestamp.now(),
            )
            return Result.success(Unit)
        }
        c.add(
            mapOf(
                "uid" to uid,
                "name" to name,
                "text" to text,
                "createdAt" to com.google.firebase.Timestamp.now(),
            )
        ).await()
        Unit
    }

    private fun PartyRoom.toMap(): Map<String, Any?> = mapOf(
        "code" to code,
        "hostUid" to hostUid,
        "hostName" to hostName,
        "mediaType" to mediaType,
        "tmdbId" to tmdbId,
        "titleName" to titleName,
        "posterPath" to posterPath,
        "playing" to playing,
        "positionSeconds" to positionSeconds,
        "stateUpdatedAt" to com.google.firebase.Timestamp.now(),
        "participants" to participants,
    )

    private fun roomFromMap(id: String, m: Map<String, Any?>): PartyRoom = PartyRoom(
        code = id,
        hostUid = m["hostUid"] as? String ?: "",
        hostName = m["hostName"] as? String ?: "",
        mediaType = m["mediaType"] as? String ?: "movie",
        tmdbId = (m["tmdbId"] as? Long)?.toInt() ?: (m["tmdbId"] as? Int) ?: 0,
        titleName = m["titleName"] as? String ?: "",
        posterPath = m["posterPath"] as? String,
        playing = m["playing"] as? Boolean ?: false,
        positionSeconds = (m["positionSeconds"] as? Long) ?: 0L,
        participants = (m["participants"] as? Map<*, *>)?.mapKeys { it.key.toString() }?.mapValues { it.value.toString() } ?: emptyMap(),
    )
}
