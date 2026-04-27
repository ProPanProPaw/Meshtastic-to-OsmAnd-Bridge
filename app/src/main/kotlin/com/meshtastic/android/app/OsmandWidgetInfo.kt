package com.meshtastic.android.app

public class OsmandWidgetInfo {
    var userId: String?
    var shortName: String?
    var positionTime: Int
    var lastHeardAt: Int
    var batteryLevel: Int?
    var snr: Float
    var rssi: Int
    var posLatitude: Double
    var posLongitude: Double

    constructor(
        userId: String? = null,
        shortName: String?,
        positionTime: Int,
        lastHeardAt: Int,
        batteryLevel: Int?,
        snr: Float,
        rssi: Int,
        posLatitude: Double,
        posLongitude: Double
    ) {
        this.userId = userId
        this.shortName = shortName
        this.positionTime = positionTime
        this.lastHeardAt = lastHeardAt
        this.batteryLevel = batteryLevel
        this.snr = snr
        this.rssi = rssi
        this.posLatitude = posLatitude
        this.posLongitude = posLongitude
    }

}