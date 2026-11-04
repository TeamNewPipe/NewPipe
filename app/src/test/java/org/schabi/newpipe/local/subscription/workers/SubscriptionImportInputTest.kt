package org.schabi.newpipe.local.subscription.workers

import androidx.work.Data
import androidx.work.workDataOf
import org.junit.Assert.assertEquals
import org.junit.Test
import org.schabi.newpipe.local.subscription.workers.SubscriptionImportInput.ChannelUrlMode
import org.schabi.newpipe.local.subscription.workers.SubscriptionImportInput.InputStreamMode
import org.schabi.newpipe.local.subscription.workers.SubscriptionImportInput.PreviousExportMode

class SubscriptionImportInputTest {

    @Test
    fun `Channel URL mode round-trips through Work Data`() {
        val input = ChannelUrlMode(serviceId = 0, url = "https://www.youtube.com")
        assertEquals(input, SubscriptionImportInput.fromData(input.toData()))
    }

    @Test
    fun `Input stream mode round-trips through Work Data`() {
        val input = InputStreamMode(
            serviceId = 0,
            url = "content://com.android.providers.downloads.documents/document/raw%3A%2Fstorage%2Fsubscriptions.csv"
        )
        assertEquals(input, SubscriptionImportInput.fromData(input.toData()))
    }

    @Test
    fun `Previous export mode round-trips through Work Data`() {
        val input = PreviousExportMode(url = "content://media/external/file/newpipe_subscriptions.json")
        assertEquals(input, SubscriptionImportInput.fromData(input.toData()))
    }

    @Test
    fun `Missing mode falls back to previous export mode`() {
        val data = Data.Builder()
            .putString("url", "content://media/external/file/newpipe_subscriptions.json")
            .build()

        assertEquals(
            PreviousExportMode("content://media/external/file/newpipe_subscriptions.json"),
            SubscriptionImportInput.fromData(data)
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `Unknown mode throws`() {
        SubscriptionImportInput.fromData(workDataOf("mode" to 99, "url" to "https://www.youtube.com"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `Channel URL mode without service id throws`() {
        SubscriptionImportInput.fromData(workDataOf("mode" to 0, "url" to "https://www.youtube.com"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `Input stream mode without service id throws`() {
        SubscriptionImportInput.fromData(
            workDataOf("mode" to 1, "url" to "content://media/external/file/subscriptions.csv")
        )
    }
}
