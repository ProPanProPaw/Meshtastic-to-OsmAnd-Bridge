package com.meshtastic.android.meshserviceexample.meshtastic

import com.meshtastic.android.meshserviceexample.IAidlService
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.model.NodeInfo
import org.meshtastic.proto.MeshProtos

interface IMeshtasticListener : IAidlService {
    fun onNodeChangeReceived(nodeInfo: NodeInfo?)
    fun onMessageStatusReceived(status: MessageStatus?)
    fun onMeshConnectedReceived()
    fun onMeshDisconnectedReceived()
    fun onTextMessageReceived(dataPacket: DataPacket?)
    fun onPositionReceived(from: String?, position: MeshProtos.Position)
    fun onNodeInfoReceived(from: String?, meshUser: MeshProtos.User)
}

