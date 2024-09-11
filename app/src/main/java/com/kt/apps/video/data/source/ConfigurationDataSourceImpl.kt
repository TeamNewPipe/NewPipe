package com.kt.apps.video.data.source

import com.kt.apps.video.data.PlayerChooser
import com.kt.apps.video.data.PlayerType
import com.kt.apps.video.data.VersionedPlayer
import com.kt.apps.video.data.api.FirebaseApi
import com.kt.apps.video.data.version.NewVersionInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.CoroutineContext

class ConfigurationDataSourceImpl(private val firebaseApi: FirebaseApi) : ConfigurationDataSource, CoroutineScope {
    override val coroutineContext: CoroutineContext by lazy { Dispatchers.Default }
    override val newVersionInfo: Flow<NewVersionInfo>
        get() {
            return firebaseApi.data.filterNotNull().mapNotNull {
                val json = JSONObject(it.iTubeNewVersion)
                NewVersionInfo(
                    newestVersionCode = json.getLong("newestVersionCode"),
                    newestVersionName = json.getString("newestVersionName"),
                    minSupportedVersionCode = json.getLong("minSupportedVersionCode"),
                    downloadMd5 = json.getString("downloadMd5"),
                    downloadUrl = json.getString("downloadUrl"),
                    outDateVersionTitle = json.getString("outDateVersionTitle"),
                    outDateVersionSubtitle = json.getString("outDateVersionSubtitle"),
                    outDateVersionAction = json.getString("outDateVersionAction"),
                    unsupportedVersionTitle = json.getString("unsupportedVersionTitle"),
                    unsupportedVersionSubtitle = json.getString("unsupportedVersionSubtitle"),
                    unsupportedVersionAction = json.getString("unsupportedVersionAction")
                )
            }.catch {
                NewVersionInfo()
            }
        }

    override val playerChooser: Flow<PlayerChooser>
        get() {
            return firebaseApi.data.filterNotNull().map {
                val versionPlayers = mutableListOf<VersionedPlayer>()
                val json = JSONArray(it.iTubePlayerChooser)
                for (i in 0 until json.length()) {
                    val item = json.getJSONObject(i)
                    versionPlayers.add(
                        VersionedPlayer(
                            playerType = item.getString("type").run {
                                when (this) {
                                    "origin" -> PlayerType.Origin
                                    "web" -> PlayerType.Web
                                    else -> PlayerType.Origin
                                }
                            },
                            version = item.getLong("version")
                        )
                    )
                }
                PlayerChooser(versionPlayers)
            }.catch { PlayerChooser() }
        }
}
