import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Apply google-services only when a Firebase config is present, so the project
// builds before Firebase is set up. The plugin classpath comes from the root
// build file's `apply false` declaration.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

// Reads API keys from local.properties and exposes them as BuildConfig fields.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) FileInputStream(f).use { load(it) }
}

// GitHub repo used by the in-app updater (owner/repo). Set in gradle.properties
// (committed, so CI builds get it) or override in local.properties.
// Example: github.repo=yourname/Moovie. Empty disables update checks gracefully.
val githubRepo = localProps.getProperty("github.repo")
    ?: project.findProperty("github.repo") as? String
    ?: ""

// Extracts the OAuth *Web* client id (client_type 3) from google-services.json.
// Firebase Auth needs it as the ID-token audience for Google sign-in; it lives in
// the Firebase config already, so it's parsed at build time rather than hardcoded.
fun webClientId(): String = try {
    val gs = file("google-services.json")
    if (!gs.exists()) ""
    else {
        val json = groovy.json.JsonSlurper().parse(gs) as Map<*, *>
        @Suppress("UNCHECKED_CAST")
        val clients = (json["client"] as? List<Map<*, *>>) ?: emptyList()
        clients.firstNotNullOfOrNull { c ->
            @Suppress("UNCHECKED_CAST")
            (c["oauth_client"] as? List<Map<*, *>>)
                ?.firstOrNull { (it["client_type"] as? String) == "3" }
                ?.get("client_id") as? String
        } ?: ""
    }
} catch (_: Exception) {
    ""
}

android {
    namespace = "com.Moovie.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.Moovie.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 9
        versionName = "1.8"

        buildConfigField("String", "TMDB_API_KEY", "\"${localProps.getProperty("tmdb.api.key") ?: ""}\"")
        buildConfigField("String", "OPENAI_API_KEY", "\"${localProps.getProperty("openai.api.key") ?: ""}\"")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${webClientId()}\"")
        buildConfigField("String", "GITHUB_REPO", "\"${githubRepo}\"")
    }

    signingConfigs {
        if (System.getenv("MOOVIE_STORE_FILE") != null) {
            create("ci") {
                storeFile = file(System.getenv("MOOVIE_STORE_FILE"))
                storePassword = System.getenv("MOOVIE_STORE_PASSWORD")
                keyAlias = System.getenv("MOOVIE_KEY_ALIAS")
                keyPassword = System.getenv("MOOVIE_KEY_PASSWORD")
            }
        }
        // Committed so all builds (local + CI) share one stable signature.
        create("committed") {
            storeFile = file("../keystore/moovie-release.jks")
            storePassword = "moovie-release"
            keyAlias = "moovie"
            keyPassword = "moovie-release"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Signing priority: CI secrets > committed release keystore > debug.
            // The committed keystore keeps every release signed with the SAME
            // key, so in-app updates install over the old version instead of
            // colliding ("App not installed" signature mismatch).
            signingConfig = when {
                System.getenv("MOOVIE_STORE_FILE") != null -> signingConfigs.getByName("ci")
                file("../keystore/moovie-release.jks").exists() -> signingConfigs.getByName("committed")
                else -> signingConfigs.getByName("debug")
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    // Compose
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.5")

    // Images
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Networking (TMDB)
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Native player (direct MP4 streams)
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
    implementation("androidx.media3:media3-datasource-okhttp:1.5.1")

    // Firebase (auth + firestore + realtime for watch party)
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")

    // Google Sign-In (Credential Manager + googleid ID tokens)
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    // Local prefs (recents, settings)
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
