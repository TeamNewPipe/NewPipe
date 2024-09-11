package com.kt.apps.video.data.api

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.ConfigUpdate
import com.google.firebase.remoteconfig.ConfigUpdateListener
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigException
import com.google.firebase.remoteconfig.ktx.remoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfigSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.Executors

class FirebaseApiImpl(private val dataStore: DataStore<Preferences>) : FirebaseApi {
    private val apiScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val apiDispatcher by lazy { Executors.newSingleThreadExecutor().asCoroutineDispatcher() }
    private val _data: MutableStateFlow<FirebaseApi.Data?> = MutableStateFlow(null)
    override val data: StateFlow<FirebaseApi.Data?> get() = _data.asStateFlow()

    override fun setUp() {
        val remoteConfig = Firebase.remoteConfig

        val configSettings = remoteConfigSettings {
            minimumFetchIntervalInSeconds = 28800 // 8h
        }
        remoteConfig.setConfigSettingsAsync(configSettings)

        remoteConfig.fetchAndActivate()
            .addOnCompleteListener {
                Timber.tag("Firebase").d("addOnCompleteListener")
                updateConfig(remoteConfig)
            }.addOnFailureListener {
                Timber.tag("Firebase").w(it, "Fetch failed")
            }
        remoteConfig.addOnConfigUpdateListener(object : ConfigUpdateListener {
            override fun onUpdate(configUpdate: ConfigUpdate) {
                Timber.tag("Firebase").d("Updated keys: %s", configUpdate.updatedKeys)

                remoteConfig.activate().addOnCompleteListener {
                    updateConfig(remoteConfig)
                }
            }

            override fun onError(error: FirebaseRemoteConfigException) {
                Timber.tag("Firebase").w(error, "Config update error with code: %s", error.code)
            }
        })
    }

    private fun updateConfig(remoteConfig: FirebaseRemoteConfig) = apiScope.launch(apiDispatcher) {
        val newVersion = remoteConfig.getString("iTubeNewVersion")
        Timber.tag("Firebase").d("newVersion: %s", newVersion)
        _data.value = FirebaseApi.Data(
            iTubeNewVersion = newVersion,
            iTubePlayerChooser = remoteConfig.getString("iTubePlayerChooser")
        )
    }
}
