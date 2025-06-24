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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import org.schabi.newpipe.R
import org.schabi.newpipe.settings.viewmodel.SettingsViewModel
import org.schabi.newpipe.ui.SettingsRoutes
import org.schabi.newpipe.ui.Toolbar
import org.schabi.newpipe.ui.theme.AppTheme

@AndroidEntryPoint
class SettingsV2Activity : ComponentActivity() {

    private val settingsViewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val navController = rememberNavController()
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            @StringRes val screenTitleRes by remember(navBackStackEntry) {
                mutableIntStateOf(
                    when (navBackStackEntry?.destination?.route) {
                        SettingsRoutes.SettingsMainRoute::class.java.canonicalName -> SettingsRoutes.SettingsMainRoute.screenTitleRes
                        SettingsRoutes.SettingsDebugRoute::class.java.canonicalName -> SettingsRoutes.SettingsDebugRoute.screenTitleRes
                        else -> R.string.settings
                    }
                )
            }

            AppTheme {
                Scaffold(topBar = {
                    Toolbar(
                        title = stringResource(screenTitleRes),
                        onNavigateBack = {
                            if (!navController.popBackStack()) {
                                finish()
                            }
                        },
                        hasSearch = true,
                        onSearch = {
                            // TODO: Add suggestions logic
                        },
                        searchResults = emptyList()
                    )
                }) { padding ->
                    NavHost(
                        navController = navController,
                        startDestination = SettingsRoutes.SettingsMainRoute,
                        modifier = Modifier.padding(padding)
                    ) {
                        composable<SettingsRoutes.SettingsMainRoute> {
                            SettingsScreen(onSelectSettingOption = { route ->
                                navController.navigate(route)
                            })
                        }
                        composable<SettingsRoutes.SettingsDebugRoute> {
                            DebugScreen(settingsViewModel)
                        }
                    }
                }
            }
        }
    }
}
