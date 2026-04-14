package com.meshtastic.android.meshserviceexample.osmand

import android.annotation.TargetApi
import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import java.io.File
import java.io.UnsupportedEncodingException
import java.net.URLEncoder

class OsmAndHelper(private val cotnext: Context) {

    fun getInfo() {
        sendRequest(OsmAndIntentBuilder(GET_INFO))
    }

    fun addMapMarker(lat: Double, lon: Double, name: String) {
        val params = mapOf(
            PARAM_LAT to lat.toString(),
            PARAM_LON to lon.toString(),
            PARAM_NAME to name
        )
        sendRequest(OsmAndIntentBuilder(ADD_MAP_MARKER).setParams(params))
    }

    fun showLocation(lat: Double, lon: Double) {
        val params = mapOf(PARAM_LAT to lat.toString(), PARAM_LON to lon.toString())
        sendRequest(OsmAndIntentBuilder(SHOW_LOCATION).setParams(params))
    }

    fun addFavorite(
        lat: Double, lon: Double, name: String,
        description: String, category: String, color: String,
        visible: Boolean
    ) {
        val params = mapOf(
            PARAM_LAT to lat.toString(),
            PARAM_LON to lon.toString(),
            PARAM_NAME to name,
            PARAM_DESC to description,
            PARAM_CATEGORY to category,
            PARAM_COLOR to color,
            PARAM_VISIBLE to visible.toString()
        )
        sendRequest(OsmAndIntentBuilder(ADD_FAVORITE).setParams(params))
    }

    /**
     * Start navigation.
     *
     * @param startName (String) - name of the start point as it displays in OsmAnd's UI. Nullable.
     * @param startLat (double) - latitude of the start point. If 0 - current location is used.
     * @param startLon (double) - longitude of the start point. If 0 - current location is used.
     * @param destName (String) - name of the start point as it displays in OsmAnd's UI.
     * @param destLat (double) - latitude of a destination point.
     * @param destLon (double) - longitude of a destination point.
     * @param profile (String)  - One of: "default", "car", "bicycle", "pedestrian", "aircraft", "boat", "hiking", "motorcycle", "truck". Nullable (default).
     * @param force (boolean) - ask to stop current navigation if any. False - ask. True - don't ask.
     */
    fun navigate(
        startName: String, startLat: Double, startLon: Double,
        destName: String, destLat: Double, destLon: Double,
        profile: String, force: Boolean, needLocationPermission: Boolean
    ) {
        val params = mapOf(
            PARAM_START_LAT to startLat.toString(),
            PARAM_START_LON to startLon.toString(),
            PARAM_START_NAME to startName,
            PARAM_DEST_LAT to destLat.toString(),
            PARAM_DEST_LON to destLon.toString(),
            PARAM_DEST_NAME to destName,
            PARAM_PROFILE to profile,
            PARAM_FORCE to force.toString(),
            PARAM_LOCATION_PERMISSION to needLocationPermission.toString()
        )
        sendRequest(OsmAndIntentBuilder(NAVIGATE).setParams(params))
    }

    fun pauseNavigation() {
        sendRequest(OsmAndIntentBuilder(PAUSE_NAVIGATION))
    }

    fun resumeNavigation() {
        sendRequest(OsmAndIntentBuilder(RESUME_NAVIGATION))
    }

    fun stopNavigation() {
        sendRequest(OsmAndIntentBuilder(STOP_NAVIGATION))
    }

    fun muteNavigation() {
        sendRequest(OsmAndIntentBuilder(MUTE_NAVIGATION))
    }

    fun umuteNavigation() {
        sendRequest(OsmAndIntentBuilder(UNMUTE_NAVIGATION))
    }

    fun importFile(fileUri: Uri) {
        cotnext.grantUriPermission(OSMAND_PACKAGE_NAME, fileUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        sendFileRequest(fileUri)
    }

    private fun sendRequest(intentBuilder: OsmAndIntentBuilder) {
        try {
            val uri = intentBuilder.uri
            val intent = Intent(Intent.ACTION_VIEW, uri)
            intent.addFlags(intentBuilder.flags)
            val extraData = intentBuilder.extraData
            if (extraData != null) {
                for ((key, value) in extraData) {
                    intent.putExtra(key, value)
                }
            }
            if (intentBuilder.gpxUri != null) {
                val clipData = ClipData.newRawUri("Gpx", intentBuilder.gpxUri)
                intent.clipData = clipData
            }
            if (isIntentSafe(intent)) {
                cotnext.startActivity(intent)
            } else {
                osmandMissing()
            }
        } catch (e: Exception) {
            Toast.makeText(cotnext, e.message, Toast.LENGTH_LONG).show()
        }
    }

    private fun sendFileRequest(fileUri: Uri) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, fileUri)
            if (isIntentSafe(intent)) {
                cotnext.startActivity(intent)
            } else {
                osmandMissing()
            }
        } catch (e: Exception) {
            Toast.makeText(cotnext, e.message, Toast.LENGTH_LONG).show()
        }
    }

    private fun osmandMissing() {
        Toast.makeText(cotnext, "OsmAnd service NOT bind", Toast.LENGTH_SHORT).show()
    }

    fun isIntentSafe(intent: Intent): Boolean {
        val packageManager: PackageManager = cotnext.packageManager
        val activities = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return activities.size > 0
    }

    fun executeQuickAction(actionNumber: Int) {
        val params = mapOf(
            PARAM_CLOSE_AFTER_COMMAND to false.toString(),
            PARAM_QUICK_ACTION_NUMBER to actionNumber.toString()
        )
        sendRequest(OsmAndIntentBuilder(API_CMD_EXECUTE_QUICK_ACTION).setParams(params))
    }

    fun getQuickActionInfo(actionNumber: Int) {
        val params = mapOf(
            PARAM_CLOSE_AFTER_COMMAND to true.toString(),
            PARAM_QUICK_ACTION_NUMBER to actionNumber.toString()
        )
        sendRequest(OsmAndIntentBuilder(API_CMD_GET_QUICK_ACTION_INFO).setParams(params))
    }

    private class OsmAndIntentBuilder(val command: String) {
        var params: Map<String, String>? = null
        var extraData: Map<String, String>? = null
        var flags = 0
        var gpxUri: Uri? = null

        fun setExtraData(extraData: Map<String, String>): OsmAndIntentBuilder {
            this.extraData = extraData
            return this
        }

        fun setFlags(flags: Int): OsmAndIntentBuilder {
            this.flags = flags
            return this
        }

        fun setGpxUri(gpxUri: Uri): OsmAndIntentBuilder {
            this.gpxUri = gpxUri
            return this
        }

        fun setParams(params: Map<String, String>): OsmAndIntentBuilder {
            this.params = params
            return this
        }

        val uri: Uri
            get() = Uri.parse(getUriString(command, params))

        private fun getUriString(command: String, parameters: Map<String, String>?): String {
            val stringBuilder = StringBuilder(PREFIX)
            stringBuilder.append(command)
            if (parameters != null && parameters.isNotEmpty()) {
                stringBuilder.append("?")
                for ((key, value) in parameters) {
                    stringBuilder.append(key).append("=").append(value).append("&")
                }
                stringBuilder.delete(stringBuilder.length - 1, stringBuilder.length)
            }
            return stringBuilder.toString()
        }
    }

    fun getPackageName(): String {
        return OSMAND_PACKAGE_NAME
    }

    companion object {
        private const val PREFIX = "osmand.api://"
        private const val OSMAND_PLUS_PACKAGE_NAME = "net.osmand.plus"
        private const val OSMAND_PACKAGE_NAME = OSMAND_PLUS_PACKAGE_NAME

        const val RESULT_CODE_ERROR_UNKNOWN = 1001
        const val RESULT_CODE_ERROR_NOT_IMPLEMENTED = 1002
        const val RESULT_CODE_ERROR_PLUGIN_INACTIVE = 1003
        const val RESULT_CODE_ERROR_GPX_NOT_FOUND = 1004
        const val RESULT_CODE_ERROR_INVALID_PROFILE = 1005
        const val RESULT_CODE_ERROR_EMPTY_SEARCH_QUERY = 1006
        const val RESULT_CODE_ERROR_SEARCH_LOCATION_UNDEFINED = 1007
        const val RESULT_CODE_ERROR_QUICK_ACTION_NOT_FOUND = 1008

        private const val GET_INFO = "get_info"
        private const val RECORD_AUDIO = "record_audio"
        private const val RECORD_VIDEO = "record_video"
        private const val RECORD_PHOTO = "record_photo"
        private const val STOP_AV_REC = "stop_av_rec"
        private const val ADD_FAVORITE = "add_favorite"
        private const val ADD_MAP_MARKER = "add_map_marker"
        private const val SHOW_LOCATION = "show_location"
        private const val SHOW_GPX = "show_gpx"
        private const val NAVIGATE_GPX = "navigate_gpx"
        private const val NAVIGATE = "navigate"
        private const val NAVIGATE_SEARCH = "navigate_search"
        private const val PAUSE_NAVIGATION = "pause_navigation"
        private const val RESUME_NAVIGATION = "resume_navigation"
        private const val STOP_NAVIGATION = "stop_navigation"
        private const val MUTE_NAVIGATION = "mute_navigation"
        private const val UNMUTE_NAVIGATION = "unmute_navigation"
        private const val START_GPX_REC = "start_gpx_rec"
        private const val STOP_GPX_REC = "stop_gpx_rec"
        private const val SAVE_GPX = "save_gpx"
        private const val CLEAR_GPX = "clear_gpx"
        const val API_CMD_EXECUTE_QUICK_ACTION = "execute_quick_action"
        const val API_CMD_GET_QUICK_ACTION_INFO = "get_quick_action_info"
        const val API_CMD_SUBSCRIBE_VOICE_NOTIFICATIONS = "subscribe_voice_notifications"
        const val PARAM_NAME = "name"
        const val PARAM_DESC = "desc"
        const val PARAM_CATEGORY = "category"
        const val PARAM_LAT = "lat"
        const val PARAM_LON = "lon"
        const val PARAM_COLOR = "color"
        const val PARAM_VISIBLE = "visible"
        const val PARAM_PATH = "path"
        const val PARAM_URI = "uri"
        const val PARAM_DATA = "data"
        const val PARAM_FORCE = "force"
        const val PARAM_SEARCH_PARAMS = "search_params"
        const val PARAM_LOCATION_PERMISSION = "location_permission"
        const val PARAM_START_NAME = "start_name"
        const val PARAM_DEST_NAME = "dest_name"
        const val PARAM_START_LAT = "start_lat"
        const val PARAM_START_LON = "start_lon"
        const val PARAM_DEST_LAT = "dest_lat"
        const val PARAM_DEST_LON = "dest_lon"
        const val PARAM_DEST_SEARCH_QUERY = "dest_search_query"
        const val PARAM_SEARCH_LAT = "search_lat"
        const val PARAM_SEARCH_LON = "search_lon"
        const val PARAM_SHOW_SEARCH_RESULTS = "show_search_results"
        const val PARAM_PROFILE = "profile"
        const val PARAM_ETA = "eta"
        const val PARAM_TIME_LEFT = "time_left"
        const val PARAM_DISTANCE_LEFT = "time_distance_left"
        const val PARAM_CLOSE_AFTER_COMMAND = "close_after_command"
        const val PARAM_QUICK_ACTION_NAME = "quick_action_name"
        const val PARAM_QUICK_ACTION_TYPE = "quick_action_type"
        const val PARAM_QUICK_ACTION_PARAMS = "quick_action_params"
        const val PARAM_QUICK_ACTION_NUMBER = "quick_action_number"
    }
}
