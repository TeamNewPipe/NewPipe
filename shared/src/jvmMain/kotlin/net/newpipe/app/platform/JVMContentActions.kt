package net.newpipe.app.platform

import co.touchlab.kermit.Logger
import org.koin.core.annotation.Singleton

@Singleton(binds = [ContentActions::class])
class JVMContentActions : ContentActions {
    override fun openMainPageTabsChooser() =
        log("openMainPageTabsChooser")
    override fun openPeertubeInstanceList() =
        log("openPeertubeInstanceList")
    override fun onAppLanguageChanged() = log("onAppLanguageChanged")
    private fun log(name: String) =
        Logger.i(messageString = "$name not implemented on JVM")
}