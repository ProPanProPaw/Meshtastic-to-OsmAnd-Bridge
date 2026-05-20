package com.mesh2osmand.android.app

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.content.FileProvider
import com.mesh2osmand.android.app.osmand.OsmAndHelper
import net.osmand.aidlapi.map.ALatLon
import net.osmand.aidlapi.maplayer.point.AMapPoint
import org.meshtastic.core.model.NodeInfo
import java.io.File
import java.io.FileOutputStream
import kotlin.random.Random


public data class Item(val userId: String, val value: NodeInfo)

public class GroupedSortedList {
    private val groups = mutableMapOf<String, MutableList<Item>>()

    fun add(item: Item) {
        val list = groups.getOrPut(item.userId) { mutableListOf() }
        val index = list.binarySearch { compareValues(it.value.position?.time,item.value.position?.time) }
        val insertIndex = if (index < 0) -index - 1 else index
        list.add(insertIndex, item)
    }

    fun getGroup(userId: String): List<Item> = groups[userId].orEmpty()
    fun allGroups(): Map<String, List<Item>> = groups




    fun generateBitmap(node: NodeInfo): Bitmap? {

        val textColor = node.colors.first
        val backgroundColor = node.colors.second

        return generateBitmap(node.user?.shortName, textColor, backgroundColor)
    }



    fun generateBitmap(shortName: String?, textColor: Int = Color.BLACK, backgroundColor: Int = Color.TRANSPARENT): Bitmap? {

        val symbol = StringHelper.getFirstCodePoint(shortName)

        if(symbol == null) {
            return null
        }

        val size = 128

        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        canvas.drawColor(backgroundColor)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            textSize = 64f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }

        val hasGlyph = paint.hasGlyph(symbol)


        val x = size / 2f
        val y = size / 2f - (paint.descent() + paint.ascent()) / 2

        canvas.drawText(symbol, x, y, paint)

        return bitmap
    }

    fun toMapPoints(layerId: String, applicationContext: Context, osmAndHelper: OsmAndHelper?): List<AMapPoint> {

        return groups.values.mapNotNull { group ->
            group.lastOrNull()?.let { lastItem ->

                lastItem.value.position?.let { position ->

                    val nodeInfo = lastItem.value
                    var shortName = StringHelper.getFirstCodePoint(nodeInfo.user?.shortName) ?: "❓"

                    val shortTimeFormat = "mm:ss"
                    val lastPositionAtFormated = TimeHelper.epochToIso8601(position.time * 1000.toLong(), shortTimeFormat)


                    val positionSecondsAgo = TimeHelper.toSecondsAgo(position.time);

                    val color = if (positionSecondsAgo <= 30) Color.GREEN
                    else if (positionSecondsAgo < 90) Color.YELLOW
                    else Color.RED

                    val locationAgoSymbol = if (positionSecondsAgo <= 30) "🟢"
                    else if (positionSecondsAgo < 90) "🟡"
                    else if (positionSecondsAgo < 180) "🟠"
                    else "🔴"


                    val lastHeardAt = TimeHelper.epochToIso8601(nodeInfo.lastHeard * 1000.toLong())

                    val lastHeardSecondsAgo = TimeHelper.toSecondsAgo(nodeInfo.lastHeard);

                    val lastHeardAgoColor = if (lastHeardSecondsAgo <= 30) Color.GREEN
                    else if (lastHeardSecondsAgo < 90) Color.YELLOW
                    else Color.RED

                    val lastHeardAgoSymbol = if (lastHeardAgoColor == Color.GREEN) "💚"
                    else if (lastHeardAgoColor == Color.YELLOW) "💛"
                    else "💔"

                    val positionTextAgo = TimeHelper.toMomentAgo(position.time)

                    val lastHeardAtFormated = TimeHelper.epochToIso8601(nodeInfo.lastHeard * 1000.toLong(), shortTimeFormat)
                    val updateAtFormated = TimeHelper.epochToIso8601( System.currentTimeMillis(), shortTimeFormat)


                    val lastHeardTextAgo = TimeHelper.toMomentAgo(nodeInfo.lastHeard)

                    //shortName += "${locationAgoSymbol}${positionTextAgo} ${lastHeardAgoSymbol}${lastHeardTextAgo}_${updateAtFormated}"


                    val fullName = nodeInfo.user?.longName ?: nodeInfo.user?.shortName ?: "❓"
                    val details = mutableListOf<String>()

                    nodeInfo.deviceMetrics?.batteryLevel?.let {
                        if (it > 0) details.add("Battery: $it%")
                    }

                    details.add("Snr: ${nodeInfo.snr}")
                    details.add("Rssi: ${nodeInfo.rssi}")
                    details.add("LastHeardAt: ${lastHeardAt}")
                    details.add("LastPositionAt: ${TimeHelper.epochToIso8601(position.time * 1000.toLong())}")
                    details.add("UID: ${nodeInfo.user?.id}")



                    val params = mutableMapOf<String, String>()

                    val file = File(applicationContext.cacheDir, "${nodeInfo.num}.png")

                    if(!file.exists()) {
                        val bitmap = generateBitmap(nodeInfo)

                        if(bitmap != null) {
                            FileOutputStream(file).use {
                                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                            }
                        }
                    }

                    if(file.exists() && file.canRead()) {

                        val uri = FileProvider.getUriForFile(
                            applicationContext,
                            "${applicationContext.packageName}.fileprovider",
                            file
                        )

                        applicationContext.grantUriPermission(
                            osmAndHelper?.getPackageName(),
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )

                        params[AMapPoint.POINT_IMAGE_URI_PARAM] = uri.toString()
                    }


                    val range = 2;
                    val precision = 0.00001
                    val deltaLat = Random.nextInt(-range, range) * precision
                    val deltaLon = Random.nextInt(-range, range) * precision

                    val aLatLon = ALatLon(position.latitude + deltaLat, position.longitude + deltaLon)

                    //val aLatLon = ALatLon(position.latitude, position.longitude )

                    AMapPoint(
                        "id_${nodeInfo.user?.id}",
                        shortName,
                        fullName,
                        "MeshNode",
                        layerId,
                        color,
                        aLatLon,
                        details,
                        params
                    )
                }
            }
        }
    }


    fun toWidgetInfo(layerId: String, applicationContext: Context, osmAndHelper: OsmAndHelper?): List<OsmandWidgetInfo> {

        return groups.values.mapNotNull { group ->
            group.lastOrNull()?.let { lastItem ->

                lastItem.value.position?.let { position ->

                    val nodeInfo = lastItem.value

                    OsmandWidgetInfo(
                        nodeInfo.user?.id,
                        nodeInfo.user?.shortName,
                        position.time,
                        nodeInfo.lastHeard,
                        nodeInfo.deviceMetrics?.batteryLevel,
                        nodeInfo.snr,
                        nodeInfo.rssi,
                        position.latitude,
                        position.longitude
                    )
                }
            }
        }
    }
}

