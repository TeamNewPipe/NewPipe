/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import com.russhwolf.settings.Settings
import net.newpipe.app.Constants.KEY_STREAMING_SERVICE
import org.koin.compose.koinInject

val youTubeLightScheme = lightColorScheme(
    primaryContainer = Color(0xFFE53935),
    onPrimaryContainer = Color(0xFFFFFFFF)
)

val youTubeDarkScheme = darkColorScheme(
    primaryContainer = Color(0xFF992722),
    onPrimaryContainer = Color(0xFFFFFFFF)
)

val soundCloudLightScheme = lightColorScheme(
    primaryContainer = Color(0xFFF57C00),
    onPrimaryContainer = Color(0xFFFFFFFF)
)

val soundCloudDarkScheme = darkColorScheme(
    primaryContainer = Color(0xFFA35300),
    onPrimaryContainer = Color(0xFFFFFFFF)
)

val mediaCCCLightScheme = lightColorScheme(
    primaryContainer = Color(0xFF9E9E9E),
    onPrimaryContainer = Color(0xFFFFFFFF)
)

val mediaCCCDarkScheme = darkColorScheme(
    primaryContainer = Color(0xFF878787),
    onPrimaryContainer = Color(0xFFFFFFFF)
)

val peerTubeLightScheme = lightColorScheme(
    primaryContainer = Color(0xFFFF6F00),
    onPrimaryContainer = Color(0xFFFFFFFF)
)

val peerTubeDarkScheme = darkColorScheme(
    primaryContainer = Color(0xFFA34700),
    onPrimaryContainer = Color(0xFFFFFFFF)
)

val bandCampLightScheme = lightColorScheme(
    primaryContainer = Color(0xFF17A0C4),
    onPrimaryContainer = Color(0xFFFFFFFF)
)

val bandCampDarkScheme = darkColorScheme(
    primaryContainer = Color(0xFF1383A1),
    onPrimaryContainer = Color(0xFFFFFFFF)
)

/**
 * Supported services in the NewPipe app and minor information about them for UI decisions.
 * @property serviceId ID of the service as defined in NewPipeExtractor
 * @property serviceName Name of the service as defined in NewPipeExtractor
 * @property lightScheme Light color scheme to reflect the brand
 * @property darkScheme Dark color scheme to reflect the brand
 * @property isSchemeColorDensityLight Whether this brand's color schemes are of lighter density.
 */
enum class Service(
    val serviceId: Int,
    val serviceName: String,
    val lightScheme: ColorScheme,
    val darkScheme: ColorScheme,
    val isSchemeColorDensityLight: Boolean = false
) {
    YOUTUBE(
        serviceId = 0,
        serviceName = "YouTube",
        lightScheme = youTubeLightScheme,
        darkScheme = youTubeDarkScheme
    ),
    SOUNDCLOUD(
        serviceId = 1,
        serviceName = "SoundCloud",
        lightScheme = soundCloudLightScheme,
        darkScheme = soundCloudDarkScheme
    ),
    MEDIA_CCC(
        serviceId = 2,
        serviceName = "media.ccc.de",
        lightScheme = mediaCCCLightScheme,
        darkScheme = mediaCCCDarkScheme
    ),
    PEERTUBE(
        serviceId = 3,
        serviceName = "PeerTube",
        lightScheme = peerTubeLightScheme,
        darkScheme = peerTubeDarkScheme
    ),
    BANDCAMP(
        serviceId = 4,
        serviceName = "Bandcamp",
        lightScheme = bandCampLightScheme,
        darkScheme = bandCampDarkScheme
    )
}

/**
 * Currently active/selected service by user
 */
@Composable
fun currentService(settings: Settings = koinInject()): Service {
    return Service.entries.find { service ->
        service.serviceName == settings.getString(
            KEY_STREAMING_SERVICE,
            Service.YOUTUBE.serviceName
        )
    }!!
}

/**
 * Currently active/selected service's color that can be used to represent it.
 * Fallbacks to YouTube on preview.
 */
@Composable
fun currentServiceScheme(
    isPreview: Boolean = LocalInspectionMode.current,
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    service: Service = if (isPreview) Service.YOUTUBE else currentService()
): ColorScheme {
    return when {
        useDarkTheme -> service.darkScheme
        else -> service.lightScheme
    }
}

/**
 * Top app bar colors to represent the currently active service.
 * Fallbacks to YouTube on preview.
 */
@Composable
fun currentServiceTopAppBarColors(
    serviceScheme: ColorScheme = currentServiceScheme()
): TopAppBarColors {
    return TopAppBarDefaults.topAppBarColors(
        containerColor = serviceScheme.primaryContainer,
        scrolledContainerColor = serviceScheme.primaryContainer,
        navigationIconContentColor = serviceScheme.onPrimaryContainer,
        titleContentColor = serviceScheme.onPrimaryContainer,
        subtitleContentColor = serviceScheme.onPrimaryContainer,
        actionIconContentColor = serviceScheme.onPrimaryContainer
    )
}
