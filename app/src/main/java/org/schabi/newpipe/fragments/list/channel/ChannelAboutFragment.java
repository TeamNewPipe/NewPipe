package org.schabi.newpipe.fragments.list.channel;

import static org.schabi.newpipe.extractor.stream.StreamExtractor.UNKNOWN_SUBSCRIBER_COUNT;

import android.content.Context;
import android.view.LayoutInflater;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.channel.ChannelInfo;
import org.schabi.newpipe.extractor.stream.Description;
import org.schabi.newpipe.fragments.detail.BaseDescriptionFragment;
import org.schabi.newpipe.util.Localization;

import java.util.List;

import icepick.State;

public class ChannelAboutFragment extends BaseDescriptionFragment {
    @State
    protected ChannelInfo channelInfo;

    public static ChannelAboutFragment getInstance(final ChannelInfo channelInfo) {
        final ChannelAboutFragment fragment = new ChannelAboutFragment();
        fragment.channelInfo = channelInfo;
        return fragment;
    }

    public ChannelAboutFragment() {
        super();
    }

    @Nullable
    @Override
    protected Description getDescription() {
        if (channelInfo == null) {
            return null;
        }
        return new Description(channelInfo.getDescription(), Description.PLAIN_TEXT);
    }

    @Nullable
    @Override
    protected StreamingService getService() {
        if (channelInfo == null) {
            return null;
        }
        return channelInfo.getService();
    }

    @Override
    protected int getServiceId() {
        return channelInfo.getServiceId();
    }

    @Nullable
    @Override
    protected String getStreamUrl() {
        return null;
    }

    @Nullable
    @Override
    public List<String> getTags() {
        if (channelInfo == null) {
            return null;
        }
        return channelInfo.getTags();
    }

    protected void setupMetadata(final LayoutInflater inflater,
                                 final LinearLayout layout) {
        final Context context = getContext();

        if (channelInfo.getSubscriberCount() != UNKNOWN_SUBSCRIBER_COUNT) {
            addMetadataItem(inflater, layout, false, R.string.metadata_subscribers,
                    Localization.localizeNumber(context, channelInfo.getSubscriberCount()));
        }
    }
}
