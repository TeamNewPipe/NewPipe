package org.schabi.newpipe.cast.driver.lounge

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.preference.PreferenceManager
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicBoolean
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.schabi.newpipe.cast.driver.CastDriver
import org.schabi.newpipe.cast.model.CastDevice
import org.schabi.newpipe.cast.model.CastDeviceType
import org.schabi.newpipe.cast.session.CastSession
import org.schabi.newpipe.cast.session.CastStateListener

class YouTubeLoungeDriver(
    private val context: Context,
    private val client: OkHttpClient = OkHttpClient()
) : CastDriver {

    private val isDiscovering = AtomicBoolean(false)
    private var ssdpThread: Thread? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    companion object {
        private const val PREF_PAIRED_LOUNGE_DEVICES = "cast_paired_lounge_devices"
        private const val GET_SCREEN_URL = "https://www.youtube.com/api/lounge/pairing/get_screen"

        private val SSDP_MULTICAST_ADDR = byteArrayOf(
            239.toByte(),
            255.toByte(),
            255.toByte(),
            250.toByte()
        )
        private const val SSDP_PORT = 1900
    }

    override fun startDiscovery(
        onDeviceFound: (CastDevice) -> Unit,
        onDeviceLost: (String) -> Unit
    ) {
        if (isDiscovering.getAndSet(true)) return

        // First, load previously paired TVs from preferences
        loadSavedDevices().forEach { onDeviceFound(it) }

        // Second, search for Smart TVs via SSDP/DIAL
        ssdpThread = Thread {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket()
                socket.soTimeout = 4000

                val group = InetAddress.getByAddress(SSDP_MULTICAST_ADDR)
                val query = "M-SEARCH * HTTP/1.1\r\n" +
                    "HOST: ${group.hostAddress}:$SSDP_PORT\r\n" +
                    "MAN: \"ssdp:discover\"\r\n" +
                    "MX: 3\r\n" +
                    "ST: urn:dial-multiscreen-org:service:dial:1\r\n\r\n"

                val queryBytes = query.toByteArray(Charsets.UTF_8)
                val packet = DatagramPacket(queryBytes, queryBytes.size, group, SSDP_PORT)
                socket.send(packet)

                val buffer = ByteArray(2048)
                val rxPacket = DatagramPacket(buffer, buffer.size)

                while (isDiscovering.get()) {
                    try {
                        socket.receive(rxPacket)
                        val response = String(rxPacket.data, 0, rxPacket.length, Charsets.UTF_8)
                        val location = extractHeader(response, "LOCATION")
                        if (location != null) {
                            resolveDialDevice(location, rxPacket.address) { device ->
                                mainHandler.post { onDeviceFound(device) }
                            }
                        }
                    } catch (_: Exception) {
                        break
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                socket?.close()
            }
        }.also { it.start() }
    }

    override fun stopDiscovery() {
        isDiscovering.set(false)
        ssdpThread?.interrupt()
        ssdpThread = null
    }

    override fun createSession(device: CastDevice, listener: CastStateListener): CastSession {
        return LoungeSession(device, listener, client)
    }

    fun pairWithTvCode(
        rawCode: String,
        callback: (Result<CastDevice>) -> Unit
    ) {
        val cleanCode = rawCode.replace(" ", "").replace("-", "")
        Thread {
            try {
                val formBody = FormBody.Builder()
                    .add("pairing_code", cleanCode)
                    .build()

                val request = Request.Builder()
                    .url(GET_SCREEN_URL)
                    .post(formBody)
                    .build()

                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: ""
                response.close()

                val json = JSONObject(body)
                val screen = json.optJSONObject("screen")
                if (screen != null) {
                    val screenId = screen.optString("screenId")
                    val name = screen.optString("name", "Smart TV")
                    val loungeToken = screen.optString("loungeToken")

                    val device = CastDevice(
                        id = "lounge_$screenId",
                        name = name,
                        type = CastDeviceType.YOUTUBE_LOUNGE,
                        screenId = screenId,
                        loungeToken = loungeToken
                    )
                    saveDevice(device)
                    mainHandler.post { callback(Result.success(device)) }
                } else {
                    mainHandler.post {
                        callback(Result.failure(IllegalArgumentException("Invalid TV code response")))
                    }
                }
            } catch (e: Exception) {
                mainHandler.post { callback(Result.failure(e)) }
            }
        }.start()
    }

    private fun resolveDialDevice(location: String, address: InetAddress, onResolved: (CastDevice) -> Unit) {
        Thread {
            try {
                val request = Request.Builder().url(location).build()
                val response = client.newCall(request).execute()
                val appUrlHeader = response.header("Application-URL")
                val body = response.body?.string() ?: ""
                response.close()

                val friendlyName = Regex("<friendlyName>(.*?)</friendlyName>").find(body)?.groupValues?.get(1)
                val name = friendlyName ?: "Smart TV (${address.hostAddress})"

                val dialAppUrl = if (!appUrlHeader.isNullOrEmpty()) {
                    if (appUrlHeader.endsWith("/")) appUrlHeader else "$appUrlHeader/"
                } else {
                    val port = java.net.URI(location).port.let { if (it > 0) it else 8008 }
                    "http://${address.hostAddress}:$port/apps/"
                }

                val device = CastDevice(
                    id = "dial_${address.hostAddress}",
                    name = name,
                    type = CastDeviceType.YOUTUBE_LOUNGE,
                    hostAddress = address,
                    dialAppUrl = dialAppUrl
                )
                onResolved(device)
            } catch (ignored: Exception) {
                // Ignore devices failing UPnP/DIAL resolution
            }
        }.start()
    }

    private fun extractHeader(response: String, headerName: String): String? {
        val lines = response.split("\r\n")
        for (line in lines) {
            val parts = line.split(":", limit = 2)
            if (parts.size == 2 && parts[0].trim().equals(headerName, ignoreCase = true)) {
                return parts[1].trim()
            }
        }
        return null
    }

    private fun saveDevice(device: CastDevice) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val list = loadSavedDevices().filter { it.id != device.id }.toMutableList()
        list.add(device)

        val array = JSONArray()
        list.forEach { dev ->
            val obj = JSONObject().apply {
                put("id", dev.id)
                put("name", dev.name)
                put("screenId", dev.screenId)
                put("loungeToken", dev.loungeToken)
            }
            array.put(obj)
        }
        prefs.edit().putString(PREF_PAIRED_LOUNGE_DEVICES, array.toString()).apply()
    }

    fun loadSavedDevices(): List<CastDevice> {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val jsonStr = prefs.getString(PREF_PAIRED_LOUNGE_DEVICES, null) ?: return emptyList()
        val result = mutableListOf<CastDevice>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                result.add(
                    CastDevice(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        type = CastDeviceType.YOUTUBE_LOUNGE,
                        screenId = obj.optString("screenId"),
                        loungeToken = obj.optString("loungeToken")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return result
    }
}
