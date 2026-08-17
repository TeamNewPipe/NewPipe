package org.schabi.newpipe.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import org.koin.compose.koinInject
import org.koin.compose.navigation3.koinEntryProvider
import org.koin.core.annotation.KoinExperimentalAPI
import org.schabi.newpipe.R
import org.schabi.newpipe.ui.navigation.AppDestination
import org.schabi.newpipe.ui.navigation.AppNavigator
import org.schabi.newpipe.ui.screen.rememberIsInPipMode
import org.schabi.newpipe.ui.theme.YoutubeRed

@OptIn(ExperimentalMaterial3Api::class, KoinExperimentalAPI::class)
@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    navigator: AppNavigator = koinInject()
) {
    val backstack = navigator.backstack
    val currentDestination = backstack.lastOrNull() ?: AppDestination.Home

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isPipMode = rememberIsInPipMode()
    val isFullscreen = (isLandscape || isPipMode) && currentDestination is AppDestination.VideoDetail

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())
    val density = LocalDensity.current
    val bottomBarHeight = 80.dp
    val bottomBarHeightPx = with(density) { bottomBarHeight.roundToPx().toFloat() }
    val systemBottomInsetsPx = WindowInsets.systemBars.getBottom(density).toFloat()
    val totalBottomBarHeightPx = bottomBarHeightPx + systemBottomInsetsPx

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            if (!isFullscreen) {
                TopAppBar(
                    scrollBehavior = scrollBehavior,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.PlayCircle, // YouTube-like play logo
                            contentDescription = null,
                            tint = YoutubeRed,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "NewPipe",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp
                        )
                    }
                },
                actions = {
                    if (currentDestination is AppDestination.Library || currentDestination is AppDestination.History) {
                        IconButton(onClick = { navigator.navigateTo(AppDestination.Settings) }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    }
                    IconButton(onClick = { navigator.navigateTo(AppDestination.Search()) }) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }
                }
            )
            }
        },
        bottomBar = {
            if (!isFullscreen) {
                Column(
                    modifier = Modifier.layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        val offset = (scrollBehavior.state.collapsedFraction * totalBottomBarHeightPx).roundToInt()
                        val newHeight = (placeable.height - offset).coerceAtLeast(0)
                        layout(placeable.width, newHeight) {
                            placeable.placeRelative(0, 0)
                        }
                    }
                ) {
                    val streamInfo by org.schabi.newpipe.player.PlayerManager.currentStreamInfo.collectAsStateWithLifecycle()
                    val url by org.schabi.newpipe.player.PlayerManager.currentUrl.collectAsStateWithLifecycle()
                    
                    if (streamInfo != null && url != null && currentDestination !is AppDestination.VideoDetail) {
                        org.schabi.newpipe.ui.components.MiniPlayer(
                            streamInfo = streamInfo!!,
                            onClick = {
                                navigator.navigateTo(AppDestination.VideoDetail(url!!))
                            },
                            onClose = {
                                org.schabi.newpipe.player.PlayerManager.clearVideo()
                            }
                        )
                    }
                    
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.background,
                        tonalElevation = 0.dp,
                        windowInsets = NavigationBarDefaults.windowInsets
                    ) {
                        NavigationBarItem(
                            selected = currentDestination is AppDestination.Home,
                            onClick = {
                                if (currentDestination !is AppDestination.Home) {
                                    backstack.clear()
                                    navigator.navigateTo(AppDestination.Home)
                                }
                            },
                            icon = { 
                                Icon(
                                    imageVector = if (currentDestination is AppDestination.Home) Icons.Filled.Home else Icons.Outlined.Home,
                                    contentDescription = "Home" 
                                ) 
                            },
                            label = { Text("Home", style = MaterialTheme.typography.labelSmall) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onSurface,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                indicatorColor = Color.Transparent
                            )
                        )
                        NavigationBarItem(
                            selected = currentDestination is AppDestination.Subscriptions,
                            onClick = {
                                if (currentDestination !is AppDestination.Subscriptions) {
                                    navigator.navigateTo(AppDestination.Subscriptions)
                                }
                            },
                            icon = { 
                                Icon(
                                    imageVector = if (currentDestination is AppDestination.Subscriptions) Icons.Filled.Subscriptions else Icons.Outlined.Subscriptions,
                                    contentDescription = "Subscriptions" 
                                ) 
                            },
                            label = { Text("Subscriptions", style = MaterialTheme.typography.labelSmall) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onSurface,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                indicatorColor = Color.Transparent
                            )
                        )
                        NavigationBarItem(
                            selected = currentDestination is AppDestination.Downloads,
                            onClick = {
                                if (currentDestination !is AppDestination.Downloads) {
                                    navigator.navigateTo(AppDestination.Downloads)
                                }
                            },
                            icon = { 
                                Icon(
                                    imageVector = if (currentDestination is AppDestination.Downloads) Icons.Filled.FileDownload else Icons.Outlined.FileDownload,
                                    contentDescription = "Downloads" 
                                ) 
                            },
                            label = { Text("Downloads", style = MaterialTheme.typography.labelSmall) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onSurface,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                indicatorColor = Color.Transparent
                            )
                        )
                        NavigationBarItem(
                            selected = currentDestination is AppDestination.Library || currentDestination is AppDestination.History,
                            onClick = {
                                if (currentDestination !is AppDestination.Library && currentDestination !is AppDestination.History) {
                                    navigator.navigateTo(AppDestination.Library)
                                }
                            },
                            icon = { 
                                Icon(
                                    imageVector = if (currentDestination is AppDestination.Library || currentDestination is AppDestination.History) Icons.Filled.AccountCircle else Icons.Outlined.AccountCircle,
                                    contentDescription = "You" 
                                ) 
                            },
                            label = { Text("You", style = MaterialTheme.typography.labelSmall) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onSurface,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                indicatorColor = Color.Transparent
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        AnimatedContent(
            targetState = currentDestination,
            transitionSpec = {
                (fadeIn(animationSpec = tween(220, delayMillis = 30)) +
                        scaleIn(initialScale = 0.98f, animationSpec = tween(220)))
                    .togetherWith(
                        fadeOut(animationSpec = tween(150)) +
                                scaleOut(targetScale = 1.02f, animationSpec = tween(150))
                    )
            },
            label = "NavDisplayTransition",
            modifier = Modifier.padding(innerPadding)
        ) { _ ->
            NavDisplay(
                backStack = backstack,
                onBack = { navigator.navigateUp() },
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator()
                ),
                entryProvider = koinEntryProvider()
            )
        }
    }
}
