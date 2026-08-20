package com.example.WEB

import android.app.Application
import timber.log.Timber

class WebRtcApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
