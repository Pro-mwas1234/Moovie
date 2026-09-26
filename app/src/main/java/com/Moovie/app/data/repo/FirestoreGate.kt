package com.Moovie.app.data.repo

import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings

/**
 * True when a google-services.json was bundled, so Firestore has a real project
 * to talk to. Without it every Firebase repo works against an in-memory fake,
 * letting the whole UI run before Firebase is set up.
 */
object FirestoreGate {

    @Volatile
    var configured: Boolean = false
        private set

    @Volatile
    private var instance: FirebaseFirestore? = null

    fun init(apps: List<FirebaseApp>) {
        configured = apps.isNotEmpty()
    }

    fun db(): FirebaseFirestore? {
        if (!configured) return null
        return instance ?: synchronized(this) {
            instance ?: FirebaseFirestore.getInstance().also { db ->
                db.firestoreSettings = FirebaseFirestoreSettings.Builder()
                    .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                    .build()
            }.also { instance = it }
        }
    }
}
