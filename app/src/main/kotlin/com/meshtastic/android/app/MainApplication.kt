package com.meshtastic.android.app

import android.app.Application

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {

        }
    }
}
