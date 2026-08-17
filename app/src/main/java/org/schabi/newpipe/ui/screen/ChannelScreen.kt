package org.schabi.newpipe.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.util.image.ImageStrategy
import org.schabi.newpipe.ui.components.VideoItem
import org.schabi.newpipe.ui.navigation.AppDestination
import org.schabi.newpipe.ui.navigation.AppNavigator
import org.schabi.newpipe.ui.viewmodel.ChannelViewModel
import org.schabi.newpipe.util.ServiceHelper

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ChannelScreen(
    url: String,
    modifier: Modifier = Modifier,
    viewModel: ChannelViewModel = koinViewModel(),
    navigator: AppNavigator = koinInject()
) {
    val channelInfo by viewModel.channelInfo.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(url) {
        viewModel.loadChannel(ServiceHelper.getSelectedServiceId(context), url)
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (isLoading && channelInfo == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularWavyProgressIndicator()
            }
        } else {
            channelInfo?.let { info ->
                ChannelContent(info, navigator)
            }
        }
    }
}

@Composable
fun ChannelContent(
    info: ChannelInfo,
    navigator: AppNavigator,
    modifier: Modifier = Modifier
) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Videos", "Playlists", "About")

    Column(modifier = modifier) {
        // Banner
        AsyncImage(
            model = ImageStrategy.choosePreferredImage(info.banners),
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            contentScale = ContentScale.Crop
        )

        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AsyncImage(
                model = ImageStrategy.choosePreferredImage(info.avatars),
                contentDescription = null,
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = info.name ?: "", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                text = "${info.subscriberCount} subscribers",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = { /* Subscribe */ }) {
                Text("Subscribe")
            }
        }

        TabRow(selectedTabIndex = selectedTabIndex) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = { Text(title) }
                )
            }
        }

        when (selectedTabIndex) {
            0 -> ChannelVideosList(info, navigator)
            1 -> Text("Playlists Section", modifier = Modifier.padding(16.dp))
            2 -> Text(text = info.description ?: "No description", modifier = Modifier.padding(16.dp))
        }
    }
}

@Composable
fun ChannelVideosList(
    info: ChannelInfo,
    navigator: AppNavigator,
    modifier: Modifier = Modifier
) {
    // This needs a separate ViewModel or better data handling to fetch actual videos for the tab
    // For now, we'll just show a message
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Channel videos coming soon.")
    }
}
