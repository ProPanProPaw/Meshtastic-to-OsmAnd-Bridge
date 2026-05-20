package com.mesh2osmand.android.app

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.mesh2osmand.android.app.IntentHelper.Companion.IntentType
import com.mesh2osmand.android.app.meshtastic.MeshtasticAidlHelper
import com.mesh2osmand.android.app.osmand.OsmAndAidlHelper


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
        intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        return intent
    }

    fun getMeshtasticIntent(): Intent? {

        val intent: Intent? = packageManager
            .getLaunchIntentForPackage(MeshtasticAidlHelper.MESHTASTIC_PACKAGE_NAME)
        intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

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

    fun getDistanceWidgetClickedIntent(lat: Double, lon: Double, zoom: Int = 16): Intent? {

        val intent = Intent(context, IntentReceiver::class.java).apply {
            putExtra("type", IntentType.REQUEST_LOC_PERMISSION_AND_SET_MAP_POSITION.name)
            putExtra("lat", lat)
            putExtra("lon", lon)
            putExtra("zoom", zoom)
        }

        return intent
    }

    companion object {

        enum class IntentType {
            NONE,
            SET_MAP_POSITION,
            REQUEST_LOC_PERMISSION_AND_SET_MAP_POSITION
        }


    }
}


private const val PERMISSION_REQUEST_LOCATION = 100

class IntentReceiver : Activity() {


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        onReceive(intent)
    }

    private fun onReceive(intent: Intent) {

        val typeName = intent.getStringExtra("type") ?: IntentType.NONE.name
        val type = IntentType.valueOf(typeName)

        if(type == IntentType.NONE) {
            finish()
            return
        }

        if(type == IntentType.REQUEST_LOC_PERMISSION_AND_SET_MAP_POSITION) {

            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), PERMISSION_REQUEST_LOCATION)
            }
            else {
                setMapPosition(intent)
            }

            return
        }


        if(type == IntentType.SET_MAP_POSITION) {

            setMapPosition(intent)
            return
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {

        if (requestCode == PERMISSION_REQUEST_LOCATION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                setMapPosition(intent)
            } else {
                finish()
            }
        }
    }

    private fun setMapPosition(intent: Intent) {

        if (OsmAndHolder.aidlHelper == null) {
            finish()
            return
        }

        val lat = intent.getDoubleExtra("lat", 0.0)
        val lon = intent.getDoubleExtra("lon", 0.0)
        val zoom = intent.getIntExtra("zoom", 16)

        val osmandIntent = IntentHelper(this).getOsmandIntent()
        var delayMs = 5000L

        if(osmandIntent != null) {
            startActivity(osmandIntent)
            delayMs = 1000L
        }

        Handler(Looper.getMainLooper()).postDelayed({
            OsmAndHolder.aidlHelper!!.setMapLocation(lat, lon, zoom, 0f, true)
            finish()
        }, delayMs)
    }
}