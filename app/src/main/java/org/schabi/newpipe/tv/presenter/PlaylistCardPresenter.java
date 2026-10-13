/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.tv.presenter;

import android.content.Context;
import android.graphics.Color;
import android.view.ViewGroup;

import androidx.core.content.ContextCompat;
import androidx.leanback.widget.ImageCardView;
import androidx.leanback.widget.Presenter;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem;
import org.schabi.newpipe.util.DeviceUtils;
import org.schabi.newpipe.util.image.CoilHelper;

public class PlaylistCardPresenter extends Presenter {

    private static final int CARD_WIDTH_DP = 260;
    private static final int CARD_HEIGHT_DP = 150;

    @Override
    public ViewHolder onCreateViewHolder(final ViewGroup parent) {
        final Context context = parent.getContext();
        final ImageCardView cardView = new ImageCardView(context);

        cardView.setFocusable(true);
        cardView.setFocusableInTouchMode(true);
        cardView.setBackgroundColor(Color.parseColor("#1F1F1F"));
        cardView.setInfoAreaBackgroundColor(Color.parseColor("#262626"));

        final int widthPx = DeviceUtils.dpToPx(CARD_WIDTH_DP, context);
        final int heightPx = DeviceUtils.dpToPx(CARD_HEIGHT_DP, context);
        cardView.setMainImageDimensions(widthPx, heightPx);

        return new ViewHolder(cardView);
    }

    @Override
    public void onBindViewHolder(final ViewHolder viewHolder, final Object item) {
        if (!(item instanceof PlaylistInfoItem)) {
            return;
        }

        final PlaylistInfoItem playlist = (PlaylistInfoItem) item;
        final ImageCardView cardView = (ImageCardView) viewHolder.view;
        final Context context = cardView.getContext();

        cardView.setTitleText(playlist.getName());

        final StringBuilder subtitle = new StringBuilder();
        if (playlist.getUploaderName() != null) {
            subtitle.append(playlist.getUploaderName());
        }
        if (playlist.getStreamCount() >= 0) {
            if (subtitle.length() > 0) {
                subtitle.append(" • ");
            }
            subtitle.append(context.getResources().getQuantityString(
                    R.plurals.videos, (int) playlist.getStreamCount(), playlist.getStreamCount()));
        }
        cardView.setContentText(subtitle.toString());

        if (playlist.getThumbnails() != null && !playlist.getThumbnails().isEmpty()) {
            CoilHelper.INSTANCE.loadPlaylistThumbnail(cardView.getMainImageView(),
                    playlist.getThumbnails());
        } else {
            cardView.getMainImageView().setImageDrawable(
                    ContextCompat.getDrawable(context, R.drawable.placeholder_thumbnail_playlist));
        }
    }

    @Override
    public void onUnbindViewHolder(final ViewHolder viewHolder) {
        final ImageCardView cardView = (ImageCardView) viewHolder.view;
        cardView.setBadgeImage(null);
        cardView.setMainImage(null);
    }
}
