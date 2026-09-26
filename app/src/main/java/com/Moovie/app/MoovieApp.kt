package com.Moovie.app

import android.app.Application
import com.google.firebase.FirebaseApp
import com.Moovie.app.data.repo.FirestoreGate

class MoovieApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Safe to call even without google-services.json (it no-ops with a warning).
        FirebaseApp.initializeApp(this)
        // Only turn on cloud mode when a real Firebase project is bundled; otherwise
        // every repo falls back to the in-memory LocalDataStore.
        FirestoreGate.init(FirebaseApp.getApps(this))
        ServiceLocator.init(this)
    }
}
