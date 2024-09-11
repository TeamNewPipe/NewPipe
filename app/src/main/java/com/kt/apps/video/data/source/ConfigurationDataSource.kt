package com.kt.apps.video.data.source

import com.kt.apps.video.data.PlayerChooser
import com.kt.apps.video.data.version.NewVersionInfo
import kotlinx.coroutines.flow.Flow

interface ConfigurationDataSource {
    val newVersionInfo: Flow<NewVersionInfo>
    val playerChooser: Flow<PlayerChooser>
}
