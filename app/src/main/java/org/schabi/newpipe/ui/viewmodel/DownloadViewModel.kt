package org.schabi.newpipe.ui.viewmodel

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import us.shandian.giga.get.DownloadMission
import us.shandian.giga.get.Mission
import us.shandian.giga.service.DownloadManagerService
import us.shandian.giga.util.Utility

data class DownloadItemUiState(
    val id: String,
    val mission: Mission,
    val isRunning: Boolean,
    val isFinished: Boolean,
    val isMuxing: Boolean,
    val doneBytes: Long,
    val totalBytes: Long,
    val progress: Float,
    val progressPercent: Int,
    val title: String,
    val uploader: String,
    val isAudio: Boolean,
    val duration: Long,
    val thumbnailUrl: String?,
    val uri: Uri?,
    val speedBytesPerSec: Long = 0L,
    val statusText: String = ""
)

class DownloadViewModel(private val context: Context) : ViewModel() {

    private var downloadManagerBinder: DownloadManagerService.DownloadManagerBinder? = null

    private val _downloadItems = MutableStateFlow<List<DownloadItemUiState>>(emptyList())
    val downloadItems: StateFlow<List<DownloadItemUiState>> = _downloadItems.asStateFlow()

    private val _missions = MutableStateFlow<List<Mission>>(emptyList())
    val missions: StateFlow<List<Mission>> = _missions.asStateFlow()

    private val previousDoneMap = mutableMapOf<String, Long>()
    private var lastSpeedCalcTimestamp = System.currentTimeMillis()

    private val handlerCallback = Handler.Callback { _ ->
        refreshMissions()
        true
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            downloadManagerBinder = service as? DownloadManagerService.DownloadManagerBinder
            downloadManagerBinder?.addMissionEventListener(handlerCallback)
            refreshMissions()
            startPollingProgress()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            downloadManagerBinder?.removeMissionEventListener(handlerCallback)
            downloadManagerBinder = null
        }
    }

    init {
        val intent = Intent(context, DownloadManagerService::class.java)
        try {
            context.startService(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun refreshMissions() {
        val allMissions = downloadManagerBinder?.downloadManager?.missions?.toList() ?: emptyList()
        _missions.value = allMissions

        val now = System.currentTimeMillis()
        val timeDeltaSec = ((now - lastSpeedCalcTimestamp).coerceAtLeast(1L)) / 1000.0

        val mapped = allMissions.map { mission ->
            val downloadMission = mission as? DownloadMission
            val isRunning = downloadMission?.running ?: false
            val isFinished = downloadMission?.isFinished() ?: true
            val isMuxing = downloadMission?.psState == 1
            val done = if (downloadMission != null) downloadMission.done else mission.length
            val total = if (mission.length > 0) mission.length else (downloadMission?.nearLength ?: 0L)
            val progress = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 0f
            val progressPercent = (progress * 100).toInt().coerceIn(0, 100)

            val id = mission.storage?.getUri()?.toString() ?: mission.timestamp.toString()
            val prevDone = previousDoneMap[id] ?: done
            val speed = if (isRunning && timeDeltaSec > 0.1 && done >= prevDone) {
                ((done - prevDone) / timeDeltaSec).toLong()
            } else 0L

            if (isRunning) {
                previousDoneMap[id] = done
            }

            val title = mission.title ?: mission.storage?.getName() ?: "Unknown Media"
            val uploader = mission.uploader ?: if (mission.kind == 'a') "Audio" else "Video"
            val isAudio = mission.kind == 'a'
            val uri = try { mission.storage?.getUri() } catch (e: Exception) { null }

            val statusText = when {
                isFinished -> if (total > 0) "Downloaded • ${Utility.formatBytes(total)}" else "Downloaded"
                isMuxing -> "Muxing audio & video • $progressPercent%"
                isRunning -> {
                    val sizeProgress = if (total > 0) "${Utility.formatBytes(done)} / ${Utility.formatBytes(total)}" else Utility.formatBytes(done)
                    val speedStr = if (speed > 0) " • ${Utility.formatBytes(speed)}/s" else ""
                    "$progressPercent% • $sizeProgress$speedStr"
                }
                else -> {
                    val sizeProgress = if (total > 0) "${Utility.formatBytes(done)} / ${Utility.formatBytes(total)}" else Utility.formatBytes(done)
                    "Paused • $sizeProgress"
                }
            }

            DownloadItemUiState(
                id = id,
                mission = mission,
                isRunning = isRunning,
                isFinished = isFinished,
                isMuxing = isMuxing,
                doneBytes = done,
                totalBytes = total,
                progress = progress,
                progressPercent = progressPercent,
                title = title,
                uploader = uploader,
                isAudio = isAudio,
                duration = mission.duration,
                thumbnailUrl = mission.thumbnailUrl,
                uri = uri,
                speedBytesPerSec = speed,
                statusText = statusText
            )
        }

        lastSpeedCalcTimestamp = now
        _downloadItems.value = mapped
    }

    private fun startPollingProgress() {
        viewModelScope.launch(Dispatchers.Default) {
            while (true) {
                withContext(Dispatchers.Main.immediate) {
                    refreshMissions()
                }
                delay(300)
            }
        }
    }

    fun pauseMission(mission: Mission) {
        if (mission is DownloadMission) {
            viewModelScope.launch(Dispatchers.IO) {
                downloadManagerBinder?.downloadManager?.pauseMission(mission)
                withContext(Dispatchers.Main.immediate) {
                    refreshMissions()
                }
            }
        }
    }

    fun resumeMission(mission: Mission) {
        if (mission is DownloadMission) {
            viewModelScope.launch(Dispatchers.IO) {
                downloadManagerBinder?.downloadManager?.resumeMission(mission)
                withContext(Dispatchers.Main.immediate) {
                    refreshMissions()
                }
            }
        }
    }

    fun playMission(mission: Mission) {
        val uri = try { mission.storage?.getUri() } catch (e: Exception) { null } ?: return
        val mimeType = mission.storage?.getType() ?: "*/*"
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                val fallbackIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "*/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }
    }

    fun deleteMission(mission: Mission) {
        viewModelScope.launch(Dispatchers.IO) {
            downloadManagerBinder?.downloadManager?.deleteMission(mission, false)
            withContext(Dispatchers.Main.immediate) {
                refreshMissions()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            downloadManagerBinder?.removeMissionEventListener(handlerCallback)
            context.unbindService(serviceConnection)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
