package org.schabi.newpipe.local.subscription.services

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.schabi.newpipe.extractor.subscription.SubscriptionExtractor
import org.schabi.newpipe.local.subscription.workers.ImportExportJsonHelper
import org.schabi.newpipe.local.subscription.workers.SubscriptionItem

/**
 * @see ImportExportJsonHelper
 */
class ImportExportJsonHelperTest {
    @Test
    @Throws(Exception::class)
    fun testEmptySource() {
        val emptySource =
            "{\"app_version\":\"0.11.6\",\"app_version_int\": 47,\"subscriptions\":[]}"

        val items = ImportExportJsonHelper.readFrom(
            ByteArrayInputStream(emptySource.toByteArray(StandardCharsets.UTF_8))
        )
        assertTrue(items.isEmpty())
    }

    @Test
    fun testInvalidSource() {
        val invalidList = listOf("{}", "", null, "gibberish")

        for (invalidContent in invalidList) {
            try {
                if (invalidContent != null) {
                    val bytes = invalidContent.toByteArray(StandardCharsets.UTF_8)
                    ImportExportJsonHelper.readFrom(ByteArrayInputStream(bytes))
                } else {
                    ImportExportJsonHelper.readFrom(null)
                }

                fail("didn't throw exception")
            } catch (e: Exception) {
                val isExpectedException = e is SubscriptionExtractor.InvalidSourceException
                assertTrue(
                    "\"" + e.javaClass.simpleName + "\" is not the expected exception",
                    isExpectedException
                )
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
        if (itemsFromFile != itemsSecondRead) {
            fail("The list of items were different from each other")
        }
    }

    @Throws(Exception::class)
    private fun readFromFile(): List<SubscriptionItem> {
        val inputStream = javaClass.classLoader!!
            .getResourceAsStream("import_export_test.json")
        val itemsFromFile = ImportExportJsonHelper.readFrom(inputStream)

        if (itemsFromFile.isEmpty()) {
            fail("ImportExportJsonHelper.readFrom(input) returned a null or empty list")
        }

        return itemsFromFile
    }

    private fun testWriteTo(itemsFromFile: List<SubscriptionItem>): String {
        val out = ByteArrayOutputStream()
        ImportExportJsonHelper.writeTo(itemsFromFile, out)
        val jsonOut = out.toString(StandardCharsets.UTF_8)

        if (jsonOut.isEmpty()) {
            fail("JSON returned by writeTo was empty")
        }

        return jsonOut
    }

    @Throws(Exception::class)
    private fun readFromWriteTo(jsonOut: String): List<SubscriptionItem> {
        val inputStream = ByteArrayInputStream(jsonOut.toByteArray(StandardCharsets.UTF_8))
        val secondReadItems = ImportExportJsonHelper.readFrom(inputStream)

        if (secondReadItems.isEmpty()) {
            fail("second call to readFrom returned an empty list")
        }

        return secondReadItems
    }
}
