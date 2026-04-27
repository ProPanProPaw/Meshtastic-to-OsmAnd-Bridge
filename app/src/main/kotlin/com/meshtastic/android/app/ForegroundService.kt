package com.meshtastic.android.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.meshtastic.android.app.meshtastic.IMeshtasticListener
import com.meshtastic.android.app.meshtastic.MeshtasticAidlHelper
import com.meshtastic.android.app.osmand.IOsmAndServiceListener
import com.meshtastic.android.app.osmand.OsmAndAidlHelper
import com.meshtastic.android.app.osmand.OsmAndHelper
import net.osmand.aidlapi.maplayer.point.AMapPoint
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.MeshUser
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.model.NodeInfo
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


    private fun resetOsmandAidlHelper(){
        OsmAndHolder.aidlHelper = OsmAndAidlHelper(applicationContext,this)
    }

    private fun heartbeatCheck() {

        val meshId = meshAidlHelper?.getMyId()
        if (meshId == null) {
            meshAidlHelper = MeshtasticAidlHelper(applicationContext, this@ForegroundService)
            meshLastAction = "🖤 unsuccess";
        } else {
            meshLastAction = "💚 success $meshId";
        }

        val osmandAppInfo = OsmAndHolder.aidlHelper?.getAppInfo()
        if (osmandAppInfo == null) {
            osmAndLastAction = "🖤 unsuccess";
            resetOsmandAidlHelper()
            shouldRecreateOsmandLayer = true
        } else {
            osmAndLastAction = "💚 success ${osmandAppInfo.osmAndVersion}";
        }


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
            org.meshtastic.core.model.Position(position), lastNodeInfo.snr, lastNodeInfo.rssi, lastNodeInfo.lastHeard)))

        if(OsmAndHolder.aidlHelper == null){
            return
        }

        sendPointesToOsmand();
        updateNotification()
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
    }


    fun upsertLastPositionOsmandWidget(widgetInfo: OsmandWidgetInfo) {

        if(widgetInfo.userId == null) {
            return
        }

        val intentHelper = IntentHelper(applicationContext)

        val widgetId = "M2OB_lastPositionAt_${widgetInfo.userId!!}"
        val menuIconName = "ic_action_time"
        val veryShortName = StringHelper.getFirstCodePoint(widgetInfo.shortName) ?: widgetInfo.userId!!.take(3)
        val menuTitle = "M2OB #${veryShortName} last position at"
        val lightIconName = "ic_action_time"
        val darkIconName = "ic_action_time"

        val lastPositionSecondsAgo = TimeHelper.toSecondsAgo(widgetInfo.positionTime)
        val momentAgo = TimeHelper.toMomentAgo(lastPositionSecondsAgo)

        val locationAgoSymbol = if (lastPositionSecondsAgo <= 30) "🟢"
        else if (lastPositionSecondsAgo < 90) "🟡"
        else if (lastPositionSecondsAgo < 180) "🟠"
        else "🔴"

        val widgetText = "${veryShortName}: ${locationAgoSymbol}${momentAgo.value}"
        val description = momentAgo.prefix
        val order = 50

        val intentOnClick = intentHelper.getSetMapPositionIntent(widgetInfo.posLatitude, widgetInfo.posLongitude) ?: Intent()

        upsertOsmandWidget(widgetId, menuIconName, menuTitle, lightIconName, darkIconName, widgetText, description, order, intentOnClick)

        val all = null
        val none = emptyList<String>()

        Handler(Looper.getMainLooper()).postDelayed({
            OsmAndHolder.aidlHelper?.regWidgetVisibility(widgetId, all)
        }, 700)
    }


    fun upsertLastHeardAtOsmandWidget(widgetInfo: OsmandWidgetInfo) {

        if(widgetInfo.userId == null) {
            return
        }

        val intentHelper = IntentHelper(applicationContext)

        val widgetId = "M2OB_lastHeardAt_${widgetInfo.userId!!}"
        val menuIconName = "ic_action_time"
        val veryShortName = StringHelper.getFirstCodePoint(widgetInfo.shortName) ?: widgetInfo.userId!!.take(3)
        val menuTitle = "M2OB #${veryShortName} last heard at"
        val lightIconName = "ic_action_time"
        val darkIconName = "ic_action_time"

        val lastHeardSecondsAgo = TimeHelper.toSecondsAgo(widgetInfo.lastHeardAt)
        val momentAgo = TimeHelper.toMomentAgo(lastHeardSecondsAgo)

        val symbol = if (lastHeardSecondsAgo <= 30) "💚"
        else if (lastHeardSecondsAgo < 90) "💛"
        else if (lastHeardSecondsAgo < 180) "🧡"
        else "💔"

        val widgetText = "${veryShortName}: ${symbol}${momentAgo.value}"
        val description = momentAgo.prefix
        val order = 50

        val intentOnClick = intentHelper.getSetMapPositionIntent(widgetInfo.posLatitude, widgetInfo.posLongitude) ?: Intent()

        upsertOsmandWidget(widgetId, menuIconName, menuTitle, lightIconName, darkIconName, widgetText, description, order, intentOnClick)

        val all = null
        val none = emptyList<String>()

        Handler(Looper.getMainLooper()).postDelayed({
            OsmAndHolder.aidlHelper?.regWidgetVisibility(widgetId, all)
        }, 700)
    }

    fun upsertBatteryLevelOsmandWidget(widgetInfo: OsmandWidgetInfo) {

        if(widgetInfo.userId == null) {
            return
        }

        val intentHelper = IntentHelper(applicationContext)

        val widgetId = "M2OB_batteryLevel_${widgetInfo.userId!!}"
        val menuIconName = "ic_action_time"
        val veryShortName = StringHelper.getFirstCodePoint(widgetInfo.shortName) ?: widgetInfo.userId!!.take(3)
        val menuTitle = "M2OB #${veryShortName} battery level"
        val lightIconName = "ic_action_time"
        val darkIconName = "ic_action_time"

        val symbol = if (widgetInfo.batteryLevel == null) "❓"
        else if (widgetInfo.batteryLevel!! > 90)"🔋"
        else if (widgetInfo.batteryLevel!! > 75) "🟪"
        else if (widgetInfo.batteryLevel!! > 50) "🟨"
        else if (widgetInfo.batteryLevel!! > 30) "🟧"
        else "🟥"

        val widgetText = "${veryShortName}: ${symbol}${widgetInfo.batteryLevel ?: ""}"
        val description = "%"
        val order = 50

        val intentOnClick = intentHelper.getSetMapPositionIntent(widgetInfo.posLatitude, widgetInfo.posLongitude) ?: Intent()

        upsertOsmandWidget(widgetId, menuIconName, menuTitle, lightIconName, darkIconName, widgetText, description, order, intentOnClick)

        val all = null
        val none = emptyList<String>()

        Handler(Looper.getMainLooper()).postDelayed({
            OsmAndHolder.aidlHelper?.regWidgetVisibility(widgetId, all)
        }, 700)
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