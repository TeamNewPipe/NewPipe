package org.schabi.newpipe.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dagger.hilt.android.AndroidEntryPoint
import org.schabi.newpipe.R
import org.schabi.newpipe.settings.viewmodel.SettingsViewModel
import org.schabi.newpipe.ui.Toolbar
import org.schabi.newpipe.ui.theme.AppTheme

const val SCREEN_TITLE_KEY = "SCREEN_TITLE_KEY"

@AndroidEntryPoint
class SettingsV2Activity : ComponentActivity() {

    private val settingsViewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val navController = rememberNavController()
            var screenTitle by remember { mutableIntStateOf(SettingsScreenKey.ROOT.screenTitle) }
            navController.addOnDestinationChangedListener { _, _, arguments ->
                screenTitle =
                    arguments?.getInt(SCREEN_TITLE_KEY) ?: SettingsScreenKey.ROOT.screenTitle
            }

            AppTheme {
                Scaffold(topBar = {
                    Toolbar(
                        title = stringResource(id = screenTitle),
                        hasSearch = true,
                        onSearchQueryChange = null // TODO: Add suggestions logic
                    )
                }) { padding ->
                    NavHost(
                        navController = navController,
                        startDestination = SettingsScreenKey.ROOT.name,
                        modifier = Modifier.padding(padding)
                    ) {
                        composable(
                            SettingsScreenKey.ROOT.name,
                            listOf(createScreenTitleArg(SettingsScreenKey.ROOT.screenTitle))
                        ) {
                            SettingsScreen(onSelectSettingOption = { screen ->
                                navController.navigate(screen.name)
                            })
                        }
                        composable(
                            SettingsScreenKey.DEBUG.name,
                            listOf(createScreenTitleArg(SettingsScreenKey.DEBUG.screenTitle))
                        ) {
                            DebugScreen(settingsViewModel)
                        }
                    }
                }
            }
        }
    }
}

fun createScreenTitleArg(@StringRes screenTitle: Int) = navArgument(SCREEN_TITLE_KEY) {
    defaultValue = screenTitle
}

enum class SettingsScreenKey(@StringRes val screenTitle: Int) {
    ROOT(R.string.settings),
    DEBUG(R.string.settings_category_debug_title)
}
