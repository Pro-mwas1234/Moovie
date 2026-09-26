package com.Moovie.app.ui.screens.auth

import android.content.Context
import android.util.Patterns
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.Moovie.app.ServiceLocator
import com.Moovie.app.data.repo.AuthUser
import com.Moovie.app.data.repo.authMessage
import com.Moovie.app.ui.navigation.Routes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class AuthViewModel : ViewModel() {
    val user = ServiceLocator.auth.user
    val busy = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    val notice = MutableStateFlow<String?>(null)

    fun signIn(email: String, password: String, onDone: () -> Unit) {
        if (!validate(email, password)) return
        viewModelScope.launch {
            busy.value = true; error.value = null; notice.value = null
            ServiceLocator.auth.signIn(email, password)
                .onSuccess { onDone() }
                .onFailure { error.value = it.authMessage() }
            busy.value = false
        }
    }

    fun signUp(email: String, password: String, name: String, onDone: () -> Unit) {
        if (!validate(email, password)) return
        viewModelScope.launch {
            busy.value = true; error.value = null; notice.value = null
            ServiceLocator.auth.signUp(email, password, name)
                .onSuccess { onDone() }
                .onFailure { error.value = it.authMessage() }
            busy.value = false
        }
    }

    fun google(context: Context, onDone: () -> Unit) {
        viewModelScope.launch {
            busy.value = true; error.value = null; notice.value = null
            ServiceLocator.auth.googleSignIn(context)
                .onSuccess { onDone() }
                .onFailure {
                    // Silently ignore the user closing the Google sheet.
                    if (it !is androidx.credentials.exceptions.GetCredentialCancellationException) {
                        error.value = it.authMessage()
                    }
                }
            busy.value = false
        }
    }

    fun guest(onDone: () -> Unit) {
        viewModelScope.launch {
            busy.value = true; error.value = null
            ServiceLocator.auth.continueAsGuest()
                .onSuccess { onDone() }
                .onFailure { error.value = it.authMessage() }
            busy.value = false
        }
    }

    fun rename(name: String) {
        viewModelScope.launch {
            error.value = null
            ServiceLocator.auth.updateDisplayName(name)
                .onSuccess { notice.value = "Name updated" }
                .onFailure { error.value = it.authMessage() }
        }
    }

    fun signOut(context: Context) {
        viewModelScope.launch {
            ServiceLocator.auth.signOut(context)
            error.value = null
            notice.value = "Signed out"
        }
    }

    private fun validate(email: String, password: String): Boolean {
        if (!Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) {
            error.value = "Enter a valid email address."
            return false
        }
        if (password.length < 6) {
            error.value = "Password must be at least 6 characters."
            return false
        }
        return true
    }
}

@Composable
fun AuthScreen(nav: NavController, vm: AuthViewModel = viewModel()) {
    val user by vm.user.collectAsState()
    val busy by vm.busy.collectAsState()
    val error by vm.error.collectAsState()
    val notice by vm.notice.collectAsState()
    val canGoBack = nav.previousBackStackEntry != null

    val onDone: () -> Unit = {
        if (canGoBack) nav.popBackStack()
        else nav.navigate(Routes.HOME) { popUpTo(0) }
    }
    val context = LocalContext.current

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        if (canGoBack) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { nav.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }
                Text("Account", style = MaterialTheme.typography.headlineSmall)
            }
            Spacer(Modifier.height(8.dp))
        }

        Text(
            "Moovie account",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "Sign in so your watchlist, downloads, reviews and watch parties follow you to any device.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        if (!ServiceLocator.auth.isCloud) {
            Text(
                "Local mode: Firebase isn't configured, so accounts stay on this device.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        Spacer(Modifier.height(20.dp))

        val current = user
        if (current != null && !current.isAnonymous) {
            SignedInCard(
                user = current,
                busy = busy,
                onRename = vm::rename,
                onSignOut = { vm.signOut(context) },
            )
        } else {
            AuthForm(
                isGuest = current?.isAnonymous == true,
                busy = busy,
                onSignIn = { e, p -> vm.signIn(e, p, onDone) },
                onSignUp = { e, p, n -> vm.signUp(e, p, n, onDone) },
                onGoogle = { vm.google(context, onDone) },
                onGuest = { vm.guest(onDone) },
            )
        }

        Spacer(Modifier.height(12.dp))
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        notice?.let {
            Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun AuthForm(
    isGuest: Boolean,
    busy: Boolean,
    onSignIn: (String, String) -> Unit,
    onSignUp: (String, String, String) -> Unit,
    onGoogle: () -> Unit,
    onGuest: () -> Unit,
) {
    var mode by remember { mutableIntStateOf(0) } // 0 sign in, 1 create
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }

    if (isGuest) {
        Text(
            "You're browsing as a guest. Create an account to keep everything you've saved — " +
                "we'll upgrade this guest in place.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 12.dp),
        )
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = mode == 0,
            onClick = { mode = 0 },
            label = { Text("Sign in") },
        )
        FilterChip(
            selected = mode == 1,
            onClick = { mode = 1 },
            label = { Text("Create account") },
        )
    }
    Spacer(Modifier.height(12.dp))

    if (mode == 1) {
        OutlinedTextField(
            value = displayName,
            onValueChange = { displayName = it },
            label = { Text("Display name") },
            leadingIcon = { Icon(Icons.Filled.Person, null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
    }

    OutlinedTextField(
        value = email,
        onValueChange = { email = it },
        label = { Text("Email") },
        leadingIcon = { Icon(Icons.Filled.Email, null) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(10.dp))
    OutlinedTextField(
        value = password,
        onValueChange = { password = it },
        label = { Text("Password") },
        leadingIcon = { Icon(Icons.Filled.Lock, null) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(16.dp))

    Button(
        enabled = !busy && email.isNotBlank() && password.isNotBlank(),
        onClick = {
            if (mode == 0) onSignIn(email, password)
            else onSignUp(email, password, displayName)
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (busy) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        } else {
            Text(if (mode == 0) "Sign in" else "Create account")
        }
    }

    val googleConfigured = com.Moovie.app.BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()
    if (googleConfigured) {
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            enabled = !busy,
            onClick = onGoogle,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("G", color = androidx.compose.ui.graphics.Color(0xFF4285F4), fontWeight = FontWeight.Bold)
            Text("  Continue with Google")
        }
    }

    if (!isGuest) {
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            enabled = !busy,
            onClick = onGuest,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Continue as guest") }
    }
}

@Composable
private fun SignedInCard(
    user: AuthUser,
    busy: Boolean,
    onRename: (String) -> Unit,
    onSignOut: () -> Unit,
) {
    var name by remember(user.uid) { mutableStateOf(user.name ?: "") }

    Box(
        Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.Person,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(40.dp),
        )
    }
    Spacer(Modifier.height(12.dp))
    Text(user.name ?: "You", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Text(
        user.email ?: "Signed in",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(16.dp))

    OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        label = { Text("Display name") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
    OutlinedButton(
        enabled = !busy && name.isNotBlank() && name != (user.name ?: ""),
        onClick = { onRename(name) },
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Save name") }
    Spacer(Modifier.height(8.dp))
    OutlinedButton(
        onClick = onSignOut,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(Icons.AutoMirrored.Filled.Logout, null)
        Text(" Sign out")
    }
}
