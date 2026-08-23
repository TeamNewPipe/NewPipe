package us.shandian.giga.hls.transfer

import java.io.IOException
import kotlin.math.min

internal class TransferRetryPolicy(
    private val maxRetries: Int,
) {
    @Throws(IOException::class)
    fun <T> withRetries(operation: () -> T): T {
        var attempt = 0
        while (true) {
            try {
                return operation()
            } catch (error: IOException) {
                if (!canRetry(error) || attempt >= maxRetries) {
                    throw error
                }
                attempt++
                sleepBeforeRetry(attempt)
            }
        }
    }

    private fun canRetry(error: IOException): Boolean {
        return when (error) {
            is TransferInterruptedException -> false
            is TransferHttpException -> error.statusCode == 408 || error.statusCode == 429 ||
                error.statusCode >= 500
            else -> true
        }
    }

    @Throws(TransferInterruptedException::class)
    private fun sleepBeforeRetry(attempt: Int) {
        try {
            Thread.sleep(min(attempt * 500L, 2_000L))
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            throw TransferInterruptedException()
        }
    }
}
