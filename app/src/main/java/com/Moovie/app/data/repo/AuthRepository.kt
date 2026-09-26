package com.Moovie.app.data.repo

import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.Firebase
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.firestore.SetOptions
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

/** Lightweight, UI-facing view of the signed-in user. */
data class AuthUser(
    val uid: String,
    val name: String?,
    val email: String?,
    val isAnonymous: Boolean,
)

/**
 * Firebase Auth wrapper with a reactive [user] flow. When FirestoreGate isn't
 * configured (no google-services.json) it falls back to a local pseudo-user so
 * the app still runs, but accounts only sync across devices in cloud mode.
 *
 * Signing up while an anonymous guest links the credential to the existing uid,
 * so a guest's watchlist/downloads/party history carry over instead of being lost.
 */
class AuthRepository {

    private val _user = MutableStateFlow<AuthUser?>(null)
    val user: StateFlow<AuthUser?> = _user.asStateFlow()

    val isCloud: Boolean get() = FirestoreGate.configured

    init {
        if (isCloud) {
            runCatching {
                Firebase.auth.addAuthStateListener { fa ->
                    _user.value = fa.currentUser?.let {
                        AuthUser(it.uid, it.displayName, it.email, it.isAnonymous)
                    }
                }
            }
        }
    }

    private fun firebaseUser() = runCatching { Firebase.auth.currentUser }.getOrNull()

    val uid: String?
        get() = if (isCloud) firebaseUser()?.uid else _user.value?.uid ?: LOCAL_UID

    /**
     * Like [uid] but self-heals: if cloud mode has no session (e.g. onboarding's
     * anonymous sign-in failed or was skipped), signs in as a guest first.
     * Use this on any action that must not silently no-op — downloads, ratings,
     * watchlist — instead of the plain `uid ?: return` pattern.
     */
    suspend fun ensureUid(): String? {
        if (!isCloud) {
            if (_user.value == null) {
                _user.value = AuthUser(LOCAL_UID, "Guest", null, true)
            }
            return _user.value?.uid
        }
        firebaseUser()?.let { return it.uid }
        // No session: try to mint an anonymous one in place.
        runCatching { Firebase.auth.signInAnonymously().await() }
            .onFailure { android.util.Log.w("AuthRepository", "Anonymous sign-in failed: ${it.message}") }
        return firebaseUser()?.uid
    }

    val name: String?
        get() = if (isCloud) firebaseUser()?.displayName ?: "You" else _user.value?.name ?: "You"

    val isLoggedIn: Boolean get() = uid != null

    /** True for a guest (Firebase anonymous) account with no email attached. */
    val isAnonymous: Boolean
        get() = if (isCloud) firebaseUser()?.isAnonymous ?: false
        else _user.value?.isAnonymous ?: true

    suspend fun signUp(email: String, password: String, displayName: String): Result<Unit> = runCatching {
        if (!isCloud) {
            _user.value = AuthUser(LOCAL_UID, displayName.ifBlank { "You" }, email.trim(), false)
            return Result.success(Unit)
        }
        val auth = Firebase.auth
        val current = auth.currentUser
        val user = if (current != null && current.isAnonymous) {
            // Upgrade the guest in place so their existing uid (and data) is kept.
            current.linkWithCredential(EmailAuthProvider.getCredential(email.trim(), password))
                .await().user
        } else {
            auth.createUserWithEmailAndPassword(email.trim(), password).await().user
        } ?: error("Could not create the account")

        if (displayName.isNotBlank()) {
            val dn = displayName.trim()
            user.updateProfile(userProfileChangeRequest { this.displayName = dn }).await()
        }
        FirestoreGate.db()?.collection("profiles")
            ?.document(user.uid)
            ?.set(mapOf("uid" to user.uid, "name" to displayName.trim().ifBlank { "You" }), SetOptions.merge())
            ?.await()
        Unit
    }

    suspend fun signIn(email: String, password: String): Result<Unit> = runCatching {
        if (!isCloud) {
            _user.value = AuthUser(LOCAL_UID, "You", email.trim(), false)
            return Result.success(Unit)
        }
        Firebase.auth.signInWithEmailAndPassword(email.trim(), password).await()
        Unit
    }

    /**
     * Google Sign-In via Credential Manager. Mints an ID token, exchanges it for
     * a Firebase credential, and — exactly like email sign-up — links it to the
     * current anonymous account so guest data survives.
     */
    suspend fun googleSignIn(activityContext: Context): Result<Unit> = runCatching {
        if (!isCloud) {
            _user.value = AuthUser(LOCAL_UID, "You", null, false)
            return Result.success(Unit)
        }
        val serverClientId = com.Moovie.app.BuildConfig.GOOGLE_WEB_CLIENT_ID
        if (serverClientId.isBlank()) error("Google sign-in isn't configured (no web client id).")

        val cm = CredentialManager.create(activityContext)
        val option = GetGoogleIdOption.Builder()
            .setServerClientId(serverClientId)
            .setFilterByAuthorizedAccounts(false)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val response = cm.getCredential(activityContext, request)
        val cred = response.credential
        if (cred !is CustomCredential || cred.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            error("Unexpected credential from Google")
        }
        val googleCred = GoogleIdTokenCredential.createFrom(cred.data)
        val firebaseCred = GoogleAuthProvider.getCredential(googleCred.idToken, null)

        val auth = Firebase.auth
        val current = auth.currentUser
        if (current != null && current.isAnonymous) {
            current.linkWithCredential(firebaseCred).await()
        } else {
            auth.signInWithCredential(firebaseCred).await()
        }
        Unit
    }

    /** Guest sign-in; local mode just seeds a pseudo-user. */
    suspend fun continueAsGuest(): Result<Unit> = runCatching {
        if (!isCloud) {
            _user.value = AuthUser(LOCAL_UID, "Guest", null, true)
            return Result.success(Unit)
        }
        val auth = Firebase.auth
        if (auth.currentUser == null) auth.signInAnonymously().await()
        Unit
    }

    suspend fun updateDisplayName(name: String): Result<Unit> = runCatching {
        val dn = name.trim()
        if (dn.isBlank()) return Result.success(Unit)
        if (!isCloud) {
            val u = _user.value
            _user.value = AuthUser(u?.uid ?: LOCAL_UID, dn, u?.email, u?.isAnonymous ?: true)
            return Result.success(Unit)
        }
        val user = Firebase.auth.currentUser ?: error("Not signed in")
        user.updateProfile(userProfileChangeRequest { this.displayName = dn }).await()
        FirestoreGate.db()?.collection("profiles")?.document(user.uid)
            ?.set(mapOf("uid" to user.uid, "name" to dn), SetOptions.merge())?.await()
        Unit
    }

    suspend fun signOut(activityContext: Context? = null) {
        if (isCloud) runCatching { Firebase.auth.signOut() }
        if (activityContext != null) {
            runCatching {
                CredentialManager.create(activityContext)
                    .clearCredentialState(ClearCredentialStateRequest())
            }
        }
        _user.value = null
    }

    companion object {
        const val LOCAL_UID = "local-user"
    }
}

/** Human-readable message for the common Firebase Auth failures. */
fun Throwable.authMessage(): String = when ((this as? FirebaseAuthException)?.errorCode) {
    "ERROR_INVALID_EMAIL" -> "That email doesn't look right."
    "ERROR_EMAIL_ALREADY_IN_USE" -> "That email is already registered — try signing in."
    "ERROR_WEAK_PASSWORD" -> "Password is too weak (use at least 6 characters)."
    "ERROR_WRONG_PASSWORD" -> "Wrong password."
    "ERROR_USER_NOT_FOUND" -> "No account found with that email."
    "ERROR_CREDENTIAL_ALREADY_IN_USE" -> "That email is already linked to another account."
    "ERROR_REQUIRES_RECENT_LOGIN" -> "Please sign in again to make this change."
    "ERROR_NETWORK_REQUEST_FAILED" -> "Network error — check your connection."
    "ERROR_TOO_MANY_REQUESTS" -> "Too many attempts. Try again in a bit."
    "ERROR_OPERATION_NOT_ALLOWED" -> "Email sign-in isn't enabled for this project yet."
    "ERROR_GOOGLE_SIGN_IN_FAILED", "ERROR_INVALID_CREDENTIAL" -> "Google sign-in failed — try another account."
    else -> message ?: "Something went wrong. Try again."
}
