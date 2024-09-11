package com.kt.apps.video.domain.repository

import kotlinx.coroutines.flow.StateFlow

interface BlockRepository {
    val pickedVideoDetailPlayer: StateFlow<Boolean>
}
