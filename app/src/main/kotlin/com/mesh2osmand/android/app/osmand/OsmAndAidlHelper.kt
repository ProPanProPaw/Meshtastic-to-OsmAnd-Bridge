package com.mesh2osmand.android.app.osmand

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.os.RemoteException
import android.view.KeyEvent
import net.osmand.aidlapi.IOsmAndAidlCallback
import net.osmand.aidlapi.IOsmAndAidlInterface
import net.osmand.aidlapi.OsmandAidlConstants
import net.osmand.aidlapi.contextmenu.AContextMenuButton
import net.osmand.aidlapi.contextmenu.ContextMenuButtonsParams
import net.osmand.aidlapi.contextmenu.RemoveContextMenuButtonsParams
import net.osmand.aidlapi.contextmenu.UpdateContextMenuButtonsParams
import net.osmand.aidlapi.copyfile.CopyFileParams
import net.osmand.aidlapi.customization.*
import net.osmand.aidlapi.exit.ExitAppParams
import net.osmand.aidlapi.favorite.AFavorite
import net.osmand.aidlapi.favorite.AddFavoriteParams
import net.osmand.aidlapi.favorite.RemoveFavoriteParams
import net.osmand.aidlapi.favorite.UpdateFavoriteParams
import net.osmand.aidlapi.favorite.group.AFavoriteGroup
import net.osmand.aidlapi.favorite.group.AddFavoriteGroupParams
import net.osmand.aidlapi.favorite.group.RemoveFavoriteGroupParams
import net.osmand.aidlapi.favorite.group.UpdateFavoriteGroupParams
import net.osmand.aidlapi.gpx.*
import net.osmand.aidlapi.info.AppInfoParams
import net.osmand.aidlapi.info.GetTextParams
import net.osmand.aidlapi.logcat.ALogcatListenerParams
import net.osmand.aidlapi.logcat.OnLogcatMessageParams
import net.osmand.aidlapi.map.ALatLon
import net.osmand.aidlapi.map.SetMapLocationParams
import net.osmand.aidlapi.maplayer.AMapLayer
import net.osmand.aidlapi.maplayer.AddMapLayerParams
import net.osmand.aidlapi.maplayer.RemoveMapLayerParams
import net.osmand.aidlapi.maplayer.UpdateMapLayerParams
import net.osmand.aidlapi.maplayer.point.AMapPoint
import net.osmand.aidlapi.maplayer.point.AddMapPointParams
import net.osmand.aidlapi.maplayer.point.RemoveMapPointParams
import net.osmand.aidlapi.maplayer.point.ShowMapPointParams
import net.osmand.aidlapi.maplayer.point.UpdateMapPointParams
import net.osmand.aidlapi.mapmarker.AMapMarker
import net.osmand.aidlapi.mapmarker.AddMapMarkerParams
import net.osmand.aidlapi.mapmarker.RemoveMapMarkerParams
import net.osmand.aidlapi.mapmarker.RemoveMapMarkersParams
import net.osmand.aidlapi.mapmarker.UpdateMapMarkerParams
import net.osmand.aidlapi.mapwidget.AMapWidget
import net.osmand.aidlapi.mapwidget.AddMapWidgetParams
import net.osmand.aidlapi.mapwidget.RemoveMapWidgetParams
import net.osmand.aidlapi.mapwidget.UpdateMapWidgetParams
import net.osmand.aidlapi.navdrawer.NavDrawerFooterParams
import net.osmand.aidlapi.navdrawer.NavDrawerHeaderParams
import net.osmand.aidlapi.navdrawer.NavDrawerItem
import net.osmand.aidlapi.navdrawer.SetNavDrawerItemsParams
import net.osmand.aidlapi.navigation.*
import net.osmand.aidlapi.plugins.PluginParams
import net.osmand.aidlapi.profile.AExportSettingsType
import net.osmand.aidlapi.profile.ExportProfileParams
import net.osmand.aidlapi.search.SearchParams
import net.osmand.aidlapi.search.SearchResult
import net.osmand.aidlapi.tiles.ASqliteDbFile
import java.util.*

class OsmAndAidlHelper(private val context: Context, private val listener: IOsmAndServiceListener?) {

    private var mIOsmAndAidlInterface: IOsmAndAidlInterface? = null

    var searchCompleteListener: SearchCompleteListener? = null
    var updateListener: UpdateListener? = null
    var osmandInitializedListener: OsmandInitializedListener? = null
    var gpxBitmapCreatedListener: GpxBitmapCreatedListener? = null
    var navigationInfoUpdateListener: NavigationInfoUpdateListener? = null
    var contextButtonClickListener: ContextButtonClickListener? = null
    var voiceRouterNotifyListener: VoiceRouterNotifyListener? = null
    var logcatMessageListener: LogcatMessageListener? = null

    /**
     * Class for interacting with the main interface of the service.
     */
    private val mConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            mIOsmAndAidlInterface = IOsmAndAidlInterface.Stub.asInterface(service)
            listener?.onServiceConnected(TAG)
        }

        override fun onServiceDisconnected(className: ComponentName) {
            mIOsmAndAidlInterface = null
            listener?.onServiceDisconnected(TAG)
        }
    }

    private fun bindService(): Boolean {
        if (mIOsmAndAidlInterface == null) {
            val intent = Intent("net.osmand.aidl.OsmandAidlServiceV2")
            intent.setPackage(OSMAND_PACKAGE_NAME)
            val res = context.bindService(intent, mConnection, Context.BIND_AUTO_CREATE)
            if (res) {
                listener?.onServiceBinded(TAG)
                return true
            } else {
                listener?.onServiceNotBinded(TAG)
                return false
            }
        } else {
            return true
        }
    }

    fun cleanupResources() {
        if (mIOsmAndAidlInterface != null) {
            context.unbindService(mConnection)
            listener?.onServiceUnbinded(TAG)
        }
    }

    fun interface SearchCompleteListener {
        fun onSearchComplete(resultSet: List<SearchResult>)
    }

    fun interface UpdateListener {
        fun onUpdatePing()
    }

    fun interface OsmandInitializedListener {
        fun onOsmandInitilized()
    }

    fun interface GpxBitmapCreatedListener {
        fun onGpxBitmapCreated(bitmap: Bitmap)
    }

    fun interface NavigationInfoUpdateListener {
        fun onNavigationInfoUpdate(directionInfo: ADirectionInfo)
    }

    fun interface ContextButtonClickListener {
        fun onContextButtonClick(buttonId: Int, pointId: String, layerId: String)
    }

    fun interface VoiceRouterNotifyListener {
        fun onVoiceRouterNotify(params: OnVoiceNavigationParams)
    }

    fun interface LogcatMessageListener {
        fun onNewLogcatMessage(params: OnLogcatMessageParams)
    }

    private val mIOsmAndAidlCallback: IOsmAndAidlCallback.Stub = object : IOsmAndAidlCallback.Stub() {
        override fun onSearchComplete(resultSet: List<SearchResult>) {
            searchCompleteListener?.onSearchComplete(resultSet)
        }

        override fun onUpdate() {
            updateListener?.onUpdatePing()
        }

        override fun onAppInitialized() {
            osmandInitializedListener?.onOsmandInitilized()
        }

        override fun onGpxBitmapCreated(bitmap: AGpxBitmap) {
            gpxBitmapCreatedListener?.onGpxBitmapCreated(bitmap.bitmap)
        }

        override fun updateNavigationInfo(directionInfo: ADirectionInfo) {
            navigationInfoUpdateListener?.onNavigationInfoUpdate(directionInfo)
        }

        override fun onContextMenuButtonClicked(buttonId: Int, pointId: String, layerId: String) {
            contextButtonClickListener?.onContextButtonClick(buttonId, pointId, layerId)
        }

        override fun onVoiceRouterNotify(params: OnVoiceNavigationParams) {
            voiceRouterNotifyListener?.onVoiceRouterNotify(params)
        }

        override fun onKeyEvent(keyEvent: KeyEvent) {}

        override fun onLogcatMessage(params: OnLogcatMessageParams) {
            logcatMessageListener?.onNewLogcatMessage(params)
        }
    }

    /**
     * Refresh the map (UI)
     */
    fun refreshMap(): Boolean = execute { it.refreshMap() }

    fun addFavoriteGroup(name: String, color: String, visible: Boolean): Boolean = execute {
        val favoriteGroup = AFavoriteGroup(name, color, visible)
        it.addFavoriteGroup(AddFavoriteGroupParams(favoriteGroup))
    }

    fun updateFavoriteGroup(
        namePrev: String, colorPrev: String, visiblePrev: Boolean,
        nameNew: String, colorNew: String, visibleNew: Boolean
    ): Boolean = execute {
        val favoriteGroupPrev = AFavoriteGroup(namePrev, colorPrev, visiblePrev)
        val favoriteGroupNew = AFavoriteGroup(nameNew, colorNew, visibleNew)
        it.updateFavoriteGroup(UpdateFavoriteGroupParams(favoriteGroupPrev, favoriteGroupNew))
    }

    fun removeFavoriteGroup(name: String): Boolean = execute {
        val favoriteGroup = AFavoriteGroup(name, "", false)
        it.removeFavoriteGroup(RemoveFavoriteGroupParams(favoriteGroup))
    }

    fun addFavorite(
        lat: Double, lon: Double, name: String, description: String, address: String,
        category: String, color: String, visible: Boolean
    ): Boolean = execute {
        val favorite = AFavorite(lat, lon, name, description, address, category, color, visible)
        it.addFavorite(AddFavoriteParams(favorite))
    }

    fun updateFavorite(
        latPrev: Double, lonPrev: Double, namePrev: String, categoryPrev: String,
        latNew: Double, lonNew: Double, nameNew: String, descriptionNew: String,
        categoryNew: String, colorNew: String, visibleNew: Boolean
    ): Boolean = execute {
        val favoritePrev = AFavorite(latPrev, lonPrev, namePrev, "", "", categoryPrev, "", false)
        val favoriteNew = AFavorite(latNew, lonNew, nameNew, descriptionNew, "", categoryNew, colorNew, visibleNew)
        it.updateFavorite(UpdateFavoriteParams(favoritePrev, favoriteNew))
    }

    fun removeFavorite(lat: Double, lon: Double, name: String, category: String): Boolean = execute {
        val favorite = AFavorite(lat, lon, name, "", "", category, "", false)
        it.removeFavorite(RemoveFavoriteParams(favorite))
    }

    fun addMapMarker(lat: Double, lon: Double, name: String): Boolean = execute {
        val marker = AMapMarker(ALatLon(lat, lon), name)
        it.addMapMarker(AddMapMarkerParams(marker))
    }

    fun updateMapMarker(
        latPrev: Double, lonPrev: Double, namePrev: String,
        latNew: Double, lonNew: Double, nameNew: String
    ): Boolean = execute {
        val markerPrev = AMapMarker(ALatLon(latPrev, lonPrev), namePrev)
        val markerNew = AMapMarker(ALatLon(latNew, lonNew), nameNew)
        it.updateMapMarker(UpdateMapMarkerParams(markerPrev, markerNew))
    }

    fun removeMapMarker(lat: Double, lon: Double, name: String): Boolean = execute {
        val marker = AMapMarker(ALatLon(lat, lon), name)
        it.removeMapMarker(RemoveMapMarkerParams(marker))
    }

    fun addMapWidget(
        id: String, menuIconName: String, menuTitle: String,
        lightIconName: String, darkIconName: String, text: String, description: String,
        order: Int, intentOnClick: Intent
    ): Boolean = execute {
        val widget = AMapWidget(id, menuIconName, menuTitle, lightIconName, darkIconName, text, description, order, intentOnClick)
        it.addMapWidget(AddMapWidgetParams(widget))
    }

    fun updateMapWidget(
        id: String, menuIconName: String, menuTitle: String,
        lightIconName: String, darkIconName: String, text: String, description: String,
        order: Int, intentOnClick: Intent
    ): Boolean = execute {
        val widget = AMapWidget(id, menuIconName, menuTitle, lightIconName, darkIconName, text, description, order, intentOnClick)
        it.updateMapWidget(UpdateMapWidgetParams(widget))
    }

    fun removeMapWidget(id: String): Boolean = execute { it.removeMapWidget(RemoveMapWidgetParams(id)) }

    /**
     * Add user layer on the map.
     *
     * @param id (String) - layer id.
     * @param name (String) - layer name.
     * @param zOrder (float) - z-order position of layer. Default value is 5.5f
     * @param points Map<Sting, AMapPoint> - initial list of points. Nullable.
     * @param imagePoints (boolean) - use new style for points on map or not. Also default zoom bounds for new style can be edited.
     */
    fun addMapLayer(id: String, name: String, zOrder: Float, points: List<AMapPoint>?, imagePoints: Boolean): Boolean = execute {
        val layer = AMapLayer(id, name, zOrder, points)
        layer.isImagePoints = imagePoints
        it.addMapLayer(AddMapLayerParams(layer))
    }

    /**
     * Update user layer.
     *
     * @param id (String) - layer id.
     * @param name (String) - layer name.
     * @param zOrder (float) - z-order position of layer. Default value is 5.5f
     * @param points Map<Sting, AMapPoint> - list of points. Nullable.
     * @param imagePoints (boolean) - use new style for points on map or not. Also default zoom bounds for new style can be edited.
     */
    fun updateMapLayer(id: String, name: String, zOrder: Float, points: List<AMapPoint>?, imagePoints: Boolean): Boolean = execute {
        val layer = AMapLayer(id, name, zOrder, points)
        layer.isImagePoints = imagePoints
        it.updateMapLayer(UpdateMapLayerParams(layer))
    }

    fun removeMapLayer(id: String): Boolean = execute { it.removeMapLayer(RemoveMapLayerParams(id)) }

    /**
     * Show AMapPoint on map in OsmAnd.
     *
     * @param layerId (String) - layer id. Note: layer should be added first.
     * @param pointId (String) - point id.
     * @param shortName (String) - short name (single char). Displayed on the map.
     * @param fullName (String) - full name. Displayed in the context menu on first row.
     * @param typeName (String) - type name. Displayed in context menu on second row.
     * @param color (int) - color of circle's background.
     * @param location (ALatLon) - location of the point.
     * @param details List<String> - list of details. Displayed under context menu.
     * @param params Map<String, String> - optional map of params for point.
     */
    fun showMapPoint(
        layerId: String, pointId: String, shortName: String, fullName: String,
        typeName: String, color: Int, location: ALatLon, details: List<String>,
        params: Map<String, String>
    ): Boolean = execute {
        val point = AMapPoint(pointId, shortName, fullName, typeName, layerId, color, location, details, params)
        it.showMapPoint(ShowMapPointParams(layerId, point))
    }

    /**
     * Add point to user layer.
     *
     * @param layerId (String) - layer id. Note: layer should be added first.
     * @param pointId (String) - point id.
     * @param shortName (String) - short name (single char). Displayed on the map.
     * @param fullName (String) - full name. Displayed in the context menu on first row.
     * @param typeName (String) - type name. Displayed in context menu on second row.
     * @param color (int) - color of circle's background.
     * @param location (ALatLon) - location of the point.
     * @param details (List<String>)- list of details. Displayed under context menu.
     * @param params (Map<String, String>) - optional map of params for point.
     */
    fun addMapPoint(
        layerId: String, pointId: String, shortName: String, fullName: String,
        typeName: String, color: Int, location: ALatLon, details: List<String>,
        params: Map<String, String>
    ): Boolean = execute {
        val point = AMapPoint(pointId, shortName, fullName, typeName, layerId, color, location, details, params)
        it.addMapPoint(AddMapPointParams(layerId, point))
    }

    /**
     * Update point.
     *
     * @param layerId (String) - layer id.
     * @param pointId (String) - point id.
     * @param updateOpenedMenuAndMap (boolean) - flag to enable folowing mode and menu updates for point
     * @param shortName (String) - short name (single char). Displayed on the map.
     * @param fullName (String) - full name. Displayed in the context menu on first row.
     * @param typeName (String) - type name. Displayed in context menu on second row.
     * @param color (String) - color of circle's background.
     * @param location (ALatLon)- location of the point.
     * @param details (List<String>) - list of details. Displayed under context menu.
     * @param params (Map<String, String>) - optional map of params for point.
     */
    fun updateMapPoint(
        layerId: String, pointId: String, shortName: String, fullName: String,
        typeName: String, color: Int, location: ALatLon, details: List<String>,
        params: Map<String, String>,
        updateOpenedMenuAndMap: Boolean
    ): Boolean = execute {
        val point = AMapPoint(pointId, shortName, fullName, typeName, layerId, color, location, details, params)
        it.updateMapPoint(UpdateMapPointParams(layerId, point, updateOpenedMenuAndMap))
    }

    fun removeMapPoint(layerId: String, pointId: String): Boolean = execute { it.removeMapPoint(RemoveMapPointParams(layerId, pointId)) }

    fun setMapLocation(latitude: Double, longitude: Double, zoom: Int, rotation: Float, animated: Boolean): Boolean = execute {
        it.setMapLocation(SetMapLocationParams(latitude, longitude, zoom, rotation, animated))
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
        startName: String?, startLat: Double, startLon: Double, destName: String,
        destLat: Double, destLon: Double, profile: String?, force: Boolean, needLocationPermission: Boolean
    ): Boolean = execute {
        it.navigate(NavigateParams(startName, startLat, startLon, destName, destLat, destLon, profile, force, needLocationPermission))
    }

    fun setNavDrawerItems(appPackage: String, names: List<String>, uris: List<String>, iconNames: List<String>, flags: List<Int>): Boolean = execute {
        val items = names.indices.map { i -> NavDrawerItem(names[i], uris[i], iconNames[i], flags[i]) }
        it.setNavDrawerItems(SetNavDrawerItemsParams(appPackage, items))
    }

    fun pauseNavigation(): Boolean = execute { it.pauseNavigation(PauseNavigationParams()) }

    fun resumeNavigation(): Boolean = execute { it.resumeNavigation(ResumeNavigationParams()) }

    fun stopNavigation(): Boolean = execute { it.stopNavigation(StopNavigationParams()) }

    fun muteNavigation(): Boolean = execute { it.muteNavigation(MuteNavigationParams()) }

    fun unmuteNavigation(): Boolean = execute { it.unmuteNavigation(UnmuteNavigationParams()) }

    fun search(searchQuery: String, searchType: Int, latitude: Double, longitude: Double, radiusLevel: Int, totalLimit: Int): Boolean = execute {
        it.search(SearchParams(searchQuery, searchType, latitude, longitude, radiusLevel, totalLimit), mIOsmAndAidlCallback)
    }

    fun registerForUpdates(updateTimeMS: Long): Long =
        executeWithResult { it.registerForUpdates(updateTimeMS, mIOsmAndAidlCallback) } ?: -1

    fun unregisterFromUpdates(callbackId: Long): Boolean = execute { it.unregisterFromUpdates(callbackId) }

    fun setNavDrawerLogoWithParams(imageUri: String, packageName: String, intent: String): Boolean = execute {
        it.setNavDrawerLogoWithParams(NavDrawerHeaderParams(imageUri, packageName, intent))
    }

    fun setEnabledIds(ids: List<String>): Boolean = execute { it.setEnabledIds(ids) }

    fun setDisabledIds(ids: List<String>): Boolean = execute { it.setDisabledIds(ids) }

    fun setEnabledPatterns(patterns: List<String>): Boolean = execute { it.setEnabledPatterns(patterns) }

    fun setDisabledPatterns(patterns: List<String>): Boolean = execute { it.setDisabledPatterns(patterns) }

    fun regWidgetVisibility(widgetKey: String, appModesKeys: List<String>?): Boolean = execute {
        it.regWidgetVisibility(SetWidgetsParams(widgetKey, appModesKeys))
    }

    fun regWidgetAvailability(widgetKey: String, appModesKeys: List<String>?): Boolean = execute {
        it.regWidgetAvailability(SetWidgetsParams(widgetKey, appModesKeys))
    }

    fun customizeOsmandSettings(sharedPreferencesName: String, bundle: Bundle): Boolean = execute {
        it.customizeOsmandSettings(OsmandSettingsParams(sharedPreferencesName, bundle))
    }

    fun getImportedGpx(): List<AGpxFile>? = executeWithResult {
        val fileList = ArrayList<AGpxFile>()
        it.getImportedGpx(fileList)
        fileList
    }

    fun getSqliteDbFiles(): List<ASqliteDbFile>? = executeWithResult {
        val fileList = ArrayList<ASqliteDbFile>()
        it.getSqliteDbFiles(fileList)
        fileList
    }

    fun getActiveSqliteDbFiles(): List<ASqliteDbFile>? = executeWithResult {
        val fileList = ArrayList<ASqliteDbFile>()
        it.getActiveSqliteDbFiles(fileList)
        fileList
    }

    fun showSqliteDbFile(fileName: String): Boolean = execute { it.showSqliteDbFile(fileName) }

    fun hideSqliteDbFile(fileName: String): Boolean = execute { it.hideSqliteDbFile(fileName) }

    fun setNavDrawerFooterWithParams(packageName: String, intent: String, appName: String): Boolean = execute {
        it.setNavDrawerFooterWithParams(NavDrawerFooterParams(packageName, intent, appName))
    }

    fun restoreOsmand(): Boolean = execute { it.restoreOsmand() }

    fun changePluginState(pluginId: String, newState: Int): Boolean = execute {
        it.changePluginState(PluginParams(pluginId, newState))
    }

    fun registerForOsmandInitListener(): Boolean = execute { it.registerForOsmandInitListener(mIOsmAndAidlCallback) }

    fun copyFile(destinationDir: String, fileName: String, filePartData: ByteArray, startTime: Long, isDone: Boolean): Int {
        return executeWithResult { it.copyFile(CopyFileParams(destinationDir, fileName, filePartData, startTime, isDone)) } ?: OsmandAidlConstants.COPY_FILE_IO_ERROR
    }

    fun registerForNavigationUpdates(subscribeToUpdates: Boolean, callbackId: Long): Long {
        val params = ANavigationUpdateParams().apply {
            setCallbackId(callbackId)
            setSubscribeToUpdates(subscribeToUpdates)
        }
        return executeWithResult { it.registerForNavigationUpdates(params, mIOsmAndAidlCallback) } ?: -1L
    }

    fun getBlockedRoads(blockedRoads: MutableList<ABlockedRoad>): Boolean = execute { it.getBlockedRoads(blockedRoads) }

    fun addRoadBlock(blockedRoad: ABlockedRoad): Boolean = execute { it.addRoadBlock(AddBlockedRoadParams(blockedRoad)) }

    fun removeRoadBlock(blockedRoad: ABlockedRoad): Boolean = execute { it.removeRoadBlock(RemoveBlockedRoadParams(blockedRoad)) }

    fun addContextMenuButtons(
        buttonIdL: Int, leftTextCaptionL: String?, rightTextCaptionL: String?, leftIconNameL: String?,
        rightIconNameL: String?, needColorizeIconL: Boolean, enabledL: Boolean,
        buttonIdR: Int, leftTextCaptionR: String?, rightTextCaptionR: String?, leftIconNameR: String?,
        rightIconNameR: String?, needColorizeIconR: Boolean, enabledR: Boolean,
        id: String, appPackage: String, layerId: String, callbackId: Long, pointsIds: List<String>?
    ): Boolean = execute {
        val leftButtonParams = AContextMenuButton(buttonIdL, leftTextCaptionL, rightTextCaptionL, leftIconNameL, rightIconNameL, needColorizeIconL, enabledL)
        val rightButtonParams = AContextMenuButton(buttonIdR, leftTextCaptionR, rightTextCaptionR, leftIconNameR, rightIconNameR, needColorizeIconR, enabledR)
        val params = ContextMenuButtonsParams(leftButtonParams, rightButtonParams, id, appPackage, layerId, callbackId, pointsIds)
        it.addContextMenuButtons(params, mIOsmAndAidlCallback) >= 0
    }

    fun removeContextMenuButtons(paramsId: String, callbackId: Long): Boolean = execute {
        val params = RemoveContextMenuButtonsParams(paramsId, callbackId)
        it.removeContextMenuButtons(params)
    }

    fun updateContextMenuButtons(
        buttonIdL: Int, leftTextCaptionL: String?, rightTextCaptionL: String?, leftIconNameL: String?,
        rightIconNameL: String?, needColorizeIconL: Boolean, enabledL: Boolean,
        buttonIdR: Int, leftTextCaptionR: String?, rightTextCaptionR: String?, leftIconNameR: String?,
        rightIconNameR: String?, needColorizeIconR: Boolean, enabledR: Boolean,
        id: String, appPackage: String, layerId: String, callbackId: Long, pointsIds: List<String>?
    ): Boolean = execute {
        val leftButtonParams = AContextMenuButton(buttonIdL, leftTextCaptionL, rightTextCaptionL, leftIconNameL, rightIconNameL, needColorizeIconL, enabledL)
        val rightButtonParams = AContextMenuButton(buttonIdR, leftTextCaptionR, rightTextCaptionR, leftIconNameR, rightIconNameR, needColorizeIconR, enabledR)
        val params = ContextMenuButtonsParams(leftButtonParams, rightButtonParams, id, appPackage, layerId, callbackId, pointsIds)
        val updateParams = UpdateContextMenuButtonsParams(params)
        it.updateContextMenuButtons(updateParams)
    }

    fun areOsmandSettingsCustomized(sharedPreferencesName: String): Boolean = execute {
        it.areOsmandSettingsCustomized(OsmandSettingsInfoParams(sharedPreferencesName))
    }

    fun setCustomization(
        settingsParams: OsmandSettingsParams?,
        navDrawerHeaderParams: NavDrawerHeaderParams?,
        navDrawerFooterParams: NavDrawerFooterParams?,
        navDrawerItemsParams: SetNavDrawerItemsParams?,
        visibilityWidgetsParams: ArrayList<SetWidgetsParams>?,
        availabilityWidgetsParams: ArrayList<SetWidgetsParams>?,
        pluginsParams: ArrayList<PluginParams>?,
        featuresEnabledIds: List<String>?,
        featuresDisabledIds: List<String>?,
        featuresEnabledPatterns: List<String>?,
        featuresDisabledPatterns: List<String>?
    ): Boolean {
        val customizationParams = CustomizationInfoParams(settingsParams, navDrawerHeaderParams, navDrawerFooterParams, navDrawerItemsParams, visibilityWidgetsParams, availabilityWidgetsParams, pluginsParams, featuresEnabledIds, featuresDisabledIds, featuresEnabledPatterns, featuresDisabledPatterns)
        return execute { it.setCustomization(customizationParams) }
    }

    fun registerForVoiceRouterMessages(subscribeToUpdates: Boolean, callbackId: Long): Long {
        val params = ANavigationVoiceRouterMessageParams().apply {
            setCallbackId(callbackId)
            setSubscribeToUpdates(subscribeToUpdates)
        }
        return executeWithResult { it.registerForVoiceRouterMessages(params, mIOsmAndAidlCallback) } ?: -1L
    }

    fun removeAllActiveMapMarkers(): Boolean = execute { it.removeAllActiveMapMarkers(RemoveMapMarkersParams()) }

    fun setMapMargins(
        leftMargin: Int, topMargin: Int, rightMargin: Int, bottomMargin: Int,
        appModesKeys: List<String>?
    ): Boolean = execute {
        it.setMapMargins(MapMarginsParams(leftMargin, topMargin, rightMargin, bottomMargin, appModesKeys))
    }

    fun importProfile(profileUri: Uri, settingsTypeList: ArrayList<AExportSettingsType>?, replace: Boolean, silent: Boolean): Boolean = execute {
        context.grantUriPermission(OSMAND_PACKAGE_NAME, profileUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        it.importProfile(ProfileSettingsParams(profileUri, settingsTypeList, replace, silent, null, -1))
    }

    fun exportProfile(profileKey: String, settingsTypeList: ArrayList<AExportSettingsType>?): Boolean = execute {
        it.exportProfile(ExportProfileParams(profileKey, settingsTypeList))
    }

    fun isFragmentOpen(): Boolean = execute { it.isFragmentOpen() }

    fun isMenuOpen(): Boolean = execute { it.isMenuOpen() }

    fun exitApp(shouldRestart: Boolean): Boolean = execute { it.exitApp(ExitAppParams(shouldRestart)) }

    fun getText(key: String, locale: Locale): String? = executeWithResult {
        val params = GetTextParams(key, locale)
        it.getText(params)
        params.value
    }

    fun getPreferenceValue(preferenceId: String, appModeKey: String?): String? = executeWithResult {
        val params = PreferenceParams(preferenceId).apply { setAppModeKey(appModeKey) }
        it.getPreference(params)
        params.value
    }

    fun setPreferenceValue(preferenceId: String, value: String, appModeKey: String?): Boolean = execute {
        val params = PreferenceParams(preferenceId).apply {
            setAppModeKey(appModeKey)
            setValue(value)
        }
        it.setPreference(params)
    }

    fun registerForLogcatMessages(subscribeToUpdates: Boolean, callbackId: Long, filterLevel: String): Long {
        val params = ALogcatListenerParams().apply {
            setCallbackId(callbackId)
            setSubscribeToUpdates(subscribeToUpdates)
            setFilterLevel(filterLevel)
        }
        return executeWithResult { it.registerForLogcatMessages(params, mIOsmAndAidlCallback) } ?: -1L
    }

    private fun <T> executeWithResult(block: (IOsmAndAidlInterface) -> T): T? {
        mIOsmAndAidlInterface?.let {
            return try {
                block(it)
            } catch (e: RemoteException) {
                e.printStackTrace()
                null
            }
        }
        return null
    }

    private fun execute(block: (IOsmAndAidlInterface) -> Boolean): Boolean {
        return executeWithResult(block) ?: false
    }

    public fun getAppInfo(): AppInfoParams? {
        try {
            return mIOsmAndAidlInterface?.getAppInfo()
        } catch (e: Exception) {
            return null
        }
    }

    init {
        bindService()
    }

    companion object {
        private const val OSMAND_PLUS_PACKAGE_NAME = "net.osmand.plus"
        public const val OSMAND_PACKAGE_NAME = OSMAND_PLUS_PACKAGE_NAME

        private const val MAX_RETRY_COUNT = 10
        private val BUFFER_SIZE = OsmandAidlConstants.COPY_FILE_PART_SIZE_LIMIT
        const val TAG: String = "OsmAnd Service"
    }
}
