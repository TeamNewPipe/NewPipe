package org.schabi.newpipe.local.subscription.services;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;
import org.schabi.newpipe.extractor.subscription.SubscriptionExtractor;
import org.schabi.newpipe.local.subscription.workers.SubscriptionItem;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * @see ImportExportJsonHelper
 */
public class ImportExportJsonHelperTest {
    @Test
    public void testEmptySource() throws Exception {
        final String emptySource =
                "{\"app_version\":\"0.11.6\",\"app_version_int\": 47,\"subscriptions\":[]}";

        final List<SubscriptionItem> items = ImportExportJsonHelper.readFrom(
                new ByteArrayInputStream(emptySource.getBytes(StandardCharsets.UTF_8)));
        assertTrue(items.isEmpty());
    }

    @Test
    public void testInvalidSource() {
        final List<String> invalidList = Arrays.asList(
                "{}",
                "",
                null,
                "gibberish");

        for (final String invalidContent : invalidList) {
            try {
                if (invalidContent != null) {
                    final byte[] bytes = invalidContent.getBytes(StandardCharsets.UTF_8);
                    ImportExportJsonHelper.readFrom(new ByteArrayInputStream(bytes));
                } else {
                    ImportExportJsonHelper.readFrom(null);
                }

                fail("didn't throw exception");
            } catch (final Exception e) {
                final boolean isExpectedException =
                        e instanceof SubscriptionExtractor.InvalidSourceException;
                assertTrue("\"" + e.getClass().getSimpleName()
                        + "\" is not the expected exception", isExpectedException);
            }
        }
    }

    @Test
    public void ultimateTest() throws Exception {
        // Read from file
        final List<SubscriptionItem> itemsFromFile = readFromFile();

        // Test writing to an output
        final String jsonOut = testWriteTo(itemsFromFile);

        // Read again
        final List<SubscriptionItem> itemsSecondRead = readFromWriteTo(jsonOut);

        // Check if both lists have the exact same items
        if (itemsFromFile.size() != itemsSecondRead.size()) {
            fail("The list of items were different from each other");
        }

        for (int i = 0; i < itemsFromFile.size(); i++) {
            final SubscriptionItem item1 = itemsFromFile.get(i);
            final SubscriptionItem item2 = itemsSecondRead.get(i);

            final boolean equals = item1.getServiceId() == item2.getServiceId()
                    && item1.getUrl().equals(item2.getUrl())
                    && item1.getName().equals(item2.getName());

            if (!equals) {
                fail("The list of items were different from each other");
            }
        }
    }

    private List<SubscriptionItem> readFromFile() throws Exception {
        final InputStream inputStream = getClass().getClassLoader().getResourceAsStream(
                "import_export_test.json");
        final List<SubscriptionItem> itemsFromFile = ImportExportJsonHelper.readFrom(
                inputStream);

        if (itemsFromFile.isEmpty()) {
            fail("ImportExportJsonHelper.readFrom(input) returned a null or empty list");
        }

        return itemsFromFile;
    }

    private String testWriteTo(final List<SubscriptionItem> itemsFromFile) {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImportExportJsonHelper.writeTo(itemsFromFile, out);
        final String jsonOut = out.toString(StandardCharsets.UTF_8);

        if (jsonOut.isEmpty()) {
            fail("JSON returned by writeTo was empty");
        }

        return jsonOut;
    }

    private List<SubscriptionItem> readFromWriteTo(final String jsonOut) throws Exception {
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(
                jsonOut.getBytes(StandardCharsets.UTF_8));
        final List<SubscriptionItem> secondReadItems = ImportExportJsonHelper.readFrom(
                inputStream);

        if (secondReadItems.isEmpty()) {
            fail("second call to readFrom returned an empty list");
        }

        return secondReadItems;
    }
}
