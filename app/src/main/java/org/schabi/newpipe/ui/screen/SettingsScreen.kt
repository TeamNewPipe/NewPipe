package org.schabi.newpipe.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject
import org.schabi.newpipe.ui.navigation.AppDestination
import org.schabi.newpipe.ui.navigation.AppNavigator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    navigator: AppNavigator = koinInject()
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            item {
                SettingsCategoryItem(
                    icon = Icons.Default.Palette,
                    title = "Appearance",
                    onClick = { /* Navigate to Appearance */ }
                )
            }
            item {
                SettingsCategoryItem(
                    icon = Icons.Default.Headset,
                    title = "Video and audio",
                    onClick = { /* Navigate to Video/Audio */ }
                )
            }
            item {
                SettingsCategoryItem(
                    icon = Icons.Default.CloudDownload,
                    title = "Download",
                    onClick = { /* Navigate to Download */ }
                )
            }
            item {
                SettingsCategoryItem(
                    icon = Icons.Default.History,
                    title = "History and cache",
                    onClick = { navigator.navigateTo(AppDestination.History) }
                )
            }
            item {
                SettingsCategoryItem(
                    icon = Icons.Default.Notifications,
                    title = "Notifications",
                    onClick = { /* Navigate to Notifications */ }
                )
            }
        }
    }
}

@Composable
fun SettingsCategoryItem(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = title, style = MaterialTheme.typography.titleMedium)
    }
}
