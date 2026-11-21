package com.aurora.player

import android.app.Application
import android.content.Context
import com.aurora.player.data.local.AppDatabase
import com.aurora.player.data.prefs.SettingsRepository
import com.aurora.player.data.repository.LibraryRepository
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

class AppContainer(context: Context) {
    val database: AppDatabase by lazy { AppDatabase.create(context) }
    val settingsRepository: SettingsRepository = SettingsRepository(context.applicationContext)
    val libraryRepository: LibraryRepository by lazy { LibraryRepository(context.contentResolver, database) }
}

class MusicApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        if (FirebaseApp.getApps(this).isEmpty()) {
            val appId = if (BuildConfig.APPLICATION_ID.endsWith(".test")) {
                "1:463692900230:android:aae24466db5c701b57ee07"
            } else {
                "1:463692900230:android:1966fe3efbbbb7ae57ee07"
            }
            val options = FirebaseOptions.Builder()
                .setApplicationId(appId)
                .setApiKey("AIzaSyAz0RwnP7ViiyRF2o4HRtSBiUf5nBFxZEo")
                .setProjectId("whisper-player")
                .setStorageBucket("whisper-player.firebasestorage.app")
                .build()
            FirebaseApp.initializeApp(this, options)
        }
        container = AppContainer(this)
    }
}
