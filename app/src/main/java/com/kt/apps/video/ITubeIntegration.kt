package com.kt.apps.video

import android.content.Context
import com.google.firebase.FirebaseApp
import com.kt.apps.video.data.DataContainer
import com.kt.apps.video.domain.repository.BlockRepository
import com.kt.apps.video.domain.repository.BlockRepositoryImpl
import com.kt.apps.video.domain.repository.CommonRepository
import com.kt.apps.video.domain.repository.CommonRepositoryImpl

class ITubeIntegration private constructor() {
    private val dataContainer: DataContainer by lazy {
        DataContainer()
    }

    private lateinit var blockRepository: BlockRepository

    val commonRepository: CommonRepository by lazy {
        CommonRepositoryImpl(dataContainer.dataStore, dataContainer.configurationDataSource, dataContainer.downloadApi, blockRepository)
    }

    fun init(context: Context) {
        FirebaseApp.initializeApp(context)
        dataContainer.init(context)
        blockRepository = BlockRepositoryImpl(dataContainer.configurationDataSource)
    }

    companion object {
        const val ENABLE_NEW_PIPE_AUTO_CHECK_UPDATE = false
        @JvmStatic
        val instance by lazy {
            ITubeIntegration()
        }
    }
}
