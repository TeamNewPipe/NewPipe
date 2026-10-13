package org.schabi.newpipe.cast.driver.chromecast

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import java.nio.charset.StandardCharsets
import org.schabi.newpipe.cast.driver.CastDriver
import org.schabi.newpipe.cast.model.CastDevice
import org.schabi.newpipe.cast.model.CastDeviceType
import org.schabi.newpipe.cast.session.CastSession
import org.schabi.newpipe.cast.session.CastStateListener

class ChromecastDriver(private val context: Context) : CastDriver {

    private val nsdManager by lazy {
        context.getSystemService(Context.NSD_SERVICE) as? NsdManager
    }

    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var isDiscovering = false

    companion object {
        private const val SERVICE_TYPE = "_googlecast._tcp."
    }

    override fun startDiscovery(
        onDeviceFound: (CastDevice) -> Unit,
        onDeviceLost: (String) -> Unit
    ) {
        val manager = nsdManager ?: return
        if (isDiscovering) return

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                isDiscovering = true
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                try {
                    manager.resolveService(
                        serviceInfo,
                        object : NsdManager.ResolveListener {
                            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                                // Non-fatal, another resolution may succeed
                            }

                            override fun onServiceResolved(resolvedInfo: NsdServiceInfo) {
                                val attributes = resolvedInfo.attributes
                                val fnBytes = attributes["fn"]
                                val friendlyName = if (fnBytes != null) {
                                    String(fnBytes, StandardCharsets.UTF_8)
                                } else {
                                    resolvedInfo.serviceName
                                }

                                val idBytes = attributes["id"]
                                val deviceId = if (idBytes != null) {
                                    String(idBytes, StandardCharsets.UTF_8)
                                } else {
                                    resolvedInfo.serviceName
                                }

                                val device = CastDevice(
                                    id = deviceId,
                                    name = friendlyName,
                                    type = CastDeviceType.CHROMECAST,
                                    hostAddress = resolvedInfo.host,
                                    port = if (resolvedInfo.port > 0) resolvedInfo.port else 8009
                                )
                                onDeviceFound(device)
                            }
                        }
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                onDeviceLost(serviceInfo.serviceName)
            }

            override fun onDiscoveryStopped(serviceType: String) {
                isDiscovering = false
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                isDiscovering = false
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                isDiscovering = false
            }
        }

        try {
            manager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            e.printStackTrace()
            isDiscovering = false
        }
    }

    override fun stopDiscovery() {
        if (!isDiscovering) return
        val manager = nsdManager ?: return
        discoveryListener?.let {
            try {
                manager.stopServiceDiscovery(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        discoveryListener = null
        isDiscovering = false
    }

    override fun createSession(device: CastDevice, listener: CastStateListener): CastSession {
        return CastChannelSession(device, listener)
    }
}
