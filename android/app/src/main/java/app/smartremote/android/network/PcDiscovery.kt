package app.smartremote.android.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.io.Closeable
import java.net.Inet4Address
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DiscoveredPc(
    val serviceName: String,
    val serverId: String,
    val name: String,
    val host: String,
    val port: Int,
)

sealed interface DiscoveryStatus {
    data object Idle : DiscoveryStatus
    data object Searching : DiscoveryStatus
    data class Failed(val errorCode: Int) : DiscoveryStatus
}

/** Foreground-only DNS-SD browser for RemoteCore instances on the current LAN. */
class PcDiscovery(context: Context) : Closeable {
    private val nsdManager = context.getSystemService(NsdManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val lock = Any()
    private val pending = ArrayDeque<NsdServiceInfo>()
    private val resolved = LinkedHashMap<String, DiscoveredPc>()
    private val lostServices = mutableSetOf<String>()
    private var listener: NsdManager.DiscoveryListener? = null
    private var restartRunnable: Runnable? = null
    private var generation = 0L
    private var resolving = false

    private val _pcs = MutableStateFlow<List<DiscoveredPc>>(emptyList())
    val pcs: StateFlow<List<DiscoveredPc>> = _pcs.asStateFlow()

    private val _status = MutableStateFlow<DiscoveryStatus>(DiscoveryStatus.Idle)
    val status: StateFlow<DiscoveryStatus> = _status.asStateFlow()

    fun start() {
        val scheduledRestart = synchronized(lock) {
            restartRunnable.also { restartRunnable = null }
        }
        if (scheduledRestart != null) mainHandler.removeCallbacks(scheduledRestart)

        val currentGeneration: Long
        val discoveryListener: NsdManager.DiscoveryListener
        synchronized(lock) {
            if (listener != null) return
            generation += 1
            currentGeneration = generation
            pending.clear()
            resolved.clear()
            lostServices.clear()
            resolving = false
            _pcs.value = emptyList()
            _status.value = DiscoveryStatus.Searching
            discoveryListener = createDiscoveryListener(currentGeneration)
            listener = discoveryListener
        }

        try {
            nsdManager.discoverServices(
                SERVICE_TYPE,
                NsdManager.PROTOCOL_DNS_SD,
                discoveryListener,
            )
        } catch (_: RuntimeException) {
            failDiscovery(currentGeneration, NsdManager.FAILURE_INTERNAL_ERROR)
        }
    }

    fun restart() {
        stop()
        val stoppedGeneration = synchronized(lock) { generation }
        lateinit var task: Runnable
        task = Runnable {
            val shouldStart = synchronized(lock) {
                val current = generation == stoppedGeneration && restartRunnable === task
                if (current) restartRunnable = null
                current
            }
            if (shouldStart) start()
        }
        synchronized(lock) { restartRunnable = task }
        mainHandler.postDelayed(task, RESTART_DELAY_MS)
    }

    fun stop() {
        val (listenerToStop, scheduledRestart) = synchronized(lock) {
            generation += 1
            pending.clear()
            resolving = false
            lostServices.clear()
            _status.value = DiscoveryStatus.Idle
            val currentListener = listener.also { listener = null }
            val currentRestart = restartRunnable.also { restartRunnable = null }
            currentListener to currentRestart
        }
        if (scheduledRestart != null) mainHandler.removeCallbacks(scheduledRestart)
        if (listenerToStop != null) {
            runCatching { nsdManager.stopServiceDiscovery(listenerToStop) }
        }
    }

    override fun close() = stop()

    private fun createDiscoveryListener(currentGeneration: Long) =
        object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                if (isCurrent(currentGeneration)) {
                    _status.value = DiscoveryStatus.Searching
                }
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                val shouldResolve = synchronized(lock) {
                    if (generation != currentGeneration) return@synchronized false
                    lostServices.remove(serviceInfo.serviceName)
                    val known = resolved.containsKey(serviceInfo.serviceName) ||
                        pending.any { it.serviceName == serviceInfo.serviceName }
                    if (!known) pending.addLast(serviceInfo)
                    !known
                }
                if (shouldResolve) resolveNext(currentGeneration)
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                synchronized(lock) {
                    if (generation != currentGeneration) return
                    lostServices += serviceInfo.serviceName
                    resolved.remove(serviceInfo.serviceName)
                    pending.removeAll { it.serviceName == serviceInfo.serviceName }
                    publishPcsLocked()
                }
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                failDiscovery(currentGeneration, errorCode)
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                if (isCurrent(currentGeneration)) {
                    _status.value = DiscoveryStatus.Failed(errorCode)
                }
            }

            override fun onDiscoveryStopped(serviceType: String) {
                if (isCurrent(currentGeneration)) {
                    _status.value = DiscoveryStatus.Idle
                }
            }
        }

    private fun resolveNext(currentGeneration: Long) {
        val service = synchronized(lock) {
            if (generation != currentGeneration || resolving) return
            val next = pending.removeFirstOrNull() ?: return
            resolving = true
            next
        }

        @Suppress("DEPRECATION")
        try {
            nsdManager.resolveService(
                service,
                object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                        finishResolution(currentGeneration)
                    }

                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                        val pc = serviceInfo.toDiscoveredPc()
                        synchronized(lock) {
                            if (
                                generation == currentGeneration &&
                                pc != null &&
                                serviceInfo.serviceName !in lostServices
                            ) {
                                resolved[serviceInfo.serviceName] = pc
                                publishPcsLocked()
                            }
                        }
                        finishResolution(currentGeneration)
                    }
                },
            )
        } catch (_: RuntimeException) {
            finishResolution(currentGeneration)
        }
    }

    private fun finishResolution(currentGeneration: Long) {
        synchronized(lock) {
            if (generation != currentGeneration) return
            resolving = false
        }
        resolveNext(currentGeneration)
    }

    private fun failDiscovery(currentGeneration: Long, errorCode: Int) {
        val listenerToStop = synchronized(lock) {
            if (generation != currentGeneration) return
            generation += 1
            pending.clear()
            resolving = false
            _status.value = DiscoveryStatus.Failed(errorCode)
            listener.also { listener = null }
        }
        if (listenerToStop != null) {
            runCatching { nsdManager.stopServiceDiscovery(listenerToStop) }
        }
    }

    private fun isCurrent(expectedGeneration: Long) = synchronized(lock) {
        generation == expectedGeneration
    }

    private fun publishPcsLocked() {
        _pcs.value = resolved.values.sortedWith(
            compareBy(String.CASE_INSENSITIVE_ORDER, DiscoveredPc::name)
                .thenBy { it.host }
                .thenBy { it.port },
        )
    }

    private fun NsdServiceInfo.toDiscoveredPc(): DiscoveredPc? {
        val advertisedProtocol = attribute("protocol")?.toIntOrNull() ?: PROTOCOL_VERSION
        if (advertisedProtocol != PROTOCOL_VERSION || port !in 1..65535) return null

        @Suppress("DEPRECATION")
        val addresses = if (Build.VERSION.SDK_INT >= 34) hostAddresses else listOfNotNull(host)
        val address = addresses.firstOrNull { it is Inet4Address } ?: addresses.firstOrNull()
        val resolvedHost = address?.hostAddress ?: return null
        return DiscoveredPc(
            serviceName = serviceName,
            serverId = attribute("server_id").orEmpty(),
            name = attribute("server_name").orEmpty().ifBlank { serviceName },
            host = resolvedHost,
            port = port,
        )
    }

    private fun NsdServiceInfo.attribute(key: String): String? =
        attributes.entries.firstOrNull { it.key.equals(key, ignoreCase = true) }
            ?.value
            ?.toString(StandardCharsets.UTF_8)

    companion object {
        const val SERVICE_TYPE = "_smartremote._tcp."
        private const val PROTOCOL_VERSION = 1
        private const val RESTART_DELAY_MS = 300L
    }
}
