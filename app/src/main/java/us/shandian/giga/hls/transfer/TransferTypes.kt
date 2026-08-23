package us.shandian.giga.hls.transfer

import java.io.IOException

data class TransferProgress(
    val bytesWritten: Long,
    val expectedBytes: Long,
    val resourceUrl: String,
)

data class TransferResult(
    val bytesWritten: Long,
    val expectedBytes: Long,
    val statusCode: Int,
    val finalUrl: String,
)

interface TransferProgressListener {
    fun onProgress(progress: TransferProgress)
}

interface TransferController {
    fun isRunning(): Boolean
}

class TransferInterruptedException : IOException("Transfer interrupted")

class TransferHttpException(
    val statusCode: Int,
) : IOException("HTTP $statusCode")
