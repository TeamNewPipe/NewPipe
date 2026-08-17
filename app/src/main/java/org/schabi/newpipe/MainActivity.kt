package org.schabi.newpipe

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import net.newpipe.app.di.KoinApp
import net.newpipe.app.navigation.navModule
import org.koin.compose.KoinApplication
import org.koin.plugin.module.dsl.koinConfiguration
import org.schabi.newpipe.ui.MainScreen
import org.schabi.newpipe.ui.navigation.AppDestination
import org.schabi.newpipe.ui.navigation.AppNavigator
import org.schabi.newpipe.ui.navigation.appNavModule
import org.schabi.newpipe.ui.theme.YouTubeTheme
import org.schabi.newpipe.util.Localization

class MainActivity : ComponentActivity() {

    private var appNavigator: AppNavigator? = null

    companion object {
        const val EXTRA_DESTINATION = "org.schabi.newpipe.EXTRA_DESTINATION"
        const val DESTINATION_DOWNLOADS = "DESTINATION_DOWNLOADS"
        const val DESTINATION_HISTORY = "DESTINATION_HISTORY"
        const val DESTINATION_SUBSCRIPTIONS = "DESTINATION_SUBSCRIPTIONS"
        const val ACTION_OPEN_DOWNLOADS = "org.schabi.newpipe.ACTION_OPEN_DOWNLOADS"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        Localization.migrateAppLanguageSettingIfNecessary(applicationContext)

        val navigator = AppNavigator(
            startDestination = getDestinationFromIntent(intent),
            onCloseRequest = ::finish
        )
        appNavigator = navigator

        setContent {
            KoinApplication(
                configuration = koinConfiguration<KoinApp>(
                    appDeclaration = {
                        modules(navModule(), appNavModule(navigator = navigator))
                    }
                )
            ) {
                YouTubeTheme {
                    MainScreen()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val destination = getDestinationFromIntent(intent)
        if (destination != AppDestination.Home || intent.hasExtra(EXTRA_DESTINATION) || intent.action == ACTION_OPEN_DOWNLOADS) {
            appNavigator?.navigateToTab(destination)
        }
    }

    private fun getDestinationFromIntent(intent: Intent?): AppDestination {
        if (intent == null) return AppDestination.Home
        if (intent.action == ACTION_OPEN_DOWNLOADS) return AppDestination.Downloads

        return when (intent.getStringExtra(EXTRA_DESTINATION)) {
            DESTINATION_DOWNLOADS -> AppDestination.Downloads
            DESTINATION_HISTORY -> AppDestination.History
            DESTINATION_SUBSCRIPTIONS -> AppDestination.Subscriptions
            else -> AppDestination.Home
        }
    }
}
