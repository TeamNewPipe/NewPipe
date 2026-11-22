package org.schabi.newpipe.local.feed;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.nio.file.Files;
import java.nio.file.Path;

import javax.xml.parsers.DocumentBuilderFactory;

public class FeedRefreshControlsTest {
    private static final String ANDROID_NAMESPACE =
            "http://schemas.android.com/apk/res/android";
    private static final String APP_NAMESPACE =
            "http://schemas.android.com/apk/res-auto";

    private final Path sourceDirectory = Files.exists(Path.of("src/main/java"))
            ? Path.of("src/main/java") : Path.of("app/src/main/java");
    private final Path resourcesDirectory = Files.exists(Path.of("src/main/res"))
            ? Path.of("src/main/res") : Path.of("app/src/main/res");

    @Test
    public void filtersYieldTheHeaderRowToRefreshControls() throws Exception {
        final String source = readSource(
                "org/schabi/newpipe/local/feed/FeedFragment.kt");
        final String showLoading = methodBody(
                source, "override fun showLoading()", "override fun hideLoading()");
        final String showRefresh = methodBody(
                source,
                "private fun showRefreshProgress",
                "private fun hideRefreshProgress");
        final String hideRefresh = methodBody(
                source,
                "private fun hideRefreshProgress",
                "private fun showInfoItemDialog");

        assertFalse(showLoading.contains("refreshRootView.animate(false, 0)"));
        assertTrue(showLoading.contains("itemsList.animate(true, 0)"));
        assertTrue(showLoading.contains("showRefreshProgress(binding)"));

        assertTrue(showRefresh.contains("streamFilterChips.root.isVisible = false"));
        assertTrue(showRefresh.contains("refreshProgressContainer.isVisible = true"));
        assertTrue(showRefresh.contains("cancelRefreshButton.isVisible = true"));

        assertTrue(hideRefresh.contains("refreshProgressContainer.isVisible = false"));
        assertTrue(hideRefresh.contains("cancelRefreshButton.isVisible = false"));
        assertTrue(hideRefresh.contains("streamFilterChips.root.isVisible = true"));
    }

    @Test
    public void refreshProgressLivesInHeaderWithoutBlockingFeedContent() throws Exception {
        final Document document = parseLayout();
        final Element progressContainer = findByAndroidId(
                document, "@+id/refresh_progress_container");

        assertNotNull(progressContainer);
        assertEquals("gone",
                progressContainer.getAttributeNS(ANDROID_NAMESPACE, "visibility"));
        assertNull(findByAndroidId(document, "@+id/refresh_loading_overlay"));
        assertNull(findByAndroidId(document, "@+id/refresh_loading_scrim"));
        assertNull(findByAndroidId(document, "@+id/loading_progress_bar"));
        assertNotNull(findByAndroidId(document, "@+id/items_list"));

        final String source = readSource(
                "org/schabi/newpipe/local/feed/FeedFragment.kt");
        assertTrue(source.contains("showRefreshProgress(binding)"));
        assertTrue(source.contains("hideRefreshProgress(binding)"));
        assertFalse(source.contains("refreshLoadingOverlay.animate("));
        assertFalse(source.contains("private var isRefreshing"));
        assertFalse(source.contains("&& !isRefreshing"));
    }

    @Test
    public void progressFiltersAndCancelShareOneCompactHeaderRow() throws Exception {
        final Document document = parseLayout();
        final Element controlsRow = findByAndroidId(
                document, "@+id/feed_controls_row");
        final Element progressContainer = findByAndroidId(
                document, "@+id/refresh_progress_container");
        final Element progress = findByAndroidId(
                document, "@+id/feed_refresh_progress_bar");
        final Element indeterminateProgress = findByAndroidId(
                document, "@+id/feed_refresh_indeterminate_progress_bar");
        final Element filters = findByAndroidId(
                document, "@+id/stream_filter_chips");
        final Element cancel = findByAndroidId(
                document, "@+id/cancel_refresh_button");

        assertNotNull(controlsRow);
        assertEquals("LinearLayout", controlsRow.getTagName());
        assertEquals("horizontal",
                controlsRow.getAttributeNS(ANDROID_NAMESPACE, "orientation"));

        assertNotNull(progressContainer);
        assertEquals("LinearLayout", progressContainer.getTagName());
        assertEquals("0dp",
                progressContainer.getAttributeNS(ANDROID_NAMESPACE, "layout_width"));
        assertEquals("1",
                progressContainer.getAttributeNS(ANDROID_NAMESPACE, "layout_weight"));
        assertEquals("wrap_content",
                progressContainer.getAttributeNS(ANDROID_NAMESPACE, "layout_height"));
        assertEquals("gone",
                progressContainer.getAttributeNS(ANDROID_NAMESPACE, "visibility"));

        assertNotNull(progress);
        assertNotNull(indeterminateProgress);
        assertEquals(
                "com.google.android.material.progressindicator.LinearProgressIndicator",
                progress.getTagName());
        assertEquals("match_parent",
                progress.getAttributeNS(ANDROID_NAMESPACE, "layout_width"));
        assertEquals("wrap_content",
                progress.getAttributeNS(ANDROID_NAMESPACE, "layout_height"));
        assertEquals("polite",
                progress.getAttributeNS(ANDROID_NAMESPACE, "accessibilityLiveRegion"));
        assertEquals("4dp", progress.getAttributeNS(APP_NAMESPACE, "trackThickness"));
        assertEquals("2dp", progress.getAttributeNS(APP_NAMESPACE, "trackCornerRadius"));
        assertEquals(
                "org.schabi.newpipe.local.feed.ExpressiveRefreshIndicator",
                indeterminateProgress.getTagName());
        assertEquals("30dp", indeterminateProgress.getAttributeNS(
                ANDROID_NAMESPACE, "layout_width"));
        assertEquals("26dp", indeterminateProgress.getAttributeNS(
                ANDROID_NAMESPACE, "layout_height"));

        assertNotNull(filters);
        assertEquals("include", filters.getTagName());
        assertEquals("0dp",
                filters.getAttributeNS(ANDROID_NAMESPACE, "layout_width"));
        assertEquals("1",
                filters.getAttributeNS(ANDROID_NAMESPACE, "layout_weight"));

        assertNotNull(cancel);
        assertEquals("ImageButton", cancel.getTagName());
        assertEquals("48dp", cancel.getAttributeNS(ANDROID_NAMESPACE, "layout_width"));
        assertEquals("48dp", cancel.getAttributeNS(ANDROID_NAMESPACE, "layout_height"));
        assertEquals("gone",
                cancel.getAttributeNS(ANDROID_NAMESPACE, "visibility"));
        assertEquals("?attr/selectableItemBackgroundBorderless",
                cancel.getAttributeNS(ANDROID_NAMESPACE, "background"));
        assertEquals("@string/cancel_refresh",
                cancel.getAttributeNS(ANDROID_NAMESPACE, "contentDescription"));
        assertEquals("@drawable/ic_close",
                cancel.getAttributeNS(ANDROID_NAMESPACE, "src"));

        assertTrue(progressContainer.getParentNode() == controlsRow);
        assertTrue(filters.getParentNode() == controlsRow);
        assertTrue(cancel.getParentNode() == controlsRow);

        final String source = readSource(
                "org/schabi/newpipe/local/feed/FeedFragment.kt");
        assertTrue(source.contains("streamFilterChips.root.isVisible = false"));
        assertTrue(source.contains("streamFilterChips.root.isVisible = true"));
    }

    @Test
    public void determinateCounterUsesTheCircularIndicatorsExactCanvasCenter()
            throws Exception {
        final String source = readSource(
                "org/schabi/newpipe/local/feed/FeedProgressIndicator.java");

        assertTrue(source.contains("super.onDraw(canvas)"));
        assertTrue(source.contains("counterBounds.exactCenterX()"));
        assertTrue(source.contains("counterBounds.exactCenterY()"));
        assertTrue(source.contains("canvas.drawText(counterText"));
    }

    @Test
    public void cancelButtonStopsTheSharedRefreshServicePath() throws Exception {
        final String fragment = readSource(
                "org/schabi/newpipe/local/feed/FeedFragment.kt");
        final String service = readSource(
                "org/schabi/newpipe/local/feed/service/FeedLoadService.kt");

        assertTrue(fragment.contains("cancelRefreshButton.setOnClickListener"));
        assertTrue(fragment.contains("FeedLoadService.cancel(requireContext())"));
        assertTrue(service.contains("fun cancel(context: Context)"));
        assertTrue(service.contains("getBroadcast(this, NOTIFICATION_ID, cancelIntent(this)"));
        assertTrue(methodBody(service, "private fun cancelLoading()", "private fun stopService()")
                .contains("feedLoadManager.cancel()"));
        assertTrue(methodBody(service, "private fun cancelLoading()", "private fun stopService()")
                .contains("FeedEventManager.reset(feedScope)"));
        assertTrue(methodBody(service, "private fun cancelLoading()", "private fun stopService()")
                .contains("stopService()"));
        assertTrue(methodBody(
                service,
                "private fun setupBroadcastReceiver()",
                "// Error handling"
        ).contains("cancelLoading()"));
    }

    @Test
    public void preferenceChangesCannotOutliveTheFeedViewBinding() throws Exception {
        final String source = readSource(
                "org/schabi/newpipe/local/feed/FeedFragment.kt");

        assertTrue(methodBody(
                source,
                "override fun onViewCreated",
                "override fun onResume"
        ).contains("registerOnSharedPreferenceChangeListener(onSettingsChangeListener)"));
        assertTrue(methodBody(
                source,
                "override fun onDestroyView()",
                "// Handling"
        ).contains("unregisterOnSharedPreferenceChangeListener(onSettingsChangeListener)"));
        final String filteredItems = methodBody(
                source,
                "private fun showFilteredFeedItems",
                "override fun setContextualSearchQuery");
        assertTrue(filteredItems.contains("if (_feedBinding == null)"));
        assertTrue(filteredItems.contains("item.copy("));
        assertTrue(filteredItems.contains("itemVersion = itemVersion,"));
        assertTrue(filteredItems.contains("isHighlighted = highlighted,"));
        assertTrue(filteredItems.contains(
                "isSeen = currentSeenStore?.isSeen(item.seenIdentity) == true"));
        assertTrue(filteredItems.contains(
                "displayItem.onUploaderSelected = ::openUploaderChannel"));
        assertFalse(filteredItems.contains("it.itemVersion = itemVersion"));
    }

    private String readSource(final String relativePath) throws Exception {
        return Files.readString(sourceDirectory.resolve(relativePath));
    }

    private Document parseLayout() throws Exception {
        final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature(
                "http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature(
                "http://xml.org/sax/features/external-parameter-entities", false);
        return factory.newDocumentBuilder()
                .parse(resourcesDirectory.resolve("layout/fragment_feed.xml").toFile());
    }

    private Element findByAndroidId(final Document document, final String id) {
        final NodeList elements = document.getElementsByTagName("*");
        for (int index = 0; index < elements.getLength(); index++) {
            final Element element = (Element) elements.item(index);
            if (id.equals(element.getAttributeNS(ANDROID_NAMESPACE, "id"))) {
                return element;
            }
        }
        return null;
    }

    private String methodBody(final String source, final String signature,
                              final String nextSignature) {
        final int start = source.indexOf(signature);
        final int nextMethod = source.indexOf(nextSignature, start + signature.length());
        return source.substring(start, nextMethod);
    }
}
