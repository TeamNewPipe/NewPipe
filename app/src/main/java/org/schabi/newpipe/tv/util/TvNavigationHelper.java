/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.tv.util;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.schabi.newpipe.player.playqueue.PlayQueue;
import org.schabi.newpipe.tv.ui.TvChannelActivity;
import org.schabi.newpipe.tv.ui.TvPlayerActivity;
import org.schabi.newpipe.tv.ui.TvSearchActivity;
import org.schabi.newpipe.tv.ui.TvSettingsActivity;
import org.schabi.newpipe.tv.ui.TvVideoDetailsActivity;

public final class TvNavigationHelper {

    public static final String KEY_SERVICE_ID = "tv_service_id";
    public static final String KEY_URL = "tv_url";
    public static final String KEY_TITLE = "tv_title";
    public static final String KEY_SEARCH_QUERY = "tv_search_query";

    private TvNavigationHelper() {
    }

    public static void openVideoDetails(@NonNull final Context context,
                                        final int serviceId,
                                        @NonNull final String url,
                                        @Nullable final String title) {
        final Intent intent = new Intent(context, TvVideoDetailsActivity.class);
        intent.putExtra(KEY_SERVICE_ID, serviceId);
        intent.putExtra(KEY_URL, url);
        intent.putExtra(KEY_TITLE, title);
        context.startActivity(intent);
    }

    public static void playOnTv(@NonNull final Context context,
                                @NonNull final PlayQueue playQueue) {
        final Intent intent = new Intent(context, TvPlayerActivity.class);
        // Save or pass play queue
        TvPlayerActivity.setPendingPlayQueue(playQueue);
        context.startActivity(intent);
    }

    public static void openChannel(@NonNull final Context context,
                                   final int serviceId,
                                   @NonNull final String url,
                                   @Nullable final String title) {
        final Intent intent = new Intent(context, TvChannelActivity.class);
        intent.putExtra(KEY_SERVICE_ID, serviceId);
        intent.putExtra(KEY_URL, url);
        intent.putExtra(KEY_TITLE, title);
        context.startActivity(intent);
    }

    public static void openSearch(@NonNull final Context context,
                                  @Nullable final String query) {
        final Intent intent = new Intent(context, TvSearchActivity.class);
        if (query != null && !query.isEmpty()) {
            intent.putExtra(KEY_SEARCH_QUERY, query);
        }
        context.startActivity(intent);
    }

    public static void openSettings(@NonNull final Context context) {
        final Intent intent = new Intent(context, TvSettingsActivity.class);
        context.startActivity(intent);
    }
}
