package com.sandnes.familyapp

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import com.sandnes.familyapp.data.remote.FamilyMedia
import com.sandnes.familyapp.data.remote.PrivateMediaInterceptor
import com.sandnes.familyapp.data.remote.SupabaseManager
import com.sandnes.familyapp.util.LocaleManager
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MainApplication :
    Application(),
    SingletonImageLoader.Factory {
    override fun newImageLoader(context: Context): ImageLoader =
        ImageLoader.Builder(context).components { add(PrivateMediaInterceptor(FamilyMedia.resolver)) }.build()

    override fun onCreate() {
        super.onCreate()
        SupabaseManager.initialize(this)
        // Re-apply the persisted in-app language before any activity starts, so the chosen
        // language survives process restarts independently of the device locale.
        LocaleManager.applyPersisted(this)
    }
}
