package org.schabi.newpipe.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import org.koin.android.ext.android.inject
import org.schabi.newpipe.ui.navigation.AppNavigator
import org.schabi.newpipe.ui.screen.SettingsScreen
import org.schabi.newpipe.util.ThemeHelper

class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeHelper.setDayNightMode(this)

        setContent {
            SettingsScreen(onBack = { finish() })
        }
    }
}
