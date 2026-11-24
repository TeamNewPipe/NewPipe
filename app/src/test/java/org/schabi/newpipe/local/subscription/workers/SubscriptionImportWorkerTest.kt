package org.schabi.newpipe.local.subscription.workers

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.Mockito.withSettings
import org.mockito.junit.MockitoJUnitRunner
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.subscription.SubscriptionItem as ExtractorSubscriptionItem
import org.schabi.newpipe.streams.io.StoredFileHelper

@RunWith(MockitoJUnitRunner::class)
class SubscriptionImportWorkerTest {

    @Test
    fun `Octet-stream csv import must resolve to csv`() {
        assertResolvedContentType("csv", StoredFileHelper.DEFAULT_MIME, "subscriptions.csv")
    }

    @Test
    fun `Octet-stream csv import must parse through YouTube extractor`() {
        val contentType = resolveContentType(StoredFileHelper.DEFAULT_MIME, "subscriptions.csv")
        val items = ServiceList.YouTube.subscriptionExtractor.fromInputStream(
            ByteArrayInputStream(SUBSCRIPTIONS_CSV.toByteArray(StandardCharsets.UTF_8)),
            contentType
        )

        assertSampleSubscriptions(items)
    }

    @Test
    fun `Missing content type csv import must resolve to csv`() {
        assertResolvedContentType("csv", null, "subscriptions.csv")
    }

    @Test
    fun `Octet-stream takeout archive must resolve to zip`() {
        assertResolvedContentType("zip", StoredFileHelper.DEFAULT_MIME, "takeout.zip")
    }

    @Test
    fun `Octet-stream takeout archive must parse through YouTube extractor`() {
        val contentType = resolveContentType(StoredFileHelper.DEFAULT_MIME, "takeout.zip")
        val items = ServiceList.YouTube.subscriptionExtractor.fromInputStream(
            ByteArrayInputStream(createTakeoutZip()),
            contentType
        )

        assertSampleSubscriptions(items)
    }

    @Test
    fun `NewPipe JSON export with 84 subscriptions has no artificial limit`() {
        val export = buildString {
            append("""{"app_version":"0.28.0","app_version_int":1020000,"subscriptions":[""")
            repeat(84) { index ->
                if (index > 0) {
                    append(',')
                }
                append(
                    """{"service_id":0,"url":"https://www.youtube.com/channel/test$index","name":"Channel $index"}"""
                )
            }
            append("]}")
        }

        val subscriptions = ImportExportJsonHelper.readFrom(
            ByteArrayInputStream(export.toByteArray(StandardCharsets.UTF_8))
        )

        assertEquals(84, subscriptions.size)
        assertEquals("Channel 83", subscriptions.last().name)
    }

    @Test
    fun `large imports are converted directly to subscription entities`() {
        val subscriptions = List(350) { index ->
            SubscriptionItem(
                ServiceList.YouTube.serviceId,
                "https://www.youtube.com/channel/test$index",
                "Channel $index"
            )
        }

        val entities = SubscriptionImportWorker.prepareSubscriptionEntities(
            subscriptions,
            setOf(ServiceList.YouTube.serviceId)
        )

        assertEquals(350, entities.size)
        assertEquals("Channel 349", entities.last().name)
        assertEquals(SubscriptionEntity.YOUTUBE_SERVICE_ID, entities.last().serviceId)
    }

    @Test
    fun `unsupported services and empty urls are skipped without network resolution`() {
        val validUrl = "https://www.youtube.com/channel/valid"
        val entities = SubscriptionImportWorker.prepareSubscriptionEntities(
            listOf(
                SubscriptionItem(ServiceList.YouTube.serviceId, validUrl, ""),
                SubscriptionItem(Int.MAX_VALUE, "https://example.com/channel", "Unknown"),
                SubscriptionItem(ServiceList.YouTube.serviceId, "   ", "Missing")
            ),
            setOf(ServiceList.YouTube.serviceId)
        )

        assertEquals(1, entities.size)
        assertEquals(validUrl, entities.single().url)
        assertEquals(validUrl, entities.single().name)
    }

    @Test
    fun `plugin subscriptions survive export and import before the plugin is installed`() {
        val pluginId = 1_234_567
        val original = listOf(SubscriptionItem(pluginId, "https://example.com/channel", "Course"))
        val output = java.io.ByteArrayOutputStream()
        ImportExportJsonHelper.writeTo(original, output)
        val imported = ImportExportJsonHelper.readFrom(output.toByteArray().inputStream())

        val entities = SubscriptionImportWorker.prepareSubscriptionEntities(imported, setOf(0, 10))

        assertEquals(1, entities.size)
        assertEquals(pluginId, entities.single().serviceId)
        assertEquals(original.single().url, entities.single().url)
        assertEquals("Course", entities.single().name)
    }

    @Test
    fun `Provider content type must be preserved`() {
        assertResolvedContentType("application/json", "application/json")
    }

    @Test
    fun `Unknown octet-stream import must stay octet-stream`() {
        assertResolvedContentType(StoredFileHelper.DEFAULT_MIME, StoredFileHelper.DEFAULT_MIME, "subscriptions")
    }

    private fun assertResolvedContentType(
        expectedContentType: String,
        contentType: String?,
        fileName: String? = null
    ) {
        assertEquals(expectedContentType, resolveContentType(contentType, fileName))
    }

    private fun resolveContentType(contentType: String?, fileName: String? = null): String {
        val fileHelper = Mockito.mock(StoredFileHelper::class.java, withSettings().stubOnly())
        `when`(fileHelper.type).thenReturn(contentType)
        if (fileName != null) {
            `when`(fileHelper.name).thenReturn(fileName)
        }

        return SubscriptionImportWorker.getInputStreamContentType(fileHelper)
    }

    private fun createTakeoutZip(): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("YouTube and YouTube Music/subscriptions/subscriptions.csv"))
            zip.write(SUBSCRIPTIONS_CSV.toByteArray(StandardCharsets.UTF_8))
            zip.closeEntry()
        }
        return output.toByteArray()
    }

    private fun assertSampleSubscriptions(items: List<ExtractorSubscriptionItem>) {
        assertEquals(SAMPLE_SUBSCRIPTIONS.size, items.size)
        SAMPLE_SUBSCRIPTIONS.forEachIndexed { index, subscription ->
            assertEquals(ServiceList.YouTube.serviceId, items[index].serviceId)
            assertEquals(subscription.url.replace("http://", "https://"), items[index].url)
            assertEquals(subscription.name, items[index].name)
        }
    }

    private data class SampleSubscription(val id: String, val url: String, val name: String)

    companion object {
        private val SAMPLE_SUBSCRIPTIONS = listOf(
            SampleSubscription(
                "UCCj956IF62FbT7Gouszaj9w",
                "http://www.youtube.com/channel/UCCj956IF62FbT7Gouszaj9w",
                "BBC"
            ),
            SampleSubscription(
                "UCXuqSBlHAE6Xw-yeJA0Tunw",
                "http://www.youtube.com/channel/UCXuqSBlHAE6Xw-yeJA0Tunw",
                "Linus Tech Tips"
            ),
            SampleSubscription(
                "UCBJycsmduvYEL83R_U4JriQ",
                "http://www.youtube.com/channel/UCBJycsmduvYEL83R_U4JriQ",
                "Marques Brownlee"
            )
        )

        private val SUBSCRIPTIONS_CSV = buildString {
            appendLine("Channel Id,Channel Url,Channel Title")
            SAMPLE_SUBSCRIPTIONS.forEach { subscription ->
                appendLine("${subscription.id},${subscription.url},${subscription.name}")
            }
        }.trimEnd()
    }
}
