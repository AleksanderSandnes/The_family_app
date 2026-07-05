package com.example.mainactivity

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.example.mainactivity.data.FamilyRepository
import com.example.mainactivity.data.ThemeMode
import com.example.mainactivity.data.remote.SupabaseManager
import com.example.mainactivity.notifications.NotificationHelper
import com.example.mainactivity.ui.navigation.FamilyApp
import com.example.mainactivity.ui.theme.TheFamilyAppTheme
import dagger.hilt.android.AndroidEntryPoint
import io.github.jan.supabase.auth.handleDeeplinks
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var repo: FamilyRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        NotificationHelper.createAllChannels(this)
        handleAuthDeepLink(intent)
        setContent {
            val themeMode by repo.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
            val darkTheme =
                when (themeMode) {
                    ThemeMode.LIGHT -> false
                    ThemeMode.DARK -> true
                    ThemeMode.SYSTEM -> isSystemInDarkTheme()
                }
            TheFamilyAppTheme(darkTheme = darkTheme) {
                FamilyApp()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        androidx.core.app.NotificationManagerCompat
            .from(this)
            .cancelAll()
        lifecycleScope.launch { repo.touchLastActive() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleAuthDeepLink(intent)
    }

    private fun handleAuthDeepLink(intent: Intent) {
        SupabaseManager.client.handleDeeplinks(intent)
        // Family invite: familyapp://join?code=XXXX — stash the code so FamilyScreen
        // can open the join flow once the user is signed in.
        val data = intent.data
        if (data?.scheme == "familyapp" && data.host == "join") {
            data.getQueryParameter("code")?.trim()?.takeIf { it.isNotEmpty() }?.let {
                repo.setPendingJoinCode(it.uppercase())
            }
        }
    }
}
