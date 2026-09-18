package app.smartremote.android.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

const val PROTOCOL_VERSION: Int = 1
const val REALTIME_FRAME_SIZE: Int = 30

@Serializable
data class ClientHello(
    val type: String = "client_hello",
    val protocol: Int = PROTOCOL_VERSION,
    @SerialName("client_id") val clientId: String,
    @SerialName("device_name") val deviceName: String,
    val capabilities: List<String> = listOf("pointer", "scroll", "volume", "media", "keyboard"),
)

@Serializable
data class ControlMessage(
    val type: String = "control",
    @SerialName("request_id") val requestId: String,
    val action: String,
    val payload: JsonObject = JsonObject(emptyMap()),
)

@Serializable
data class Heartbeat(
    val type: String = "heartbeat",
    val sequence: Long,
)

data class RemoteState(
    val serverId: String = "",
    val serverName: String = "Windows PC",
    val volume: Float = 0.5f,
    val muted: Boolean = false,
    val revision: Long = 0,
)

data class AnimeVostState(
    val revision: Long = 0,
    val available: Boolean = false,
    val siteId: String? = null,
    val siteName: String? = null,
    val title: String? = null,
    val episode: Int? = null,
    val episodeCount: Int? = null,
    val playing: Boolean = false,
    val skipAvailable: Boolean = false,
    val previousAvailable: Boolean = false,
    val nextAvailable: Boolean = false,
    val autoMode: Boolean = false,
    val countdownSeconds: Int? = null,
    val message: String? = null,
)

enum class RealtimeKind(val wireValue: Byte) {
    Pointer(1),
    Scroll(2),
}

data class RealtimeFrame(
    val kind: RealtimeKind,
    val streamId: UInt,
    val sequence: UInt,
    val clientTimestampUs: ULong,
    val cumulativeX: Float,
    val cumulativeY: Float,
) {
    fun encode(): ByteArray {
        require(cumulativeX.isFinite() && cumulativeY.isFinite()) {
            "Realtime displacement must be finite"
        }
        return ByteArray(REALTIME_FRAME_SIZE).also { bytes ->
            bytes[0] = PROTOCOL_VERSION.toByte()
            bytes[1] = kind.wireValue
            bytes.putUIntLe(2, streamId)
            bytes.putUIntLe(6, sequence)
            bytes.putULongLe(10, clientTimestampUs)
            bytes.putUIntLe(18, cumulativeX.toRawBits().toUInt())
            bytes.putUIntLe(22, cumulativeY.toRawBits().toUInt())
            // bytes 26..29 are reserved and remain zero.
        }
    }
}

private fun ByteArray.putUIntLe(offset: Int, value: UInt) {
    repeat(4) { index -> this[offset + index] = (value shr (index * 8)).toByte() }
}

private fun ByteArray.putULongLe(offset: Int, value: ULong) {
    repeat(8) { index -> this[offset + index] = (value shr (index * 8)).toByte() }
}
