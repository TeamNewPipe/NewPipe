package org.schabi.newpipe.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

public class ThemeColorIntegrationTest {
    private final Path sourceDirectory = Files.exists(Path.of("src/main/java"))
            ? Path.of("src/main/java") : Path.of("app/src/main/java");
    private final Path resourceDirectory = Files.exists(Path.of("src/main/res"))
            ? Path.of("src/main/res") : Path.of("app/src/main/res");

    @Test
    public void blackThemeKeepsDynamicAccentsAndRestoresOnlyBlackSurfaces() throws Exception {
        final String helper = Files.readString(sourceDirectory.resolve(
                "org/schabi/newpipe/util/ThemeHelper.java"));
        final String styles = Files.readString(resourceDirectory.resolve("values/styles.xml"));
        final String blackOverlay = styleBody(styles,
                "ThemeOverlay_wizestream_BlackSurfaces");

        assertTrue(helper.contains("&& (isFollowSystemThemeColor(context)"
                + " || getCustomThemeColor(context) != null);"));
        assertFalse(helper.contains("&& !isBlackThemeSelected(context)"));
        assertTrue(helper.contains("DynamicColors.applyToActivityIfAvailable("
                + "(Activity) context, options);\n            applyBlackSurfaceOverlay(context);"));
        assertTrue(helper.contains("DynamicColors.applyToActivityIfAvailable("
                + "(Activity) context);\n            applyBlackSurfaceOverlay(context);"));
        assertTrue(blackOverlay.contains(
                "<item name=\"colorSurface\">@color/black_background_color</item>"));
        assertTrue(blackOverlay.contains(
                "<item name=\"colorSurfaceContainer\">"
                        + "@color/black_m3_surface_container_color</item>"));
        assertTrue(blackOverlay.contains(
                "<item name=\"colorSurfaceContainerHigh\">"
                        + "@color/black_m3_surface_container_high_color</item>"));
        assertFalse(blackOverlay.contains("colorPrimary"));
        assertFalse(blackOverlay.contains("colorSecondary"));
    }

    @Test
    public void themesExposeMaterialSurfaceAndOutlineRoles() throws Exception {
        final String styles = Files.readString(resourceDirectory.resolve("values/styles.xml"));

        for (final String theme : new String[]{
                "Base.V21.LightTheme",
                "Base.V21.DarkTheme",
                "Base.V21.BlackTheme"
        }) {
            final String body = styleBody(styles, theme);
            assertTrue(theme, body.contains("<item name=\"colorSurfaceContainerLow\">"));
            assertTrue(theme, body.contains("<item name=\"colorSurfaceContainer\">"));
            assertTrue(theme, body.contains("<item name=\"colorSurfaceContainerHigh\">"));
            assertTrue(theme, body.contains("<item name=\"colorOutlineVariant\">"));
            assertTrue(theme, body.contains(
                    "<item name=\"separator_color\">?attr/colorOutlineVariant</item>"));
            assertTrue(theme, body.contains(
                    "<item name=\"card_item_background_color\">"
                            + "?attr/colorSurfaceContainerLow</item>"));
        }

        final String baseTheme = styleBody(styles, "Base.V21");
        assertTrue(baseTheme.contains(
                "<item name=\"colorControlActivated\">?attr/colorPrimary</item>"));
        assertTrue(baseTheme.contains(
                "<item name=\"android:textColorLink\">?attr/colorPrimary</item>"));
        assertFalse(baseTheme.contains(
                "<item name=\"colorControlActivated\">?attr/colorSecondary</item>"));
    }

    @Test
    public void explicitLegacyWidgetsUseMaterial3Components() throws Exception {
        final String relatedItems = Files.readString(
                resourceDirectory.resolve("layout/related_items_header.xml"));
        final String colorPicker = Files.readString(
                resourceDirectory.resolve("layout/dialog_sponsor_block_color_picker.xml"));
        final String queue = Files.readString(
                resourceDirectory.resolve("layout/activity_player_queue_control.xml"));
        final String queueLandscape = Files.readString(
                resourceDirectory.resolve("layout-land/activity_player_queue_control.xml"));
        final String feedGroupDialog = Files.readString(
                resourceDirectory.resolve("layout/dialog_feed_group_create.xml"));

        assertTrue(relatedItems.contains(
                "com.google.android.material.materialswitch.MaterialSwitch"));
        assertFalse(relatedItems.contains("androidx.appcompat.widget.SwitchCompat"));
        assertTrue(colorPicker.contains(
                "com.google.android.material.textfield.TextInputLayout"));
        assertTrue(colorPicker.contains(
                "com.google.android.material.textfield.TextInputEditText"));
        assertFalse(colorPicker.contains("<EditText"));
        assertMaterialToolbar(queue);
        assertMaterialToolbar(queueLandscape);
        assertMaterialToolbar(feedGroupDialog);
    }

    @Test
    public void xmlSeekBarsShareTheMaterialColorStyle() throws Exception {
        final String styles = Files.readString(
                resourceDirectory.resolve("values/styles_misc.xml"));
        final String materialSeekBar = styleBody(styles, "Widget.WizeStream.SeekBar");

        assertTrue(materialSeekBar.contains(
                "<item name=\"android:thumbTint\">?attr/colorPrimary</item>"));
        assertTrue(materialSeekBar.contains(
                "<item name=\"android:progressTint\">?attr/colorPrimary</item>"));
        assertTrue(materialSeekBar.contains(
                "<item name=\"android:progressBackgroundTint\">"
                        + "?attr/colorSurfaceVariant</item>"));
        assertTrue(materialSeekBar.contains(
                "<item name=\"android:secondaryProgressTint\">"
                        + "?attr/colorPrimaryContainer</item>"));

        assertLayoutUsesMaterialSeekBar("layout/player.xml");
        assertLayoutUsesMaterialSeekBar("layout/activity_player_queue_control.xml");
        assertLayoutUsesMaterialSeekBar("layout-land/activity_player_queue_control.xml");
        assertLayoutUsesMaterialSeekBar("layout/dialog_sponsor_block_color_picker.xml");
    }

    private void assertLayoutUsesMaterialSeekBar(final String layout) throws Exception {
        final String source = Files.readString(resourceDirectory.resolve(layout));
        assertTrue(source.contains("style=\"@style/Widget.WizeStream.SeekBar\""));
    }

    private void assertMaterialToolbar(final String layout) {
        assertTrue(layout.contains("com.google.android.material.appbar.MaterialToolbar"));
        assertTrue(layout.contains("android:background=\"?attr/colorSurfaceContainer\"")
                || layout.contains("android:background=\"?attr/colorSurface\""));
        assertFalse(layout.contains("androidx.appcompat.widget.Toolbar"));
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
}
