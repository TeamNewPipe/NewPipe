package com.kt.apps.video.data

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import com.kt.apps.video.data.api.FirebaseApiImpl
import com.kt.apps.video.data.api.Logger
import com.kt.apps.video.data.api.LoggerImpl
import com.kt.apps.video.data.api.download.DownloadApi
import com.kt.apps.video.data.api.download.DownloadApiImpl
import com.kt.apps.video.data.source.ConfigurationDataSource
import com.kt.apps.video.data.source.ConfigurationDataSourceImpl

class DataContainer {
    private val Context.dataStore by preferencesDataStore(name = "itube-settings")
    val dataStore by lazy { context.dataStore }
    private val firebaseApi: FirebaseApiImpl by lazy {
        FirebaseApiImpl(dataStore)
    }
    private val logger: Logger by lazy {
        LoggerImpl()
    }

    private lateinit var context: Context

    val downloadApi: DownloadApi by lazy {
        DownloadApiImpl(context)
    }

    val configurationDataSource: ConfigurationDataSource by lazy {
        ConfigurationDataSourceImpl(firebaseApi)
    }

    fun init(context: Context) {
        this.context = context
        firebaseApi.setUp()
        logger.init()
    }
}
