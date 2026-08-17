package org.schabi.newpipe.util

import android.content.Context
import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.ui.graphics.vector.ImageVector

object AudioOutputHelper {

    /**
     * Returns the name of the currently active/connected audio output route,
     * e.g., "This phone", "Pixel Buds", "Bluetooth Speaker", etc.
     */
    fun getCurrentAudioOutputName(context: Context): String {
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                ?: return "This phone"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                val bluetoothDevice = devices.firstOrNull { device ->
                    device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                    device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && (
                        device.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                        device.type == AudioDeviceInfo.TYPE_BLE_SPEAKER ||
                        device.type == AudioDeviceInfo.TYPE_BLE_BROADCAST
                    ))
                }

                if (bluetoothDevice != null) {
                    val name = bluetoothDevice.productName?.toString()?.trim()
                    if (!name.isNullOrEmpty()) return name
                    return "Bluetooth"
                }

                val wiredDevice = devices.firstOrNull { device ->
                    device.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                    device.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                    device.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                    device.type == AudioDeviceInfo.TYPE_USB_DEVICE
                }

                if (wiredDevice != null) {
                    val name = wiredDevice.productName?.toString()?.trim()
                    if (!name.isNullOrEmpty()) return name
                    return "Headphones"
                }
            }

            "This phone"
        } catch (_: Exception) {
            "This phone"
        }
    }

    /**
     * Returns an icon corresponding to the active audio output.
     */
    fun getCurrentAudioOutputIcon(context: Context): ImageVector {
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                ?: return Icons.Default.PhoneAndroid

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                val isBluetooth = devices.any { device ->
                    device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                    device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && (
                        device.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                        device.type == AudioDeviceInfo.TYPE_BLE_SPEAKER ||
                        device.type == AudioDeviceInfo.TYPE_BLE_BROADCAST
                    ))
                }
                if (isBluetooth) return Icons.Default.Headphones

                val isWired = devices.any { device ->
                    device.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                    device.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                    device.type == AudioDeviceInfo.TYPE_USB_HEADSET
                }
                if (isWired) return Icons.Default.Headphones
            }

            Icons.Default.PhoneAndroid
        } catch (_: Exception) {
            Icons.Default.PhoneAndroid
        }
    }

    /**
     * Opens Android's standard Output Switcher panel (Settings.Panel.ACTION_MEDIA_OUTPUT)
     * allowing the user to switch playback between Phone Speaker, Bluetooth, Earbuds, Cast, etc.
     */
    fun openAudioOutputSwitcher(context: Context) {
        val packageName = context.packageName

        // 1. Try Android 11+ Media Output Switcher panel
        try {
            val intent = Intent("com.android.settings.panel.action.MEDIA_OUTPUT").apply {
                putExtra("com.android.settings.panel.extra.PACKAGE_NAME", packageName)
                putExtra("package_name", packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return
        } catch (_: Exception) {
        }

        // 2. Try Android 10+ Volume Panel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val intent = Intent(Settings.Panel.ACTION_VOLUME).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return
            } catch (_: Exception) {
            }
        }

        // 3. Fallback to Sound Settings
        try {
            val intent = Intent(Settings.ACTION_SOUND_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "Playing on: ${getCurrentAudioOutputName(context)}", Toast.LENGTH_SHORT).show()
        }
    }
}
