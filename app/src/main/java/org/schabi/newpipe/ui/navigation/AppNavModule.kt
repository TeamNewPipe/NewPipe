package org.schabi.newpipe.ui.navigation

import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.koin.dsl.navigation3.navigation
import org.schabi.newpipe.ui.screen.*
import org.schabi.newpipe.ui.viewmodel.*

@OptIn(KoinExperimentalAPI::class)
fun appNavModule(onCloseRequest: () -> Unit = {}) = appNavModule(AppNavigator(onCloseRequest = onCloseRequest))

@OptIn(KoinExperimentalAPI::class)
fun appNavModule(navigator: AppNavigator) = module {
    single { navigator }

    viewModel { HomeViewModel(get()) }
    viewModel { VideoDetailViewModel(get()) }
    viewModel { SearchViewModel(get()) }
    viewModel { SubscriptionsViewModel(get()) }
    viewModel { HistoryViewModel(get()) }
    viewModel { ChannelViewModel() }
    viewModel { PlaylistViewModel() }
    viewModel { DownloadViewModel(get()) }
    viewModel { LocalPlaylistsViewModel(get()) }
    viewModel { LocalPlaylistDetailViewModel(get()) }

    navigation<AppDestination.Home> {
        HomeScreen()
    }

    navigation<AppDestination.Subscriptions> {
        SubscriptionsScreen()
    }

    navigation<AppDestination.Search> { destination ->
        SearchScreen(query = destination.query)
    }

    navigation<AppDestination.VideoDetail> { destination ->
        VideoDetailScreen(url = destination.url)
    }

    navigation<AppDestination.Channel> { destination ->
        ChannelScreen(url = destination.url)
    }

    navigation<AppDestination.Playlist> { destination ->
        PlaylistScreen(url = destination.url)
    }

    navigation<AppDestination.Library> {
        LibraryScreen()
    }

    navigation<AppDestination.History> {
        HistoryScreen()
    }

    navigation<AppDestination.Downloads> {
        DownloadsScreen()
    }

    navigation<AppDestination.Settings> {
        val navigator = get<AppNavigator>()
        SettingsScreen(onBack = { navigator.navigateUp() })
    }

    navigation<AppDestination.LocalPlaylists> {
        LocalPlaylistsScreen()
    }

    navigation<AppDestination.LocalPlaylistDetail> { destination ->
        LocalPlaylistDetailScreen(playlistId = destination.playlistId)
    }
}
