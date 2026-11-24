package org.schabi.newpipe.fragments;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

public class MainBottomNavigationLayoutTest {
    private final Path sourceDirectory = Files.exists(Path.of("src/main/java"))
            ? Path.of("src/main/java") : Path.of("app/src/main/java");
    private final Path resourcesDirectory = Files.exists(Path.of("src/main/res"))
            ? Path.of("src/main/res") : Path.of("app/src/main/res");

    @Test
    public void menuSelectionAndResumeScheduleAFullRemeasure() throws Exception {
        final String source = Files.readString(sourceDirectory.resolve(
                "org/schabi/newpipe/fragments/MainFragment.java"));

        assertMethodContains(source, "public void onResume()",
                "scheduleBottomNavigationRemeasure();");
        assertMethodContains(source, "private void updateBottomNavigationItems()",
                "scheduleBottomNavigationRemeasure();");
        assertMethodContains(source, "private void updateBottomNavigationSelection(",
                "scheduleBottomNavigationRemeasure();");
        assertMethodContains(source, "private void scheduleBottomNavigationRemeasure()",
                "navigation.post(() -> {");
        assertMethodContains(source, "private void scheduleBottomNavigationRemeasure()",
                "navigation.requestLayout();");
        assertMethodContains(source, "private void scheduleBottomNavigationRemeasure()",
                "navigation.getChildAt(i).requestLayout();");
    }

    @Test
    public void tabletsCanSwitchBetweenBottomAndLeftNavigation() throws Exception {
        final String layout = Files.readString(
                resourcesDirectory.resolve("layout-sw600dp/activity_main.xml"));
        final String settings = Files.readString(
                resourcesDirectory.resolve("values/settings_keys.xml"));
        final String appearanceSettings = Files.readString(
                resourcesDirectory.resolve("xml/appearance_settings.xml"));
        final String source = Files.readString(sourceDirectory.resolve(
                "org/schabi/newpipe/fragments/MainFragment.java"));

        assertTrue(layout.contains(
                "com.google.android.material.navigationrail.NavigationRailView"));
        assertTrue(layout.contains(
                "org.schabi.newpipe.views.FloatingBottomNavigationView"));
        assertTrue(layout.contains("android:id=\"@+id/main_navigation_rail\""));
        assertTrue(layout.contains("android:id=\"@+id/main_bottom_navigation\""));
        assertFalse(layout.contains(
                "android:layout_marginStart=\"@dimen/main_navigation_rail_width\""));
        assertTrue(layout.contains(
                "android:background=\"?attr/main_navigation_background_color\""));
        assertTrue(layout.contains("android:elevation=\"0dp\""));
        assertTrue(layout.contains("app:labelVisibilityMode=\"labeled\""));
        assertTrue(settings.contains(
                "bottom_navigation_labels_default_value\">"
                        + "@string/bottom_navigation_labels_always_value"));
        assertTrue(settings.contains("tablet_navigation_portrait_position_key"));
        assertTrue(settings.contains("tablet_navigation_landscape_position_key"));
        assertTrue(appearanceSettings.contains(
                "android:key=\"@string/tablet_navigation_portrait_position_key\""));
        assertTrue(appearanceSettings.contains(
                "android:key=\"@string/tablet_navigation_landscape_position_key\""));
        assertTrue(source.contains("private NavigationBarView bottomNavigation;"));
        assertTrue(source.contains("private BottomNavigationView bottomNavigationView;"));
        assertTrue(source.contains("private NavigationRailView navigationRailView;"));
        assertTrue(source.contains("TabletNavigationPositionResolver.useNavigationRail("));
        assertTrue(source.contains("bottomNavigation instanceof NavigationRailView"));
    }

    @Test
    public void orientationChangeUpdatesTabletNavigationWithoutPlayerInset() throws Exception {
        final String source = Files.readString(sourceDirectory.resolve(
                "org/schabi/newpipe/fragments/MainFragment.java"));

        assertMethodContains(source, "public void onConfigurationChanged(",
                "selectMainNavigation()");
        assertMethodContains(source, "public void onConfigurationChanged(",
                "updateMainNavigationMode();");
        assertFalse(source.contains(
                "setStartMargin(requireActivity().findViewById("
                        + "R.id.fragment_player_holder), inset)"));
    }

    private void assertMethodContains(final String source,
                                      final String signature,
                                      final String expected) {
        final int methodStart = source.indexOf(signature);
        assertTrue(signature, methodStart >= 0);
        final int nextMethod = source.indexOf("\n    private ", methodStart + signature.length());
        final int methodEnd = nextMethod >= 0 ? nextMethod : source.length();
        assertTrue(signature + " should contain " + expected,
                source.substring(methodStart, methodEnd).contains(expected));
    }
}
