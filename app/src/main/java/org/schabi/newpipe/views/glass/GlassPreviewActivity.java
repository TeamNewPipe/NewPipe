package org.schabi.newpipe.views.glass;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import org.schabi.newpipe.R;

import eightbitlab.com.blurview.BlurTarget;
import eightbitlab.com.blurview.BlurView;

/**
 * Screenshot-ready preview Activity showcasing the Frost Glass Design System:
 * - Dynamic Island vertical floating glass nav pill over colorful feed cards
 * - Real backdrop blur with Dimezis BlurView (radius 22, saturation boosted 1.3x)
 * - Tiled 128px noise grain, 1dp gradient stroke + 1dp inner dark stroke
 * - Active red glass capsule indicator sliding with overshoot motion (250ms)
 * - Floating glass mini player (20dp corners)
 * - Low-end / battery saver toggle fallback demo
 */
public class GlassPreviewActivity extends AppCompatActivity {

    private BlurTarget blurTarget;
    private BlurView navPill;
    private BlurView miniPlayer;
    private View activeCapsule;

    private LinearLayout itemHome;
    private LinearLayout itemSubs;
    private LinearLayout itemLibrary;

    private ImageView iconHome;
    private ImageView iconSubs;
    private ImageView iconLibrary;

    private TextView labelHome;
    private TextView labelSubs;
    private TextView labelLibrary;

    private boolean isBlurActive = true;

    @Override
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_glass_preview);

        blurTarget = findViewById(R.id.preview_blur_target);
        navPill = findViewById(R.id.preview_nav_pill);
        miniPlayer = findViewById(R.id.preview_mini_player);
        activeCapsule = findViewById(R.id.preview_active_capsule);

        itemHome = findViewById(R.id.preview_item_home);
        itemSubs = findViewById(R.id.preview_item_subs);
        itemLibrary = findViewById(R.id.preview_item_library);

        iconHome = findViewById(R.id.preview_icon_home);
        iconSubs = findViewById(R.id.preview_icon_subs);
        iconLibrary = findViewById(R.id.preview_icon_library);

        labelHome = findViewById(R.id.preview_label_home);
        labelSubs = findViewById(R.id.preview_label_subs);
        labelLibrary = findViewById(R.id.preview_label_library);

        // Bind 6-layer Glass Design System to the floating chrome
        setupGlassChrome();

        // Bind motion physics
        setupMotionAndInteractions();
    }

    private void setupGlassChrome() {
        if (blurTarget != null) {
            // Nav pill: full radius (Dynamic Island capsule)
            if (navPill != null) {
                GlassDrawable.setupBlurView(navPill, blurTarget, GlassDrawable.RADIUS_FULL);
            }

            // Mini player: 20dp token radius
            if (miniPlayer != null) {
                GlassDrawable.setupBlurView(miniPlayer, blurTarget, GlassDrawable.RADIUS_20);
            }
        }
    }

    private void setupMotionAndInteractions() {
        // Press scale (0.96 with spring, 150ms)
        GlassMotion.applyPressScale(itemHome);
        GlassMotion.applyPressScale(itemSubs);
        GlassMotion.applyPressScale(itemLibrary);
        if (miniPlayer != null) {
            GlassMotion.applyPressScale(miniPlayer);
        }

        // Selection switching with 250ms overshoot sliding capsule
        itemHome.setOnClickListener(v -> selectTab(0, itemHome));
        itemSubs.setOnClickListener(v -> selectTab(1, itemSubs));
        itemLibrary.setOnClickListener(v -> selectTab(2, itemLibrary));

        // Toggle blur button for low-end / battery saver fallback demonstration
        final TextView toggleBlurBtn = findViewById(R.id.preview_chip_toggle_blur);
        if (toggleBlurBtn != null) {
            GlassMotion.applyPressScale(toggleBlurBtn);
            toggleBlurBtn.setOnClickListener(v -> {
                isBlurActive = !isBlurActive;
                if (navPill != null) {
                    navPill.setBlurEnabled(isBlurActive);
                }
                if (miniPlayer != null) {
                    miniPlayer.setBlurEnabled(isBlurActive);
                }
                toggleBlurBtn.setText(isBlurActive ? "Blur: ON" : "Blur: OFF (Fallback)");
            });
        }
    }

    private void selectTab(final int index, final View targetView) {
        // Slide active red capsule with overshoot interpolator (250ms)
        final float targetY = targetView.getTop();
        GlassMotion.slideIndicator(activeCapsule, targetY);

        final int colorPrimary = ContextCompat.getColor(this, R.color.glass_text_primary);
        final int colorSecondary = ContextCompat.getColor(this, R.color.glass_text_secondary);

        // Update active vs inactive iconography and typography tokens
        iconHome.setImageTintList(ColorStateList.valueOf(index == 0 ? colorPrimary : colorSecondary));
        labelHome.setTextColor(index == 0 ? colorPrimary : colorSecondary);

        iconSubs.setImageTintList(ColorStateList.valueOf(index == 1 ? colorPrimary : colorSecondary));
        labelSubs.setTextColor(index == 1 ? colorPrimary : colorSecondary);

        iconLibrary.setImageTintList(ColorStateList.valueOf(index == 2 ? colorPrimary : colorSecondary));
        labelLibrary.setTextColor(index == 2 ? colorPrimary : colorSecondary);
    }
}
