package com.kt.apps.video.data.api.download

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.os.ResultReceiver
import android.util.Log
import com.kt.apps.video.utils.HashUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.channels.Channels
import kotlin.coroutines.CoroutineContext

/**
 * Created by thangnv8 on 2022/05/28
 *
 * A service that response to download apk file
 * */
class DownloadService : Service(), CoroutineScope {
    /**
     * Hash set to avoid duplicate download
     * */
    private val downloadingSet = hashSetOf<String>()
    private val listeners = hashMapOf<String, ResultReceiver>()
    private val runningJobs = hashMapOf<String, Job>()
    private val runningCall = hashMapOf<String, Call>()

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            when (it.action) {
                START_DOWNLOAD_ACTION -> {
                    handleStartDownload(intent)
                }

                REMOVE_LISTENER_ACTION -> {
                    handleRemoveListener(intent)
                }

                CANCEL_DOWNLOAD_ACTION -> {
                    handleCancelDownload(intent)
                }

                else -> {}
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun handleCancelDownload(intent: Intent) {
        val downloadKey = intent.getStringExtra(DOWNLOAD_KEY)
        downloadingSet.remove(downloadKey)
        handleRemoveListener(intent)
        runningJobs[downloadKey]?.cancel()
        runningCall[downloadKey]?.cancel()
    }

    private fun handleRemoveListener(intent: Intent) {
        val downloadKey = intent.getStringExtra(DOWNLOAD_KEY)
        listeners.remove(downloadKey)
    }

    /**
     * @param intent
     * @throws IllegalArgumentException If url, filePath download is null or empty
     * */
    private fun handleStartDownload(intent: Intent) {
        val url = intent.getStringExtra(DOWNLOAD_URL)
        val filePath = intent.getStringExtra(FILE_PATH)
        val md5 = intent.getStringExtra(MD5)
        val downloadKey = intent.getStringExtra(DOWNLOAD_KEY)
        val receiver = intent.getParcelableExtra(LISTENER) as ResultReceiver?

        if (url.isNullOrEmpty()) {
            throw IllegalArgumentException("Url download is empty or null")
        }
        if (filePath.isNullOrEmpty()) {
            throw IllegalArgumentException("File is empty or null")
        }
        if (downloadKey.isNullOrEmpty()) {
            throw IllegalArgumentException("Download key is empty or null")
        }
        receiver?.let {
            listeners[downloadKey] = it
        }
        if (downloadingSet.contains(downloadKey)) {
            listeners[downloadKey]?.send(DOWNLOAD_START, null)
            Log.d("Download", "Download is running")
            return
        } else {
            Log.d("Download", "Download is starting")
            downloadingSet.add(downloadKey)
        }
        val outputFile = File(filePath)
        if (outputFile.exists() && HashUtils.checkMD5(md5, outputFile)) {
            val bundle = Bundle().apply {
                putString(FILE_PATH, filePath)
            }
            receiver?.send(DOWNLOAD_SUCCESS, bundle)
            return
        }

        launch {
            var result: Result<Boolean>? = null
            try {
                listeners[downloadKey]?.send(DOWNLOAD_START, null)
                Log.d("Download", "Download is starting here 1")
                result = downloadFile(url, outputFile, md5, downloadKey)
            } finally {
                Log.d("Download", "Download is starting here finally")
                if (!this.coroutineContext.job.isCancelled) {
                    Log.d("Download", "Download is starting here 3")
                    if (result?.isSuccess == true) {
                        val bundle = Bundle().apply {
                            putString(FILE_PATH, filePath)
                        }
                        val listener = listeners[downloadKey]
                        Log.d("Download", "Download is starting here 4: ${listener != null}")
                        listener?.send(DOWNLOAD_SUCCESS, bundle)
                    } else if (result?.isFailure == true) {
                        val bundle = Bundle().apply {
                            putString(ERROR_MESSAGE, result.exceptionOrNull()?.message)
                        }
                        val listener = listeners[downloadKey]
                        Log.d("Download", "Download is starting here 5: ${listener != null}")
                        listener?.send(DOWNLOAD_FAIL, bundle)
                    }
                } else {
                    outputFile.delete()
                }

                Log.d("Download", "Download is starting here 6")
                downloadingSet.remove(downloadKey)
                if (downloadingSet.isEmpty()) {
                    stopSelf()
                }
            }
        }.also {
            runningJobs[downloadKey] = it
        }
    }

    /**
     * Download file with given url and file path
     * @param url to load file, must is direct download link
     * @param file to save file
     * @param name file wish to save
     * @param md5 checksum
     * @param retryCount retry if download fail, default is 1
     * @return true if success, otherwise false
     * */
    private fun downloadFile(
        url: String,
        file: File,
        md5: String?,
        downloadKey: String,
        retryCount: Int = 1
    ): Result<Boolean> {
        repeat(retryCount) { time ->
            runCatching {
                val client = OkHttpClient()
                val request = Request.Builder().url(url)
                    .addHeader("Content-Type", "application/json").build()
                val call = client.newCall(request)
                runningCall[downloadKey] = call
                val response = call.execute()
                val outputFile = saveFile(response.body!!, file, downloadKey)

                if (!isValidFile(outputFile, md5)) {
                    outputFile.deleteOnExit()
                    throw Exception("Checksum not match")
                } else {
                    return Result.success(true)
                }
            }.onFailure {
                Log.e("Download", "Download fail", it)
                if (time == retryCount - 1) {
                    return Result.failure(it)
                }
            }
        }

        return Result.failure(Exception("Download fail after retry $retryCount"))
    }

    /**
     * Save file with given input stream and file path
     * @param inputStream
     * @param output
     * @return File if success, otherwise null
     * */
    @Throws(IOException::class)
    private fun saveFile(responseBody: ResponseBody, output: File, downloadKey: String): File {
        var fileSize = responseBody.contentLength()
        if (fileSize == -1L) {
            fileSize = DEFAULT_FILE_SIZE
        }
        val inputStream = responseBody.byteStream()
        output.parentFile?.mkdirs()
        output.delete()
        output.createNewFile()
        inputStream.use {
            Channels.newChannel(inputStream).use { byteChanel ->
                FileOutputStream(output).use { outputStream ->
                    Log.d("Download", "Download is starting here ${System.currentTimeMillis()}")
                    val blockSize: Long = (8 * 1024).toLong()
                    var position: Long = 0
                    var loaded: Long
                    val destChannel = outputStream.channel
                    while (destChannel.transferFrom(byteChanel, position, blockSize)
                        .also { loaded = it } > 0
                    ) {
                        position += loaded

                        val progress = ((position.toDouble() / fileSize) * 100).toInt()
                        val bundle = Bundle().apply {
                            putInt(PROGRESS, progress)
                        }
                        listeners[downloadKey]?.send(UPDATE_PROGRESS, bundle)
                    }
                }
            }
        }
        return output
    }

    /**
     * Check if downloaded file is valid with checksum
     * @param file wish to check
     * @param md5 checksum
     * @return true if valid file, otherwise false
     * */
    private fun isValidFile(file: File, md5: String?): Boolean {
        if (md5 == null) return true
        return HashUtils.checkMD5(md5, file)
    }

    companion object {

        /**
         * Action download
         * */
        const val START_DOWNLOAD_ACTION = "action_start_download"
        const val CANCEL_DOWNLOAD_ACTION = "action_cancel_download"
        const val REMOVE_LISTENER_ACTION = "action_remove_listener"

        const val LISTENER = "listener"

        /**
         * Url to download file
         * */
        const val DOWNLOAD_URL = "file_url"
        const val DOWNLOAD_KEY = "download_key"

        /**
         * Path to save file after download
         * */
        const val FILE_PATH = "file_path"

        /**
         * Checksum for download file
         * */
        const val MD5 = "md5"

        /**
         * Default file name if not pass file name and can't get file name from download url
         * */

        const val DOWNLOAD_START = 0
        const val UPDATE_PROGRESS = 1
        const val DOWNLOAD_SUCCESS = 2
        const val DOWNLOAD_FAIL = 3

        const val ERROR_DOWNLOAD = -301

        const val PROGRESS = "progress"
        const val ERROR_MESSAGE = "error_message"
        const val ERROR_CODE = "error_code"

        const val DEFAULT_FILE_SIZE = 15 * 1024 * 1024L
    }

    override val coroutineContext: CoroutineContext by lazy {
        Dispatchers.IO + SupervisorJob()
    }
}
