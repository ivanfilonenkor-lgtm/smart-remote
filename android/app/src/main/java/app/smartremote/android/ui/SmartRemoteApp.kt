package app.smartremote.android.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.automirrored.filled.KeyboardTab
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.smartremote.android.R
import app.smartremote.android.RemoteViewModel
import app.smartremote.android.input.AirMouseActivationState
import app.smartremote.android.input.Button as MouseButton
import app.smartremote.android.input.GestureOutput
import app.smartremote.android.input.GyroStabilizationProfile
import app.smartremote.android.input.TouchPoint
import app.smartremote.android.input.TouchpadGestureMachine
import app.smartremote.android.network.ConnectionStatus
import app.smartremote.android.network.DiscoveredPc
import app.smartremote.android.network.DiscoveryStatus
import app.smartremote.android.protocol.AnimeVostState
import app.smartremote.android.settings.PointerMode
import app.smartremote.android.settings.RemoteSettings
import kotlin.math.floor
import kotlin.math.roundToInt

private val PanelShape = RoundedCornerShape(18.dp)
private val ControlShape = RoundedCornerShape(12.dp)

@Composable
fun SmartRemoteApp(viewModel: RemoteViewModel) {
    val status by viewModel.connectionStatus.collectAsStateWithLifecycle()
    val remote by viewModel.remoteState.collectAsStateWithLifecycle()
    val anime by viewModel.animeState.collectAsStateWithLifecycle()
    val discoveredPcs by viewModel.discoveredPcs.collectAsStateWithLifecycle()
    val discoveryStatus by viewModel.discoveryStatus.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val host by viewModel.host.collectAsStateWithLifecycle()
    val port by viewModel.port.collectAsStateWithLifecycle()
    val endpointError by viewModel.endpointError.collectAsStateWithLifecycle()
    val showSettings by viewModel.showSettings.collectAsStateWithLifecycle()
    val dragVolume by viewModel.dragVolume.collectAsStateWithLifecycle()
    val pointerMode by viewModel.pointerMode.collectAsStateWithLifecycle()
    val airMouseState by viewModel.airMouseState.collectAsStateWithLifecycle()
    val connected = status is ConnectionStatus.Connected
    val remoteVisible = connected && !showSettings
    val view = LocalView.current

    DisposableEffect(view, remoteVisible) {
        view.keepScreenOn = remoteVisible
        viewModel.setRemoteForeground(remoteVisible)
        onDispose {
            view.keepScreenOn = false
            viewModel.setRemoteForeground(false)
        }
    }

    SmartRemoteTheme {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.safeDrawing,
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when {
                    connected && showSettings -> SettingsScreen(
                        settings = settings,
                        onPointerSensitivity = viewModel::setPointerSensitivity,
                        onScrollSpeed = viewModel::setScrollSpeed,
                        onHaptics = viewModel::setHaptics,
                        airMouseAvailable = viewModel.airMouseAvailable,
                        onAirMouseEnabled = viewModel::setAirMouseEnabled,
                        onGyroSensitivity = viewModel::setGyroSensitivity,
                        onGyroStabilizationProfile = viewModel::setGyroStabilizationProfile,
                        onInvertGyroX = viewModel::setInvertGyroX,
                        onInvertGyroY = viewModel::setInvertGyroY,
                        onDone = { viewModel.showSettings(false) },
                    )
                    connected -> RemoteScreen(
                        serverName = (status as ConnectionStatus.Connected).serverName,
                        volume = dragVolume ?: remote.volume,
                        muted = remote.muted,
                        anime = anime,
                        settings = settings,
                        pointerMode = pointerMode,
                        airMouseAvailable = viewModel.airMouseAvailable,
                        airMouseState = airMouseState,
                        onGesture = viewModel::handleGesture,
                        streamIdProvider = viewModel::allocateRealtimeStreamId,
                        onPointerMode = viewModel::setPointerMode,
                        onAimHeld = viewModel::setAimHeld,
                        onToggleAirMouseLock = viewModel::toggleAirMouseLock,
                        onAirMouseButton = viewModel::setAirMouseButton,
                        onVolume = viewModel::updateVolume,
                        onMute = viewModel::toggleMute,
                        onBack = viewModel::navigateBack,
                        onPlayPause = viewModel::playPause,
                        onDesktop = viewModel::showDesktop,
                        onAnimePrevious = viewModel::animePrevious,
                        onAnimePlayPause = viewModel::animePlayPause,
                        onAnimeNext = viewModel::animeNext,
                        onAnimeSkip = viewModel::animeSkip,
                        onAnimeAutoMode = viewModel::setAnimeAutoMode,
                        onAnimeCancelNext = viewModel::animeCancelNext,
                        onTypeText = viewModel::typeText,
                        onBackspace = viewModel::keyboardBackspace,
                        onEnter = viewModel::keyboardEnter,
                        onTab = viewModel::keyboardTab,
                        onSettings = { viewModel.showSettings(true) },
                        onDisconnect = viewModel::disconnect,
                    )
                    else -> ConnectionScreen(
                        host = host,
                        port = port,
                        status = status,
                        discoveredPcs = discoveredPcs,
                        discoveryStatus = discoveryStatus,
                        endpointError = endpointError,
                        onHost = viewModel::updateHost,
                        onPort = viewModel::updatePort,
                        onConnect = viewModel::connect,
                        onConnectToPc = viewModel::connectToPc,
                        onStartDiscovery = viewModel::startDiscovery,
                        onRestartDiscovery = viewModel::restartDiscovery,
                        onStopDiscovery = viewModel::stopDiscovery,
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectionScreen(
    host: String,
    port: String,
    status: ConnectionStatus,
    discoveredPcs: List<DiscoveredPc>,
    discoveryStatus: DiscoveryStatus,
    endpointError: Boolean,
    onHost: (String) -> Unit,
    onPort: (String) -> Unit,
    onConnect: () -> Unit,
    onConnectToPc: (DiscoveredPc) -> Unit,
    onStartDiscovery: () -> Unit,
    onRestartDiscovery: () -> Unit,
    onStopDiscovery: () -> Unit,
) {
    var manualExpanded by rememberSaveable { mutableStateOf(false) }
    val connecting = status is ConnectionStatus.Connecting

    DisposableEffect(Unit) {
        onStartDiscovery()
        onDispose(onStopDiscovery)
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            modifier = Modifier.size(56.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Computer,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.connect_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.connect_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Card(
            shape = PanelShape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.available_pcs),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onRestartDiscovery) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.refresh_pcs),
                        )
                    }
                }

                if (discoveredPcs.isEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (discoveryStatus is DiscoveryStatus.Searching) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                Icons.Default.Computer,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = when (discoveryStatus) {
                                    is DiscoveryStatus.Failed -> stringResource(R.string.pc_search_failed)
                                    else -> stringResource(R.string.searching_pcs)
                                },
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                text = stringResource(R.string.start_remote_core_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    discoveredPcs.forEachIndexed { index, pc ->
                        if (index > 0) HorizontalDivider(Modifier.padding(vertical = 4.dp))
                        DiscoveredPcRow(
                            pc = pc,
                            onClick = { onConnectToPc(pc) },
                        )
                    }
                }

                if (connecting) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.connecting), maxLines = 1)
                    }
                }
            }
        }

        if (status is ConnectionStatus.Failed) {
            Text(
                stringResource(R.string.connection_failed, status.message),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 10.dp, start = 4.dp, end = 4.dp),
            )
        }

        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = { manualExpanded = !manualExpanded },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            shape = ControlShape,
        ) {
            Text(
                text = stringResource(R.string.manual_connection),
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                imageVector = if (manualExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
            )
        }

        if (manualExpanded) {
            Spacer(Modifier.height(10.dp))
            Card(
                shape = PanelShape,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = host,
                        onValueChange = onHost,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.host_address)) },
                        singleLine = true,
                        isError = endpointError,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = port,
                        onValueChange = onPort,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.port)) },
                        singleLine = true,
                        isError = endpointError,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    if (endpointError) {
                        Text(
                            stringResource(R.string.invalid_endpoint),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = onConnect,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        shape = ControlShape,
                    ) {
                        Text(
                            stringResource(R.string.connect),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                Icons.Default.Shield,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                stringResource(R.string.trusted_lan_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DiscoveredPcRow(
    pc: DiscoveredPc,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = ControlShape,
        color = Color.Transparent,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Computer,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = pc.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${pc.host}:${pc.port}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.connect_short),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun RemoteScreen(
    serverName: String,
    volume: Float,
    muted: Boolean,
    anime: AnimeVostState,
    settings: RemoteSettings,
    pointerMode: PointerMode,
    airMouseAvailable: Boolean,
    airMouseState: AirMouseActivationState,
    onGesture: (GestureOutput) -> Unit,
    streamIdProvider: () -> UInt,
    onPointerMode: (PointerMode) -> Unit,
    onAimHeld: (Boolean) -> Unit,
    onToggleAirMouseLock: () -> Unit,
    onAirMouseButton: (MouseButton, Boolean) -> Unit,
    onVolume: (Float, Boolean) -> Unit,
    onMute: () -> Unit,
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onDesktop: () -> Unit,
    onAnimePrevious: () -> Unit,
    onAnimePlayPause: () -> Unit,
    onAnimeNext: () -> Unit,
    onAnimeSkip: () -> Unit,
    onAnimeAutoMode: (Boolean) -> Unit,
    onAnimeCancelNext: () -> Unit,
    onTypeText: (String) -> Unit,
    onBackspace: () -> Unit,
    onEnter: () -> Unit,
    onTab: () -> Unit,
    onSettings: () -> Unit,
    onDisconnect: () -> Unit,
) {
    var keyboardMode by rememberSaveable { mutableStateOf(false) }
    val airMouseEnabled = settings.airMouseEnabled && airMouseAvailable

    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 8.dp)) {
        RemoteHeader(
            serverName = serverName,
            onSettings = onSettings,
            onDisconnect = onDisconnect,
        )
        Spacer(Modifier.height(10.dp))
        ModeSelector(
            keyboardMode = keyboardMode,
            pointerMode = pointerMode,
            airMouseEnabled = airMouseEnabled,
            onTouchpad = {
                keyboardMode = false
                onPointerMode(PointerMode.Touchpad)
            },
            onAirMouse = {
                keyboardMode = false
                onPointerMode(PointerMode.AirMouse)
            },
            onKeyboard = {
                keyboardMode = true
                onPointerMode(PointerMode.Touchpad)
            },
        )
        Spacer(Modifier.height(10.dp))
        if (keyboardMode) {
            KeyboardPanel(
                onTypeText = onTypeText,
                onBackspace = onBackspace,
                onEnter = onEnter,
                onTab = onTab,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        } else {
            Row(Modifier.weight(1f)) {
                if (pointerMode == PointerMode.AirMouse && airMouseEnabled) {
                    AirMousePanel(
                        state = airMouseState,
                        haptics = settings.haptics,
                        onAimHeld = onAimHeld,
                        onToggleLock = onToggleAirMouseLock,
                        onButton = onAirMouseButton,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                } else {
                    Touchpad(
                        settings = settings,
                        onGesture = onGesture,
                        streamIdProvider = streamIdProvider,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
                Spacer(Modifier.width(10.dp))
                VolumeStrip(
                    volume = volume,
                    muted = muted,
                    haptics = settings.haptics,
                    onVolume = onVolume,
                    onMute = onMute,
                    modifier = Modifier.width(68.dp).fillMaxHeight(),
                )
            }
            Spacer(Modifier.height(10.dp))
            if (anime.available) {
                AnimeVostPanel(
                    state = anime,
                    onPrevious = onAnimePrevious,
                    onPlayPause = onAnimePlayPause,
                    onNext = onAnimeNext,
                    onSkip = onAnimeSkip,
                    onAutoMode = onAnimeAutoMode,
                    onCancelNext = onAnimeCancelNext,
                )
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuickActionButton(
                        label = stringResource(R.string.back),
                        icon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) },
                        onClick = onBack,
                        modifier = Modifier.weight(1f),
                    )
                    QuickActionButton(
                        label = stringResource(R.string.play_pause),
                        icon = { Icon(Icons.Default.PlayArrow, null) },
                        onClick = onPlayPause,
                        modifier = Modifier.weight(1f),
                    )
                    QuickActionButton(
                        label = stringResource(R.string.desktop),
                        icon = { Icon(Icons.Default.Computer, null) },
                        onClick = onDesktop,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun AnimeVostPanel(
    state: AnimeVostState,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    onAutoMode: (Boolean) -> Unit,
    onCancelNext: () -> Unit,
) {
    val episodeLabel = when {
        state.episode != null && state.episodeCount != null -> stringResource(
            R.string.anime_episode_count,
            state.episode,
            state.episodeCount,
        )
        state.episode != null -> stringResource(R.string.anime_episode, state.episode)
        else -> stringResource(R.string.anime_player_ready)
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = PanelShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = state.siteName ?: stringResource(R.string.video_site),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                    )
                    Text(
                        text = listOfNotNull(episodeLabel, state.title).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.anime_auto_mode),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(6.dp))
                Switch(
                    checked = state.autoMode,
                    onCheckedChange = onAutoMode,
                    modifier = Modifier.semantics {
                        contentDescription = "${state.siteName ?: "Video"} ${state.autoMode}"
                    },
                )
            }
            if (state.countdownSeconds != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 36.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(
                            R.string.anime_next_countdown,
                            state.countdownSeconds,
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                    )
                    IconButton(onClick = onCancelNext, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.anime_cancel_next),
                            modifier = Modifier.size(19.dp),
                        )
                    }
                }
            } else {
                Spacer(Modifier.height(7.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                AnimeActionButton(
                    label = stringResource(R.string.anime_previous),
                    enabled = state.previousAvailable,
                    icon = { Icon(Icons.Default.SkipPrevious, null) },
                    onClick = onPrevious,
                    modifier = Modifier.weight(1f),
                )
                AnimeActionButton(
                    label = stringResource(if (state.playing) R.string.anime_pause else R.string.anime_play),
                    icon = {
                        Icon(if (state.playing) Icons.Default.Pause else Icons.Default.PlayArrow, null)
                    },
                    onClick = onPlayPause,
                    modifier = Modifier.weight(1f),
                )
                AnimeActionButton(
                    label = stringResource(R.string.anime_next),
                    enabled = state.nextAvailable,
                    icon = { Icon(Icons.Default.SkipNext, null) },
                    onClick = onNext,
                    modifier = Modifier.weight(1f),
                )
                AnimeActionButton(
                    label = stringResource(R.string.anime_skip_opening),
                    enabled = state.skipAvailable,
                    icon = { Icon(Icons.Default.FastForward, null) },
                    onClick = onSkip,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun AnimeActionButton(
    label: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 62.dp),
        shape = ControlShape,
        color = if (enabled) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
        contentColor = if (enabled) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
        },
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 3.dp, vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            icon()
            Spacer(Modifier.height(3.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun RemoteHeader(
    serverName: String,
    onSettings: () -> Unit,
    onDisconnect: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(54.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(42.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Computer, null, modifier = Modifier.size(21.dp))
            }
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = serverName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(Color(0xFF63D899)))
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(R.string.connected),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        FilledTonalIconButton(onClick = onSettings, modifier = Modifier.size(42.dp)) {
            Icon(Icons.Default.Settings, stringResource(R.string.settings), modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(6.dp))
        IconButton(onClick = onDisconnect, modifier = Modifier.size(42.dp)) {
            Icon(
                Icons.Default.LinkOff,
                stringResource(R.string.disconnect),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun ModeSelector(
    keyboardMode: Boolean,
    pointerMode: PointerMode,
    airMouseEnabled: Boolean,
    onTouchpad: () -> Unit,
    onAirMouse: () -> Unit,
    onKeyboard: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = ControlShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ModeButton(
                selected = !keyboardMode && pointerMode == PointerMode.Touchpad,
                label = stringResource(R.string.touchpad_mode),
                icon = { Icon(Icons.Default.TouchApp, null, modifier = Modifier.size(17.dp)) },
                onClick = onTouchpad,
                modifier = Modifier.weight(1f),
            )
            if (airMouseEnabled) {
                ModeButton(
                    selected = !keyboardMode && pointerMode == PointerMode.AirMouse,
                    label = stringResource(R.string.air_mouse_short),
                    icon = { Icon(Icons.Default.ScreenRotation, null, modifier = Modifier.size(17.dp)) },
                    onClick = onAirMouse,
                    modifier = Modifier.weight(1f),
                )
            }
            ModeButton(
                selected = keyboardMode,
                label = stringResource(R.string.keyboard),
                icon = { Icon(Icons.Default.Keyboard, null, modifier = Modifier.size(17.dp)) },
                onClick = onKeyboard,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ModeButton(
    selected: Boolean,
    label: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(40.dp),
        shape = RoundedCornerShape(9.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 7.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon()
            Spacer(Modifier.width(6.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun Touchpad(
    settings: RemoteSettings,
    onGesture: (GestureOutput) -> Unit,
    streamIdProvider: () -> UInt,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val configuration = LocalViewConfiguration.current
    Card(
        modifier = modifier.pointerInput(
            settings.pointerSensitivity,
            settings.scrollSpeed,
            configuration.touchSlop,
        ) {
            val machine = TouchpadGestureMachine(
                touchSlop = configuration.touchSlop,
                doubleTapTimeoutMs = configuration.doubleTapTimeoutMillis,
                pointerSensitivity = settings.pointerSensitivity,
                scrollSpeed = settings.scrollSpeed,
                streamIdProvider = streamIdProvider,
            )
            try {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val points = event.changes
                            .filter { it.pressed }
                            .map { TouchPoint(it.id.value, it.position.x, it.position.y) }
                        val time = event.changes.maxOfOrNull { it.uptimeMillis }
                            ?: android.os.SystemClock.uptimeMillis()
                        machine.update(time, points).forEach { output ->
                            if (settings.haptics && output.isClickLike()) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            onGesture(output)
                        }
                        event.changes.forEach { change ->
                            if (change.pressed || change.previousPressed) change.consume()
                        }
                    }
                }
            } finally {
                machine.cancel().forEach(onGesture)
            }
        },
        shape = PanelShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Box(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.TouchApp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.touchpad_mode),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Column(
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Mouse,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(23.dp),
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    text = stringResource(R.string.touchpad_hint),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                text = stringResource(R.string.touchpad_drag_hint),
                modifier = Modifier.align(Alignment.BottomCenter).padding(14.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun AirMousePanel(
    state: AirMouseActivationState,
    haptics: Boolean,
    onAimHeld: (Boolean) -> Unit,
    onToggleLock: () -> Unit,
    onButton: (MouseButton, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val aimDescription = stringResource(R.string.hold_to_aim)
    DisposableEffect(Unit) {
        onDispose {
            onAimHeld(false)
            onButton(MouseButton.Left, false)
            onButton(MouseButton.Right, false)
        }
    }
    Card(
        modifier = modifier,
        shape = PanelShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.fillMaxSize().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(36.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.ScreenRotation,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                }
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.air_mouse), style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = when {
                            state.locked -> stringResource(R.string.air_mouse_locked)
                            state.active -> stringResource(R.string.air_mouse_active)
                            else -> stringResource(R.string.air_mouse_idle)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                FilledTonalIconButton(
                    onClick = {
                        if (haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onToggleLock()
                    },
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        if (state.locked) Icons.Default.Lock else Icons.Default.LockOpen,
                        stringResource(if (state.locked) R.string.unlock_air_mouse else R.string.lock_air_mouse),
                        modifier = Modifier.size(19.dp),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(ControlShape)
                    .semantics {
                        role = Role.Button
                        contentDescription = aimDescription
                    }
                    .pointerInput(haptics) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            if (haptics) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onAimHeld(true)
                            try {
                                waitForUpOrCancellation()
                            } finally {
                                onAimHeld(false)
                            }
                        }
                    },
                shape = ControlShape,
                color = if (state.active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(
                    1.dp,
                    if (state.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                ),
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        Icons.Default.Mouse,
                        contentDescription = null,
                        modifier = Modifier.size(38.dp),
                        tint = if (state.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        stringResource(R.string.hold_to_aim),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.air_mouse_hint),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AirMouseButton(
                    label = stringResource(R.string.left_click),
                    pressed = state.leftHeld,
                    onPressed = { onButton(MouseButton.Left, it) },
                    haptics = haptics,
                    modifier = Modifier.weight(1f),
                )
                AirMouseButton(
                    label = stringResource(R.string.right_click),
                    pressed = state.rightHeld,
                    onPressed = { onButton(MouseButton.Right, it) },
                    haptics = haptics,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun AirMouseButton(
    label: String,
    pressed: Boolean,
    onPressed: (Boolean) -> Unit,
    haptics: Boolean,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    Surface(
        modifier = modifier
            .height(54.dp)
            .clip(ControlShape)
            .semantics {
                role = Role.Button
                contentDescription = label
            }
            .pointerInput(label, haptics) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    if (haptics) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onPressed(true)
                    try {
                        waitForUpOrCancellation()
                    } finally {
                        onPressed(false)
                    }
                }
            },
        shape = ControlShape,
        color = if (pressed) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(
            1.dp,
            if (pressed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Box(Modifier.fillMaxSize().padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun GestureOutput.isClickLike(): Boolean =
    this is GestureOutput.Click || this is GestureOutput.RightClick ||
        (this is GestureOutput.MouseButton && down)

@Composable
private fun VolumeStrip(
    volume: Float,
    muted: Boolean,
    haptics: Boolean,
    onVolume: (Float, Boolean) -> Unit,
    onMute: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    var height by remember { mutableFloatStateOf(1f) }
    var lastStep by remember { mutableIntStateOf(floor(volume * 4).toInt()) }
    val primaryColor = MaterialTheme.colorScheme.primary
    val knobColor = MaterialTheme.colorScheme.onPrimary
    val volumeDescription = stringResource(R.string.volume_percent, (volume * 100).roundToInt())
    Surface(
        modifier = modifier,
        shape = PanelShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "${(volume * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.semantics { contentDescription = volumeDescription },
            )
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .pointerInput(haptics) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Main)
                                val change = event.changes.firstOrNull() ?: continue
                                height = size.height.toFloat().coerceAtLeast(1f)
                                val value = (1f - change.position.y / height).coerceIn(0f, 1f)
                                val final = change.previousPressed && !change.pressed
                                if (change.pressed || final) {
                                    val step = floor(value * 4).toInt()
                                    if (haptics && step != lastStep) {
                                        lastStep = step
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                    onVolume(value, final)
                                    change.consume()
                                }
                            }
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 15.dp),
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val centerX = size.width / 2
                    val knobY = (1f - volume.coerceIn(0f, 1f)) * size.height
                    drawLine(
                        color = Color.White.copy(alpha = 0.14f),
                        start = Offset(centerX, 0f),
                        end = Offset(centerX, size.height),
                        strokeWidth = 7.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        color = primaryColor,
                        start = Offset(centerX, knobY),
                        end = Offset(centerX, size.height),
                        strokeWidth = 7.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                    drawCircle(color = knobColor, radius = 8.dp.toPx(), center = Offset(centerX, knobY))
                    drawCircle(color = primaryColor, radius = 5.dp.toPx(), center = Offset(centerX, knobY))
                }
            }
            Spacer(Modifier.height(8.dp))
            FilledTonalIconButton(onClick = onMute, modifier = Modifier.size(44.dp)) {
                Icon(
                    if (muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                    if (muted) stringResource(R.string.unmute) else stringResource(R.string.mute),
                    modifier = Modifier.size(20.dp),
                    tint = if (muted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun QuickActionButton(
    label: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(64.dp),
        shape = ControlShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 5.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            icon()
            Spacer(Modifier.height(4.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun KeyboardPanel(
    onTypeText: (String) -> Unit,
    onBackspace: () -> Unit,
    onEnter: () -> Unit,
    onTab: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by rememberSaveable { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val sendDraft = {
        if (draft.isNotEmpty()) {
            onTypeText(draft)
            draft = ""
            keyboardController?.show()
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    Card(
        modifier = modifier,
        shape = PanelShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(38.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Keyboard,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.keyboard), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.keyboard_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it.take(4096) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(116.dp)
                    .focusRequester(focusRequester),
                label = { Text(stringResource(R.string.keyboard_text_label)) },
                placeholder = { Text(stringResource(R.string.keyboard_placeholder)) },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Send,
                ),
                keyboardActions = KeyboardActions(onSend = { sendDraft() }),
                supportingText = {
                    Text(stringResource(R.string.keyboard_char_count, draft.length), maxLines = 1)
                },
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = sendDraft,
                enabled = draft.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = ControlShape,
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.send_to_pc),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KeyboardKeyButton(
                    label = stringResource(R.string.key_backspace),
                    icon = { Icon(Icons.AutoMirrored.Filled.Backspace, null, modifier = Modifier.size(18.dp)) },
                    onClick = onBackspace,
                    modifier = Modifier.weight(1f),
                )
                KeyboardKeyButton(
                    label = stringResource(R.string.key_enter),
                    icon = { Icon(Icons.AutoMirrored.Filled.KeyboardReturn, null, modifier = Modifier.size(18.dp)) },
                    onClick = onEnter,
                    modifier = Modifier.weight(1f),
                )
                KeyboardKeyButton(
                    label = stringResource(R.string.key_tab),
                    icon = { Icon(Icons.AutoMirrored.Filled.KeyboardTab, null, modifier = Modifier.size(18.dp)) },
                    onClick = onTab,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun KeyboardKeyButton(
    label: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = ControlShape,
        contentPadding = PaddingValues(horizontal = 5.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        icon()
        Spacer(Modifier.width(5.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun SettingsScreen(
    settings: RemoteSettings,
    onPointerSensitivity: (Float) -> Unit,
    onScrollSpeed: (Float) -> Unit,
    onHaptics: (Boolean) -> Unit,
    airMouseAvailable: Boolean,
    onAirMouseEnabled: (Boolean) -> Unit,
    onGyroSensitivity: (Float) -> Unit,
    onGyroStabilizationProfile: (GyroStabilizationProfile) -> Unit,
    onInvertGyroX: (Boolean) -> Unit,
    onInvertGyroY: (Boolean) -> Unit,
    onDone: () -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(21.dp),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Text(
                stringResource(R.string.settings),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            FilledIconButton(onClick = onDone, modifier = Modifier.size(42.dp)) {
                Icon(Icons.Default.Check, stringResource(R.string.done), modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(R.string.pointer_settings),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(8.dp))
        SettingsCard {
            SettingSlider(
                label = stringResource(R.string.pointer_sensitivity),
                value = settings.pointerSensitivity,
                onValue = onPointerSensitivity,
            )
            HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
            SettingSlider(
                label = stringResource(R.string.scroll_speed),
                value = settings.scrollSpeed,
                onValue = onScrollSpeed,
            )
            HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
            SettingSwitch(
                label = stringResource(R.string.haptics),
                checked = settings.haptics,
                onChecked = onHaptics,
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            stringResource(R.string.motion_settings),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(8.dp))
        SettingsCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.air_mouse), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.air_mouse_setting_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Switch(
                    checked = settings.airMouseEnabled && airMouseAvailable,
                    onCheckedChange = onAirMouseEnabled,
                    enabled = airMouseAvailable,
                )
            }
            if (!airMouseAvailable) {
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.gyroscope_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (settings.airMouseEnabled && airMouseAvailable) {
                HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
                SettingSlider(
                    label = stringResource(R.string.gyro_sensitivity),
                    value = settings.gyroSensitivity,
                    onValue = onGyroSensitivity,
                    valueRange = 0.5f..3f,
                    steps = 9,
                )
                HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
                GyroProfileSelector(
                    selected = settings.gyroStabilizationProfile,
                    onSelected = onGyroStabilizationProfile,
                )
                HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
                SettingSwitch(
                    label = stringResource(R.string.invert_gyro_x),
                    checked = settings.invertGyroX,
                    onChecked = onInvertGyroX,
                )
                Spacer(Modifier.height(8.dp))
                SettingSwitch(
                    label = stringResource(R.string.invert_gyro_y),
                    checked = settings.invertGyroY,
                    onChecked = onInvertGyroY,
                )
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun GyroProfileSelector(
    selected: GyroStabilizationProfile,
    onSelected: (GyroStabilizationProfile) -> Unit,
) {
    Text(
        stringResource(R.string.gyro_stabilization),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
    )
    Text(
        stringResource(R.string.gyro_stabilization_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 3.dp, bottom = 10.dp),
    )
    GyroStabilizationProfile.entries.forEachIndexed { index, profile ->
        val isSelected = profile == selected
        val title = when (profile) {
            GyroStabilizationProfile.Smooth -> stringResource(R.string.gyro_profile_smooth)
            GyroStabilizationProfile.Balanced -> stringResource(R.string.gyro_profile_balanced)
            GyroStabilizationProfile.Fast -> stringResource(R.string.gyro_profile_fast)
        }
        val description = when (profile) {
            GyroStabilizationProfile.Smooth -> stringResource(R.string.gyro_profile_smooth_hint)
            GyroStabilizationProfile.Balanced -> stringResource(R.string.gyro_profile_balanced_hint)
            GyroStabilizationProfile.Fast -> stringResource(R.string.gyro_profile_fast_hint)
        }
        Surface(
            onClick = { onSelected(profile) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            border = BorderStroke(
                1.dp,
                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
            ),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = isSelected, onClick = null)
                Spacer(Modifier.width(6.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (index != GyroStabilizationProfile.entries.lastIndex) Spacer(Modifier.height(7.dp))
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = PanelShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

@Composable
private fun SettingSwitch(
    label: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun SettingSlider(
    label: String,
    value: Float,
    onValue: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 0.5f..2f,
    steps: Int = 5,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Text(
                "${"%.1f".format(value)}×",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                maxLines = 1,
            )
        }
    }
    Slider(value = value, onValueChange = onValue, valueRange = valueRange, steps = steps)
}
