package org.schabi.newpipe.fragments.list.channel;

import static org.schabi.newpipe.extractor.stream.StreamExtractor.UNKNOWN_SUBSCRIBER_COUNT;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.evernote.android.state.State;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.channel.ChannelInfo;
import org.schabi.newpipe.extractor.stream.Description;
import org.schabi.newpipe.fragments.detail.BaseDescriptionFragment;
import org.schabi.newpipe.util.DeviceUtils;
import org.schabi.newpipe.util.Localization;

import java.util.List;

public class ChannelAboutFragment extends BaseDescriptionFragment {
    @State
    protected ChannelInfo channelInfo;

    ChannelAboutFragment(@NonNull final ChannelInfo channelInfo) {
        this.channelInfo = channelInfo;
    }

    public ChannelAboutFragment() {
        // keep empty constructor for State when resuming fragment from memory
    }

    @Override
    protected void initViews(final View rootView, final Bundle savedInstanceState) {
        super.initViews(rootView, savedInstanceState);
        binding.constraintLayout.setPadding(0, DeviceUtils.dpToPx(8, requireContext()), 0, 0);
    }

    @Nullable
    @Override
    protected Description getDescription() {
        return new Description(channelInfo.getDescription(), Description.PLAIN_TEXT);
    }

    @NonNull
    @Override
    protected StreamingService getService() {
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

    @NonNull
    @Override
    public List<String> getTags() {
        return channelInfo.getTags();
    }

    @Override
    protected void setupMetadata(final LayoutInflater inflater,
                                 final LinearLayout layout) {
        // There is no upload date available for channels, so hide the relevant UI element
        binding.detailUploadDateView.setVisibility(View.GONE);

        if (channelInfo == null) {
            return;
        }

        if (channelInfo.getSubscriberCount() != UNKNOWN_SUBSCRIBER_COUNT) {
            addMetadataItem(inflater, layout, false, R.string.metadata_subscribers,
                    Localization.localizeNumber(
                            requireContext(),
                            channelInfo.getSubscriberCount()));
        }

        addImagesMetadataItem(inflater, layout, R.string.metadata_avatars,
                channelInfo.getAvatars());
        addImagesMetadataItem(inflater, layout, R.string.metadata_banners,
                channelInfo.getBanners());
    }
}
