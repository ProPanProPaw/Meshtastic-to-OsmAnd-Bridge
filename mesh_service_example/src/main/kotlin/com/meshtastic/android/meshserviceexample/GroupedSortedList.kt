package com.meshtastic.android.meshserviceexample

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.content.FileProvider
import com.meshtastic.android.meshserviceexample.osmand.OsmAndHelper
import net.osmand.aidlapi.map.ALatLon
import net.osmand.aidlapi.maplayer.point.AMapPoint
import org.meshtastic.core.model.NodeInfo
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone


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

    private fun epochToIso8601(timeInMs: Long, format: String = "dd MMM yyyy HH:mm:ss"): String {
        val sdf = SimpleDateFormat(format, Locale.getDefault())
        sdf.timeZone = TimeZone.getDefault()
        return sdf.format(Date(timeInMs))
    }

    fun toSecondsAgo(time: Int): Long {
        val currentTime = System.currentTimeMillis();
        val secondsAgo = (currentTime / 1000 - time);

        return secondsAgo
    }

    fun toMomentAgo(time: Int): String {

        val secondsAgo = toSecondsAgo(time);

        val textAgo = if (secondsAgo < 1) "now"
        else if (secondsAgo < 60) "${secondsAgo}s ago"
        else if (secondsAgo < 3600) "${(secondsAgo/60).toInt()}m ago"
        else "${(secondsAgo/3600).toInt()}h ago"

        return textAgo
    }


    fun generateBitmap(node: NodeInfo): Bitmap? {

        val textColor = node.colors.first
        val backgroundColor = node.colors.second

        return generateBitmap(node.user?.shortName, textColor, backgroundColor)
    }


    fun getFirstCodePoint(text: String?): String? {

        if(text == null) {
            return null
        }

        val symbol = text.let {
            val cp = it.codePointAt(0)
            String(Character.toChars(cp))
        }

        return symbol
    }



    fun generateBitmap(shortName: String?, textColor: Int = Color.BLACK, backgroundColor: Int = Color.TRANSPARENT): Bitmap? {

        val symbol = getFirstCodePoint(shortName)

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
                    var shortName = getFirstCodePoint(nodeInfo.user?.shortName) ?: "❓"

                    val shortTimeFormat = "mm:ss"
                    val lastPositionAtFormated = epochToIso8601(position.time * 1000.toLong(), shortTimeFormat)


                    val positionSecondsAgo = toSecondsAgo(position.time);

                    val color = if (positionSecondsAgo <= 30) Color.GREEN
                    else if (positionSecondsAgo < 90) Color.YELLOW
                    else Color.RED

                    val locationAgoSymbol = if (color == Color.GREEN) "\uD83D\uDFE2"
                    else if (color == Color.YELLOW) "\uD83D\uDFE1"
                    else "\uD83D\uDD34"


                    val lastHeardAt = epochToIso8601(nodeInfo.lastHeard * 1000.toLong())

                    val lastHeardSecondsAgo = toSecondsAgo(nodeInfo.lastHeard);

                    val lastHeardAgoColor = if (lastHeardSecondsAgo <= 30) Color.GREEN
                    else if (lastHeardSecondsAgo < 90) Color.YELLOW
                    else Color.RED

                    val lastHeardAgoSymbol = if (lastHeardAgoColor == Color.GREEN) "💚"
                    else if (lastHeardAgoColor == Color.YELLOW) "💛"
                    else "💔"

                    val positionTextAgo = toMomentAgo(position.time)

                    val lastHeardAtFormated = epochToIso8601(nodeInfo.lastHeard * 1000.toLong(), shortTimeFormat)
                    val updateAtFormated = epochToIso8601( System.currentTimeMillis(), shortTimeFormat)


                    val lastHeardTextAgo = toMomentAgo(nodeInfo.lastHeard)

                    shortName += "${locationAgoSymbol}${positionTextAgo} ${lastHeardAgoSymbol}${lastHeardTextAgo}_${updateAtFormated}"


                    val fullName = nodeInfo.user?.longName ?: nodeInfo.user?.shortName ?: "❓"
                    val details = mutableListOf<String>()

                    nodeInfo.deviceMetrics?.batteryLevel?.let {
                        if (it > 0) details.add("Battery: $it%")
                    }

                    details.add("Snr: ${nodeInfo.snr}")
                    details.add("Rssi: ${nodeInfo.rssi}")
                    details.add("LastHeardAt: ${lastHeardAt}")
                    details.add("LastPositionAt: ${epochToIso8601(position.time * 1000.toLong())}")
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


                    AMapPoint(
                        "id_${nodeInfo.user?.id}",
                        shortName,
                        fullName,
                        "MeshNode",
                        layerId,
                        color,
                        ALatLon(position.latitude, position.longitude),
                        details,
                        params
                    )
                }
            }
        }
    }
}