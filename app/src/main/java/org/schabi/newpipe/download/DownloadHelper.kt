package org.schabi.newpipe.download

import android.content.*
import android.net.Uri
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import io.reactivex.rxjava3.disposables.CompositeDisposable
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.Stream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.VideoStream
import org.schabi.newpipe.local.download.DownloadRecordManager
import org.schabi.newpipe.player.playqueue.PlayQueue
import org.schabi.newpipe.settings.NewPipeSettings
import org.schabi.newpipe.streams.io.StoredDirectoryHelper
import org.schabi.newpipe.streams.io.StoredFileHelper
import org.schabi.newpipe.util.FilenameUtils
import us.shandian.giga.get.MissionRecoveryInfo
import us.shandian.giga.postprocessing.Postprocessing
import us.shandian.giga.service.DownloadManager
import us.shandian.giga.service.DownloadManagerService
import us.shandian.giga.service.DownloadManagerService.DownloadManagerBinder
import us.shandian.giga.service.MissionState
import java.io.IOException

fun download(
    queue: PlayQueue,
    activity: AppCompatActivity
) {
    queue.streams.map {
        it.stream

        it.stream.subscribe({ streamInfo ->
            download(activity, streamInfo)
        }, {})
    }
}

fun download(
    activity: AppCompatActivity,
    currentInfo: StreamInfo
) {
    download(activity, currentInfo, mutableListOf(), 0)
}

fun download(
    activity: AppCompatActivity,
    currentInfo: StreamInfo,
    sortedVideoStreams: MutableList<VideoStream>,
    selectedVideoStreamIndex: Int,
) {

    val useDefaultKey: String = activity.getString(R.string.downloads_use_default)
    val sharedPref = PreferenceManager.getDefaultSharedPreferences(activity)
    val defaultValue = sharedPref.getBoolean(useDefaultKey, false)

    if (defaultValue) {
        try {
            val intent = Intent(activity, DownloadManagerService::class.java)
            activity.startService(intent)

            var mainStorageAudio: StoredDirectoryHelper? = null
            var mainStorageVideo: StoredDirectoryHelper? = null
            var downloadManager: DownloadManager? = null
            var askForSavePath = false

            activity.bindService(
                intent,
                object : ServiceConnection {
                    override fun onServiceConnected(cname: ComponentName, service: IBinder) {
                        val mgr = service as DownloadManagerBinder
                        mainStorageAudio = mgr.mainStorageAudio
                        mainStorageVideo = mgr.mainStorageVideo
                        downloadManager = mgr.downloadManager
                        askForSavePath = mgr.askForSavePath()
                        prepareSelectedDownload(
                            activity,
                            currentInfo,
                            mainStorageAudio,
                            askForSavePath,
                            downloadManager!!,
                            sortedVideoStreams, selectedVideoStreamIndex, defaultValue
                        )
                        activity.unbindService(this)
                    }

                    override fun onServiceDisconnected(name: ComponentName) {
                        // nothing to do
                    }
                },
                Context.BIND_AUTO_CREATE
            )
        } catch (e: Exception) {
            openDialog(
                activity, currentInfo,
                sortedVideoStreams,
                selectedVideoStreamIndex,
                defaultValue
            )
        }
    } else {
        openDialog(
            activity, currentInfo,
            sortedVideoStreams,
            selectedVideoStreamIndex,
            defaultValue
        )
    }
}

fun openDialog(
    activity: AppCompatActivity,
    currentInfo: StreamInfo,
    sortedVideoStreams: MutableList<VideoStream>,
    selectedVideoStreamIndex: Int,
    defaultValues: Boolean
) {
    val downloadDialog = DownloadDialog.newInstance(currentInfo)
    downloadDialog.setVideoStreams(sortedVideoStreams)
    downloadDialog.setAudioStreams(currentInfo.audioStreams)
    downloadDialog.setSelectedVideoStream(selectedVideoStreamIndex)
    downloadDialog.setSubtitleStreams(currentInfo.subtitles)
    downloadDialog.setDefaultValues(defaultValues)

    downloadDialog.show(activity.supportFragmentManager, "downloadDialog")
}

private fun prepareSelectedDownload(
    activity: AppCompatActivity,
    currentInfo: StreamInfo,
    mainStorageAudio: StoredDirectoryHelper?,
    askForSavePath: Boolean,
    downloadManager: DownloadManager,
    sortedVideoStreams: MutableList<VideoStream>,
    selectedVideoStreamIndex: Int,
    defaultValues: Boolean
) {

    try {
// first, build the filename and get the output folder (if possible)
        // later, run a very very very large file checking logic
        var filenameTmp = FilenameUtils.createFilename(activity, currentInfo.name) + "."
        val mimeTmp: String
        val selectedMediaType: String = activity.getString(R.string.last_download_type_audio_key)
        val mainStorage: StoredDirectoryHelper? = mainStorageAudio
        val format: MediaFormat = MediaFormat.M4A
        if (format == MediaFormat.WEBMA_OPUS) {
            mimeTmp = "audio/ogg"
            filenameTmp += "opus"
        } else {
            mimeTmp = format.mimeType
            filenameTmp += format.suffix
        }

        if (!askForSavePath &&
            (
                    mainStorage == null || mainStorage.isDirect == NewPipeSettings.useStorageAccessFramework(
                        activity
                    ) || mainStorage.isInvalidSafStorage
                    )
        ) {
            // Pick new download folder if one of:
            // - Download folder is not set
            // - Download folder uses SAF while SAF is disabled
            // - Download folder doesn't use SAF while SAF is enabled
            // - Download folder uses SAF but the user manually revoked access to it
            throw java.lang.Exception()
        }

        // check for existing file with the same name

        // check for existing file with the same name
        checkSelectedDownload(
            activity,
            mainStorage,
            mainStorage!!.findFile(filenameTmp),
            filenameTmp,
            mimeTmp,
            downloadManager,
            currentInfo
        )
    } catch (e: Exception) {
        openDialog(
            activity,
            currentInfo,
            sortedVideoStreams,
            selectedVideoStreamIndex,
            defaultValues,
        )
    }
}

private fun checkSelectedDownload(
    activity: AppCompatActivity,
    mainStorage: StoredDirectoryHelper?,
    targetFile: Uri?,
    filename: String,
    mime: String,
    downloadManager: DownloadManager,
    currentInfo: StreamInfo
) {
    var storage: StoredFileHelper?
    storage =
        if (mainStorage == null) {
            // using SAF on older android version
            StoredFileHelper(activity, null, targetFile!!, "")
        } else if (targetFile == null) {
            // the file does not exist, but it is probably used in a pending download
            StoredFileHelper(
                mainStorage.uri, filename, mime,
                mainStorage.tag
            )
        } else {
            // the target filename is already use, attempt to use it
            StoredFileHelper(
                activity, mainStorage.uri, targetFile,
                mainStorage.tag
            )
        }

    // get state of potential mission referring to the same file
    val state: MissionState = downloadManager.checkForExistingMission(storage)

    when (state) {
        MissionState.None -> {
            if (mainStorage == null) {
                // This part is called if:
                // * using SAF on older android version
                // * save path not defined
                // * if the file exists overwrite it, is not necessary ask
                if (!storage.existsAsFile() && !storage.create()) {
                    throw java.lang.Exception()
                }
                continueSelectedDownload(storage, activity, currentInfo)
                return
            } else if (targetFile == null) {
                // This part is called if:
                // * the filename is not used in a pending/finished download
                // * the file does not exists, create
                if (!mainStorage.mkdirs()) {
                    throw java.lang.Exception()
                }
                storage = mainStorage.createFile(filename, mime)
                if (storage == null || !storage.canWrite()) {
                    throw java.lang.Exception()
                }
                continueSelectedDownload(storage, activity, currentInfo)
                return
            }
        }
        else -> return // unreachable
    }

    val finalStorage: StoredFileHelper = storage
    if (mainStorage == null) {
        // This part is called if:
        // * using SAF on older android version
        // * save path not defined
        when (state) {
            MissionState.Pending, MissionState.Finished -> throw java.lang.Exception()
        }
        return
    }

    val storageNew: StoredFileHelper?

    when (state) {
        MissionState.Finished, MissionState.Pending -> {
            downloadManager.forgetMission(finalStorage)
            storageNew = if (targetFile == null) {
                mainStorage.createFile(filename, mime)
            } else {
                // try take (or steal) the file
                StoredFileHelper(
                    activity, mainStorage.uri,
                    targetFile, mainStorage.tag
                )
            }
            if (storageNew != null && storageNew.canWrite()) {
                continueSelectedDownload(storageNew, activity, currentInfo)
            } else {
            }
        }
        MissionState.None -> {
            storageNew = if (targetFile == null) {
                mainStorage.createFile(filename, mime)
            } else {
                StoredFileHelper(
                    activity, mainStorage.uri,
                    targetFile, mainStorage.tag
                )
            }
            if (storageNew != null && storageNew.canWrite()) {
                continueSelectedDownload(storageNew, activity, currentInfo)
            } else {
                throw java.lang.Exception()
            }
        }
        MissionState.PendingRunning -> {
            storageNew = mainStorage.createUniqueFile(filename, mime)
            storageNew?.let { continueSelectedDownload(it, activity, currentInfo) }
                ?: throw java.lang.Exception()
        }
    }
}

private fun continueSelectedDownload(
    storage: StoredFileHelper,
    activity: AppCompatActivity,
    currentInfo: StreamInfo,
) {

    val selectedStream: Stream
    val kind: Char = 'a'

    selectedStream = currentInfo.audioStreams.first { it.getFormat() == MediaFormat.M4A }

    val psName = if (selectedStream.getFormat() == MediaFormat.M4A) {
        Postprocessing.ALGORITHM_M4A_NO_DASH
    } else if (selectedStream.getFormat() == MediaFormat.WEBMA_OPUS) {
        Postprocessing.ALGORITHM_OGG_FROM_WEBM_DEMUXER
    } else {
        null
    }

    val urls: Array<String> = arrayOf(
        selectedStream.getUrl()
    )
    val recoveryInfo: Array<MissionRecoveryInfo> = arrayOf(
        MissionRecoveryInfo(selectedStream)
    )

    val threads = 10
    val nearLength: Long = 0
    val psArgs: Array<String>? = null

    DownloadManagerService.startMission(
        activity.baseContext, urls, storage, kind, threads,
        currentInfo.url, psName, psArgs, nearLength, recoveryInfo
    )

    val data = "" + currentInfo.id + " -> " + storage.uri
    Log.d("GERRR", "continueSelectedDownload: $data")

    val recordManager = DownloadRecordManager(activity)

    val disposables = CompositeDisposable()

    disposables.add(recordManager.insert(currentInfo.id, storage.uri.toString(), currentInfo.url).onErrorComplete()
        .subscribe(
            { ignored: Long? ->
                /* successful */
            },
            { error: Throwable? ->
                Log.e(
                    "Download Helper",
                    "Register view failure: ",
                    error
                )
            }
        ))


    Toast.makeText(
        activity, activity.getString(R.string.download_has_started),
        Toast.LENGTH_SHORT
    ).show()

}

fun verifyStorage(storage: StoredFileHelper) {
    if (!storage.canWrite()) {
        throw java.lang.Exception()
    }

    // check if the selected file has to be overwritten, by simply checking its length

    // check if the selected file has to be overwritten, by simply checking its length
    try {
        if (storage.length() > 0) {
            storage.truncate()
        }
    } catch (e: IOException) {
        throw java.lang.Exception("failed to truncate the file: " + storage.uri.toString())
    }
}
