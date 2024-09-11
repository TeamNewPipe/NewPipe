package com.kt.apps.video.data.api.download

import kotlinx.coroutines.flow.Flow
import java.io.File

/**
 * Download file by link and md5 then report result
 */
interface DownloadApi {
    sealed interface Result
    class Fail(val message: String) : Result
    class Success(val file: File) : Result
    class Progress(val progress: Int) : Result

    fun downloadNewVersion(url: String, md5: String): Flow<Result>
}
