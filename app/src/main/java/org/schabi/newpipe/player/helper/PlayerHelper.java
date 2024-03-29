package org.schabi.newpipe.player.helper;

import static com.google.android.exoplayer2.Player.REPEAT_MODE_ALL;
import static com.google.android.exoplayer2.Player.REPEAT_MODE_OFF;
import static com.google.android.exoplayer2.Player.REPEAT_MODE_ONE;
import static org.schabi.newpipe.player.helper.PlayerHelper.AutoplayType.AUTOPLAY_TYPE_ALWAYS;
import static org.schabi.newpipe.player.helper.PlayerHelper.AutoplayType.AUTOPLAY_TYPE_NEVER;
import static org.schabi.newpipe.player.helper.PlayerHelper.AutoplayType.AUTOPLAY_TYPE_WIFI;
import static org.schabi.newpipe.player.helper.PlayerHelper.MinimizeMode.MINIMIZE_ON_EXIT_MODE_BACKGROUND;
import static org.schabi.newpipe.player.helper.PlayerHelper.MinimizeMode.MINIMIZE_ON_EXIT_MODE_NONE;
import static org.schabi.newpipe.player.helper.PlayerHelper.MinimizeMode.MINIMIZE_ON_EXIT_MODE_POPUP;
import static java.lang.annotation.RetentionPolicy.SOURCE;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.view.accessibility.CaptioningManager;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player.RepeatMode;
import com.google.android.exoplayer2.SeekParameters;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.ExoTrackSelection;
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout;
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout.ResizeMode;
import com.google.android.exoplayer2.ui.CaptionStyleCompat;
import com.google.android.exoplayer2.util.MimeTypes;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.SubtitlesStream;
import org.schabi.newpipe.extractor.utils.Utils;
import org.schabi.newpipe.player.Player;
import org.schabi.newpipe.player.playqueue.PlayQueue;
import org.schabi.newpipe.player.playqueue.PlayQueueItem;
import org.schabi.newpipe.player.playqueue.SinglePlayQueue;
import org.schabi.newpipe.util.ListHelper;

import java.lang.annotation.Retention;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Formatter;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public final class PlayerHelper {
    private static final StringBuilder STRING_BUILDER = new StringBuilder();
    private static final Formatter STRING_FORMATTER =
            new Formatter(STRING_BUILDER, Locale.getDefault());
    private static final NumberFormat SPEED_FORMATTER = new DecimalFormat("0.##x");
    private static final NumberFormat PITCH_FORMATTER = new DecimalFormat("##%");

    @Retention(SOURCE)
    @IntDef({AUTOPLAY_TYPE_ALWAYS, AUTOPLAY_TYPE_WIFI,
            AUTOPLAY_TYPE_NEVER})
    public @interface AutoplayType {
        int AUTOPLAY_TYPE_ALWAYS = 0;
        int AUTOPLAY_TYPE_WIFI = 1;
        int AUTOPLAY_TYPE_NEVER = 2;
    }

    @Retention(SOURCE)
    @IntDef({MINIMIZE_ON_EXIT_MODE_NONE, MINIMIZE_ON_EXIT_MODE_BACKGROUND,
            MINIMIZE_ON_EXIT_MODE_POPUP})
    public @interface MinimizeMode {
        int MINIMIZE_ON_EXIT_MODE_NONE = 0;
        int MINIMIZE_ON_EXIT_MODE_BACKGROUND = 1;
        int MINIMIZE_ON_EXIT_MODE_POPUP = 2;
    }

    private PlayerHelper() {
    }

    ////////////////////////////////////////////////////////////////////////////
    // Exposed helpers
    ////////////////////////////////////////////////////////////////////////////

    @NonNull
    public static String getTimeString(final int milliSeconds) {
        final int seconds = (milliSeconds % 60000) / 1000;
        final int minutes = (milliSeconds % 3600000) / 60000;
        final int hours = (milliSeconds % 86400000) / 3600000;
        final int days = (milliSeconds % (86400000 * 7)) / 86400000;

        STRING_BUILDER.setLength(0);
        return (days > 0
                ? STRING_FORMATTER.format("%d:%02d:%02d:%02d", days, hours, minutes, seconds)
                : hours > 0
                ? STRING_FORMATTER.format("%d:%02d:%02d", hours, minutes, seconds)
                : STRING_FORMATTER.format("%02d:%02d", minutes, seconds)
        ).toString();
    }

    @NonNull
    public static String formatSpeed(final double speed) {
        return SPEED_FORMATTER.format(speed);
    }

    @NonNull
    public static String formatPitch(final double pitch) {
        return PITCH_FORMATTER.format(pitch);
    }

    @NonNull
    public static String subtitleMimeTypesOf(@NonNull final MediaFormat format) {
        switch (format) {
            case VTT:
                return MimeTypes.TEXT_VTT;
            case TTML:
                return MimeTypes.APPLICATION_TTML;
            default:
                throw new IllegalArgumentException("Unrecognized mime type: " + format.name());
        }
    }

    @NonNull
    public static String captionLanguageOf(@NonNull final Context context,
                                           @NonNull final SubtitlesStream subtitles) {
        final String displayName = subtitles.getDisplayLanguageName();
        return displayName + (subtitles.isAutoGenerated()
                ? " (" + context.getString(R.string.caption_auto_generated) + ")" : "");
    }

    @NonNull
    public static String captionLanguageStemOf(@NonNull final String language) {
        if (!language.contains("(") || !language.contains(")")) {
            return language;
        }

        if (language.startsWith("(")) {
            // language text is right-to-left
            final String[] parts = language.split("\\)");
            return parts[parts.length - 1].trim();
        }

        return language.split("\\(")[0].trim();
    }

    @NonNull
    public static String resizeTypeOf(@NonNull final Context context,
                                      @ResizeMode final int resizeMode) {
        switch (resizeMode) {
            case AspectRatioFrameLayout.RESIZE_MODE_FIT:
                return context.getResources().getString(R.string.resize_fit);
            case AspectRatioFrameLayout.RESIZE_MODE_FILL:
                return context.getResources().getString(R.string.resize_fill);
            case AspectRatioFrameLayout.RESIZE_MODE_ZOOM:
                return context.getResources().getString(R.string.resize_zoom);
            case AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT:
            case AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH:
            default:
                throw new IllegalArgumentException("Unrecognized resize mode: " + resizeMode);
        }
    }

    /**
     * Given a {@link StreamInfo} and the existing queue items,
     * provide the {@link SinglePlayQueue} consisting of the next video for auto queueing.
     * <p>
     * This method detects and prevents cycles by naively checking
     * if a candidate next video's url already exists in the existing items.
     * </p>
     * <p>
     * The first item in {@link StreamInfo#getRelatedItems()} is checked first.
     * If it is non-null and is not part of the existing items, it will be used as the next stream.
     * Otherwise, a random stream with non-repeating url will be selected
     * from the {@link StreamInfo#getRelatedItems()}. Non-stream items are ignored.
     * </p>
     *
     * @param info          currently playing stream
     * @param existingItems existing items in the queue
     * @return {@link SinglePlayQueue} with the next stream to queue
     */
    @Nullable
    public static PlayQueue autoQueueOf(@NonNull final StreamInfo info,
                                        @NonNull final List<PlayQueueItem> existingItems) {
        final Set<String> urls = new HashSet<>(existingItems.size());
        for (final PlayQueueItem item : existingItems) {
            urls.add(item.getUrl());
        }

        final List<InfoItem> relatedItems = info.getRelatedItems();
        if (Utils.isNullOrEmpty(relatedItems)) {
            return null;
        }

        if (relatedItems.get(0) instanceof StreamInfoItem
                && !urls.contains(relatedItems.get(0).getUrl())) {
            return getAutoQueuedSinglePlayQueue((StreamInfoItem) relatedItems.get(0));
        }

        final List<StreamInfoItem> autoQueueItems = new ArrayList<>();
        for (final InfoItem item : relatedItems) {
            if (item instanceof StreamInfoItem && !urls.contains(item.getUrl())) {
                autoQueueItems.add((StreamInfoItem) item);
            }
        }

        Collections.shuffle(autoQueueItems);
        return autoQueueItems.isEmpty()
                ? null : getAutoQueuedSinglePlayQueue(autoQueueItems.get(0));
    }

    ////////////////////////////////////////////////////////////////////////////
    // Settings Resolution
    ////////////////////////////////////////////////////////////////////////////

    public static boolean isResumeAfterAudioFocusGain(@NonNull final Context context) {
        return getPreferences(context)
                .getBoolean(context.getString(R.string.resume_on_audio_focus_gain_key), false);
    }

    public static String getActionForRightGestureSide(@NonNull final Context context) {
        return getPreferences(context)
                .getString(context.getString(R.string.right_gesture_control_key),
                        context.getString(R.string.default_right_gesture_control_value));
    }

    public static String getActionForLeftGestureSide(@NonNull final Context context) {
        return getPreferences(context)
                .getString(context.getString(R.string.left_gesture_control_key),
                        context.getString(R.string.default_left_gesture_control_value));
    }

    public static boolean isStartMainPlayerFullscreenEnabled(@NonNull final Context context) {
        return getPreferences(context)
                .getBoolean(context.getString(R.string.start_main_player_fullscreen_key), false);
    }

    public static boolean isAutoQueueEnabled(@NonNull final Context context) {
        return getPreferences(context)
                .getBoolean(context.getString(R.string.auto_queue_key), false);
    }

    public static boolean isClearingQueueConfirmationRequired(@NonNull final Context context) {
        return getPreferences(context)
                .getBoolean(context.getString(R.string.clear_queue_confirmation_key), false);
    }

    @MinimizeMode
    public static int getMinimizeOnExitAction(@NonNull final Context context) {
        final String action = getPreferences(context)
                .getString(context.getString(R.string.minimize_on_exit_key), "");
        if (action.equals(context.getString(R.string.minimize_on_exit_popup_key))) {
            return MINIMIZE_ON_EXIT_MODE_POPUP;
        } else if (action.equals(context.getString(R.string.minimize_on_exit_none_key))) {
            return MINIMIZE_ON_EXIT_MODE_NONE;
        } else {
            return MINIMIZE_ON_EXIT_MODE_BACKGROUND; // default
        }
    }

    @AutoplayType
    public static int getAutoplayType(@NonNull final Context context) {
        final String type = getPreferences(context).getString(
                context.getString(R.string.autoplay_key), "");
        if (type.equals(context.getString(R.string.autoplay_always_key))) {
            return AUTOPLAY_TYPE_ALWAYS;
        } else if (type.equals(context.getString(R.string.autoplay_never_key))) {
            return AUTOPLAY_TYPE_NEVER;
        } else {
            return AUTOPLAY_TYPE_WIFI; // default
        }
    }

    public static boolean isAutoplayAllowedByUser(@NonNull final Context context) {
        switch (PlayerHelper.getAutoplayType(context)) {
            case PlayerHelper.AutoplayType.AUTOPLAY_TYPE_NEVER:
                return false;
            case PlayerHelper.AutoplayType.AUTOPLAY_TYPE_WIFI:
                return !ListHelper.isMeteredNetwork(context);
            case PlayerHelper.AutoplayType.AUTOPLAY_TYPE_ALWAYS:
            default:
                return true;
        }
    }

    @NonNull
    public static SeekParameters getSeekParameters(@NonNull final Context context) {
        return isUsingInexactSeek(context) ? SeekParameters.CLOSEST_SYNC : SeekParameters.EXACT;
    }

    public static long getPreferredCacheSize() {
        return 64 * 1024 * 1024L;
    }

    public static long getPreferredFileSize() {
        return 2 * 1024 * 1024L; // ExoPlayer CacheDataSink.MIN_RECOMMENDED_FRAGMENT_SIZE
    }

    @NonNull
    public static ExoTrackSelection.Factory getQualitySelector() {
        return new AdaptiveTrackSelection.Factory(
                1000,
                AdaptiveTrackSelection.DEFAULT_MAX_DURATION_FOR_QUALITY_DECREASE_MS,
                AdaptiveTrackSelection.DEFAULT_MIN_DURATION_TO_RETAIN_AFTER_DISCARD_MS,
                AdaptiveTrackSelection.DEFAULT_BANDWIDTH_FRACTION);
    }

    public static boolean isUsingDSP() {
        return true;
    }

    @NonNull
    public static CaptionStyleCompat getCaptionStyle(@NonNull final Context context) {
        final CaptioningManager captioningManager = ContextCompat.getSystemService(context,
                CaptioningManager.class);
        if (captioningManager == null || !captioningManager.isEnabled()) {
            return CaptionStyleCompat.DEFAULT;
        }

        return CaptionStyleCompat.createFromCaptionStyle(captioningManager.getUserStyle());
    }

    /**
     * Get scaling for captions based on system font scaling.
     * <p>Options:</p>
     * <ul>
     *     <li>Very small: 0.25f</li>
     *     <li>Small: 0.5f</li>
     *     <li>Normal: 1.0f</li>
     *     <li>Large: 1.5f</li>
     *     <li>Very large: 2.0f</li>
     * </ul>
     *
     * @param context Android app context
     * @return caption scaling
     */
    public static float getCaptionScale(@NonNull final Context context) {
        final CaptioningManager captioningManager = ContextCompat.getSystemService(context,
                CaptioningManager.class);
        if (captioningManager == null || !captioningManager.isEnabled()) {
            return 1.0f;
        }

        return captioningManager.getFontScale();
    }

    /**
     * @param context the Android context
     * @return the screen brightness to use. A value less than 0 (the default) means to use the
     * preferred screen brightness
     */
    public static float getScreenBrightness(@NonNull final Context context) {
        final SharedPreferences sp = getPreferences(context);
        final long timestamp =
                sp.getLong(context.getString(R.string.screen_brightness_timestamp_key), 0);
        // Hypothesis: 4h covers a viewing block, e.g. evening.
        // External lightning conditions will change in the next
        // viewing block so we fall back to the default brightness
        if ((System.currentTimeMillis() - timestamp) > TimeUnit.HOURS.toMillis(4)) {
            return -1;
        } else {
            return sp.getFloat(context.getString(R.string.screen_brightness_key), -1);
        }
    }

    public static void setScreenBrightness(@NonNull final Context context,
                                           final float screenBrightness) {
        getPreferences(context).edit()
                .putFloat(context.getString(R.string.screen_brightness_key), screenBrightness)
                .putLong(context.getString(R.string.screen_brightness_timestamp_key),
                        System.currentTimeMillis())
                .apply();
    }

    public static boolean globalScreenOrientationLocked(final Context context) {
        // 1: Screen orientation changes using accelerometer
        // 0: Screen orientation is locked
        // if the accelerometer sensor is missing completely, assume locked orientation
        return android.provider.Settings.System.getInt(
                context.getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, 0) == 0
                    || !context.getPackageManager()
                        .hasSystemFeature(PackageManager.FEATURE_SENSOR_ACCELEROMETER);
    }

    public static int getProgressiveLoadIntervalBytes(@NonNull final Context context) {
        final String preferredIntervalBytes = getPreferences(context).getString(
                context.getString(R.string.progressive_load_interval_key),
                context.getString(R.string.progressive_load_interval_default_value));

        if (context.getString(R.string.progressive_load_interval_exoplayer_default_value)
                .equals(preferredIntervalBytes)) {
            return ProgressiveMediaSource.DEFAULT_LOADING_CHECK_INTERVAL_BYTES;
        }
        // Keeping the same KiB unit used by ProgressiveMediaSource
        return Integer.parseInt(preferredIntervalBytes) * 1024;
    }

    ////////////////////////////////////////////////////////////////////////////
    // Private helpers
    ////////////////////////////////////////////////////////////////////////////

    @NonNull
    private static SharedPreferences getPreferences(@NonNull final Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }

    private static boolean isUsingInexactSeek(@NonNull final Context context) {
        return getPreferences(context)
                .getBoolean(context.getString(R.string.use_inexact_seek_key), false);
    }

    private static SinglePlayQueue getAutoQueuedSinglePlayQueue(
            final StreamInfoItem streamInfoItem) {
        final SinglePlayQueue singlePlayQueue = new SinglePlayQueue(streamInfoItem);
        Objects.requireNonNull(singlePlayQueue.getItem()).setAutoQueued(true);
        return singlePlayQueue;
    }


    ////////////////////////////////////////////////////////////////////////////
    // Utils used by player
    ////////////////////////////////////////////////////////////////////////////

    @RepeatMode
    public static int nextRepeatMode(@RepeatMode final int repeatMode) {
        switch (repeatMode) {
            case REPEAT_MODE_OFF:
                return REPEAT_MODE_ONE;
            case REPEAT_MODE_ONE:
                return REPEAT_MODE_ALL;
            case REPEAT_MODE_ALL:
            default:
                return REPEAT_MODE_OFF;
        }
    }

    @ResizeMode
    public static int retrieveResizeModeFromPrefs(final Player player) {
        return player.getPrefs().getInt(player.getContext().getString(R.string.last_resize_mode),
                AspectRatioFrameLayout.RESIZE_MODE_FIT);
    }

    @SuppressLint("SwitchIntDef") // only fit, fill and zoom are supported by NewPipe
    @ResizeMode
    public static int nextResizeModeAndSaveToPrefs(final Player player,
                                                   @ResizeMode final int resizeMode) {
        final int newResizeMode;
        switch (resizeMode) {
            case AspectRatioFrameLayout.RESIZE_MODE_FIT:
                newResizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL;
                break;
            case AspectRatioFrameLayout.RESIZE_MODE_FILL:
                newResizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM;
                break;
            case AspectRatioFrameLayout.RESIZE_MODE_ZOOM:
            default:
                newResizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT;
                break;
        }

        // save the new resize mode so it can be restored in a future session
        player.getPrefs().edit().putInt(
                player.getContext().getString(R.string.last_resize_mode), newResizeMode).apply();
        return newResizeMode;
    }

    public static PlaybackParameters retrievePlaybackParametersFromPrefs(final Player player) {
        final float speed = player.getPrefs().getFloat(player.getContext().getString(
                R.string.playback_speed_key), player.getPlaybackSpeed());
        final float pitch = player.getPrefs().getFloat(player.getContext().getString(
                R.string.playback_pitch_key), player.getPlaybackPitch());
        return new PlaybackParameters(speed, pitch);
    }

    public static void savePlaybackParametersToPrefs(final Player player,
                                                     final float speed,
                                                     final float pitch,
                                                     final boolean skipSilence) {
        player.getPrefs().edit()
                .putFloat(player.getContext().getString(R.string.playback_speed_key), speed)
                .putFloat(player.getContext().getString(R.string.playback_pitch_key), pitch)
                .putBoolean(player.getContext().getString(R.string.playback_skip_silence_key),
                        skipSilence)
                .apply();
    }

    public static float getMinimumVideoHeight(final float width) {
        return width / (16.0f / 9.0f); // Respect the 16:9 ratio that most videos have
    }

    public static int retrieveSeekDurationFromPreferences(final Player player) {
        return Integer.parseInt(Objects.requireNonNull(player.getPrefs().getString(
                player.getContext().getString(R.string.seek_duration_key),
                player.getContext().getString(R.string.seek_duration_default_value))));
    }
}
