package org.schabi.newpipe.download

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import org.schabi.newpipe.ui.screen.DownloadsScreen
import org.schabi.newpipe.util.ThemeHelper
import us.shandian.giga.service.DownloadManagerService

class DownloadActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val i = Intent(this, DownloadManagerService::class.java)
        startService(i)

        ThemeHelper.setDayNightMode(this)

        setContent {
            DownloadsScreen()
        }
    }
}
