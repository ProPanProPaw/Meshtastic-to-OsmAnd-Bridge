package com.meshtastic.android.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.meshtastic.android.app.IntentHelper.Companion.IntentType
import com.meshtastic.android.app.osmand.OsmAndAidlHelper


public class IntentHelper {

    private lateinit var appContext: Context

    constructor(context: Context) {
        appContext = context.applicationContext
    }

    val context: Context
        get() = appContext

    val packageManager: PackageManager
        get() = appContext.packageManager

    val packageName: String
        get() = appContext.packageName


    fun getAppLauncherIntent(): Intent? {

        val startDemoIntent = packageManager.getLaunchIntentForPackage(packageName)
        startDemoIntent?.addCategory(Intent.CATEGORY_LAUNCHER)

        return startDemoIntent
    }


    fun getOsmandIntent(): Intent? {

        val intent: Intent? = packageManager
            .getLaunchIntentForPackage(OsmAndAidlHelper.OSMAND_PACKAGE_NAME)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        }

        return intent
    }

    fun getSetMapPositionIntent(lat: Double, lon: Double, zoom: Int = 16): Intent? {

        val intent = Intent(context, IntentReceiver::class.java).apply {
            putExtra("type", IntentType.SET_MAP_POSITION.name)
            putExtra("lat", lat)
            putExtra("lon", lon)
            putExtra("zoom", zoom)
        }

        return intent
    }

    companion object {

        enum class IntentType {
            NONE,
            SET_MAP_POSITION
        }


    }
}

class IntentReceiver : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        onReceive(intent)

        finish()
    }

    private fun onReceive(intent: Intent) {

        val type = IntentType.valueOf(
            intent.getStringExtra("type") ?: IntentType.NONE.name
        )

        if(type == IntentType.NONE) {
            return
        }

        if(type == IntentType.SET_MAP_POSITION) {

            if(OsmAndHolder.aidlHelper == null) {
                return
            }


            val lat = intent.getDoubleExtra("lat", 0.0)
            val lon = intent.getDoubleExtra("lon", 0.0)
            val zoom = intent.getIntExtra("zoom", 16)

            val intent = IntentHelper(this).getOsmandIntent()

            var delayMs = 5000L

            if(intent != null) {
                startActivity(intent)
                delayMs = 1000L
            }



            Handler(Looper.getMainLooper()).postDelayed({

                val test = OsmAndHolder.aidlHelper!!.setMapLocation(lat, lon, zoom, 0f, true)
            }, delayMs)
            return
        }
    }
}