/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.util;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.schabi.newpipe.R;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Applies the user-selected behavior for the medium-confidence AiSList warnlist. */
public final class AiSListContentHelper {
    private AiSListContentHelper() {
    }

    public enum WarnBehavior {
        IGNORE("ignore"),
        LABEL("label"),
        WARN("warn"),
        HIDE("hide");

        @NonNull
        private final String preferenceValue;

        WarnBehavior(@NonNull final String preferenceValue) {
            this.preferenceValue = preferenceValue;
        }

        @NonNull
        static WarnBehavior fromPreferenceValue(@Nullable final String value) {
            for (final WarnBehavior behavior : values()) {
                if (behavior.preferenceValue.equals(value)) {
                    return behavior;
                }
            }
            return LABEL;
        }
    }

    @NonNull
    public static WarnBehavior getWarnBehavior(@NonNull final Context context) {
        final String key = context.getString(R.string.aislist_warn_behavior_key);
        final String fallback = context.getString(R.string.aislist_warn_behavior_label_value);
        return WarnBehavior.fromPreferenceValue(
                PreferenceManager.getDefaultSharedPreferences(context)
                        .getString(key, fallback));
    }

    public static boolean isActive(@NonNull final Context context) {
        return AiSListRepository.isEnabled(context)
                && PreferenceManager.getDefaultSharedPreferences(context).getBoolean(
                        context.getString(R.string.content_blocking_enabled_key), true);
    }

    public static boolean shouldLabel(@NonNull final Context context,
                                      final int serviceId,
                                      @Nullable final String channelUrl,
                                      @Nullable final String channelName) {
        if (!isActive(context)
                || !AiSListRepository.isWarnListed(
                        context, serviceId, channelUrl, channelName)) {
            return false;
        }
        final WarnBehavior behavior = getWarnBehavior(context);
        return behavior == WarnBehavior.LABEL || behavior == WarnBehavior.WARN;
    }

    public static boolean shouldWarnBeforePlayback(@NonNull final Context context,
                                                   final int serviceId,
                                                   @Nullable final String channelUrl,
                                                   @Nullable final String channelName) {
        return isActive(context)
                && getWarnBehavior(context) == WarnBehavior.WARN
                && AiSListRepository.isWarnListed(
                        context, serviceId, channelUrl, channelName);
    }

    public static boolean shouldHideWarnlisted(@NonNull final Context context) {
        return isActive(context)
                && getWarnBehavior(context) == WarnBehavior.HIDE;
    }

    @NonNull
    public static Set<String> hiddenChannelEntries(@NonNull final Context context) {
        if (!isActive(context)) {
            return Collections.emptySet();
        }
        final Set<String> hidden = new HashSet<>(AiSListRepository.blockEntries(context));
        if (shouldHideWarnlisted(context)) {
            hidden.addAll(AiSListRepository.warnEntries(context));
        }
        return hidden;
    }

    public static AlertDialog showPlaybackWarning(@NonNull final Context context,
                                           @Nullable final String channelUrl,
                                           @Nullable final String channelName,
                                           @NonNull final Runnable playAnyway) {
        final String displayName = channelName == null || channelName.trim().isEmpty()
                ? context.getString(R.string.aislist_warnlist_channel_fallback)
                : channelName;
        return new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.aislist_possible_ai_title)
                .setMessage(context.getString(
                        R.string.aislist_warnlist_playback_message,
                        displayName))
                .setNegativeButton(R.string.cancel, null)
                .setNeutralButton(R.string.block_channel, (dialog, which) ->
                        ContentBlockingHelper.blockChannel(context, channelUrl, channelName))
                .setPositiveButton(R.string.aislist_play_anyway, (dialog, which) ->
                        playAnyway.run())
                .show();
    }
}
