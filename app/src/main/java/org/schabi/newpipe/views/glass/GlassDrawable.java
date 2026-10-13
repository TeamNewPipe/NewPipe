package org.schabi.newpipe.views.glass;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Outline;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.PowerManager;
import android.view.View;
import android.view.ViewOutlineProvider;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.schabi.newpipe.R;

import eightbitlab.com.blurview.BlurTarget;
import eightbitlab.com.blurview.BlurView;

/**
 * Unified Glassmorphism Drawable implementing the Frost glass design system.
 *
 * Layers (bottom to top):
 * 1. Backdrop blur: BlurView radius 20-25 with backdrop saturation boosted ~1.3x.
 * 2. Tint: dark smoke (#0B0B12 at 58% alpha) + faint white wash (#FFFFFF at 7%).
 * 3. Specular highlight: top 35% gradient (#1FFFFFFF to transparent) at ~160 degrees.
 * 4. Edge: 1dp gradient stroke (#66FFFFFF to #14FFFFFF) + 1dp inner dark stroke (#33000000).
 * 5. Grain: tiled 128px monochrome noise bitmap at 4.5% alpha, tileMode=REPEAT.
 * 6. Shadow: neutral soft drop shadow (black 35%, 24dp blur, 6dp y-offset via elevation/shadow).
 */
public class GlassDrawable extends Drawable {

    // Radius scale tokens (12 / 20 / 28 / full)
    public static final int RADIUS_12 = 12;
    public static final int RADIUS_20 = 20;
    public static final int RADIUS_28 = 28;
    public static final int RADIUS_FULL = -1;

    // Color tokens
    public static final int ACCENT_RED = 0xFFE53935;
    public static final int COLOR_DARK_SMOKE = 0x940B0B12;      // #0B0B12 at 58% alpha
    public static final int COLOR_WHITE_WASH = 0x12FFFFFF;      // #FFFFFF at 7% alpha
    public static final int COLOR_SPECULAR_START = 0x1FFFFFFF;  // ~12% white
    public static final int COLOR_SPECULAR_END = 0x00FFFFFF;    // transparent
    public static final int COLOR_STROKE_TOP_LEFT = 0x66FFFFFF; // 40% white
    public static final int COLOR_STROKE_BOT_RIGHT = 0x14FFFFFF; // 8% white
    public static final int COLOR_INNER_DARK = 0x33000000;      // 20% black
    public static final int COLOR_CAPSULE_FILL = 0x59E53935;    // #E53935 at 35% alpha
    public static final int COLOR_CAPSULE_STROKE = 0xB3E53935;  // #E53935 at 70% alpha

    private final Context context;
    private final float density;
    private final int radiusToken;
    private final boolean isActiveCapsule;

    private boolean liquidSurface;
    private int ambientTop = 0x404D8EF0;
    private int ambientBottom = 0x406F375E;
    private final Paint ambientPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Paint smokeTintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint whiteWashPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint specularPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outerStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint innerStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint grainPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint capsuleFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint capsuleStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF boundsF = new RectF();
    private final RectF specularRectF = new RectF();
    private final RectF outerStrokeRectF = new RectF();
    private final RectF innerStrokeRectF = new RectF();
    private final Path roundPath = new Path();
    private final Path strokePath = new Path();

    @Nullable
    private static Bitmap sharedNoiseBitmap = null;
    @Nullable
    private BitmapShader noiseShader = null;

    public GlassDrawable(@NonNull final Context context, final int radiusToken) {
        this(context, radiusToken, false);
    }

    public GlassDrawable(@NonNull final Context context, final int radiusToken,
                         final boolean isActiveCapsule) {
        this.context = context.getApplicationContext();
        this.density = context.getResources().getDisplayMetrics().density;
        this.radiusToken = radiusToken;
        this.isActiveCapsule = isActiveCapsule;

        initPaints();
    }

    private void initPaints() {
        smokeTintPaint.setStyle(Paint.Style.FILL);
        smokeTintPaint.setColor(COLOR_DARK_SMOKE);

        whiteWashPaint.setStyle(Paint.Style.FILL);
        whiteWashPaint.setColor(COLOR_WHITE_WASH);

        specularPaint.setStyle(Paint.Style.FILL);

        outerStrokePaint.setStyle(Paint.Style.STROKE);
        outerStrokePaint.setStrokeWidth(dpToPx(1f));

        innerStrokePaint.setStyle(Paint.Style.STROKE);
        innerStrokePaint.setStrokeWidth(dpToPx(1f));
        innerStrokePaint.setColor(COLOR_INNER_DARK);

        capsuleFillPaint.setStyle(Paint.Style.FILL);
        capsuleFillPaint.setColor(COLOR_CAPSULE_FILL);

        capsuleStrokePaint.setStyle(Paint.Style.STROKE);
        capsuleStrokePaint.setStrokeWidth(dpToPx(1f));
        capsuleStrokePaint.setColor(COLOR_CAPSULE_STROKE);

        // Load 128px monochrome noise grain tile
        try {
            if (sharedNoiseBitmap == null) {
                sharedNoiseBitmap = BitmapFactory.decodeResource(context.getResources(),
                        R.drawable.glass_noise_128);
            }
            if (sharedNoiseBitmap != null) {
                noiseShader = new BitmapShader(sharedNoiseBitmap,
                        Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
                grainPaint.setShader(noiseShader);
                grainPaint.setAlpha((int) (255 * 0.045f)); // 4.5% alpha grain
            }
        } catch (final Exception ignored) {
        }
    }

    private float dpToPx(final float dp) {
        return dp * density;
    }

    private float getCornerRadius(final RectF rect) {
        if (radiusToken == RADIUS_FULL) {
            return Math.min(rect.width(), rect.height()) / 2f;
        }
        return dpToPx(radiusToken);
    }

    @Override
    protected void onBoundsChange(@NonNull final Rect bounds) {
        super.onBoundsChange(bounds);
        if (bounds.isEmpty()) {
            return;
        }

        boundsF.set(bounds);
        final float cornerRadius = getCornerRadius(boundsF);

        roundPath.reset();
        roundPath.addRoundRect(boundsF, cornerRadius, cornerRadius, Path.Direction.CW);

        // Top 35% specular highlight (reflection)
        final float specularHeight = boundsF.height() * 0.35f;
        specularRectF.set(boundsF.left, boundsF.top, boundsF.right, boundsF.top + specularHeight);

        // ~160 degree specular linear gradient
        final float xEnd = boundsF.left + boundsF.width() * 0.35f;
        final float yEnd = boundsF.top + specularHeight;
        specularPaint.setShader(new LinearGradient(
                boundsF.left, boundsF.top,
                xEnd, yEnd,
                COLOR_SPECULAR_START, COLOR_SPECULAR_END,
                Shader.TileMode.CLAMP
        ));

        if (liquidSurface) {
            ambientPaint.setShader(new LinearGradient(boundsF.left, boundsF.top,
                    boundsF.right, boundsF.bottom, ambientTop, ambientBottom,
                    Shader.TileMode.CLAMP));
        }

        // 1dp outer edge gradient stroke (top-left #66FFFFFF to bottom-right #14FFFFFF)
        final float halfStroke = dpToPx(0.5f);
        outerStrokeRectF.set(
                boundsF.left + halfStroke,
                boundsF.top + halfStroke,
                boundsF.right - halfStroke,
                boundsF.bottom - halfStroke
        );
        outerStrokePaint.setShader(new LinearGradient(
                outerStrokeRectF.left, outerStrokeRectF.top,
                outerStrokeRectF.right, outerStrokeRectF.bottom,
                COLOR_STROKE_TOP_LEFT, COLOR_STROKE_BOT_RIGHT,
                Shader.TileMode.CLAMP
        ));

        // 1dp inner dark stroke inset for depth
        final float innerInset = dpToPx(1.5f);
        innerStrokeRectF.set(
                boundsF.left + innerInset,
                boundsF.top + innerInset,
                boundsF.right - innerInset,
                boundsF.bottom - innerInset
        );
    }

    @Override
    public void draw(@NonNull final Canvas canvas) {
        final Rect bounds = getBounds();
        if (bounds.isEmpty()) {
            return;
        }

        final float cornerRadius = getCornerRadius(boundsF);

        canvas.save();
        // Clip to shape boundary
        canvas.clipPath(roundPath);

        if (isActiveCapsule) {
            // Layer 2: Red glass capsule fill (#E53935 at 35%)
            canvas.drawRoundRect(boundsF, cornerRadius, cornerRadius, capsuleFillPaint);
        } else {
            // Layer 2: Dark smoke (#0B0B12 at 58%)
            canvas.drawRoundRect(boundsF, cornerRadius, cornerRadius, smokeTintPaint);

            // Layer 2b: Faint white wash (#FFFFFF at 7%)
            canvas.drawRoundRect(boundsF, cornerRadius, cornerRadius, whiteWashPaint);
        }

        if (liquidSurface) {
            canvas.drawRoundRect(boundsF, cornerRadius, cornerRadius, ambientPaint);
        }

        // Layer 3: Specular highlight (top 35% gradient)
        canvas.drawRoundRect(specularRectF, cornerRadius, cornerRadius, specularPaint);

        // Layer 5: Subtle noise grain
        if (noiseShader != null) {
            canvas.drawRoundRect(boundsF, cornerRadius, cornerRadius, grainPaint);
        }

        canvas.restore();

        // Layer 4: Edge strokes
        if (isActiveCapsule) {
            final float strokeRadius = Math.max(0f, cornerRadius - dpToPx(0.5f));
            canvas.drawRoundRect(outerStrokeRectF, strokeRadius, strokeRadius, capsuleStrokePaint);
        } else {
            // Outer 1dp gradient highlight stroke
            final float outerRadius = Math.max(0f, cornerRadius - dpToPx(0.5f));
            canvas.drawRoundRect(outerStrokeRectF, outerRadius, outerRadius, outerStrokePaint);

            // Inner 1dp dark stroke for depth
            final float innerRadius = Math.max(0f, cornerRadius - dpToPx(1.5f));
            canvas.drawRoundRect(innerStrokeRectF, innerRadius, innerRadius, innerStrokePaint);
        }
    }

    @Override
    public void setAlpha(final int alpha) {
        smokeTintPaint.setAlpha((int) (0x94 * (alpha / 255f)));
        whiteWashPaint.setAlpha((int) (0x12 * (alpha / 255f)));
        specularPaint.setAlpha((int) (0x1F * (alpha / 255f)));
        outerStrokePaint.setAlpha((int) (0x66 * (alpha / 255f)));
        innerStrokePaint.setAlpha((int) (0x33 * (alpha / 255f)));
        capsuleFillPaint.setAlpha((int) (0x59 * (alpha / 255f)));
        capsuleStrokePaint.setAlpha((int) (0xB3 * (alpha / 255f)));
        invalidateSelf();
    }

    @Override
    public void setColorFilter(@Nullable final ColorFilter colorFilter) {
        smokeTintPaint.setColorFilter(colorFilter);
        whiteWashPaint.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Factory methods
    //////////////////////////////////////////////////////////////////////////*/

    /**
     * A separate liquid material: rail tokens and rendering are deliberately unchanged.
     * @param context resource context
     * @param radius corner radius in dp
     * @return thumbnail-tinted reflective card material
     */
    public static GlassDrawable createLiquidSurface(final Context context, final int radius) {
        final GlassDrawable drawable = new GlassDrawable(context, radius);
        drawable.liquidSurface = true;
        drawable.smokeTintPaint.setColor(0x70101826);
        drawable.whiteWashPaint.setColor(0x20FFFFFF);
        return drawable;
    }

    /**
     * Sample a few software pixels without retaining or copying the full thumbnail.
     * @param thumbnail software bitmap, or null to reset a recycled card
     */
    public void setThumbnail(@Nullable final Bitmap thumbnail) {
        if (!liquidSurface) {
            return;
        }
        ambientTop = 0x404D8EF0;
        ambientBottom = 0x406F375E;
        if (thumbnail != null && !thumbnail.isRecycled()) {
            final int width = thumbnail.getWidth();
            final int height = thumbnail.getHeight();
            ambientTop = (thumbnail.getPixel(width / 3, height / 3) & 0x00FFFFFF) | 0x50000000;
            ambientBottom = (thumbnail.getPixel(width * 2 / 3, height * 2 / 3)
                    & 0x00FFFFFF) | 0x40000000;
        }
        onBoundsChange(getBounds());
        invalidateSelf();
    }

    @Override
    public void getOutline(@NonNull final Outline outline) {
        if (liquidSurface) {
            outline.setRoundRect(getBounds(), getCornerRadius(boundsF));
        } else {
            super.getOutline(outline);
        }
    }

    public static GlassDrawable createPill(@NonNull final Context context) {
        return new GlassDrawable(context, RADIUS_FULL);
    }

    public static GlassDrawable createCard20(@NonNull final Context context) {
        return new GlassDrawable(context, RADIUS_20);
    }

    public static GlassDrawable createCard12(@NonNull final Context context) {
        return new GlassDrawable(context, RADIUS_12);
    }

    public static GlassDrawable createCard28(@NonNull final Context context) {
        return new GlassDrawable(context, RADIUS_28);
    }

    public static GlassDrawable createActiveCapsule(@NonNull final Context context) {
        return new GlassDrawable(context, RADIUS_FULL, true);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // BlurView Integration & Performance Tier Support
    //////////////////////////////////////////////////////////////////////////*/

    /**
     * Checks if device is in low-end hardware or battery-saver mode.
     * @param context resource context
     * @return whether battery saver is active
     */
    public static boolean isLowEndOrPowerSave(@NonNull final Context context) {
        try {
            final PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (pm != null && pm.isPowerSaveMode()) {
                return true;
            }
        } catch (final Exception ignored) {
        }
        return false;
    }

    /**
     * Binds a BlurView to a target with the Frost glass design system.
     * Configures radius 22f, backdrop saturation boost ~1.3x, outline clipping, and fallback.
     * @param blurView destination surface
     * @param blurTarget source backdrop
     * @param radiusToken corner radius in dp
     */
    public static void setupBlurView(@NonNull final BlurView blurView,
                                     @NonNull final BlurTarget blurTarget,
                                     final int radiusToken) {
        final Context ctx = blurView.getContext();
        final GlassDrawable glassBg = new GlassDrawable(ctx, radiusToken);
        blurView.setBackground(glassBg);
        blurView.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        blurView.setClipToOutline(true);

        if (isLowEndOrPowerSave(ctx)) {
            // Low-end / battery saver: skip blur, use glass tint + edge + grain fallback
            blurView.setBlurEnabled(false);
            return;
        }

        // Layer 1: Real backdrop blur (radius 22f, within 20-25 spec)
        blurView.setupWith(blurTarget)
                .setBlurRadius(22f)
                .setOverlayColor(Color.TRANSPARENT);

        // Backdrop saturation boost ~1.3x on Android 12+ (API 31+)
        applySaturationBoost(blurView, 1.3f);
    }

    /**
     * Boosts backdrop vibrancy so thumbnail colors glow through.
     * @param view destination view
     * @param saturation color saturation multiplier
     */
    public static void applySaturationBoost(@NonNull final View view, final float saturation) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                final ColorMatrix cm = new ColorMatrix();
                cm.setSaturation(saturation);
                final ColorMatrixColorFilter filter = new ColorMatrixColorFilter(cm);
                final android.graphics.RenderEffect colorFilterEffect =
                        android.graphics.RenderEffect.createColorFilterEffect(filter);
                view.setRenderEffect(colorFilterEffect);
            } catch (final Throwable ignored) {
            }
        }
    }
}
