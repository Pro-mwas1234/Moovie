package com.Moovie.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import com.Moovie.app.data.local.AppPrefs
import com.Moovie.app.ui.navigation.RootNav
import com.Moovie.app.ui.onboarding.OnboardingFlow
import com.Moovie.app.ui.splash.SplashScreen
import com.Moovie.app.ui.theme.MoovieTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val prefs = ServiceLocator.prefs.prefs.collectAsState(initial = null).value
            when (prefs) {
                null -> MoovieTheme { SplashScreen() }
                else -> MoovieTheme(darkTheme = prefs.darkMode) {
                    if (!prefs.onboarded) {
                        OnboardingFlow()
                    } else {
                        RootNav()
                    }
                }
            }
        }
    }
}
