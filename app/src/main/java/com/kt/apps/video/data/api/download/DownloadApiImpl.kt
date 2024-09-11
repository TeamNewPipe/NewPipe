package com.kt.apps.video.data.api.download

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.ResultReceiver
import android.util.Log
import com.kt.apps.video.utils.HashUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import java.io.File
import kotlin.coroutines.CoroutineContext

class DownloadApiImpl(val context: Context) : CoroutineScope, DownloadApi {
    override val coroutineContext: CoroutineContext by lazy { Dispatchers.Default }
    override fun downloadNewVersion(url: String, md5: String) = callbackFlow {
        Log.d("Download", "downloadNewVersion:$url $md5")
        val apkFile = newVersionPath(md5, context)
        if (apkFile.exists() && HashUtils.checkMD5(md5, apkFile)) {
            send(DownloadApi.Success(apkFile))
            awaitClose()
            return@callbackFlow
        }
        val handlerThread = HandlerThread("Download")
        handlerThread.start()
        val handler = Handler(handlerThread.looper)
        val intent = Intent(context, DownloadService::class.java).apply {
            val downloadKey = url + apkFile.absolutePath
            action = DownloadService.START_DOWNLOAD_ACTION
            putExtra(DownloadService.DOWNLOAD_URL, url)
            putExtra(DownloadService.DOWNLOAD_KEY, downloadKey)
            putExtra(DownloadService.FILE_PATH, apkFile.absolutePath)
            putExtra(DownloadService.MD5, md5)
            putExtra(
                DownloadService.LISTENER,
                object : ResultReceiver(handler) {
                    override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                        super.onReceiveResult(resultCode, resultData)
                        Log.d("Download", "onReceiveResult:$resultCode $resultData")
                        launch {
                            when (resultCode) {
                                DownloadService.DOWNLOAD_START -> {
                                    send(DownloadApi.Progress(0))
                                }

                                DownloadService.UPDATE_PROGRESS -> {
                                    val progress = resultData?.getInt(DownloadService.PROGRESS) ?: 0
                                    send(DownloadApi.Progress(progress))
                                }

                                DownloadService.DOWNLOAD_SUCCESS -> {
                                    val filePath = resultData?.getString(DownloadService.FILE_PATH)
                                    if (filePath != null) {
                                        send(DownloadApi.Success(File(filePath)))
                                    }
                                    close()
                                }

                                DownloadService.DOWNLOAD_FAIL -> {
                                    val errorMessage = resultData?.getString(DownloadService.ERROR_MESSAGE) ?: ""
                                    send(DownloadApi.Fail(errorMessage))
                                    close()
                                }
                            }
                        }
                    }
                }
            )
        }

        context.startService(intent)
        awaitClose {
            Log.d("Download", "awaitClose")
            runCatching {
                handlerThread.quitSafely()
            }
        }
    }

    private fun newVersionPath(md5: String, context: Context): File {
        val apkFolder = File(context.filesDir, "NewVersion")
        if (!apkFolder.exists()) {
            apkFolder.mkdirs()
        }
        val apkFile = File(apkFolder, "$md5.apk")
        apkFolder.walkTopDown().forEach {
            if (it != apkFile) {
                Log.d("Download", "delete:${it.absolutePath}")
                it.delete()
            }
        }
        return apkFile
    }
}
