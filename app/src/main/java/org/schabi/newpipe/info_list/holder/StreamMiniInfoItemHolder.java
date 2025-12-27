package org.schabi.newpipe.info_list.holder;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.AccessibilityDelegateCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.preference.PreferenceManager;

import org.schabi.newpipe.R;
import org.schabi.newpipe.database.stream.model.StreamStateEntity;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.info_list.InfoItemBuilder;
import org.schabi.newpipe.info_list.dialog.StreamDialogDefaultEntry;
import org.schabi.newpipe.ktx.ViewUtils;
import org.schabi.newpipe.local.history.HistoryRecordManager;
import org.schabi.newpipe.player.helper.PlayerHolder;
import org.schabi.newpipe.util.DependentPreferenceHelper;
import org.schabi.newpipe.util.Localization;
import org.schabi.newpipe.util.StreamTypeUtil;
import org.schabi.newpipe.util.external_communication.KoreUtils;
import org.schabi.newpipe.util.image.CoilHelper;
import org.schabi.newpipe.views.AnimatedProgressBar;

import java.util.concurrent.TimeUnit;

public class StreamMiniInfoItemHolder extends InfoItemHolder {
    public final ImageView itemThumbnailView;
    public final TextView itemVideoTitleView;
    public final TextView itemUploaderView;
    public final TextView itemDurationView;
    private final AnimatedProgressBar itemProgressView;

    StreamMiniInfoItemHolder(final InfoItemBuilder infoItemBuilder, final int layoutId,
                             final ViewGroup parent) {
        super(infoItemBuilder, layoutId, parent);

        itemThumbnailView = itemView.findViewById(R.id.itemThumbnailView);
        itemVideoTitleView = itemView.findViewById(R.id.itemVideoTitleView);
        itemUploaderView = itemView.findViewById(R.id.itemUploaderView);
        itemDurationView = itemView.findViewById(R.id.itemDurationView);
        itemProgressView = itemView.findViewById(R.id.itemProgressView);
    }

    public StreamMiniInfoItemHolder(final InfoItemBuilder infoItemBuilder, final ViewGroup parent) {
        this(infoItemBuilder, R.layout.list_stream_mini_item, parent);
    }

    @Override
    public void updateFromItem(final InfoItem infoItem,
                               final HistoryRecordManager historyRecordManager) {
        if (!(infoItem instanceof StreamInfoItem)) {
            return;
        }
        final StreamInfoItem item = (StreamInfoItem) infoItem;

        itemVideoTitleView.setText(item.getName());
        itemUploaderView.setText(item.getUploaderName());

        if (item.getDuration() > 0) {
            itemDurationView.setText(Localization.getDurationString(item.getDuration()));
            itemDurationView.setBackgroundColor(ContextCompat.getColor(
                    itemBuilder.getContext(),
                    R.color.duration_background_color));
            itemDurationView.setVisibility(View.VISIBLE);

            StreamStateEntity state2 = null;
            if (DependentPreferenceHelper
                    .getPositionsInListsEnabled(itemProgressView.getContext())) {
                state2 = historyRecordManager.loadStreamState(infoItem).blockingGet();
            }
            if (state2 != null) {
                itemProgressView.setVisibility(View.VISIBLE);
                itemProgressView.setMax((int) item.getDuration());
                itemProgressView.setProgress((int) TimeUnit.MILLISECONDS
                        .toSeconds(state2.getProgressMillis()));
            } else {
                itemProgressView.setVisibility(View.GONE);
            }
        } else if (StreamTypeUtil.isLiveStream(item.getStreamType())) {
            itemDurationView.setText(R.string.duration_live);
            itemDurationView.setBackgroundColor(ContextCompat.getColor(
                    itemBuilder.getContext(),
                    R.color.live_duration_background_color));
            itemDurationView.setVisibility(View.VISIBLE);
            itemProgressView.setVisibility(View.GONE);
        } else {
            itemDurationView.setVisibility(View.GONE);
            itemProgressView.setVisibility(View.GONE);
        }

        // Default thumbnail is shown on error, while loading and if the url is empty
        CoilHelper.INSTANCE.loadThumbnail(itemThumbnailView, item.getThumbnails());

        itemView.setOnClickListener(view -> {
            if (itemBuilder.getOnStreamSelectedListener() != null) {
                itemBuilder.getOnStreamSelectedListener().selected(item);
            }
        });

        switch (item.getStreamType()) {
            case AUDIO_STREAM:
            case VIDEO_STREAM:
            case LIVE_STREAM:
            case AUDIO_LIVE_STREAM:
            case POST_LIVE_STREAM:
            case POST_LIVE_AUDIO_STREAM:
                enableLongClick(item);
                break;
            case NONE:
            default:
                disableLongClick();
                break;
        }
    }

    @Override
    public void updateState(final InfoItem infoItem,
                            final HistoryRecordManager historyRecordManager) {
        final StreamInfoItem item = (StreamInfoItem) infoItem;

        StreamStateEntity state = null;
        if (DependentPreferenceHelper.getPositionsInListsEnabled(itemProgressView.getContext())) {
            state = historyRecordManager
                    .loadStreamState(infoItem)
                    .blockingGet();
        }
        if (state != null && item.getDuration() > 0
                && !StreamTypeUtil.isLiveStream(item.getStreamType())) {
            itemProgressView.setMax((int) item.getDuration());
            if (itemProgressView.getVisibility() == View.VISIBLE) {
                itemProgressView.setProgressAnimated((int) TimeUnit.MILLISECONDS
                        .toSeconds(state.getProgressMillis()));
            } else {
                itemProgressView.setProgress((int) TimeUnit.MILLISECONDS
                        .toSeconds(state.getProgressMillis()));
                ViewUtils.animate(itemProgressView, true, 500);
            }
        } else if (itemProgressView.getVisibility() == View.VISIBLE) {
            ViewUtils.animate(itemProgressView, false, 500);
        }
    }

    private void enableLongClick(final StreamInfoItem item) {
        itemView.setLongClickable(true);
        itemView.setOnLongClickListener(view -> {
            if (itemBuilder.getOnStreamSelectedListener() != null) {
                itemBuilder.getOnStreamSelectedListener().held(item);
            }
            return true;
        });

        updateAccessibilityActions(item);
    }

    private void updateAccessibilityActions(final StreamInfoItem item) {
        ViewCompat.setAccessibilityDelegate(itemView, new AccessibilityDelegateCompat() {
            @Override
            public void onInitializeAccessibilityNodeInfo(
                    @NonNull final View host,
                    @NonNull final AccessibilityNodeInfoCompat info) {
                super.onInitializeAccessibilityNodeInfo(host, info);

                final Context context = itemBuilder.getContext();
                if (context == null) {
                    return;
                }

                final PlayerHolder holder = PlayerHolder.INSTANCE;
                if (holder.isPlayQueueReady()) {
                    info.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                            R.id.accessibility_action_enqueue,
                            context.getString(R.string.enqueue_stream)));

                    if (holder.getQueuePosition() < holder.getQueueSize() - 1) {
                        info.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                                R.id.accessibility_action_enqueue_next,
                                context.getString(R.string.enqueue_next_stream)));
                    }
                }

                info.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                        R.id.accessibility_action_background,
                        context.getString(R.string.start_here_on_background)));

                if (!StreamTypeUtil.isAudio(item.getStreamType())) {
                    info.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                            R.id.accessibility_action_popup,
                            context.getString(R.string.start_here_on_popup)));
                }

                if (context instanceof FragmentActivity) {
                    info.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                            R.id.accessibility_action_download,
                            context.getString(R.string.download)));

                    info.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                            R.id.accessibility_action_playlist,
                            context.getString(R.string.add_to_playlist)));
                }

                info.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                        R.id.accessibility_action_share,
                        context.getString(R.string.share)));

                info.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                        R.id.accessibility_action_browser,
                        context.getString(R.string.open_in_browser)));

                if (KoreUtils.shouldShowPlayWithKodi(context, item.getServiceId())) {
                    info.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                            R.id.accessibility_action_kodi,
                            context.getString(R.string.play_with_kodi_title)));
                }

                final boolean isWatchHistoryEnabled = PreferenceManager
                        .getDefaultSharedPreferences(context)
                        .getBoolean(context.getString(R.string.enable_watch_history_key), false);
                if (isWatchHistoryEnabled && !StreamTypeUtil.isLiveStream(item.getStreamType())) {
                    info.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                            R.id.accessibility_action_mark_watched,
                            context.getString(R.string.mark_as_watched)));
                }

                if (context instanceof AppCompatActivity) {
                    info.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                            R.id.accessibility_action_channel_details,
                            context.getString(R.string.accessibility_show_channel_details,
                                    item.getUploaderName())));
                }

                info.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                        R.id.accessibility_action_show_options,
                        context.getString(R.string.more_options)));
            }

            @Override
            public boolean performAccessibilityAction(@NonNull final View host, final int action,
                                                      final Bundle args) {
                final Context context = itemBuilder.getContext();
                final FragmentActivity fActivity = ((FragmentActivity) context);

                if (context == null) {
                    return super.performAccessibilityAction(host, action, args);
                }

                if (action == R.id.accessibility_action_show_options) {
                    // display stream dialog
                    if (itemBuilder.getOnStreamSelectedListener() != null) {
                        itemBuilder.getOnStreamSelectedListener().held(item);
                    }
                    return true;
                } else if (action == R.id.accessibility_action_enqueue) {
                    StreamDialogDefaultEntry.ENQUEUE.action.onClick(fActivity, item);
                    return true;
                } else if (action == R.id.accessibility_action_enqueue_next) {
                    StreamDialogDefaultEntry.ENQUEUE_NEXT.action.onClick(fActivity, item);
                    return true;
                } else if (action == R.id.accessibility_action_background) {
                    StreamDialogDefaultEntry.START_HERE_ON_BACKGROUND.action.onClick(
                            fActivity, item);
                    return true;
                } else if (action == R.id.accessibility_action_popup) {
                    StreamDialogDefaultEntry.START_HERE_ON_POPUP.action.onClick(fActivity, item);
                    return true;
                } else if (action == R.id.accessibility_action_download) {
                    StreamDialogDefaultEntry.DOWNLOAD.action.onClick(fActivity, item);
                    return true;
                } else if (action == R.id.accessibility_action_playlist) {
                    StreamDialogDefaultEntry.APPEND_PLAYLIST.action.onClick(fActivity, item);
                    return true;
                } else if (action == R.id.accessibility_action_share) {
                    StreamDialogDefaultEntry.SHARE.action.onClick(fActivity, item);
                    return true;
                } else if (action == R.id.accessibility_action_browser) {
                    StreamDialogDefaultEntry.OPEN_IN_BROWSER.action.onClick(fActivity, item);
                    return true;
                } else if (action == R.id.accessibility_action_kodi) {
                    StreamDialogDefaultEntry.PLAY_WITH_KODI.action.onClick(fActivity, item);
                    return true;
                } else if (action == R.id.accessibility_action_mark_watched) {
                    StreamDialogDefaultEntry.MARK_AS_WATCHED.action.onClick(fActivity, item);
                    return true;
                } else if (action == R.id.accessibility_action_channel_details) {
                    StreamDialogDefaultEntry.SHOW_CHANNEL_DETAILS.action.onClick(fActivity, item);
                    return true;
                }

                return super.performAccessibilityAction(host, action, args);
            }
        });
    }

    private void disableLongClick() {
        itemView.setLongClickable(false);
        itemView.setOnLongClickListener(null);
        ViewCompat.setAccessibilityDelegate(itemView, null);
    }
}

