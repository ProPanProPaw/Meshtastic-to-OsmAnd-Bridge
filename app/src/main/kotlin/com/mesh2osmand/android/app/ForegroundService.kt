package com.mesh2osmand.android.app

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.mesh2osmand.android.app.meshtastic.IMeshtasticListener
import com.mesh2osmand.android.app.meshtastic.MeshtasticAidlHelper
import com.mesh2osmand.android.app.osmand.IOsmAndServiceListener
import com.mesh2osmand.android.app.osmand.OsmAndAidlHelper
import com.mesh2osmand.android.app.osmand.OsmAndHelper
import com.mesh2osmand.android.app.osmand.OsmAndUtils
import com.mesh2osmand.android.app.R
import net.osmand.aidlapi.maplayer.point.AMapPoint
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.MeshUser
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.model.NodeInfo
import org.meshtastic.core.model.Position
import org.meshtastic.proto.MeshProtos
import java.util.Timer
import java.util.TimerTask
import kotlin.system.exitProcess

const val OsmandLayerId = "id_mesh_positions"

object OsmAndHolder {
    var aidlHelper: OsmAndAidlHelper? = null
}

class ForegroundService : Service(), IMeshtasticListener, IOsmAndServiceListener {
    private val updateTimer = Timer()
    private var lastMapUpdate = 0L
    private val updateInterval = 1000L
    private var hearBeatCounter = 0
    private val hearBeatInterval = 10
    private var meshAidlHelper: MeshtasticAidlHelper? = null

    private var positionsList: GroupedSortedList = GroupedSortedList()
    private var shouldRecreateOsmandLayer: Boolean = true
    private var osmAndHelper: OsmAndHelper? = null

    private var osmAndLastAction: String? = null
    private var meshLastAction: String? = null
    private var myMeshId: String? = null
    private var meshConnectionState: String? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())

        osmAndHelper = OsmAndHelper(applicationContext)
        resetOsmandAidlHelper()
        meshAidlHelper = MeshtasticAidlHelper(applicationContext, this)

        updateTimer.schedule(object : TimerTask() {
            override fun run() {

                hearBeatCounter++
                if(hearBeatCounter % hearBeatInterval == 0) {
                    heartbeatCheck()
                }
                sendPointesToOsmand();
                updateOsmandWidgets();
            }
        }, updateInterval, updateInterval)

    }

    fun isAppInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }


    private fun resetOsmandAidlHelper(){
        OsmAndHolder.aidlHelper = OsmAndAidlHelper(applicationContext,this)
    }

    private fun heartbeatCheck() {

        myMeshId = meshAidlHelper?.getMyId()
        meshConnectionState = meshAidlHelper?.connectionState()

        if (meshConnectionState == null) {
            meshAidlHelper = MeshtasticAidlHelper(applicationContext, this@ForegroundService)
            meshLastAction = "🖤 no service";
        } else if (myMeshId == null || meshConnectionState != "CONNECTED") {
            meshLastAction = "💔 device $meshConnectionState";
        } else {
            meshLastAction = "💚 $meshConnectionState $myMeshId";
        }

        val osmandAppInfo = OsmAndHolder.aidlHelper?.getAppInfo()
        if (osmandAppInfo == null) {
            osmAndLastAction = "🖤 unsuccess";
            resetOsmandAidlHelper()
            shouldRecreateOsmandLayer = true
        } else {
            osmAndLastAction = "💚 success ${osmandAppInfo.osmAndVersion}";
        }

        upsertMeshConnectOsmandWidget()
        updateNotification()
    }

    private fun updateNotification(){
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, createNotification())
    }

    private fun createNotification(): Notification {

        val stopIntent = Intent(this, ForegroundService::class.java).apply {
            action = ACTION_STOP
        }

        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Meshtastic for OsmAnd Service running")
            .setContentText("Mesh: $meshLastAction\nOsmAnd: $osmAndLastAction\nTap Stop to end it")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .addAction(org.osmdroid.gpkg.R.drawable.center, "Stop", stopPendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            stopForeground(STOP_FOREGROUND_REMOVE)
            exitProcess(0)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        updateTimer.cancel()
        osmAndLastAction = "\uD83D\uDDD1\uFE0F service destroyed"
        meshLastAction = "\uD83D\uDDD1\uFE0F service destroyed"
        updateNotification()

        meshAidlHelper?.cleanupResources()
        OsmAndHolder.aidlHelper?.cleanupResources()
        shouldRecreateOsmandLayer = true
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Foreground Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "foreground_service_channel"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_STOP = "ACTION_STOP"
    }

    override fun onNodeChangeReceived(nodeInfo: NodeInfo?) {
        /*val mainTextView = findViewById<TextView>(R.id.mainTextView)
        mainTextView.text = nodeInfo?.toString()*/

        meshLastAction = "✅ NodeInfo received"

        if(nodeInfo?.user?.id == null) {
            return
        }

        positionsList.add(Item(nodeInfo.user!!.id, nodeInfo))

        if(OsmAndHolder.aidlHelper == null){
            return
        }

        sendPointesToOsmand();

        /*if(shouldRecreateOsmandLayer){
            osmAndLastAction = "❌ positions were not updated"
        } else {
            osmAndLastAction = "✅ positions were updated"
        }*/

        /*if(!updateOsmandLayer(points)){
            osmAndLastAction = "❌ layer was not updated"
            shouldRecreateOsmandLayer = true
        } else {
            osmAndLastAction = "✅ layer was updated"
        }*/
        updateNotification()
    }

    fun getVeryShortName(shortName: String?, userId: String): String {
        return StringHelper.getFirstCodePoint(shortName) ?: userId.reversed().take(3).reversed()
    }

    fun sendPointesToOsmand() {

        if(System.currentTimeMillis() - updateInterval <= 1000) {
            return
        }

        val points = positionsList.toMapPoints(OsmandLayerId, applicationContext, osmAndHelper)

        if(shouldRecreateOsmandLayer){
            removeOsmandLayer()
            val res = createOsmandLayer(points)

            if(!res){
                osmAndLastAction = "❌ layer was not created"
                resetOsmandAidlHelper()
            }

            shouldRecreateOsmandLayer = false
        }

        for(mapPoint in points) {
            updateOrRecreateOsmandPoint(mapPoint)
        }

        lastMapUpdate = System.currentTimeMillis()
    }


    fun updateOsmandWidgets() {

        val widgetsInfo = positionsList.toWidgetInfo(OsmandLayerId, applicationContext, osmAndHelper)

        for(widgetInfo in widgetsInfo) {
            updateOrRecreateOsmandWidgetsForUser(widgetInfo)
        }
    }


    override fun onMessageStatusReceived(status: MessageStatus?) {
        /*val mainTextView = findViewById<TextView>(R.id.mainTextView)
        mainTextView.text = status?.toString()*/
    }

    override fun onMeshConnectedReceived() {
        /*val statusImageView = findViewById<ImageView>(R.id.statusImageView)
        statusImageView.setImageResource(android.R.color.holo_green_light)*/
    }

    override fun onMeshDisconnectedReceived() {
        /*val statusImageView = findViewById<ImageView>(R.id.statusImageView)
        statusImageView.setImageResource(android.R.color.holo_red_light)*/
    }

    override fun onTextMessageReceived(dataPacket: DataPacket?) {
        /*if (dataPacket?.bytes == null)
            return;

        val mainTextView = findViewById<TextView>(R.id.mainTextView)
        val message = String(dataPacket.bytes!!);
        mainTextView.text = message*/
    }

    override fun onPositionReceived(
        from: String?,
        position: MeshProtos.Position
    ) {
        if(from == null) {
            return
        }

        val lastNodeInfo = positionsList.getGroup(from).lastOrNull()?.value

        if(lastNodeInfo == null) {
            return
        }

        positionsList.add(Item(from, NodeInfo(lastNodeInfo.num, lastNodeInfo.user,
            Position(position), lastNodeInfo.snr, lastNodeInfo.rssi, lastNodeInfo.lastHeard)))

        if(OsmAndHolder.aidlHelper == null){
            return
        }

        sendPointesToOsmand();
        updateNotification()
    }

    private fun getMyLocation(): Location? {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return null
        }

        return locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
    }

    override fun onNodeInfoReceived(
        from: String?,
        meshUser: MeshProtos.User
    ) {

        if(from == null) {
            return
        }

        val lastNodeInfo = positionsList.getGroup(from).lastOrNull()?.value

        if(lastNodeInfo == null) {
            return
        }

        positionsList.add(Item(from, NodeInfo(lastNodeInfo.num,
            MeshUser(meshUser),
            lastNodeInfo.position, lastNodeInfo.snr, lastNodeInfo.rssi, lastNodeInfo.lastHeard)))

        if(OsmAndHolder.aidlHelper == null){
            return
        }

        sendPointesToOsmand();
        updateNotification()
    }

    override fun onServiceConnected(serviceName: String) {

        if(serviceName == OsmAndAidlHelper.TAG){
            osmAndLastAction = "❓ service connected"
        } else if (serviceName == MeshtasticAidlHelper.TAG) {
            meshLastAction = "❓ service connected"
        }
        updateNotification()

        //Toast.makeText(applicationContext, "$serviceName connected", Toast.LENGTH_SHORT).show()
    }

    override fun onServiceDisconnected(serviceName: String) {

        if(serviceName == OsmAndAidlHelper.TAG){
            osmAndLastAction = "❌ service disconnected"
        } else if (serviceName == MeshtasticAidlHelper.TAG) {
            meshLastAction = "❌ service disconnected"
        }
        updateNotification()

        //Toast.makeText(applicationContext, "$serviceName disconnected", Toast.LENGTH_SHORT).show()
    }

    override fun onServiceBinded(serviceName: String) {

        if(serviceName == OsmAndAidlHelper.TAG){
            osmAndLastAction = "❓ service binded"
        } else if (serviceName == MeshtasticAidlHelper.TAG) {
            meshLastAction = "❓ service binded"
        }
        updateNotification()

        //Toast.makeText(applicationContext, "$serviceName binded", Toast.LENGTH_SHORT).show()
    }

    override fun onServiceNotBinded(serviceName: String) {

        if(serviceName == OsmAndAidlHelper.TAG){
            osmAndLastAction = "⚠ service NOT binded"
        } else if (serviceName == MeshtasticAidlHelper.TAG) {
            meshLastAction = "⚠ service NOT binded"
        }
        updateNotification()

        //Toast.makeText(applicationContext, "$serviceName NOT bind", Toast.LENGTH_SHORT).show()
    }

    override fun onServiceUnbinded(serviceName: String) {

        if(serviceName == OsmAndAidlHelper.TAG){
            osmAndLastAction = "❌ service unbinded"
        } else if (serviceName == MeshtasticAidlHelper.TAG) {
            meshLastAction = "❌ service unbinded"
        }
        updateNotification()

        //Toast.makeText(applicationContext, "$serviceName unbinded", Toast.LENGTH_SHORT).show()
    }

    fun createOsmandLayer(points: List<AMapPoint>?): Boolean {
        val res = OsmAndHolder.aidlHelper?.addMapLayer(OsmandLayerId, "MeshPositions", 5.5f, points, true)

        return res == true
    }

    fun removeOsmandLayer(): Boolean {
        val res = OsmAndHolder.aidlHelper?.removeMapLayer(OsmandLayerId)
        return res == true
    }

    fun updateOsmandLayer(points: List<AMapPoint>?): Boolean {
        val res = OsmAndHolder.aidlHelper?.updateMapLayer(OsmandLayerId, "MeshPositions", 5.5f, points, true)

        return res == true
    }

    fun updateOrRecreateOsmandPoint(mapPoint: AMapPoint) {
        var res = OsmAndHolder.aidlHelper?.updateMapPoint(OsmandLayerId, mapPoint.id,
            mapPoint.shortName,
            mapPoint.fullName,
            mapPoint.typeName,
            mapPoint.color,
            mapPoint.location,
            mapPoint.details,
            mapPoint.params,
            true)

        if(res != true){
            recreateOsmandPoint(mapPoint)
        } else {
            osmAndLastAction = "✅ position was updated"
        }
    }

    fun updateOrRecreateOsmandWidgetsForUser(widgetInfo: OsmandWidgetInfo) {

        if(widgetInfo.userId == null) {
            return
        }

        upsertLastPositionOsmandWidget(widgetInfo)
        upsertLastHeardAtOsmandWidget(widgetInfo)
        upsertBatteryLevelOsmandWidget(widgetInfo)
        upsertRssiOsmandWidget(widgetInfo)
        upsertSnrOsmandWidget(widgetInfo)
        upsertSignalOsmandWidget(widgetInfo)
        upsertDistanceOsmandWidget(widgetInfo)

        // TODO: satelites widget
    }


    fun upsertLastPositionOsmandWidget(widgetInfo: OsmandWidgetInfo) {

        if(widgetInfo.userId == null) {
            return
        }

        val intentHelper = IntentHelper(applicationContext)

        val widgetId = "M2OB_lastPositionAt_${widgetInfo.userId!!}"
        val menuIconName = "widget_location_sharing_day"
        val veryShortName = getVeryShortName(widgetInfo.shortName, widgetInfo.userId!!)
        val menuTitle = "M2OB $veryShortName last position at"

        val lastPositionSecondsAgo = TimeHelper.toSecondsAgo(widgetInfo.positionTime)
        val momentAgo = MomentAgo(lastPositionSecondsAgo)


        val lightIconName: String
        val darkIconName: String

        if (lastPositionSecondsAgo < 90) {
            lightIconName = "widget_location_sharing_on_day"
            darkIconName = "widget_location_sharing_on_night"
        } else {
            lightIconName = "widget_location_sharing_off_day"
            darkIconName = "widget_location_sharing_off_night"
        }

        val locationAgoSymbol = if (lastPositionSecondsAgo <= 30) "🟢"
        else if (lastPositionSecondsAgo < 90) "🟡"
        else if (lastPositionSecondsAgo < 180) "🟠"
        else "🔴"

        val widgetText = "$locationAgoSymbol${momentAgo.value}"
        val description = "${momentAgo.prefix} $veryShortName"
        val order = 50

        val intentOnClick = intentHelper.getSetMapPositionIntent(widgetInfo.posLatitude, widgetInfo.posLongitude) ?: Intent()

        upsertOsmandWidget(widgetId, menuIconName, menuTitle, lightIconName, darkIconName, widgetText, description, order, intentOnClick)
    }


    fun upsertLastHeardAtOsmandWidget(widgetInfo: OsmandWidgetInfo) {

        if(widgetInfo.userId == null) {
            return
        }

        val intentHelper = IntentHelper(applicationContext)

        val widgetId = "M2OB_lastHeardAt_${widgetInfo.userId!!}"
        val menuIconName = "widget_sensor_heart_rate_day"
        val veryShortName = getVeryShortName(widgetInfo.shortName, widgetInfo.userId!!)
        val menuTitle = "M2OB $veryShortName last heard at"
        val lastHeardSecondsAgo = TimeHelper.toSecondsAgo(widgetInfo.lastHeardAt)
        val momentAgo = MomentAgo(lastHeardSecondsAgo)


        val lightIconName: String
        val darkIconName: String

        if (lastHeardSecondsAgo < 90) {
            lightIconName = "widget_sensor_heart_rate_day"
            darkIconName = "widget_sensor_heart_rate_night"
        } else {
            lightIconName = "ic_action_sensor_heart_rate_outlined"
            darkIconName = "ic_action_sensor_heart_rate_outlined"
        }

        val symbol: String
        if (lastHeardSecondsAgo <= 30) {
            symbol = "💚"
        }
        else if (lastHeardSecondsAgo < 90) {
            symbol = "💛"
        }
        else if (lastHeardSecondsAgo < 180) {
            symbol = "🧡"
        }
        else {
            symbol = "💔"
        }

        val widgetText = "$symbol${momentAgo.value}"
        val description = "${momentAgo.prefix} $veryShortName"
        val order = 50

        val intentOnClick = intentHelper.getSetMapPositionIntent(widgetInfo.posLatitude, widgetInfo.posLongitude) ?: Intent()

        upsertOsmandWidget(widgetId, menuIconName, menuTitle, lightIconName, darkIconName, widgetText, description, order, intentOnClick)
    }

    fun upsertBatteryLevelOsmandWidget(widgetInfo: OsmandWidgetInfo) {

        if(widgetInfo.userId == null) {
            return
        }

        val intentHelper = IntentHelper(applicationContext)

        val widgetId = "M2OB_batteryLevel_${widgetInfo.userId!!}"
        val menuIconName = "widget_battery_day"
        val veryShortName = getVeryShortName(widgetInfo.shortName, widgetInfo.userId!!)
        val menuTitle = "M2OB $veryShortName battery level"
        val lightIconName = "widget_battery_day"
        val darkIconName = "widget_battery_night"

        val symbol = if (widgetInfo.batteryLevel == null) "❓"
        else if (widgetInfo.batteryLevel!! > 90)"🔋"
        else if (widgetInfo.batteryLevel!! > 75) "🟪"
        else if (widgetInfo.batteryLevel!! > 50) "🟨"
        else if (widgetInfo.batteryLevel!! > 30) "🟧"
        else "\uD83E\uDEAB"

        val widgetText = "$symbol${widgetInfo.batteryLevel ?: ""}"
        val description = "% $veryShortName"
        val order = 50

        val intentOnClick = intentHelper.getSetMapPositionIntent(widgetInfo.posLatitude, widgetInfo.posLongitude) ?: Intent()

        upsertOsmandWidget(widgetId, menuIconName, menuTitle, lightIconName, darkIconName, widgetText, description, order, intentOnClick)
    }

    fun upsertRssiOsmandWidget(widgetInfo: OsmandWidgetInfo) {

        if(widgetInfo.userId == null) {
            return
        }

        val intentHelper = IntentHelper(applicationContext)

        val widgetId = "M2OB_rssi_${widgetInfo.userId!!}"
        val menuIconName = "ic_action_signal"
        val veryShortName = getVeryShortName(widgetInfo.shortName, widgetInfo.userId!!)
        val menuTitle = "M2OB $veryShortName RSSI"


        val lightIconName: String
        val darkIconName: String
        val rssi = widgetInfo.rssi

        if (rssi == 0 || rssi == Int.MAX_VALUE) {
            lightIconName = "ic_action_signal_not_found"
            darkIconName = "ic_action_signal_not_found"
        }
        else if (rssi >= -70) {
            lightIconName = "ic_action_signal_high"
            darkIconName = "ic_action_signal_high"
        }
        else if (rssi >= -85) {
            lightIconName = "ic_action_signal_middle"
            darkIconName = "ic_action_signal_middle"
        }
        else if (rssi >= -100) {
            lightIconName = "ic_action_signal_low"
            darkIconName = "ic_action_signal_low"
        }
        else {
            lightIconName = "ic_action_signal_not_found"
            darkIconName = "ic_action_signal_not_found"
        }

        val widgetText = "$rssi"
        val description = "dBm $veryShortName"
        val order = 50

        val intentOnClick = intentHelper.getSetMapPositionIntent(widgetInfo.posLatitude, widgetInfo.posLongitude) ?: Intent()

        upsertOsmandWidget(widgetId, menuIconName, menuTitle, lightIconName, darkIconName, widgetText, description, order, intentOnClick)
    }

    fun upsertSnrOsmandWidget(widgetInfo: OsmandWidgetInfo) {

        if(widgetInfo.userId == null) {
            return
        }

        val intentHelper = IntentHelper(applicationContext)

        val widgetId = "M2OB_snr_${widgetInfo.userId!!}"
        val menuIconName = "ic_action_signal"
        val veryShortName = getVeryShortName(widgetInfo.shortName, widgetInfo.userId!!)
        val menuTitle = "M2OB $veryShortName SNR"


        val lightIconName: String
        val darkIconName: String

        val snr = widgetInfo.snr

        if(snr == 0f){
            lightIconName = "ic_action_signal_not_found"
            darkIconName = "ic_action_signal_not_found"
        }
        else if (snr >= 7) {
            lightIconName = "ic_action_signal_high"
            darkIconName = "ic_action_signal_high"
        }
        else if (snr >= 0) {
            lightIconName = "ic_action_signal_middle"
            darkIconName = "ic_action_signal_middle"
        }
        else if (snr >= -10) {
            lightIconName = "ic_action_signal_low"
            darkIconName = "ic_action_signal_low"
        }
        else {
            lightIconName = "ic_action_signal_not_found"
            darkIconName = "ic_action_signal_not_found"
        }

        val widgetText = String.format("%.1f", snr)
        val description = "dB $veryShortName"
        val order = 50

        val intentOnClick = intentHelper.getSetMapPositionIntent(widgetInfo.posLatitude, widgetInfo.posLongitude) ?: Intent()

        upsertOsmandWidget(widgetId, menuIconName, menuTitle, lightIconName, darkIconName, widgetText, description, order, intentOnClick)
    }

    fun upsertSignalOsmandWidget(widgetInfo: OsmandWidgetInfo) {

        if(widgetInfo.userId == null) {
            return
        }

        val intentHelper = IntentHelper(applicationContext)

        val widgetId = "M2OB_signal_${widgetInfo.userId!!}"
        val menuIconName = "ic_action_signal"
        val veryShortName = getVeryShortName(widgetInfo.shortName, widgetInfo.userId!!)
        val menuTitle = "M2OB $veryShortName signal"


        val lightIconName: String
        val darkIconName: String
        val symbol: String

        val snr = widgetInfo.snr
        val rssi = widgetInfo.rssi
        var description = "$veryShortName"

        if (rssi == 0 || rssi == Int.MAX_VALUE || snr == 0f) {
            lightIconName = "ic_action_signal_not_found"
            darkIconName = "ic_action_signal_not_found"
            symbol = "❓"
            description = "sig $veryShortName"
        }
        else if (snr >= 7 && rssi >= -70) {
            lightIconName = "ic_action_signal_high"
            darkIconName = "ic_action_signal_high"
            symbol = "hight"
        }
        else if (snr>= 0 && rssi >= -85) {
            lightIconName = "ic_action_signal_middle"
            darkIconName = "ic_action_signal_middle"
            symbol = "middle"
        }
        else if (snr < -10 || rssi < -100) {
            lightIconName = "ic_action_signal_not_found"
            darkIconName = "ic_action_signal_not_found"
            symbol = "❌"
        }
        else {
            lightIconName = "ic_action_signal_low"
            darkIconName = "ic_action_signal_low"
            symbol = "low"
        }

        val widgetText = "$symbol"
        val order = 50

        val intentOnClick = intentHelper.getSetMapPositionIntent(widgetInfo.posLatitude, widgetInfo.posLongitude) ?: Intent()
        upsertOsmandWidget(widgetId, menuIconName, menuTitle, lightIconName, darkIconName, widgetText, description, order, intentOnClick)
    }

    fun upsertDistanceOsmandWidget(widgetInfo: OsmandWidgetInfo) {

        if(widgetInfo.userId == null) {
            return
        }

        val intentHelper = IntentHelper(applicationContext)

        val widgetId = "M2OB_lastPositionDistance_${widgetInfo.userId!!}"
        val menuIconName = "widget_location_sharing_day"
        val veryShortName = getVeryShortName(widgetInfo.shortName, widgetInfo.userId!!)
        val menuTitle = "M2OB $veryShortName last position distance"

        val myLocation = getMyLocation()
        val distanceInMeters: Double?

        if (myLocation != null) {
            val myLat = myLocation.latitude
            val myLon = myLocation.longitude

            distanceInMeters = OsmAndUtils.getDistance(myLat, myLon, widgetInfo.posLatitude, widgetInfo.posLongitude)
        } else {
            distanceInMeters = null
        }

        val lightIconName: String
        val darkIconName: String

        if (distanceInMeters != null) {
            lightIconName = "widget_location_sharing_on_day"
            darkIconName = "widget_location_sharing_on_night"
        } else {
            lightIconName = "widget_location_sharing_off_day"
            darkIconName = "widget_location_sharing_off_night"
        }

        val locationAgoSymbol = if (distanceInMeters == null || distanceInMeters >= 500) "🔴"
        else if (distanceInMeters >= 100) "🟠"
        else if (distanceInMeters >= 20) "🟡"
        else "🟢"

        val widgetText = "$locationAgoSymbol${distanceInMeters?.toInt()}"
        val description = "m $veryShortName"
        val order = 50

        val intentOnClick = intentHelper.getDistanceWidgetClickedIntent(widgetInfo.posLatitude, widgetInfo.posLongitude) ?: Intent()

        upsertOsmandWidget(widgetId, menuIconName, menuTitle, lightIconName, darkIconName, widgetText, description, order, intentOnClick)
    }

    fun upsertOsmandWidget(id: String, menuIconName: String, menuTitle: String,
                           lightIconName: String, darkIconName: String, text: String, description: String,
                           order: Int, intentOnClick: Intent){
        var res = OsmAndHolder.aidlHelper?.updateMapWidget(
            id,
            menuIconName,
            menuTitle,
            lightIconName,
            darkIconName,
            text,
            description,
            order,
            intentOnClick
        )

        if(res != true){

            var res = OsmAndHolder.aidlHelper?.removeMapWidget(id)

            Handler(Looper.getMainLooper()).postDelayed({
                res = OsmAndHolder.aidlHelper?.addMapWidget(
                    id,
                    menuIconName,
                    menuTitle,
                    lightIconName,
                    darkIconName,
                    text,
                    description,
                    order,
                    intentOnClick
                )

                if (res != true) {
                    osmAndLastAction = "❌ widget was not created"
                } else {
                    osmAndLastAction = "✅ widget was created"
                }
            }, 40)
        } else {
            osmAndLastAction = "✅ widget was updated"
        }
    }

    fun upsertMeshConnectOsmandWidget() {

        val intentHelper = IntentHelper(applicationContext)

        val widgetId = "M2OB_mesh_connect"
        val menuIconName = "ic_action_antenna"
        val menuTitle = "M2OB Meshtastic connection"


        val symbol: String
        val description: String

        val lightIconName = "ic_action_external_sensor_colored_day"
        val darkIconName = "ic_action_external_sensor_colored_night"

        if (myMeshId == null && meshConnectionState == null) {
            symbol = "📴"
            description = "mesh"
        }
        else {
            val isMeshDisconnected = meshConnectionState == "DISCONNECTED"

            symbol = if (isMeshDisconnected)  "🔴" else "🟢"
            description = if (myMeshId != null)
                getVeryShortName(null, myMeshId!!)
            else "mesh ❓"
        }

        val widgetText = "$symbol"
        val order = 50

        val intentOnClick = intentHelper.getMeshtasticIntent() ?: Intent()
        upsertOsmandWidget(widgetId, menuIconName, menuTitle, lightIconName, darkIconName, widgetText, description, order, intentOnClick)
    }

    fun recreateOsmandPoint(mapPoint: AMapPoint) {
        var res = OsmAndHolder.aidlHelper?.removeMapPoint(OsmandLayerId, mapPoint.id)

        Handler(Looper.getMainLooper()).postDelayed({
            res = OsmAndHolder.aidlHelper?.addMapPoint(
                OsmandLayerId,
                mapPoint.id,
                mapPoint.shortName,
                mapPoint.fullName,
                mapPoint.typeName,
                mapPoint.color,
                mapPoint.location,
                mapPoint.details,
                mapPoint.params
            )

            if (res != true) {
                shouldRecreateOsmandLayer = true
                osmAndLastAction = "❌ position was not created"
            } else {

                osmAndLastAction = "✅ position was created"
            }
        }, 40)
    }
}