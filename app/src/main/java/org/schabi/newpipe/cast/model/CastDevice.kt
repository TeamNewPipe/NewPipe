package org.schabi.newpipe.cast.model

import java.net.InetAddress

data class CastDevice(
    val id: String,
    val name: String,
    val type: CastDeviceType,
    val hostAddress: InetAddress? = null,
    val port: Int = 8009,
    val screenId: String? = null,
    val loungeToken: String? = null,
    val dialAppUrl: String? = null
)
