package org.schabi.newpipe.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dagger.hilt.android.AndroidEntryPoint
import org.schabi.newpipe.settings.navigation.SettingsNavigation
import org.schabi.newpipe.ui.theme.AppTheme

@AndroidEntryPoint
class SettingsV2Activity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AppTheme {
                SettingsNavigation(
                    onExitSettings = { finish() },
                )
            }
        }
    }
}
