package com.fyp.crowdlink

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import org.maplibre.android.MapLibre
import org.maplibre.android.WellKnownTileServer
import timber.log.Timber

@HiltAndroidApp
class CrowdLinkApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Timber.plant(Timber.DebugTree())
        // Initialise MapLibre once at app start
        MapLibre.getInstance(this, "", WellKnownTileServer.MapLibre)
    }
}
