package org.schabi.newpipe.cast.driver

import org.schabi.newpipe.cast.model.CastDevice
import org.schabi.newpipe.cast.session.CastSession
import org.schabi.newpipe.cast.session.CastStateListener

interface CastDriver {
    fun startDiscovery(
        onDeviceFound: (CastDevice) -> Unit,
        onDeviceLost: (String) -> Unit
    )

    fun stopDiscovery()

    fun createSession(
        device: CastDevice,
        listener: CastStateListener
    ): CastSession
}
