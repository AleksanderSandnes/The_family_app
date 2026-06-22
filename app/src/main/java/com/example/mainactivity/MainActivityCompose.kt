package com.example.mainactivity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.mainactivity.data.FamilyRepository
import com.example.mainactivity.data.ThemeMode
import com.example.mainactivity.ui.navigation.FamilyApp
import com.example.mainactivity.ui.theme.TheFamilyAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val repo = FamilyRepository.get(applicationContext)
            val themeMode by repo.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
            val darkTheme = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            TheFamilyAppTheme(darkTheme = darkTheme) {
                FamilyApp()
            }
        }
    }
}
