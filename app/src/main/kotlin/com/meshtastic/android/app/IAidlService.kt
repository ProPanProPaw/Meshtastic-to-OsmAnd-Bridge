package com.meshtastic.android.app

interface IAidlService {

    fun onServiceConnected(serviceName: String)
    fun onServiceDisconnected(serviceName: String)
    fun onServiceBinded(serviceName: String)
    fun onServiceNotBinded(serviceName: String)

    fun onServiceUnbinded(serviceName: String)
}