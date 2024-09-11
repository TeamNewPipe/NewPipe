package com.kt.apps.video.domain.repository

import com.kt.apps.video.domain.CheckNewVersion
import com.kt.apps.video.viewmodel.data.Event
import kotlinx.coroutines.flow.Flow

interface CommonRepository {
    fun checkNewVersion(): Flow<CheckNewVersion>
    suspend fun hideNewVersionHint(newVersion: CheckNewVersion.HintNewVersion)
    fun registerCommonEvents(): Flow<Event>
    fun hideVideoDetail(originPlayer: Boolean)
    fun selectVideoDetailPlayer(): Boolean
}
