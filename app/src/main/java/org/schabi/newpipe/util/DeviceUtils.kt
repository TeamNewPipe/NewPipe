package org.schabi.newpipe.util

import android.annotation.SuppressLint
import android.app.UiModeManager
import android.content.Context
import android.content.Context.INPUT_SERVICE
import android.content.res.Configuration
import android.graphics.Point
import android.hardware.input.InputManager
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings
import android.util.TypedValue
import android.view.InputDevice
import android.view.KeyEvent
import android.view.WindowInsets
import android.view.WindowManager
import android.webkit.CookieManager
import androidx.annotation.Dimension
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import org.schabi.newpipe.App
import org.schabi.newpipe.R

object DeviceUtils {

    private const val AMAZON_FEATURE_FIRE_TV = "amazon.hardware.fire_tv"
    private val SAMSUNG = Build.MANUFACTURER == "samsung"
    private var isTV: Boolean? = null
    private var isFireTV: Boolean? = null

    /**
     * The app version code that corresponds to the last update
     * of the media tunneling device blacklist.
     *
     * The value of this variable needs to be updated everytime a new device that does not
     * support media tunneling to match the **upcoming** version code.
     * @see shouldSupportMediaTunneling
     */
    const val MEDIA_TUNNELING_DEVICE_BLACKLIST_VERSION = 1015

    // region: devices not supporting media tunneling / media tunneling blacklist
    /**
     * Formuler Z8 Pro, Z8, CC, Z Alpha, Z+ Neo.
     * Blacklist reason: black screen
     * Board: HiSilicon Hi3798MV200
     */
    private val HI3798MV200 = Build.VERSION.SDK_INT == 24 && Build.DEVICE == "Hi3798MV200"

    /**
     * Zephir TS43UHD-2.
     * Blacklist reason: black screen
     */
    private val CVT_MT5886_EU_1G = Build.VERSION.SDK_INT == 24 && Build.DEVICE == "cvt_mt5886_eu_1g"

    /**
     * Hilife TV.
     * Blacklist reason: black screen
     */
    private val REALTEKATV = Build.VERSION.SDK_INT == 25 && Build.DEVICE == "RealtekATV"

    /**
     * Phillips 4K (O)LED TV.
     * Supports custom ROMs with different API levels
     */
    private val PH7M_EU_5596 = Build.VERSION.SDK_INT >= 26 && Build.DEVICE == "PH7M_EU_5596"

    /**
     * Philips QM16XE.
     * Blacklist reason: black screen
     */
    private val QM16XE_U = Build.VERSION.SDK_INT == 23 && Build.DEVICE == "QM16XE_U"

    /**
     * Sony Bravia VH1.
     * Processor: MT5895
     * Blacklist reason: fullscreen crash / stuttering
     */
    private val BRAVIA_VH1 = Build.VERSION.SDK_INT == 29 && Build.DEVICE == "BRAVIA_VH1"

    /**
     * Sony Bravia VH2.
     * Blacklist reason: fullscreen crash; this includes model A90J as reported in
     * [#9023](https://github.com/TeamNewPipe/NewPipe/issues/9023#issuecomment-1387106242)
     */
    private val BRAVIA_VH2 = Build.VERSION.SDK_INT == 29 && Build.DEVICE == "BRAVIA_VH2"

    /**
     * Sony Bravia Android TV platform 2.
     * Uses a MediaTek MT5891 (MT5596) SoC.
     * @see [https://github.com/CiNcH83/bravia_atv2](https://github.com/CiNcH83/bravia_atv2)
     */
    private val BRAVIA_ATV2 = Build.DEVICE == "BRAVIA_ATV2"

    /**
     * Sony Bravia Android TV platform 3 4K.
     * Uses ARM MT5891 and a [BRAVIA_ATV2] motherboard.
     *
     * @see [https://browser.geekbench.com/v4/cpu/9101105](https://browser.geekbench.com/v4/cpu/9101105)
     */
    private val BRAVIA_ATV3_4K = Build.DEVICE == "BRAVIA_ATV3_4K"

    /**
     * Panasonic 4KTV-JUP.
     * Blacklist reason: fullscreen crash
     */
    private val TX_50JXW834 = Build.DEVICE == "TX_50JXW834"

    /**
     * Bouygtel4K / Bouygues Telecom Bbox 4K.
     * Blacklist reason: black screen; reported at
     * [#10122](https://github.com/TeamNewPipe/NewPipe/pull/10122#issuecomment-1638475769)
     */
    private val HMB9213NW = Build.DEVICE == "HMB9213NW"

    /**
     * JMGO N1S 4K.
     * Blacklist reason: fullscreen crash
     */
    private val LONAVLA = Build.DEVICE == "lonavla"

    /**
     * TCL 65Q651G.
     * Blacklist reason: fullscreen crash
     */
    private val G10 = Build.DEVICE == "G10"
    // endregion

    @JvmStatic
    fun isFireTv(): Boolean {
        isFireTV?.let { return it }

        val fireTV = App.instance.packageManager.hasSystemFeature(AMAZON_FEATURE_FIRE_TV)
        isFireTV = fireTV
        return fireTV
    }

    @JvmStatic
    fun isTv(context: Context): Boolean {
        isTV?.let { return it }

        val pm = App.instance.packageManager

        // from doc: https://developer.android.com/training/tv/start/hardware.html#runtime-check
        var tv = ContextCompat.getSystemService(context, UiModeManager::class.java)
            ?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
            isFireTv() ||
            pm.hasSystemFeature("android.software.leanback")

        // from https://stackoverflow.com/a/58932366
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val batteryManager = context.getSystemService(BatteryManager::class.java)
            val isBatteryAbsent = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) == 0
            tv = tv || (isBatteryAbsent &&
                !pm.hasSystemFeature("android.hardware.touchscreen") &&
                pm.hasSystemFeature("android.hardware.usb.host") &&
                pm.hasSystemFeature("android.hardware.ethernet"))
        }

        isTV = tv
        return tv
    }

    /**
     * Checks if the device is in desktop or DeX mode. This function should only
     * be invoked once on view load as it is using reflection for the DeX checks.
     * @param context the context to use for services and config.
     * @return true if the Android device is in desktop mode or using DeX.
     */
    @JvmStatic
    @SuppressLint("WrongConstant")
    fun isDesktopMode(context: Context): Boolean {
        // Adapted from https://stackoverflow.com/a/64615568
        // to check for all input devices that have an active cursor
        val im = context.getSystemService(INPUT_SERVICE) as InputManager
        for (id in im.inputDeviceIds) {
            val inputDevice = im.getInputDevice(id)
            if (inputDevice != null && (inputDevice.supportsSource(InputDevice.SOURCE_BLUETOOTH_STYLUS) ||
                inputDevice.supportsSource(InputDevice.SOURCE_MOUSE) ||
                inputDevice.supportsSource(InputDevice.SOURCE_STYLUS) ||
                inputDevice.supportsSource(InputDevice.SOURCE_TOUCHPAD) ||
                inputDevice.supportsSource(InputDevice.SOURCE_TRACKBALL))
            ) {
                return true
            }
        }

        val uiModeManager = ContextCompat.getSystemService(context, UiModeManager::class.java)
        if (uiModeManager != null && uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_DESK) {
            return true
        }

        if (!SAMSUNG) {
            return false
            // DeX is Samsung-specific, skip the checks below on non-Samsung devices
        }
        // DeX check for standalone and multi-window mode, from:
        // https://developer.samsung.com/samsung-dex/modify-optimizing.html
        try {
            val config = context.resources.configuration
            val configClass = config.javaClass
            val semDesktopModeEnabledConst = configClass.getField("SEM_DESKTOP_MODE_ENABLED").getInt(configClass)
            val currentMode = configClass.getField("semDesktopModeEnabled").getInt(config)
            if (semDesktopModeEnabledConst == currentMode) {
                return true
            }
        } catch (ignored: Exception) {
            // Device doesn't seem to support DeX
        }

        val desktopModeManager = context.applicationContext.getSystemService("desktopmode")

        if (desktopModeManager != null) {
            try {
                val getDesktopModeStateMethod = desktopModeManager.javaClass.getDeclaredMethod("getDesktopModeState")
                val desktopModeState = getDesktopModeStateMethod.invoke(desktopModeManager)
                val desktopModeStateClass = desktopModeState.javaClass
                val getEnabledMethod = desktopModeStateClass.getDeclaredMethod("getEnabled")
                val enabledStatus = getEnabledMethod.invoke(desktopModeState) as Int
                if (enabledStatus == desktopModeStateClass.getDeclaredField("ENABLED").getInt(desktopModeStateClass)) {
                    return true
                }
            } catch (ignored: Exception) {
                // Device does not support DeX 3.0 or something went wrong
            }
        }

        return false
    }

    @JvmStatic
    fun isTablet(context: Context): Boolean {
        val tabletModeSetting = PreferenceManager.getDefaultSharedPreferences(context)
            .getString(context.getString(R.string.tablet_mode_key), "")

        if (tabletModeSetting == context.getString(R.string.tablet_mode_on_key)) {
            return true
        } else if (tabletModeSetting == context.getString(R.string.tablet_mode_off_key)) {
            return false
        }

        // else automatically determine whether we are in a tablet or not
        return (context.resources.configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK) >=
            Configuration.SCREENLAYOUT_SIZE_LARGE
    }

    @JvmStatic
    fun isConfirmKey(keyCode: Int): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_SPACE,
            KeyEvent.KEYCODE_NUMPAD_ENTER -> true
            else -> false
        }
    }

    @JvmStatic
    fun dpToPx(@Dimension(unit = Dimension.DP) dp: Int, context: Context): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            context.resources.displayMetrics
        ).toInt()
    }

    @JvmStatic
    fun spToPx(@Dimension(unit = Dimension.SP) sp: Int, context: Context): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            sp.toFloat(),
            context.resources.displayMetrics
        ).toInt()
    }

    @JvmStatic
    fun isLandscape(context: Context): Boolean {
        return context.resources.displayMetrics.heightPixels < context.resources.displayMetrics.widthPixels
    }

    @JvmStatic
    fun isInMultiWindow(activity: AppCompatActivity): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && activity.isInMultiWindowMode
    }

    @JvmStatic
    fun hasAnimationsAnimatorDurationEnabled(context: Context): Boolean {
        return Settings.System.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) != 0f
    }

    @JvmStatic
    fun getWindowHeight(windowManager: WindowManager): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowMetrics = windowManager.currentWindowMetrics
            val windowInsets = windowMetrics.windowInsets
            val insets = windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.navigationBars() or WindowInsets.Type.displayCutout()
            )
            windowMetrics.bounds.height() - (insets.top + insets.bottom)
        } else {
            val point = Point()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getSize(point)
            point.y
        }
    }

    /**
     * Some devices have broken tunneled video playback but claim to support it.
     *
     * This can cause a black video player surface while attempting to play a video or
     * crashes while entering or exiting the full screen player.
     * The issue effects Android TVs most commonly.
     * See [#5911](https://github.com/TeamNewPipe/NewPipe/issues/5911) and
     * [#9023](https://github.com/TeamNewPipe/NewPipe/issues/9023) for more info.
     * @note Update [MEDIA_TUNNELING_DEVICE_BLACKLIST_VERSION]
     * when adding a new device to the method.
     * @return `false` if affected device; `true` otherwise
     */
    @JvmStatic
    fun shouldSupportMediaTunneling(): Boolean {
        return !HI3798MV200 &&
            !CVT_MT5886_EU_1G &&
            !REALTEKATV &&
            !QM16XE_U &&
            !BRAVIA_VH1 &&
            !BRAVIA_VH2 &&
            !BRAVIA_ATV2 &&
            !BRAVIA_ATV3_4K &&
            !PH7M_EU_5596 &&
            !TX_50JXW834 &&
            !HMB9213NW &&
            !LONAVLA &&
            !G10
    }

    /**
     * @return whether the device has support for WebView, see
     * [https://stackoverflow.com/a/69626735](https://stackoverflow.com/a/69626735)
     */
    @JvmStatic
    fun supportsWebView(): Boolean {
        return try {
            CookieManager.getInstance()
            true
        } catch (ignored: Throwable) {
            false
        }
    }
}
