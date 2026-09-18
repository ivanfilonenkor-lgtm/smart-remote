package app.smartremote.android

import android.app.Application
import android.os.Build
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.smartremote.android.input.Button
import app.smartremote.android.input.AirMouseActivationController
import app.smartremote.android.input.AirMouseActivationState
import app.smartremote.android.input.GestureOutput
import app.smartremote.android.input.GyroPointerController
import app.smartremote.android.input.GyroStabilizationProfile
import app.smartremote.android.network.ConnectionStatus
import app.smartremote.android.network.DiscoveredPc
import app.smartremote.android.network.DiscoveryStatus
import app.smartremote.android.network.PcDiscovery
import app.smartremote.android.network.RateLimiter
import app.smartremote.android.network.SmartRemoteConnection
import app.smartremote.android.protocol.RealtimeFrame
import app.smartremote.android.protocol.RealtimeKind
import app.smartremote.android.protocol.RemoteState
import app.smartremote.android.protocol.AnimeVostState
import app.smartremote.android.settings.RemoteSettings
import app.smartremote.android.settings.SettingsRepository
import app.smartremote.android.settings.PointerMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class RemoteViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SettingsRepository(application)
    private val gyroPointer = GyroPointerController(application)
    private val pcDiscovery = PcDiscovery(application)
    private val connection = SmartRemoteConnection(
        clientIdProvider = repository::clientId,
        deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
        capabilities = buildList {
            addAll(listOf("pointer", "scroll", "volume", "media", "keyboard"))
            if (gyroPointer.isAvailable) add("gyro_pointer")
        },
    )

    val connectionStatus: StateFlow<ConnectionStatus> = connection.status
    val remoteState: StateFlow<RemoteState> = connection.remoteState
    val animeState: StateFlow<AnimeVostState> = connection.animeState
    val discoveredPcs: StateFlow<List<DiscoveredPc>> = pcDiscovery.pcs
    val discoveryStatus: StateFlow<DiscoveryStatus> = pcDiscovery.status
    val settings: StateFlow<RemoteSettings> = repository.settings.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        RemoteSettings(),
    )

    private val _host = MutableStateFlow("")
    val host = _host.asStateFlow()
    private val _port = MutableStateFlow("8765")
    val port = _port.asStateFlow()
    private val _endpointError = MutableStateFlow(false)
    val endpointError = _endpointError.asStateFlow()
    private val _showSettings = MutableStateFlow(false)
    val showSettings = _showSettings.asStateFlow()
    private val _dragVolume = MutableStateFlow<Float?>(null)
    val dragVolume = _dragVolume.asStateFlow()
    private val _pointerMode = MutableStateFlow(PointerMode.Touchpad)
    val pointerMode = _pointerMode.asStateFlow()
    private val activation = AirMouseActivationController()
    private val _airMouseState = MutableStateFlow(AirMouseActivationState())
    val airMouseState = _airMouseState.asStateFlow()
    val airMouseAvailable: Boolean = gyroPointer.isAvailable
    private val volumeRateLimiter = RateLimiter(VOLUME_SEND_INTERVAL_MS)
    private var remoteForeground = false
    private var connected = false
    private var gyroRunning = false
    private var gyroConfiguration: GyroConfiguration? = null
    private var streamCounter = 0u
    private var gyroStreamId = 0u
    private var gyroSequence = 0u
    private var currentSettings = RemoteSettings()

    init {
        viewModelScope.launch {
            val saved = repository.settings.first()
            _host.value = saved.host
            _port.value = saved.port.toString()
            if (saved.host.isNotBlank() && saved.port in 1..65535) {
                connection.connect(saved.host, saved.port)
            }
        }
        viewModelScope.launch {
            connection.status.collectLatest { status ->
                connected = status is ConnectionStatus.Connected
                if (!connected) resetAirMouse(releaseButtons = true)
                syncGyro()
            }
        }
        viewModelScope.launch {
            repository.settings.collectLatest { current ->
                currentSettings = current
                val storedMode = if (current.airMouseEnabled && airMouseAvailable) {
                    current.pointerMode
                } else {
                    PointerMode.Touchpad
                }
                if (storedMode != PointerMode.AirMouse) resetAirMouse(releaseButtons = true)
                _pointerMode.value = storedMode
                syncGyro()
            }
        }
    }

    fun updateHost(value: String) {
        _host.value = value.trim()
        _endpointError.value = false
    }

    fun updatePort(value: String) {
        _port.value = value.filter(Char::isDigit).take(5)
        _endpointError.value = false
    }

    fun connect() {
        val parsedPort = _port.value.toIntOrNull()
        if (_host.value.isBlank() || parsedPort == null || parsedPort !in 1..65535) {
            _endpointError.value = true
            return
        }
        viewModelScope.launch { repository.saveEndpoint(_host.value, parsedPort) }
        connection.connect(_host.value, parsedPort)
    }

    fun connectToPc(pc: DiscoveredPc) {
        _host.value = pc.host
        _port.value = pc.port.toString()
        _endpointError.value = false
        viewModelScope.launch { repository.saveEndpoint(pc.host, pc.port) }
        connection.connect(pc.host, pc.port)
    }

    fun startDiscovery() = pcDiscovery.start()
    fun restartDiscovery() = pcDiscovery.restart()
    fun stopDiscovery() = pcDiscovery.stop()

    fun disconnect() {
        resetAirMouse(releaseButtons = true)
        connection.disconnect()
    }
    fun showSettings(show: Boolean) { _showSettings.value = show }

    fun setRemoteForeground(foreground: Boolean) {
        remoteForeground = foreground
        if (!foreground) resetAirMouse(releaseButtons = true)
        syncGyro()
    }

    fun allocateRealtimeStreamId(): UInt {
        streamCounter++
        if (streamCounter == 0u) streamCounter++
        return streamCounter
    }

    fun handleGesture(output: GestureOutput) {
        when (output) {
            is GestureOutput.Pointer -> connection.sendRealtime(
                RealtimeFrame(
                    kind = RealtimeKind.Pointer,
                    streamId = output.streamId,
                    sequence = output.sequence,
                    clientTimestampUs = SmartRemoteConnection.monotonicTimestampUs(),
                    cumulativeX = output.totalX,
                    cumulativeY = output.totalY,
                ),
            )
            is GestureOutput.Scroll -> connection.sendRealtime(
                RealtimeFrame(
                    kind = RealtimeKind.Scroll,
                    streamId = output.streamId,
                    sequence = output.sequence,
                    clientTimestampUs = SmartRemoteConnection.monotonicTimestampUs(),
                    cumulativeX = output.totalX,
                    cumulativeY = output.totalY,
                ),
            )
            is GestureOutput.MouseButton -> mouseButton(output.button, output.down)
            GestureOutput.Click -> click(Button.Left)
            GestureOutput.RightClick -> click(Button.Right)
        }
    }

    fun navigateBack() = connection.sendControl("back")
    fun playPause() = connection.sendControl("play_pause")
    fun showDesktop() = connection.sendControl("show_desktop")
    fun animePrevious() = connection.sendControl("anime_previous")
    fun animePlayPause() = connection.sendControl("anime_play_pause")
    fun animeNext() = connection.sendControl("anime_next")
    fun animeSkip() = connection.sendControl("anime_skip")
    fun animeCancelNext() = connection.sendControl("anime_cancel_next")
    fun setAnimeAutoMode(enabled: Boolean) = connection.sendControl(
        "anime_set_auto_mode",
        buildJsonObject { put("enabled", enabled) },
    )

    fun typeText(text: String) {
        if (text.isEmpty()) return
        connection.sendControl(
            "type_text",
            buildJsonObject { put("text", text.take(MAX_TEXT_LENGTH)) },
        )
    }

    fun keyboardBackspace() = pressKeyboardKey("backspace")
    fun keyboardEnter() = pressKeyboardKey("enter")
    fun keyboardTab() = pressKeyboardKey("tab")

    fun toggleMute() {
        connection.sendControl(
            "set_mute",
            buildJsonObject { put("muted", !remoteState.value.muted) },
        )
    }

    fun updateVolume(value: Float, final: Boolean) {
        val safe = value.coerceIn(0f, 1f)
        _dragVolume.value = safe
        val now = SystemClock.elapsedRealtime()
        if (volumeRateLimiter.shouldEmit(now, force = final)) {
            connection.sendControl("set_volume", buildJsonObject { put("value", safe) })
        }
        if (final) _dragVolume.value = null
    }

    fun setPointerSensitivity(value: Float) {
        viewModelScope.launch { repository.savePointerSensitivity(value) }
    }

    fun setScrollSpeed(value: Float) {
        viewModelScope.launch { repository.saveScrollSpeed(value) }
    }

    fun setHaptics(enabled: Boolean) {
        viewModelScope.launch { repository.saveHaptics(enabled) }
    }

    fun setAirMouseEnabled(enabled: Boolean) {
        if (!enabled) setPointerMode(PointerMode.Touchpad)
        viewModelScope.launch { repository.saveAirMouseEnabled(enabled && airMouseAvailable) }
    }

    fun setGyroSensitivity(value: Float) {
        viewModelScope.launch { repository.saveGyroSensitivity(value) }
    }

    fun setGyroStabilizationProfile(profile: GyroStabilizationProfile) {
        viewModelScope.launch { repository.saveGyroStabilizationProfile(profile) }
    }

    fun setInvertGyroY(inverted: Boolean) {
        viewModelScope.launch { repository.saveInvertGyroY(inverted) }
    }

    fun setInvertGyroX(inverted: Boolean) {
        viewModelScope.launch { repository.saveInvertGyroX(inverted) }
    }

    fun setPointerMode(mode: PointerMode) {
        val safeMode = if (mode == PointerMode.AirMouse && (!airMouseAvailable || !currentSettings.airMouseEnabled)) {
            PointerMode.Touchpad
        } else {
            mode
        }
        if (safeMode != PointerMode.AirMouse) resetAirMouse(releaseButtons = true)
        _pointerMode.value = safeMode
        viewModelScope.launch { repository.savePointerMode(safeMode) }
        syncGyro()
    }

    fun setAimHeld(held: Boolean) {
        activation.setAimHeld(held)
        publishActivation()
        syncGyro()
    }

    fun toggleAirMouseLock() {
        activation.setLocked(!activation.state.locked)
        publishActivation()
        syncGyro()
    }

    fun setAirMouseButton(button: Button, down: Boolean) {
        val alreadyHeld = when (button) {
            Button.Left -> activation.state.leftHeld
            Button.Right -> activation.state.rightHeld
        }
        if (alreadyHeld == down) return
        mouseButton(button, down)
        activation.setButtonHeld(button, down)
        publishActivation()
        syncGyro()
    }

    private fun click(button: Button) {
        mouseButton(button, true)
        mouseButton(button, false)
    }

    private fun pressKeyboardKey(key: String) {
        connection.sendControl("key_press", buildJsonObject { put("key", key) })
    }

    private fun mouseButton(button: Button, down: Boolean) {
        connection.sendControl(
            "mouse_button",
            JsonObject(
                mapOf(
                    "button" to JsonPrimitive(button.name.lowercase()),
                    "state" to JsonPrimitive(if (down) "down" else "up"),
                ),
            ),
        )
    }

    private fun publishActivation() {
        _airMouseState.value = activation.state
    }

    private fun resetAirMouse(releaseButtons: Boolean) {
        val state = activation.state
        if (releaseButtons && state.leftHeld) mouseButton(Button.Left, false)
        if (releaseButtons && state.rightHeld) mouseButton(Button.Right, false)
        activation.reset()
        publishActivation()
        syncGyro()
    }

    private fun syncGyro() {
        val desiredConfiguration = GyroConfiguration(
            sensitivity = currentSettings.gyroSensitivity,
            invertX = currentSettings.invertGyroX,
            invertY = currentSettings.invertGyroY,
            stabilizationProfile = currentSettings.gyroStabilizationProfile,
        )
        val shouldRun = connected &&
            remoteForeground &&
            airMouseAvailable &&
            currentSettings.airMouseEnabled &&
            _pointerMode.value == PointerMode.AirMouse &&
            activation.state.active
        if (gyroRunning && (!shouldRun || gyroConfiguration != desiredConfiguration)) {
            gyroPointer.stop()
            gyroRunning = false
            gyroConfiguration = null
        }
        if (shouldRun && !gyroRunning) {
            gyroStreamId = allocateRealtimeStreamId()
            gyroSequence = 0u
            gyroRunning = gyroPointer.start(
                sensitivity = desiredConfiguration.sensitivity,
                invertX = desiredConfiguration.invertX,
                invertY = desiredConfiguration.invertY,
                profile = desiredConfiguration.stabilizationProfile,
            ) { sample ->
                connection.sendRealtime(
                    RealtimeFrame(
                        kind = RealtimeKind.Pointer,
                        streamId = gyroStreamId,
                        sequence = gyroSequence++,
                        clientTimestampUs = SmartRemoteConnection.monotonicTimestampUs(),
                        cumulativeX = sample.cumulativeX,
                        cumulativeY = sample.cumulativeY,
                    ),
                )
            }
            gyroConfiguration = if (gyroRunning) desiredConfiguration else null
        }
    }

    override fun onCleared() {
        resetAirMouse(releaseButtons = true)
        gyroPointer.stop()
        pcDiscovery.close()
        connection.close()
    }

    companion object {
        internal const val VOLUME_SEND_INTERVAL_MS = 34L
        internal const val MAX_TEXT_LENGTH = 4096
    }
}

private data class GyroConfiguration(
    val sensitivity: Float,
    val invertX: Boolean,
    val invertY: Boolean,
    val stabilizationProfile: GyroStabilizationProfile,
)
