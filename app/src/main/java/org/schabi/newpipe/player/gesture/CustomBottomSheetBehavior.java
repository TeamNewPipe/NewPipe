package org.schabi.newpipe.player.gesture;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.coordinatorlayout.widget.CoordinatorLayout;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.navigationrail.NavigationRailView;

import org.schabi.newpipe.R;
import org.schabi.newpipe.player.helper.PlayerHolder;
import org.schabi.newpipe.util.DeviceUtils;

import java.util.List;

public class CustomBottomSheetBehavior extends BottomSheetBehavior<FrameLayout> {
    private static final long SURFACE_RESTORE_DELAY_MILLIS = 180L;

    private final Rect globalRect = new Rect();
    private boolean skippingInterception = false;
    private final int touchSlop;
    private boolean pendingPageGesture;
    private float initialTouchX;
    private float initialTouchY;
    private int playerPeekHeight;
    private int bottomSystemBarInset;
    @Nullable
    private FrameLayout bottomSheetView;
    private final List<Integer> skipInterceptionOfElements = List.of(
            R.id.detail_content_root_layout, R.id.relatedItemsLayout,
            R.id.itemsListPanel, R.id.view_pager, R.id.tab_layout, R.id.bottomControls,
            R.id.playPauseButton, R.id.playPreviousButton, R.id.playNextButton,
            R.id.bottom_player_controls_panel);
    private final LockedOrientationFullscreenController lockedOrientationFullscreenController =
            new LockedOrientationFullscreenController();

    private boolean playerTransitionActive;
    private int playerTransitionGeneration;
    private int controlsOriginalVisibility = View.GONE;
    private float controlsOriginalAlpha = 1.0f;
    private int surfaceForegroundOriginalVisibility = View.GONE;
    private float surfaceForegroundOriginalAlpha = 1.0f;
    @Nullable
    private Drawable loadingPanelOriginalBackground;

    private final BottomSheetCallback bottomNavigationCallback = new BottomSheetCallback() {
        @Override
        public void onStateChanged(@NonNull final View bottomSheet, final int newState) {
            lockedOrientationFullscreenController.onPlayerSheetStateChanged(newState);

            if (PlayerSheetTransitionCalculator.isActiveTransitionState(newState)) {
                beginPlayerTransition(bottomSheet);
            } else if (newState == STATE_COLLAPSED || newState == STATE_EXPANDED
                    || newState == STATE_HALF_EXPANDED || newState == STATE_HIDDEN) {
                finishPlayerTransition(bottomSheet, newState);
            }

            if (newState == STATE_COLLAPSED) {
                restorePhoneOrientationAfterFullscreenCollapse(bottomSheet);
            }
            if (newState == STATE_COLLAPSED || newState == STATE_EXPANDED
                    || newState == STATE_HIDDEN) {
                updateBottomNavigation((FrameLayout) bottomSheet, newState, null);
            }
        }

        @Override
        public void onSlide(@NonNull final View bottomSheet, final float slideOffset) {
            updatePlayerTransition(bottomSheet, getState(), slideOffset);
            updateBottomNavigation((FrameLayout) bottomSheet, getState(), slideOffset);
        }
    };

    public CustomBottomSheetBehavior(@NonNull final Context context,
                                     @Nullable final AttributeSet attrs) {
        super(context, attrs);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        addBottomSheetCallback(bottomNavigationCallback);
    }

    @Override
    public boolean onLayoutChild(@NonNull final CoordinatorLayout parent,
                                 @NonNull final FrameLayout child,
                                 final int layoutDirection) {
        bottomSheetView = child;
        applyPlayerPeekHeight(child, isBottomNavigationRequested(child));
        final boolean handled = super.onLayoutChild(parent, child, layoutDirection);
        lockedOrientationFullscreenController.attach(child, getState());
        updateBottomNavigation(child, getState(), null);
        if (!playerTransitionActive) {
            resetMiniPlayerChrome(child);
        }
        return handled;
    }

    @Override
    public void setPeekHeight(final int peekHeight) {
        playerPeekHeight = peekHeight;
        if (bottomSheetView == null) {
            super.setPeekHeight(peekHeight);
        } else {
            applyPlayerPeekHeight(
                    bottomSheetView, isBottomNavigationRequested(bottomSheetView));
        }
    }

    public void onBottomNavigationVisibilityChanged() {
        if (bottomSheetView != null) {
            updateBottomNavigation(bottomSheetView, getState(), null);
        }
    }

    public void setBottomSystemBarInset(final int inset) {
        final int safeInset = Math.max(inset, 0);
        if (bottomSystemBarInset == safeInset) {
            return;
        }
        bottomSystemBarInset = safeInset;
        if (bottomSheetView != null) {
            applyPlayerPeekHeight(
                    bottomSheetView, isBottomNavigationRequested(bottomSheetView));
            bottomSheetView.requestLayout();
        }
    }

    @Override
    public boolean onInterceptTouchEvent(@NonNull final CoordinatorLayout parent,
                                         @NonNull final FrameLayout child,
                                         @NonNull final MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            initialTouchX = event.getX();
            initialTouchY = event.getY();
            pendingPageGesture = getState() == STATE_EXPANDED
                    && parent.isPointInChildBounds(child, (int) initialTouchX, (int) initialTouchY)
                    && (isShown(child, R.id.loading_progress_bar)
                    || isShown(child, R.id.loading_panel)
                    || isShown(child, R.id.error_panel));
        } else if (event.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN
                || event.getActionMasked() == MotionEvent.ACTION_CANCEL
                || event.getActionMasked() == MotionEvent.ACTION_UP) {
            pendingPageGesture = false;
        }

        // Drop following when action ends
        if (event.getAction() == MotionEvent.ACTION_CANCEL
                || event.getAction() == MotionEvent.ACTION_UP) {
            skippingInterception = false;
        }

        // Found that user still swiping, continue following
        if (skippingInterception || getState() == BottomSheetBehavior.STATE_SETTLING) {
            return false;
        }

        // The interception listens for the child view with the id "fragment_player_holder",
        // so the following two-finger gesture will be triggered only for the player view on
        // portrait and for the top controls (visible) on landscape.
        setSkipCollapsed(event.getPointerCount() == 2);
        if (event.getPointerCount() == 2) {
            return super.onInterceptTouchEvent(parent, child, event);
        }

        // Don't need to do anything if bottomSheet isn't expanded
        if (getState() == BottomSheetBehavior.STATE_EXPANDED
                && event.getAction() == MotionEvent.ACTION_DOWN) {
            // Without overriding scrolling will not work when user touches these elements
            for (final int element : skipInterceptionOfElements) {
                if (element == R.id.detail_content_root_layout
                        && (isShown(child, R.id.loading_progress_bar)
                        || isShown(child, R.id.loading_panel)
                        || isShown(child, R.id.error_panel))) {
                    // Details loading, media buffering and extraction errors use different
                    // panels. Keep their title area draggable in all three states.
                    continue;
                }
                final View view = child.findViewById(element);
                if (view != null && view.isShown()) {
                    final boolean visible = view.getGlobalVisibleRect(globalRect);
                    if (visible
                            && globalRect.contains((int) event.getRawX(), (int) event.getRawY())) {
                        // Makes bottom part of the player draggable in portrait when
                        // playbackControlRoot is hidden
                        if ((element == R.id.bottomControls
                                || element == R.id.bottom_player_controls_panel)
                                && child.findViewById(R.id.playbackControlRoot)
                                .getVisibility() != View.VISIBLE) {
                            return super.onInterceptTouchEvent(parent, child, event);
                        }
                        skippingInterception = true;
                        return false;
                    }
                }
            }
        }

        final boolean intercepted = super.onInterceptTouchEvent(parent, child, event);
        // Material's drag helper can hit a hidden sibling (e.g. the dismissed splash).
        // Its fallback requires a nested scrolling child, absent on loading/error pages.
        // Take ownership of a downward drag here; onTouchEvent then captures this sheet.
        final float deltaX = event.getX() - initialTouchX;
        final float deltaY = event.getY() - initialTouchY;
        return intercepted || (pendingPageGesture && isDraggable() && child.isShown()
                && getState() == STATE_EXPANDED
                && event.getActionMasked() == MotionEvent.ACTION_MOVE
                && deltaY > touchSlop && deltaY > Math.abs(deltaX));
    }

    private static boolean isShown(@NonNull final View root, final int id) {
        final View view = root.findViewById(id);
        return view != null && view.isShown();
    }

    private void beginPlayerTransition(@NonNull final View bottomSheet) {
        if (playerTransitionActive) {
            return;
        }
        playerTransitionActive = true;
        playerTransitionGeneration++;

        final View controls = bottomSheet.findViewById(R.id.playbackControlRoot);
        if (controls != null) {
            controlsOriginalVisibility = controls.getVisibility();
            controlsOriginalAlpha = controls.getAlpha();
        }

        final View surfaceForeground = bottomSheet.findViewById(R.id.surfaceForeground);
        if (surfaceForeground != null) {
            surfaceForegroundOriginalVisibility = surfaceForeground.getVisibility();
            surfaceForegroundOriginalAlpha = surfaceForeground.getAlpha();
        }

        final View loadingPanel = bottomSheet.findViewById(R.id.loading_panel);
        loadingPanelOriginalBackground = loadingPanel == null ? null : loadingPanel.getBackground();
    }

    private void updatePlayerTransition(@NonNull final View bottomSheet,
                                        final int state,
                                        final float slideOffset) {
        if (!PlayerSheetTransitionCalculator.isActiveTransitionState(state)) {
            return;
        }
        beginPlayerTransition(bottomSheet);

        final float expandedFraction = PlayerSheetTransitionCalculator
                .expandedFractionForState(state, slideOffset);
        final View controls = bottomSheet.findViewById(R.id.playbackControlRoot);
        if (controls != null && controlsOriginalVisibility == View.VISIBLE) {
            controls.animate().cancel();
            controls.setVisibility(View.VISIBLE);
            controls.setAlpha(controlsOriginalAlpha
                    * PlayerSheetTransitionCalculator.playerChromeAlpha(expandedFraction));
        }

        final float miniPlayerAlpha = PlayerSheetTransitionCalculator
                .miniPlayerChromeAlpha(expandedFraction);
        setMiniPlayerChromeAlpha(bottomSheet.findViewById(R.id.overlay_metadata_layout),
                miniPlayerAlpha);
        setMiniPlayerChromeAlpha(bottomSheet.findViewById(R.id.overlay_buttons_layout),
                miniPlayerAlpha);
        suppressSurfaceBlackout(bottomSheet);
    }

    private void finishPlayerTransition(@NonNull final View bottomSheet, final int state) {
        if (!playerTransitionActive) {
            return;
        }
        suppressSurfaceBlackout(bottomSheet);

        final boolean expanded = state == STATE_EXPANDED || state == STATE_HALF_EXPANDED;
        final View controls = bottomSheet.findViewById(R.id.playbackControlRoot);
        if (controls != null) {
            controls.animate().cancel();
            controls.setAlpha(controlsOriginalAlpha);
            final int targetVisibility = !expanded && controlsOriginalVisibility == View.VISIBLE
                    ? View.GONE : controlsOriginalVisibility;
            controls.setVisibility(targetVisibility);
        }
        resetMiniPlayerChrome(bottomSheet);

        playerTransitionActive = false;
        final int restoreGeneration = ++playerTransitionGeneration;
        bottomSheet.postDelayed(
                () -> restoreSurfaceProtection(bottomSheet, restoreGeneration),
                SURFACE_RESTORE_DELAY_MILLIS);
    }

    private static void setMiniPlayerChromeAlpha(@Nullable final View view, final float alpha) {
        if (view == null) {
            return;
        }
        view.animate().cancel();
        view.setAlpha(alpha);
    }

    private static void resetMiniPlayerChrome(@NonNull final View bottomSheet) {
        setMiniPlayerChromeAlpha(bottomSheet.findViewById(R.id.overlay_metadata_layout), 1.0f);
        setMiniPlayerChromeAlpha(bottomSheet.findViewById(R.id.overlay_buttons_layout), 1.0f);
    }

    private static void suppressSurfaceBlackout(@NonNull final View bottomSheet) {
        final View surfaceForeground = bottomSheet.findViewById(R.id.surfaceForeground);
        if (surfaceForeground != null) {
            surfaceForeground.animate().cancel();
            surfaceForeground.setAlpha(0.0f);
            surfaceForeground.setVisibility(View.INVISIBLE);
        }
        final View loadingPanel = bottomSheet.findViewById(R.id.loading_panel);
        if (loadingPanel != null) {
            loadingPanel.setBackgroundColor(Color.TRANSPARENT);
        }
    }

    private void restoreSurfaceProtection(@NonNull final View bottomSheet,
                                          final int restoreGeneration) {
        if (playerTransitionActive || restoreGeneration != playerTransitionGeneration) {
            return;
        }

        final View loadingPanel = bottomSheet.findViewById(R.id.loading_panel);
        final boolean loadingVisible = loadingPanel != null
                && loadingPanel.getVisibility() == View.VISIBLE;
        if (loadingPanel != null && loadingPanelOriginalBackground != null
                && (!loadingVisible || surfaceForegroundOriginalVisibility == View.VISIBLE)) {
            loadingPanel.setBackground(loadingPanelOriginalBackground);
        }

        final View surfaceForeground = bottomSheet.findViewById(R.id.surfaceForeground);
        if (surfaceForeground != null) {
            surfaceForeground.animate().cancel();
            if (surfaceForegroundOriginalVisibility == View.VISIBLE && !loadingVisible) {
                surfaceForeground.setAlpha(0.0f);
                surfaceForeground.setVisibility(View.GONE);
            } else {
                surfaceForeground.setAlpha(surfaceForegroundOriginalAlpha);
                surfaceForeground.setVisibility(surfaceForegroundOriginalVisibility);
            }
        }
        loadingPanelOriginalBackground = null;
    }

    private void restorePhoneOrientationAfterFullscreenCollapse(@NonNull final View bottomSheet) {
        final Activity activity = findActivity(bottomSheet.getContext());
        if (activity == null
                || DeviceUtils.isTablet(activity)
                || DeviceUtils.isTv(activity)
                || DeviceUtils.isDesktopMode(activity)) {
            return;
        }

        PlayerHolder.getInstance().exitMainPlayerFullscreenForMiniPlayer();
        // Navigation may already have cleared the fullscreen flag before collapsing the
        // sheet. Release its orientation request even when there is no UI state left to exit.
        activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
    }

    @Nullable
    private static Activity findActivity(@NonNull final Context context) {
        Context current = context;
        while (current instanceof ContextWrapper) {
            if (current instanceof Activity) {
                return (Activity) current;
            }
            final Context base = ((ContextWrapper) current).getBaseContext();
            if (base == current) {
                break;
            }
            current = base;
        }
        return null;
    }

    private void updateBottomNavigation(@NonNull final FrameLayout bottomSheet,
                                        final int state,
                                        @Nullable final Float slideOffset) {
        final View bottomNavigation = findRequestedNavigation(bottomSheet);
        if (bottomNavigation == null) {
            setPlayerStartMargin(bottomSheet, 0);
            applyPlayerPeekHeight(bottomSheet, false);
            return;
        }

        bottomNavigation.bringToFront();
        applyPlayerPeekHeight(bottomSheet, true);

        if (state == STATE_HIDDEN) {
            updateBottomNavigationAppearance(bottomSheet, bottomNavigation, 0.0f);
            return;
        }

        final float expandedFraction = PlayerSheetTransitionCalculator
                .expandedFractionForState(state, slideOffset);
        updateBottomNavigationAppearance(bottomSheet, bottomNavigation, expandedFraction);
    }

    private void applyPlayerPeekHeight(@NonNull final FrameLayout bottomSheet,
                                       final boolean bottomNavigationVisible) {
        final View bottomNavigation = findRequestedNavigation(bottomSheet);
        final int navigationHeight = bottomNavigation == null
                ? bottomSheet.getResources()
                .getDimensionPixelSize(R.dimen.main_bottom_navigation_height)
                : getBottomNavigationHeight(bottomNavigation);
        final int adjustedPeekHeight = PlayerSheetTransitionCalculator.adjustedPeekHeight(
                playerPeekHeight, navigationHeight, bottomNavigationVisible,
                bottomSystemBarInset);
        if (super.getPeekHeight() != adjustedPeekHeight) {
            super.setPeekHeight(adjustedPeekHeight);
        }
        // The fragment holder is already laid out inside the safe system-bar and
        // bottom-navigation bounds. Only the player portion of the peek obscures its content.
        updateMainContentBottomPadding(
                bottomSheet, getState() == STATE_HIDDEN ? 0 : playerPeekHeight);
    }

    private static void updateMainContentBottomPadding(
            @NonNull final View bottomSheet, final int bottomPadding) {
        final View content = bottomSheet.getRootView().findViewById(R.id.fragment_holder);
        if (content == null || content.getPaddingBottom() == bottomPadding) {
            return;
        }
        content.setPadding(
                content.getPaddingLeft(),
                content.getPaddingTop(),
                content.getPaddingRight(),
                bottomPadding);
    }

    private void updateBottomNavigationAppearance(@NonNull final View bottomSheet,
                                                  @NonNull final View bottomNavigation,
                                                  final float expandedFraction) {
        final float clampedFraction = PlayerSheetTransitionCalculator
                .clampExpandedFraction(expandedFraction);
        if (bottomNavigation instanceof NavigationRailView) {
            setPlayerStartMargin(bottomSheet,
                    PlayerSheetTransitionCalculator.navigationRailPlayerMargin(
                            getNavigationRailWidth(bottomNavigation), clampedFraction));
        } else {
            setPlayerStartMargin(bottomSheet, 0);
        }
        if (clampedFraction >= 1.0f) {
            bottomNavigation.setAlpha(0.0f);
            if (bottomNavigation instanceof NavigationRailView) {
                bottomNavigation.setTranslationX(-getNavigationRailWidth(bottomNavigation));
            } else {
                bottomNavigation.setTranslationY(getBottomNavigationHeight(bottomNavigation));
            }
            bottomNavigation.setVisibility(View.INVISIBLE);
            return;
        }

        bottomNavigation.setVisibility(View.VISIBLE);
        bottomNavigation.setAlpha(1.0f - clampedFraction);
        if (bottomNavigation instanceof NavigationRailView) {
            bottomNavigation.setTranslationX(
                    -getNavigationRailWidth(bottomNavigation) * clampedFraction);
            bottomNavigation.setTranslationY(0.0f);
        } else {
            bottomNavigation.setTranslationX(0.0f);
            bottomNavigation.setTranslationY(
                    PlayerSheetTransitionCalculator.bottomNavigationTranslation(
                            getBottomNavigationHeight(bottomNavigation), clampedFraction));
        }
    }

    private static int getBottomNavigationHeight(@NonNull final View view) {
        if (view instanceof NavigationRailView) {
            return 0;
        }
        return view.getHeight() > 0
                ? view.getHeight()
                : view.getResources()
                .getDimensionPixelSize(R.dimen.main_bottom_navigation_height);
    }

    private static int getNavigationRailWidth(@NonNull final View view) {
        return view.getWidth() > 0
                ? view.getWidth()
                : view.getResources().getDimensionPixelSize(R.dimen.main_navigation_rail_width);
    }

    private static void setPlayerStartMargin(@NonNull final View playerSheet,
                                             final int margin) {
        if (!(playerSheet.getLayoutParams() instanceof ViewGroup.MarginLayoutParams params)
                || params.getMarginStart() == margin) {
            return;
        }
        params.setMarginStart(margin);
        playerSheet.setLayoutParams(params);
    }

    private static boolean isBottomNavigationRequested(@NonNull final View bottomSheet) {
        return findRequestedNavigation(bottomSheet) != null;
    }

    @Nullable
    private static View findRequestedNavigation(@NonNull final View bottomSheet) {
        final View root = bottomSheet.getRootView();
        final View navigationRail = root.findViewById(R.id.main_navigation_rail);
        if (navigationRail != null && Boolean.TRUE.equals(navigationRail.getTag())) {
            return navigationRail;
        }
        final View bottomNavigation = root.findViewById(R.id.main_bottom_navigation);
        return bottomNavigation != null && Boolean.TRUE.equals(bottomNavigation.getTag())
                ? bottomNavigation : null;
    }
}
