package app.smartremote.android.network

import android.os.SystemClock
import app.smartremote.android.protocol.ClientHello
import app.smartremote.android.protocol.AnimeVostState
import app.smartremote.android.protocol.ControlMessage
import app.smartremote.android.protocol.Heartbeat
import app.smartremote.android.protocol.PROTOCOL_VERSION
import app.smartremote.android.protocol.RealtimeFrame
import app.smartremote.android.protocol.RealtimeKind
import app.smartremote.android.protocol.RemoteState
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.ktor.websocket.send
import java.io.Closeable
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

sealed interface ConnectionStatus {
    data object Disconnected : ConnectionStatus
    data object Connecting : ConnectionStatus
    data class Connected(val serverName: String) : ConnectionStatus
    data class Failed(val message: String) : ConnectionStatus
}

class SmartRemoteConnection(
    private val clientIdProvider: suspend () -> String,
    private val deviceName: String,
    private val capabilities: List<String> = listOf("pointer", "scroll", "volume", "media", "keyboard"),
) : Closeable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val client = HttpClient(OkHttp) {
        // Ktor's OkHttp engine delegates frame handling to OkHttp and does not
        // support the WebSockets maxFrameSize switch.
        install(WebSockets)
        engine {
            config {
                pingInterval(1, TimeUnit.SECONDS)
                retryOnConnectionFailure(false)
            }
        }
    }
    private val sendMutex = Mutex()
    private val pointerFrames = Channel<RealtimeFrame>(Channel.CONFLATED)
    private val scrollFrames = Channel<RealtimeFrame>(Channel.CONFLATED)
    // Control messages must remain ordered and must never be silently dropped.
    // They are low-rate compared with realtime input, so an unbounded single-consumer
    // channel is safer than making a UI callback block when a temporary send stalls.
    private val controlFrames = Channel<String>(Channel.UNLIMITED)
    private var session: DefaultClientWebSocketSession? = null
    private var sessionJob: Job? = null

    private val _status = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Disconnected)
    val status: StateFlow<ConnectionStatus> = _status.asStateFlow()

    private val _remoteState = MutableStateFlow(RemoteState())
    val remoteState: StateFlow<RemoteState> = _remoteState.asStateFlow()

    private val _animeState = MutableStateFlow(AnimeVostState())
    val animeState: StateFlow<AnimeVostState> = _animeState.asStateFlow()

    init {
        scope.launch { drainRealtime(pointerFrames) }
        scope.launch { drainRealtime(scrollFrames) }
        scope.launch {
            for (text in controlFrames) sendText(text)
        }
    }

    fun connect(host: String, port: Int) {
        disconnect()
        _animeState.value = AnimeVostState()
        _status.value = ConnectionStatus.Connecting
        sessionJob = scope.launch {
            var opened: DefaultClientWebSocketSession? = null
            try {
                val urlHost = if (':' in host && !host.startsWith('[')) {
                    "[${host.replace("%", "%25")}]"
                } else {
                    host
                }
                opened = client.webSocketSession("ws://$urlHost:$port/v1/ws")
                session = opened
                sendText(
                    json.encodeToString(
                        ClientHello(
                            clientId = clientIdProvider(),
                            deviceName = deviceName,
                            capabilities = capabilities,
                        ),
                    ),
                )
                launchHeartbeat(opened)
                receive(opened)
                if (_status.value !is ConnectionStatus.Failed) {
                    _status.value = ConnectionStatus.Disconnected
                }
            } catch (_: CancellationException) {
                _status.value = ConnectionStatus.Disconnected
            } catch (error: Exception) {
                _status.value = ConnectionStatus.Failed(error.message ?: error.javaClass.simpleName)
            } finally {
                if (session === opened) session = null
                runCatching { opened?.close() }
            }
        }
    }

    fun disconnect() {
        sessionJob?.cancel()
        sessionJob = null
        session = null
        _status.value = ConnectionStatus.Disconnected
        _animeState.value = AnimeVostState()
    }

    fun sendControl(action: String, payload: JsonObject = JsonObject(emptyMap())) {
        controlFrames.trySend(
            json.encodeToString(
                ControlMessage(
                    requestId = UUID.randomUUID().toString(),
                    action = action,
                    payload = payload,
                ),
            ),
        )
    }

    fun sendRealtime(frame: RealtimeFrame) {
        val channel = when (frame.kind) {
            RealtimeKind.Pointer -> pointerFrames
            RealtimeKind.Scroll -> scrollFrames
        }
        channel.trySend(frame)
    }

    private suspend fun receive(opened: DefaultClientWebSocketSession) {
        for (frame in opened.incoming) {
            if (frame !is Frame.Text) continue
            val value = runCatching { json.parseToJsonElement(frame.readText()).jsonObject }.getOrNull()
                ?: continue
            when (value["type"]?.jsonPrimitive?.contentOrNull) {
                "server_hello" -> {
                    val protocol = value["protocol"]?.jsonPrimitive?.longOrNull?.toInt()
                    if (protocol != PROTOCOL_VERSION) {
                        _status.value = ConnectionStatus.Failed("Unsupported protocol $protocol")
                        opened.close()
                        return
                    }
                    val state = RemoteState(
                        serverId = value["server_id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        serverName = value["server_name"]?.jsonPrimitive?.contentOrNull ?: "Windows PC",
                        volume = value["volume"]?.jsonPrimitive?.floatOrNull ?: 0.5f,
                        muted = value["muted"]?.jsonPrimitive?.booleanOrNull ?: false,
                    )
                    _remoteState.value = state
                    _status.value = ConnectionStatus.Connected(state.serverName)
                }
                "state" -> _remoteState.value = _remoteState.value.copy(
                    volume = value["volume"]?.jsonPrimitive?.floatOrNull
                        ?: _remoteState.value.volume,
                    muted = value["muted"]?.jsonPrimitive?.booleanOrNull
                        ?: _remoteState.value.muted,
                    revision = value["revision"]?.jsonPrimitive?.longOrNull
                        ?: _remoteState.value.revision,
                )
                "anime_state" -> _animeState.value = AnimeVostState(
                    revision = value["revision"]?.jsonPrimitive?.longOrNull ?: 0L,
                    available = value["available"]?.jsonPrimitive?.booleanOrNull ?: false,
                    siteId = value["site_id"]?.jsonPrimitive?.contentOrNull,
                    siteName = value["site_name"]?.jsonPrimitive?.contentOrNull,
                    title = value["title"]?.jsonPrimitive?.contentOrNull,
                    episode = value["episode"]?.jsonPrimitive?.longOrNull?.toInt(),
                    episodeCount = value["episode_count"]?.jsonPrimitive?.longOrNull?.toInt(),
                    playing = value["playing"]?.jsonPrimitive?.booleanOrNull ?: false,
                    skipAvailable = value["skip_available"]?.jsonPrimitive?.booleanOrNull ?: false,
                    previousAvailable = value["previous_available"]?.jsonPrimitive?.booleanOrNull
                        ?: false,
                    nextAvailable = value["next_available"]?.jsonPrimitive?.booleanOrNull ?: false,
                    autoMode = value["auto_mode"]?.jsonPrimitive?.booleanOrNull ?: false,
                    countdownSeconds = value["countdown_seconds"]?.jsonPrimitive?.longOrNull?.toInt(),
                    message = value["message"]?.jsonPrimitive?.contentOrNull,
                )
                "ack" -> {
                    val ok = value["ok"]?.jsonPrimitive?.booleanOrNull ?: true
                    if (!ok && _status.value !is ConnectionStatus.Connected) {
                        _status.value = ConnectionStatus.Failed(
                            value["error"]?.toString() ?: "Remote action failed",
                        )
                    }
                }
            }
        }
    }

    private fun CoroutineScope.launchHeartbeat(opened: DefaultClientWebSocketSession): Job = launch {
        var sequence = 0L
        while (isActive && session === opened) {
            delay(1.seconds)
            sendText(json.encodeToString(Heartbeat(sequence = sequence++)))
        }
    }

    private suspend fun drainRealtime(channel: Channel<RealtimeFrame>) {
        for (frame in channel) {
            sendBinary(frame.encode())
            delay(REALTIME_SEND_INTERVAL_MS)
        }
    }

    private suspend fun sendText(text: String) {
        val current = session ?: return
        sendMutex.withLock { current.send(Frame.Text(text)) }
    }

    private suspend fun sendBinary(bytes: ByteArray) {
        val current = session ?: return
        sendMutex.withLock { current.send(Frame.Binary(fin = true, data = bytes)) }
    }

    override fun close() {
        disconnect()
        pointerFrames.close()
        scrollFrames.close()
        controlFrames.close()
        client.close()
    }

    companion object {
        internal const val REALTIME_SEND_INTERVAL_MS = 17L

        fun monotonicTimestampUs(): ULong = (SystemClock.elapsedRealtimeNanos() / 1_000L).toULong()
    }
}
