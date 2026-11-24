package org.schabi.newpipe.util;

import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

import androidx.annotation.NonNull;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import org.schabi.newpipe.player.gesture.CustomBottomSheetBehavior;
import org.schabi.newpipe.views.FloatingBottomNavigationView;

/** Shared edge-to-edge setup and system-bar inset handling for app activities. */
public final class EdgeToEdgeHelper {
    private EdgeToEdgeHelper() { }

    public static void enable(@NonNull final Activity activity) {
        final Window window = activity.getWindow();
        WindowCompat.setDecorFitsSystemWindows(window, false);
        makeSystemBarsTransparent(window);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setNavigationBarContrastEnforced(false);
            window.setStatusBarContrastEnforced(false);
        }
        setLightSystemBars(activity, ThemeHelper.isLightThemeSelected(activity));
    }

    public static void applySystemBarPadding(@NonNull final View view) {
        final int initialLeft = view.getPaddingLeft();
        final int initialTop = view.getPaddingTop();
        final int initialRight = view.getPaddingRight();
        final int initialBottom = view.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(view, (target, windowInsets) -> {
            final Insets safeInsets = getSafeSystemBarInsets(windowInsets);
            target.setPadding(
                    initialLeft + safeInsets.left,
                    initialTop + safeInsets.top,
                    initialRight + safeInsets.right,
                    initialBottom + safeInsets.bottom);

            return consumeAppliedInsets(windowInsets);
        });
        ViewCompat.requestApplyInsets(view);
    }

    /**
     * Applies insets to the independently laid-out main activity and drawer children.
     *
     * <p>The player bottom sheet deliberately remains outside {@code safeContent}. Padding the
     * sheet's {@link androidx.coordinatorlayout.widget.CoordinatorLayout} parent changes the
     * height used by {@code BottomSheetBehavior} and shifts its expanded position downward. The
     * regular fragment content, toolbar, and adaptive navigation receive their insets directly
     * instead.</p>
     *
     * @param insetSource view that receives the window inset dispatch
     * @param safeContent regular fragment content constrained inside all safe edges
     * @param toolbar toolbar protected below the status bar and display cutouts
     * @param bottomNavigation bottom navigation protected inside all safe edges
     * @param navigationRail navigation rail protected inside all safe edges
     * @param navigationScrim opaque surface drawn behind the bottom system navigation bar
     * @param playerSheet edge-to-edge player sheet; only its collapsed peek height avoids the
     *                    bottom system bar
     * @param drawer navigation drawer protected on its horizontal and bottom edges
     * @param drawerHeader drawer header whose content is protected below the status bar
     */
    public static void applyMainActivitySystemBarInsets(
            @NonNull final View insetSource,
            @NonNull final View safeContent,
            @NonNull final View toolbar,
            @NonNull final View bottomNavigation,
            @NonNull final View navigationRail,
            @NonNull final View navigationScrim,
            @NonNull final View playerSheet,
            @NonNull final View drawer,
            @NonNull final View drawerHeader) {
        final int contentLeft = safeContent.getPaddingLeft();
        final int contentTop = safeContent.getPaddingTop();
        final int contentRight = safeContent.getPaddingRight();
        final int contentBottom = safeContent.getPaddingBottom();
        final int toolbarLeft = toolbar.getPaddingLeft();
        final int toolbarTop = toolbar.getPaddingTop();
        final int toolbarRight = toolbar.getPaddingRight();
        final int toolbarBottom = toolbar.getPaddingBottom();
        final ViewGroup.MarginLayoutParams bottomNavigationParams =
                (ViewGroup.MarginLayoutParams) bottomNavigation.getLayoutParams();
        final int bottomNavigationLeft = bottomNavigationParams.leftMargin;
        final int bottomNavigationTop = bottomNavigationParams.topMargin;
        final int bottomNavigationRight = bottomNavigationParams.rightMargin;
        final int bottomNavigationBottom = bottomNavigationParams.bottomMargin;
        final ViewGroup.MarginLayoutParams navigationRailParams =
                (ViewGroup.MarginLayoutParams) navigationRail.getLayoutParams();
        final int navigationRailLeft = navigationRailParams.leftMargin;
        final int navigationRailTop = navigationRailParams.topMargin;
        final int navigationRailRight = navigationRailParams.rightMargin;
        final int navigationRailBottom = navigationRailParams.bottomMargin;
        final int navigationScrimHeight = navigationScrim.getLayoutParams().height;
        final int playerLeft = playerSheet.getPaddingLeft();
        final int playerTop = playerSheet.getPaddingTop();
        final int playerRight = playerSheet.getPaddingRight();
        final int playerBottom = playerSheet.getPaddingBottom();
        final int drawerLeft = drawer.getPaddingLeft();
        final int drawerTop = drawer.getPaddingTop();
        final int drawerRight = drawer.getPaddingRight();
        final int drawerBottom = drawer.getPaddingBottom();
        final int headerLeft = drawerHeader.getPaddingLeft();
        final int headerTop = drawerHeader.getPaddingTop();
        final int headerRight = drawerHeader.getPaddingRight();
        final int headerBottom = drawerHeader.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(insetSource, (target, windowInsets) -> {
            final Insets safeInsets = getSafeSystemBarInsets(windowInsets);
            safeContent.setPadding(
                    contentLeft + safeInsets.left,
                    contentTop + safeInsets.top,
                    contentRight + safeInsets.right,
                    contentBottom + safeInsets.bottom);
            toolbar.setPadding(
                    toolbarLeft + safeInsets.left,
                    toolbarTop + safeInsets.top,
                    toolbarRight + safeInsets.right,
                    toolbarBottom);
            applyNavigationMargins(bottomNavigation,
                    bottomNavigationLeft, bottomNavigationTop,
                    bottomNavigationRight, bottomNavigationBottom, safeInsets);
            applyNavigationMargins(navigationRail,
                    navigationRailLeft, navigationRailTop,
                    navigationRailRight, navigationRailBottom, safeInsets);
            final ViewGroup.LayoutParams updatedScrimParams =
                    navigationScrim.getLayoutParams();
            final boolean isLandscape = navigationScrim.getResources()
                    .getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
            updatedScrimParams.height = isLandscape
                    ? navigationScrimHeight
                    : navigationScrimHeight + safeInsets.bottom;
            navigationScrim.setLayoutParams(updatedScrimParams);
            playerSheet.setPadding(
                    playerLeft,
                    playerTop,
                    playerRight,
                    playerBottom);
            updatePlayerSheetBottomInset(playerSheet, safeInsets.bottom);
            drawer.setPadding(
                    drawerLeft + safeInsets.left,
                    drawerTop,
                    drawerRight + safeInsets.right,
                    drawerBottom + safeInsets.bottom);
            drawerHeader.setPadding(
                    headerLeft,
                    headerTop + safeInsets.top,
                    headerRight,
                    headerBottom);

            return consumeAppliedInsets(windowInsets);
        });
        ViewCompat.requestApplyInsets(insetSource);
    }

    private static void applyNavigationMargins(@NonNull final View navigation,
                                               final int initialLeft,
                                               final int initialTop,
                                               final int initialRight,
                                               final int initialBottom,
                                               @NonNull final Insets safeInsets) {
        if (navigation instanceof FloatingBottomNavigationView floatingNavigation) {
            floatingNavigation.applySystemInsets(initialLeft, initialTop,
                    initialRight, initialBottom, safeInsets);
            return;
        }
        final ViewGroup.MarginLayoutParams params =
                (ViewGroup.MarginLayoutParams) navigation.getLayoutParams();
        params.setMargins(
                initialLeft + safeInsets.left,
                initialTop + safeInsets.top,
                initialRight + safeInsets.right,
                initialBottom + safeInsets.bottom);
        navigation.setLayoutParams(params);
    }

    private static void updatePlayerSheetBottomInset(@NonNull final View playerSheet,
                                                     final int bottomInset) {
        if (!(playerSheet.getLayoutParams() instanceof CoordinatorLayout.LayoutParams params)
                || !(params.getBehavior() instanceof CustomBottomSheetBehavior behavior)) {
            return;
        }
        behavior.setBottomSystemBarInset(bottomInset);
    }

    @NonNull
    private static Insets getSafeSystemBarInsets(
            @NonNull final WindowInsetsCompat windowInsets) {
        final Insets systemBars = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars());
        final Insets displayCutout = windowInsets.getInsets(
                WindowInsetsCompat.Type.displayCutout());
        return Insets.max(systemBars, displayCutout);
    }

    @NonNull
    private static WindowInsetsCompat consumeAppliedInsets(
            @NonNull final WindowInsetsCompat windowInsets) {
        return new WindowInsetsCompat.Builder(windowInsets)
                .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.NONE)
                .setInsets(WindowInsetsCompat.Type.displayCutout(), Insets.NONE)
                .build();
    }

    public static void showSystemBars(@NonNull final Activity activity) {
        final WindowInsetsControllerCompat controller = getInsetsController(activity);
        controller.show(WindowInsetsCompat.Type.systemBars());
        controller.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
    }

    public static void hideSystemBars(@NonNull final Activity activity,
                                      final boolean hideStatusBar) {
        final WindowInsetsControllerCompat controller = getInsetsController(activity);
        int types = WindowInsetsCompat.Type.navigationBars();
        if (hideStatusBar) {
            types |= WindowInsetsCompat.Type.statusBars();
        }
        controller.hide(types);
        controller.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
    }

    public static void setLightSystemBars(@NonNull final Activity activity,
                                          final boolean light) {
        final WindowInsetsControllerCompat controller = getInsetsController(activity);
        controller.setAppearanceLightStatusBars(light);
        controller.setAppearanceLightNavigationBars(light);
    }

    @NonNull
    private static WindowInsetsControllerCompat getInsetsController(
            @NonNull final Activity activity) {
        final Window window = activity.getWindow();
        return WindowCompat.getInsetsController(window, window.getDecorView());
    }

    @SuppressWarnings("deprecation")
    private static void makeSystemBarsTransparent(@NonNull final Window window) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            window.setStatusBarColor(Color.TRANSPARENT);
            window.setNavigationBarColor(Color.TRANSPARENT);
        }
    }
}
