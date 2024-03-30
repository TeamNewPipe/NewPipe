package org.schabi.newpipe.local.subscription.services

import org.junit.Assert
import org.junit.Test
import org.schabi.newpipe.extractor.subscription.SubscriptionExtractor.InvalidSourceException
import org.schabi.newpipe.extractor.subscription.SubscriptionItem
import org.schabi.newpipe.local.subscription.services.ImportExportJsonHelper.readFrom
import org.schabi.newpipe.local.subscription.services.ImportExportJsonHelper.writeTo
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

/**
 * @see ImportExportJsonHelper
 */
class ImportExportJsonHelperTest {
    @Test
    @Throws(Exception::class)
    fun testEmptySource() {
        val emptySource = "{\"app_version\":\"0.11.6\",\"app_version_int\": 47,\"subscriptions\":[]}"
        val items = readFrom(
                ByteArrayInputStream(emptySource.toByteArray(StandardCharsets.UTF_8)), null)
        Assert.assertTrue(items.isEmpty())
    }

    @Test
    fun testInvalidSource() {
        val invalidList: List<String?> = mutableListOf(
                "{}",
                "",
                null,
                "gibberish")
        for (invalidContent in invalidList) {
            try {
                if (invalidContent != null) {
                    val bytes = invalidContent.toByteArray(StandardCharsets.UTF_8)
                    readFrom(ByteArrayInputStream(bytes), null)
                } else {
                    readFrom(null, null)
                }
                Assert.fail("didn't throw exception")
            } catch (e: Exception) {
                val isExpectedException = e is InvalidSourceException
                Assert.assertTrue("\"" + e.javaClass.getSimpleName()
                        + "\" is not the expected exception", isExpectedException)
            }
        }
    }

    @Test
    @Throws(Exception::class)
    fun ultimateTest() {
        // Read from file
        val itemsFromFile = readFromFile()

        // Test writing to an output
        val jsonOut = testWriteTo(itemsFromFile)

        // Read again
        val itemsSecondRead = readFromWriteTo(jsonOut)

        // Check if both lists have the exact same items
        if (itemsFromFile.size != itemsSecondRead.size) {
            Assert.fail("The list of items were different from each other")
        }
        for (i in itemsFromFile.indices) {
            val item1 = itemsFromFile[i]
            val item2 = itemsSecondRead[i]
            val equals = item1!!.serviceId == item2.serviceId && item1.url == item2.url && item1.name == item2.name
            if (!equals) {
                Assert.fail("The list of items were different from each other")
            }
        }
    }

    @Throws(Exception::class)
    private fun readFromFile(): List<SubscriptionItem?> {
        val inputStream = javaClass.getClassLoader().getResourceAsStream(
                "import_export_test.json")
        val itemsFromFile: List<SubscriptionItem?> = readFrom(
                inputStream, null)
        if (itemsFromFile.isEmpty()) {
            Assert.fail("ImportExportJsonHelper.readFrom(input) returned a null or empty list")
        }
        return itemsFromFile
    }

    @Throws(Exception::class)
    private fun testWriteTo(itemsFromFile: List<SubscriptionItem?>): String {
        val out = ByteArrayOutputStream()
        writeTo(itemsFromFile, out, null)
        val jsonOut = out.toString("UTF-8")
        if (jsonOut.isEmpty()) {
            Assert.fail("JSON returned by writeTo was empty")
        }
        return jsonOut
    }

    @Throws(Exception::class)
    private fun readFromWriteTo(jsonOut: String): List<SubscriptionItem> {
        val inputStream = ByteArrayInputStream(
                jsonOut.toByteArray(StandardCharsets.UTF_8))
        val secondReadItems = readFrom(
                inputStream, null)
        if (secondReadItems.isEmpty()) {
            Assert.fail("second call to readFrom returned an empty list")
        }
        return secondReadItems
    }
}
