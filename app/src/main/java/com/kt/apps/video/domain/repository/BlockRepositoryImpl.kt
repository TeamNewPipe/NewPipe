package com.kt.apps.video.domain.repository

import com.kt.apps.video.data.PlayerType
import com.kt.apps.video.data.source.ConfigurationDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.schabi.newpipe.BuildConfig
import timber.log.Timber

class BlockRepositoryImpl(
    configurationDataSource: ConfigurationDataSource
) : BlockRepository {
    private val _selectVideoDetailPlayer = configurationDataSource.playerChooser.map { it ->
        // Đi từ cuối tới đầu, lấy giá trị có version bé hơn hoặc bằng current
        // BuildConfig.VERSION_CODE = 5
        // [{3, "origin"},{4, "web"}, {6, "origin" }] => web
        // [{3, "origin"},{4, "web"}] => web
        // [{3, "origin"},{4, "web"}, {5, "origin" }] => origin
        Timber.tag("SelectPlayer").d(it.versionPlayers.joinToString())
        it.versionPlayers.lastOrNull {
            BuildConfig.VERSION_CODE >= it.version
        }?.playerType ?: PlayerType.Origin
    }.map { it == PlayerType.Origin }.stateIn(CoroutineScope(Dispatchers.Default), SharingStarted.Eagerly, true)
    override val pickedVideoDetailPlayer: StateFlow<Boolean> = _selectVideoDetailPlayer
}
