package org.schabi.newpipe.info_list.holder;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import org.schabi.newpipe.R;
import org.schabi.newpipe.database.subscription.SubscriptionEntity;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.channel.ChannelInfoItem;
import org.schabi.newpipe.extractor.utils.Utils;
import org.schabi.newpipe.info_list.InfoItemBuilder;
import org.schabi.newpipe.local.history.HistoryRecordManager;
import org.schabi.newpipe.local.subscription.SubscriptionManager;
import org.schabi.newpipe.util.Localization;
import org.schabi.newpipe.util.image.CoilHelper;
import org.schabi.newpipe.util.image.ImageStrategy;

import java.util.List;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class ChannelMiniInfoItemHolder extends InfoItemHolder {
    private final ImageView itemThumbnailView;
    private final TextView itemTitleView;
    private final TextView itemAdditionalDetailView;
    private final TextView itemChannelDescriptionView;
    /** YewPlay: subscribe button (only in the full channel item). */
    @Nullable
    private final TextView subscribeButton;

    ChannelMiniInfoItemHolder(final InfoItemBuilder infoItemBuilder, final int layoutId,
                              final ViewGroup parent) {
        super(infoItemBuilder, layoutId, parent);

        itemThumbnailView = itemView.findViewById(R.id.itemThumbnailView);
        itemTitleView = itemView.findViewById(R.id.itemTitleView);
        itemAdditionalDetailView = itemView.findViewById(R.id.itemAdditionalDetails);
        itemChannelDescriptionView = itemView.findViewById(R.id.itemChannelDescriptionView);
        subscribeButton = itemView.findViewById(R.id.yp_channel_subscribe);
    }

    public ChannelMiniInfoItemHolder(final InfoItemBuilder infoItemBuilder,
                                     final ViewGroup parent) {
        this(infoItemBuilder, R.layout.list_channel_mini_item, parent);
    }

    @Override
    public void updateFromItem(final InfoItem infoItem,
                               final HistoryRecordManager historyRecordManager) {
        if (!(infoItem instanceof ChannelInfoItem)) {
            return;
        }
        final ChannelInfoItem item = (ChannelInfoItem) infoItem;

        itemTitleView.setText(item.getName());
        itemTitleView.setSelected(true);

        final String detailLine = getDetailLine(item);
        if (detailLine == null) {
            itemAdditionalDetailView.setVisibility(View.GONE);
        } else {
            itemAdditionalDetailView.setVisibility(View.VISIBLE);
            itemAdditionalDetailView.setText(getDetailLine(item));
        }

        CoilHelper.INSTANCE.loadAvatar(itemThumbnailView, item.getThumbnails());
        bindSubscribeButton(item);

        itemView.setOnClickListener(view -> {
            if (itemBuilder.getOnChannelSelectedListener() != null) {
                itemBuilder.getOnChannelSelectedListener().selected(item);
            }
        });

        itemView.setOnLongClickListener(view -> {
            if (itemBuilder.getOnChannelSelectedListener() != null) {
                itemBuilder.getOnChannelSelectedListener().held(item);
            }
            return true;
        });

        if (itemChannelDescriptionView != null) {
            // itemChannelDescriptionView will be null in the mini variant
            if (Utils.isBlank(item.getDescription())) {
                itemChannelDescriptionView.setVisibility(View.GONE);
            } else {
                itemChannelDescriptionView.setVisibility(View.VISIBLE);
                itemChannelDescriptionView.setText(item.getDescription());
                // setMaxLines utilize the line space for description if the additional details
                // (sub / video count) are not present.
                // Case1: 2 lines of description + 1 line additional details
                // Case2: 3 lines of description (additionalDetails is GONE)
                itemChannelDescriptionView.setMaxLines(getDescriptionMaxLineCount(detailLine));
            }
        }
    }

    /**
     * YewPlay: shows "Subscribe" or "Subscribed" for the channel, using the same local
     * subscriptions as the channel page, and toggles it on tap.
     *
     * @param item the channel of this row
     */
    private void bindSubscribeButton(final ChannelInfoItem item) {
        if (subscribeButton == null) {
            return;
        }
        subscribeButton.setVisibility(View.GONE);
        subscribeButton.setTag(item.getUrl());
        final Context context = itemBuilder.getContext();
        final SubscriptionManager manager = new SubscriptionManager(context);
        manager.subscriptionTable()
                .getSubscriptionFlowable(item.getServiceId(), item.getUrl())
                .firstElement()
                .map(List::isEmpty)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(notSubscribed -> {
                    // the row may have been recycled for another channel meanwhile
                    if (item.getUrl().equals(subscribeButton.getTag())) {
                        showSubscribed(context, !notSubscribed);
                        subscribeButton.setVisibility(View.VISIBLE);
                        subscribeButton.setOnClickListener(v -> toggleSubscription(
                                context, manager, item));
                    }
                }, e -> subscribeButton.setVisibility(View.GONE));
    }

    private void toggleSubscription(final Context context, final SubscriptionManager manager,
                                    final ChannelInfoItem item) {
        final boolean subscribe = subscribeButton != null
                && context.getString(R.string.subscribe_button_title)
                .contentEquals(subscribeButton.getText());
        final Completable action;
        if (subscribe) {
            final SubscriptionEntity entity = new SubscriptionEntity();
            entity.setServiceId(item.getServiceId());
            entity.setUrl(item.getUrl());
            entity.setName(item.getName());
            entity.setAvatarUrl(ImageStrategy.imageListToDbUrl(item.getThumbnails()));
            entity.setDescription(item.getDescription());
            if (item.getSubscriberCount() >= 0) {
                entity.setSubscriberCount(item.getSubscriberCount());
            }
            action = Completable.fromAction(() -> manager.insertSubscription(entity));
        } else {
            action = manager.deleteSubscription(item.getServiceId(), item.getUrl());
        }
        showSubscribed(context, subscribe);
        action.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(() -> { }, e -> showSubscribed(context, !subscribe));
    }

    private void showSubscribed(final Context context, final boolean subscribed) {
        if (subscribeButton == null) {
            return;
        }
        subscribeButton.setText(subscribed ? R.string.subscribed_button_title
                : R.string.subscribe_button_title);
        subscribeButton.setBackgroundResource(subscribed ? R.drawable.yp_button_secondary
                : R.drawable.yp_button_primary);
        subscribeButton.setTextColor(ContextCompat.getColor(context, subscribed
                ? R.color.yp_on_bg : R.color.yp_on_action));
    }

    /**
     * Returns max number of allowed lines for the description field.
     * @param content additional detail content (video / sub count)
     * @return max line count
     */
    protected int getDescriptionMaxLineCount(@Nullable final String content) {
        return content == null ? 3 : 2;
    }

    @Nullable
    private String getDetailLine(final ChannelInfoItem item) {
        if (item.getStreamCount() >= 0 && item.getSubscriberCount() >= 0) {
            return Localization.concatenateStrings(
                    Localization.shortSubscriberCount(itemBuilder.getContext(),
                            item.getSubscriberCount()),
                    Localization.localizeStreamCount(itemBuilder.getContext(),
                            item.getStreamCount()));
        } else if (item.getStreamCount() >= 0) {
            return Localization.localizeStreamCount(itemBuilder.getContext(),
                    item.getStreamCount());
        } else if (item.getSubscriberCount() >= 0) {
            return Localization.shortSubscriberCount(itemBuilder.getContext(),
                    item.getSubscriberCount());
        } else {
            return null;
        }
    }
}
