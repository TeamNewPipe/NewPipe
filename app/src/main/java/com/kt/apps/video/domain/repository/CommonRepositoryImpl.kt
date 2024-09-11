package com.kt.apps.video.domain.repository

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import com.kt.apps.video.data.api.download.DownloadApi
import com.kt.apps.video.data.source.ConfigurationDataSource
import com.kt.apps.video.domain.CheckNewVersion
import com.kt.apps.video.viewmodel.data.Event
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.schabi.newpipe.BuildConfig

class CommonRepositoryImpl(
    private val dataStore: DataStore<Preferences>,
    private val configurationDataSource: ConfigurationDataSource,
    private val downloadApi: DownloadApi,
    private val blockRepository: BlockRepository
) : CommonRepository {
    private val hiddenNewVersionHintKey = longPreferencesKey("hidden_new_version_hint")
    private val hiddenNewVersionHint: Flow<Long> = dataStore.data.map { preferences ->
        preferences[hiddenNewVersionHintKey] ?: 0L
    }
    private val _commonEvent by lazy { MutableSharedFlow<Event>() }
    private val commonEvent: Flow<Event> by lazy { _commonEvent }
    private val mainScope by lazy { CoroutineScope(Dispatchers.Main) }
    override fun checkNewVersion(): Flow<CheckNewVersion> {
        return configurationDataSource.newVersionInfo.filterNotNull().combine(hiddenNewVersionHint) { newVersionInfo, hiddenNewVersionHint ->
            val currentVersionCode = BuildConfig.VERSION_CODE.toLong()
            when {
                currentVersionCode < newVersionInfo.minSupportedVersionCode -> {
                    val downloadResult = downloadApi.downloadNewVersion(newVersionInfo.downloadUrl, newVersionInfo.downloadMd5).first {
                        if (it is DownloadApi.Progress) Log.d("Download", "progress: ${it.progress}")
                        it is DownloadApi.Success || it is DownloadApi.Fail
                    }
                    if (downloadResult is DownloadApi.Success) {
                        CheckNewVersion.UnsupportedVersion(
                            title = newVersionInfo.unsupportedVersionTitle,
                            subtitle = newVersionInfo.unsupportedVersionSubtitle,
                            action = newVersionInfo.unsupportedVersionAction,
                            apkFile = downloadResult.file
                        )
                    } else null
                }

                currentVersionCode < newVersionInfo.newestVersionCode && hiddenNewVersionHint < newVersionInfo.newestVersionCode -> {
                    val downloadResult = downloadApi.downloadNewVersion(newVersionInfo.downloadUrl, newVersionInfo.downloadMd5).first {
                        if (it is DownloadApi.Progress) Log.d("Download", "progress: ${it.progress}")
                        it is DownloadApi.Success || it is DownloadApi.Fail
                    }
                    if (downloadResult is DownloadApi.Success) {
                        CheckNewVersion.HintNewVersion(
                            title = newVersionInfo.outDateVersionTitle,
                            subtitle = newVersionInfo.outDateVersionSubtitle,
                            action = newVersionInfo.outDateVersionAction,
                            apkFile = downloadResult.file,
                            newVersionCode = newVersionInfo.newestVersionCode
                        )
                    } else null
                }

                else -> {
                    CheckNewVersion.Gone
                }
            }
        }.filterNotNull().flowOn(Dispatchers.Default)
    }

    override suspend fun hideNewVersionHint(newVersion: CheckNewVersion.HintNewVersion) {
        dataStore.edit {
            it[hiddenNewVersionHintKey] = newVersion.newVersionCode
        }
    }

    override fun registerCommonEvents(): Flow<Event> {
        return commonEvent
    }

    override fun hideVideoDetail(originPlayer: Boolean) {
        mainScope.launch {
            _commonEvent.emit(if (originPlayer) Event.HideVideoDetail.Origin else Event.HideVideoDetail.Web)
        }
    }

    override fun selectVideoDetailPlayer(): Boolean {
        val isOrigin = blockRepository.pickedVideoDetailPlayer.value
        hideVideoDetail(!isOrigin)
        return isOrigin
    }
}
