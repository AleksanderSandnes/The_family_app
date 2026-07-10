package com.sandnes.familyapp

import android.app.Application
import com.sandnes.familyapp.util.LocaleManager
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Re-apply the persisted in-app language before any activity starts, so the chosen
        // language survives process restarts independently of the device locale.
        LocaleManager.applyPersisted(this)
    }
}
