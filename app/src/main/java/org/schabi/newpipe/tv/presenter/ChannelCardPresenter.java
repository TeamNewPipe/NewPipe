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
import org.schabi.newpipe.extractor.channel.ChannelInfoItem;
import org.schabi.newpipe.util.DeviceUtils;
import org.schabi.newpipe.util.Localization;
import org.schabi.newpipe.util.image.CoilHelper;

public class ChannelCardPresenter extends Presenter {

    private static final int CARD_SIZE_DP = 160;

    @Override
    public ViewHolder onCreateViewHolder(final ViewGroup parent) {
        final Context context = parent.getContext();
        final ImageCardView cardView = new ImageCardView(context);

        cardView.setFocusable(true);
        cardView.setFocusableInTouchMode(true);
        cardView.setBackgroundColor(Color.parseColor("#1F1F1F"));
        cardView.setInfoAreaBackgroundColor(Color.parseColor("#262626"));

        final int sizePx = DeviceUtils.dpToPx(CARD_SIZE_DP, context);
        cardView.setMainImageDimensions(sizePx, sizePx);

        return new ViewHolder(cardView);
    }

    @Override
    public void onBindViewHolder(final ViewHolder viewHolder, final Object item) {
        if (!(item instanceof ChannelInfoItem)) {
            return;
        }

        final ChannelInfoItem channel = (ChannelInfoItem) item;
        final ImageCardView cardView = (ImageCardView) viewHolder.view;
        final Context context = cardView.getContext();

        cardView.setTitleText(channel.getName());

        if (channel.getSubscriberCount() >= 0) {
            final String subs = Localization.shortSubscriberCount(
                    context, channel.getSubscriberCount());
            cardView.setContentText(subs);
        } else {
            cardView.setContentText(context.getString(R.string.channel));
        }

        if (channel.getThumbnails() != null && !channel.getThumbnails().isEmpty()) {
            CoilHelper.INSTANCE.loadAvatar(cardView.getMainImageView(), channel.getThumbnails());
        } else {
            cardView.getMainImageView().setImageDrawable(
                    ContextCompat.getDrawable(context, R.drawable.placeholder_person));
        }
    }

    @Override
    public void onUnbindViewHolder(final ViewHolder viewHolder) {
        final ImageCardView cardView = (ImageCardView) viewHolder.view;
        cardView.setBadgeImage(null);
        cardView.setMainImage(null);
    }
}
