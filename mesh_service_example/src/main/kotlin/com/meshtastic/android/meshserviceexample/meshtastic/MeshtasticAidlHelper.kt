package com.meshtastic.android.meshserviceexample.meshtastic

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.google.protobuf.ByteString
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.model.NodeInfo
import org.meshtastic.core.service.IMeshService
import org.meshtastic.proto.MeshProtos
import org.meshtastic.proto.Portnums
import java.util.concurrent.ConcurrentHashMap


data class MeshPeer(
    val id: String,
    val ipAddress: String,
    val lastSeen: Long,
    val nickname: String? = null,
    val longName: String? = null,
    val capabilities: Set<String> = emptySet(),
    val networkQuality: Float = 1.0f,
    val lastStateVersion: Long = 0L,
    val hasPKC: Boolean = false,
)
class MeshtasticAidlHelper(private val context: Context, private val listener: IMeshtasticListener?) {

    companion object {
        const val TAG: String = "Mesh Service"
    }

    private val peersMap = ConcurrentHashMap<String, MeshPeer>()
    private var meshServiceAidlInterface: IMeshService? = null

    private val mConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            meshServiceAidlInterface = IMeshService.Stub.asInterface(service)
            listener?.onServiceConnected(TAG)
        }

        override fun onServiceDisconnected(className: ComponentName) {
            meshServiceAidlInterface = null
            listener?.onServiceDisconnected(TAG)
        }
    }

    init {
        bindService()


        val meshtasticReceiver: BroadcastReceiver =
            object : BroadcastReceiver() {
                @Suppress("ReturnCount")
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent == null) {
                        Log.w(TAG, "Received null intent")
                        return
                    }

                    val action = intent.action

                    if (intent.action == null) {
                        Log.w(TAG, "Received null action")
                        return
                    }


                    Log.d(TAG, "Received broadcast: $action")

                    when (action) {
                            "com.geeksville.mesh.NODE_CHANGE" ->
                            try {

                                val ni: NodeInfo? =
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        intent.getParcelableExtra("com.geeksville.mesh.NodeInfo", NodeInfo::class.java)
                                    } else {
                                        @Suppress("DEPRECATION")
                                        intent.getParcelableExtra("com.geeksville.mesh.NodeInfo")
                                    }

                                Log.d(TAG, "NodeInfo: $ni")
                                listener?.onNodeChangeReceived(ni)
                            } catch (e: Error) {
                                Log.e(TAG, "onReceive Error: ${e.message}")
                                return
                            }catch (e: Exception) {
                                Log.e(TAG, "onReceive: ${e.message}")
                                return
                            }
                        "com.geeksville.mesh.RECEIVED.TEXT_MESSAGE_APP" ->
                            try {

                                val dataPacket = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    intent.getParcelableExtra("com.geeksville.mesh.Payload", DataPacket::class.java)
                                } else {
                                    @Suppress("DEPRECATION")
                                    intent.getParcelableExtra("com.geeksville.mesh.Payload")
                                }

                                Log.d(TAG, "DataPacket: $dataPacket")
                                listener?.onTextMessageReceived(dataPacket)
                            } catch (e: Error) {
                                Log.e(TAG, "onReceive Error: ${e.message}")
                                return
                            }catch (e: Exception) {
                                Log.e(TAG, "onReceive: ${e.message}")
                                return
                            }

                        "com.geeksville.mesh.MESSAGE_STATUS" -> {
                            val id = intent.getIntExtra("com.geeksville.mesh.PacketId", 0)
                            val status =
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    intent.getParcelableExtra("com.geeksville.mesh.Status", MessageStatus::class.java)
                                } else {
                                    @Suppress("DEPRECATION")
                                    intent.getParcelableExtra("com.geeksville.mesh.Status")
                                }
                            listener?.onMessageStatusReceived(status)
                            Log.d(TAG, "Message Status ID: $id Status: $status")
                        }

                        "com.geeksville.mesh.MESH_CONNECTED" -> {
                            val extraConnected = intent.getStringExtra("com.geeksville.mesh.Connected")
                            val connected = extraConnected.equals("connected", ignoreCase = true)
                            Log.d(TAG, "Received ACTION_MESH_CONNECTED: $extraConnected")
                            if (connected) {
                                listener?.onMeshConnectedReceived()
                            }
                        }

                        "com.geeksville.mesh.MESH_DISCONNECTED" -> {
                            val extraConnected = intent.getStringExtra("com.geeksville.mesh.Disconnected")
                            val disconnected = extraConnected.equals("disconnected", ignoreCase = true)
                            Log.d(TAG, "Received ACTION_MESH_DISCONNECTED: $extraConnected")
                            if (disconnected) {
                                listener?.onMeshDisconnectedReceived()

                            }
                        }

                        "com.geeksville.mesh.RECEIVED.POSITION_APP" -> {
                            Log.d(TAG, "=== POSITION_APP Broadcast Debug ===")
                            Log.d(TAG, "Intent extras keys: ${intent.extras?.keySet()}")
                            intent.extras?.keySet()?.forEach { key ->
                                val value = intent.extras?.get(key)
                                Log.d(TAG, "Extra key: $key, value: $value, type: ${value?.javaClass?.name}")
                            }

                            try {
                                val dataPacket = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    intent.getParcelableExtra("com.geeksville.mesh.Payload", DataPacket::class.java)
                                } else {
                                    @Suppress("DEPRECATION")
                                    intent.getParcelableExtra("com.geeksville.mesh.Payload")
                                }

                                if(dataPacket?.bytes != null) {

                                    val dataType = dataPacket.dataType
                                    val position: MeshProtos.Position =
                                        MeshProtos.Position.parseFrom(dataPacket.bytes)

                                    Log.d(TAG, "POSITION_APP parsed position: lat: " + position.latitudeI /*/ LAT_LON_INT_TO_DOUBLE_CONVERSION*/ + ", lon: " + position.longitudeI /*/ LAT_LON_INT_TO_DOUBLE_CONVERSION*/ + ", alt: " + position.altitudeHae + ", from: " + dataPacket.from);

                                    val gpsValid = position.latitudeI != 0 || position.longitudeI != 0
                                    listener?.onPositionReceived(dataPacket.from, position)
                                    //Log.d(TAG, "dataPacket?.bytes == null")
                                }
                                else {
                                    Log.d(TAG, "dataPacket?.bytes == null")
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error unmarshalling POSITION_APP: ${e.message}", e)
                            }
                        }

                        "com.geeksville.mesh.RECEIVED.NODEINFO_APP" -> {
                            Log.d(TAG, "=== NODEINFO_APP Broadcast Debug ===")
                            Log.d(TAG, "Intent extras keys: ${intent.extras?.keySet()}")
                            intent.extras?.keySet()?.forEach { key ->
                                val value = intent.extras?.get(key)
                                Log.d(TAG, "Extra key: $key, value: $value, type: ${value?.javaClass?.name}")
                            }

                            try {
                                val dataPacket = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    intent.getParcelableExtra("com.geeksville.mesh.Payload", DataPacket::class.java)
                                } else {
                                    @Suppress("DEPRECATION")
                                    intent.getParcelableExtra("com.geeksville.mesh.Payload")
                                }

                                if(dataPacket?.bytes != null) {

                                    val meshUser: MeshProtos.User =
                                        MeshProtos.User.parseFrom(dataPacket.bytes)
                                    Log.d(
                                        TAG,
                                        "NODEINFO_APP parsed NodeInfo: " + meshUser.getId() + ", longName: " + meshUser.getLongName() + ", shortName: " + meshUser.getShortName()
                                    )
                                    listener?.onNodeInfoReceived(dataPacket.from, meshUser)
                                }
                                else {
                                    Log.d(TAG, "dataPacket?.bytes == null")
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error unmarshalling NODEINFO_APP: ${e.message}", e)
                            }
                        }

                        else -> Log.w(TAG, "Unknown action: $action")
                    }
                }
            }

        val filter =
            IntentFilter().apply {
                addAction("com.geeksville.mesh.NODE_CHANGE")
                addAction("com.geeksville.mesh.RECEIVED.NODEINFO_APP")
                addAction("com.geeksville.mesh.RECEIVED.POSITION_APP")
                addAction("com.geeksville.mesh.MESH_CONNECTED")
                addAction("com.geeksville.mesh.MESH_DISCONNECTED")
                addAction("com.geeksville.mesh.RECEIVED.TEXT_MESSAGE_APP")
            }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(meshtasticReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(meshtasticReceiver, filter)
        }
        Log.d(TAG, "Registered meshtasticPacketReceiver")
    }

    private fun bindService(): Boolean {
        if (meshServiceAidlInterface == null) {
            val intent = Intent("com.geeksville.mesh.Service").apply {
                setClassName("com.geeksville.mesh", "com.geeksville.mesh.service.MeshService")
            }

            return if (context.bindService(intent, mConnection, Context.BIND_AUTO_CREATE)) {
                listener?.onServiceBinded(TAG)
                true
            } else {
                listener?.onServiceNotBinded(TAG)
                false
            }
        }
        return true
    }



    private fun DataPacket.toMeshPacket(): MeshProtos.MeshPacket {
        try {
            val builder = MeshProtos.MeshPacket.newBuilder()
                .setId(this.id)
                .setChannel(this.channel)
                .setWantAck(this.wantAck)
                .setHopLimit(this.hopLimit)

            // Convert from/to node IDs
            this.from?.let { fromId ->
                if (fromId != DataPacket.ID_LOCAL) {
                    val nodeNum = DataPacket.idToDefaultNodeNum(fromId)
                    if (nodeNum != null) {
                        builder.setFrom(nodeNum.toInt())
                    } else {
                        Log.w(TAG, "Could not convert from ID '$fromId' to node number")
                    }
                }
            }

            this.to?.let { toId ->
                if (toId != DataPacket.ID_BROADCAST) {
                    val nodeNum = DataPacket.idToDefaultNodeNum(toId)
                    if (nodeNum != null) {
                        builder.setTo(nodeNum.toInt())
                    } else {
                        Log.w(TAG, "Could not convert to ID '$toId' to node number")
                    }
                } else {
                    builder.setTo(0xffffffffL.toInt()) // Broadcast address
                }
            }

            // Create decoded data
            val dataBuilder = MeshProtos.Data.newBuilder()
                .setPortnum(Portnums.PortNum.forNumber(this.dataType) ?: Portnums.PortNum.UNKNOWN_APP)
                .setPayload(ByteString.copyFrom(this.bytes ?: ByteArray(0)))
                .setRequestId(this.id)

            builder.setDecoded(dataBuilder.build())
            return builder.build()
        } catch (e: Exception) {
            Log.e(TAG, "Error converting DataPacket to MeshPacket: ${e.message}", e)
            throw e
        }
    }

    fun cleanupResources() {
        if (meshServiceAidlInterface != null) {
            context.unbindService(mConnection)
            listener?.onServiceUnbinded(TAG)
        }
    }

    public fun getMyId(): String? {
        try {
            return meshServiceAidlInterface?.getMyId()
        } catch (e: Exception) {
            return null
        }
    }
}
