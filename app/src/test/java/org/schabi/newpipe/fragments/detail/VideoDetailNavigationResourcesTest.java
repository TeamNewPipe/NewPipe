package org.schabi.newpipe.fragments.detail;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.nio.file.Files;
import java.nio.file.Path;

import javax.xml.parsers.DocumentBuilderFactory;

public class VideoDetailNavigationResourcesTest {
    private static final String BOTTOM_NAVIGATION_VIEW =
            "com.google.android.material.bottomnavigation.BottomNavigationView";
    private static final String MAIN_BOTTOM_NAVIGATION_VIEW =
            "org.schabi.newpipe.views.FloatingBottomNavigationView";
    private static final String NAVIGATION_RAIL_VIEW =
            "com.google.android.material.navigationrail.NavigationRailView";
    private static final String TAB_LAYOUT = "com.google.android.material.tabs.TabLayout";

    private final Path resourcesDirectory = Files.exists(Path.of("src/main/res"))
            ? Path.of("src/main/res")
            : Path.of("app/src/main/res");

    @Test
    public void phoneAndLargeLandscapeLayoutsUseExpressiveNavigation() throws Exception {
        assertPhoneBottomNavigation();
        assertLargeLandscapeHorizontalNavigation();
    }

    @Test
    public void navigationIndicatorsAreCompactRoundedRectangles() throws Exception {
        final String styles = Files.readString(resourcesDirectory.resolve("values/styles.xml"));
        final String indicator = styleBody(
                styles, "wizestreamBottomNavigationActiveIndicator");
        final String shape = styleBody(
                styles, "ShapeAppearance.WizeStream.Navigation.ActiveIndicator");

        assertTrue(indicator.contains("<item name=\"android:width\">44dp</item>"));
        assertTrue(indicator.contains("<item name=\"android:height\">28dp</item>"));
        assertTrue(indicator.contains("<item name=\"marginHorizontal\">4dp</item>"));
        assertTrue(indicator.contains(
                "<item name=\"android:color\">?attr/colorSecondaryContainer</item>"));
        assertFalse(indicator.contains(
                "<item name=\"android:color\">?attr/colorPrimaryContainer</item>"));
        assertTrue(indicator.contains(
                "@style/ShapeAppearance.WizeStream.Navigation.ActiveIndicator"));
        assertTrue(shape.contains("<item name=\"cornerFamily\">rounded</item>"));
        assertTrue(shape.contains("<item name=\"cornerSize\">8dp</item>"));
        assertFalse(shape.contains("Corner.Full"));
    }

    @Test
    public void selectedNavigationContentStaysThemeColored() throws Exception {
        final String colors = Files.readString(
                resourcesDirectory.resolve("color/tab_layout_material_item_color.xml"));

        assertTrue(colors.contains(
                "android:color=\"?attr/colorPrimary\" android:state_checked=\"true\""));
        assertTrue(colors.contains(
                "android:color=\"?attr/colorPrimary\" android:state_selected=\"true\""));
        assertTrue(colors.contains("<item android:color=\"?attr/colorOnSurfaceVariant\" />"));
        assertFalse(colors.contains("?attr/colorOnSecondaryContainer"));
    }

    @Test
    public void mainNavigationUsesTheCompactIndicator() throws Exception {
        final Document document = parse("layout/activity_main.xml");
        final var navigationViews = document.getElementsByTagName(MAIN_BOTTOM_NAVIGATION_VIEW);
        assertEquals(1, navigationViews.getLength());

        final var navigation = (Element) navigationViews.item(0);
        assertEquals("@+id/main_bottom_navigation",
                navigation.getAttribute("android:id"));
        assertEquals("@style/wizestreamBottomNavigationActiveIndicator",
                navigation.getAttribute("app:itemActiveIndicatorStyle"));

        final Document largeDocument = parse("layout-sw600dp/activity_main.xml");
        final var rails = largeDocument.getElementsByTagName(NAVIGATION_RAIL_VIEW);
        assertEquals(1, rails.getLength());
        final var rail = (Element) rails.item(0);
        assertEquals("@+id/main_navigation_rail", rail.getAttribute("android:id"));
        assertEquals("@style/wizestreamBottomNavigationActiveIndicator",
                rail.getAttribute("app:itemActiveIndicatorStyle"));

        final var largeBottomNavigation =
                largeDocument.getElementsByTagName(MAIN_BOTTOM_NAVIGATION_VIEW);
        assertEquals(1, largeBottomNavigation.getLength());
        assertEquals("@+id/main_bottom_navigation",
                ((Element) largeBottomNavigation.item(0)).getAttribute("android:id"));
    }

    @Test
    public void phoneAndLargeLandscapeLayoutsExposeCastControl() throws Exception {
        assertViewIdExists("layout/fragment_video_detail.xml", "@+id/detail_controls_cast");
        assertViewIdExists("layout-w840dp-land/fragment_video_detail.xml",
                "@+id/detail_controls_cast");
    }

    @Test
    public void navigationMenuHasStableLocalizedDestinations() throws Exception {
        final Document document = parse("menu/video_detail_navigation.xml");
        final var items = document.getElementsByTagName("item");

        assertEquals(5, items.getLength());
        assertMenuItem((Element) items.item(0),
                "@+id/video_detail_navigation_comments",
                "@string/comments_tab_description");
        assertMenuItem((Element) items.item(1),
                "@+id/video_detail_navigation_related",
                "@string/related_items_tab_description");
        assertMenuItem((Element) items.item(2),
                "@+id/video_detail_navigation_description",
                "@string/description_tab_description");
        assertMenuItem((Element) items.item(3),
                "@+id/video_detail_navigation_notes",
                "@string/learning_notes_tab_description");
    }

    private void assertPhoneBottomNavigation() throws Exception {
        final Document document = parse("layout/fragment_video_detail.xml");

        assertEquals(0, document.getElementsByTagName(TAB_LAYOUT).getLength());
        final var navigationViews = document.getElementsByTagName(BOTTOM_NAVIGATION_VIEW);
        assertEquals(1, navigationViews.getLength());

        final var navigation = (Element) navigationViews.item(0);
        assertEquals("@+id/detail_navigation", navigation.getAttribute("android:id"));
        assertEquals("labeled", navigation.getAttribute("app:labelVisibilityMode"));
        assertEquals("@menu/video_detail_navigation", navigation.getAttribute("app:menu"));
        assertEquals("?attr/colorSurfaceContainer",
                navigation.getAttribute("android:background"));
        assertEquals("0dp", navigation.getAttribute("android:elevation"));
        assertEquals(
                "@style/wizestreamBottomNavigationActiveIndicator",
                navigation.getAttribute("app:itemActiveIndicatorStyle"));
        assertEquals("@color/tab_layout_material_item_color",
                navigation.getAttribute("app:itemIconTint"));
        assertEquals("@color/tab_layout_material_item_color",
                navigation.getAttribute("app:itemTextColor"));

        final var viewPagers = document.getElementsByTagName(
                "androidx.viewpager.widget.ViewPager");
        assertEquals(1, viewPagers.getLength());
        final var viewPager = (Element) viewPagers.item(0);
        assertEquals("@dimen/video_detail_navigation_height",
                viewPager.getAttribute("android:layout_marginBottom"));
        assertEquals("", viewPager.getAttribute("android:paddingBottom"));
    }

    private void assertLargeLandscapeHorizontalNavigation() throws Exception {
        final Document document = parse("layout-w840dp-land/fragment_video_detail.xml");

        assertEquals(0, document.getElementsByTagName(TAB_LAYOUT).getLength());
        assertEquals(0, document.getElementsByTagName(NAVIGATION_RAIL_VIEW).getLength());
        final var navigationViews = document.getElementsByTagName(BOTTOM_NAVIGATION_VIEW);
        assertEquals(1, navigationViews.getLength());

        final var navigation = (Element) navigationViews.item(0);
        assertEquals("@+id/detail_navigation", navigation.getAttribute("android:id"));
        assertEquals("labeled", navigation.getAttribute("app:labelVisibilityMode"));
        assertEquals("@menu/video_detail_navigation", navigation.getAttribute("app:menu"));
        assertEquals("?attr/colorSurfaceContainer",
                navigation.getAttribute("android:background"));
        assertEquals("0dp", navigation.getAttribute("android:elevation"));
        assertEquals("match_parent",
                navigation.getAttribute("android:layout_width"));
        assertEquals("@dimen/video_detail_navigation_height",
                navigation.getAttribute("android:layout_height"));
        assertEquals("com.google.android.material.appbar.AppBarLayout",
                navigation.getParentNode().getNodeName());
        assertEquals("@style/wizestreamBottomNavigationActiveIndicator",
                navigation.getAttribute("app:itemActiveIndicatorStyle"));
        assertEquals("@color/tab_layout_material_item_color",
                navigation.getAttribute("app:itemIconTint"));
        assertEquals("@color/tab_layout_material_item_color",
                navigation.getAttribute("app:itemTextColor"));

        final var viewPagers = document.getElementsByTagName(
                "androidx.viewpager.widget.ViewPager");
        assertEquals(1, viewPagers.getLength());
        final var viewPager = (Element) viewPagers.item(0);
        assertEquals("", viewPager.getAttribute("android:layout_marginStart"));
        assertEquals("", viewPager.getAttribute("android:layout_marginBottom"));
    }

    private void assertMenuItem(final Element item,
                                final String id,
                                final String title) {
        assertEquals(id, item.getAttribute("android:id"));
        assertEquals(title, item.getAttribute("android:title"));
    }

    private void assertViewIdExists(final String layoutPath,
                                    final String id) throws Exception {
        final Document document = parse(layoutPath);
        final var elements = document.getElementsByTagName("*");
        int matches = 0;
        for (int index = 0; index < elements.getLength(); index++) {
            if (id.equals(((Element) elements.item(index)).getAttribute("android:id"))) {
                matches++;
            }
        }
        assertEquals(layoutPath, 1, matches);
    }

    private String styleBody(final String styles, final String styleName) {
        final String openingTag = "<style name=\"" + styleName + "\"";
        final int start = styles.indexOf(openingTag);
        if (start < 0) {
            throw new AssertionError("Missing style: " + styleName);
        }
        final int end = styles.indexOf("</style>", start);
        if (end < 0) {
            throw new AssertionError("Unclosed style: " + styleName);
        }
        return styles.substring(start, end);
    }

    private Document parse(final String relativePath) throws Exception {
        final var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder()
                .parse(resourcesDirectory.resolve(relativePath).toFile());
    }
}
