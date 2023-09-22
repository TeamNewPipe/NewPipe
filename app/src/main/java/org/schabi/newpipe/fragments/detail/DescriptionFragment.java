package org.schabi.newpipe.fragments.detail;

import static org.schabi.newpipe.extractor.stream.StreamExtractor.NO_AGE_LIMIT;
import static org.schabi.newpipe.util.Localization.getAppLocale;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.stream.Description;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.util.Localization;

import java.util.List;

import icepick.State;

public class DescriptionFragment extends BaseDescriptionFragment {

    @State
    StreamInfo streamInfo = null;

    public DescriptionFragment() {
    }

    public DescriptionFragment(final StreamInfo streamInfo) {
        this.streamInfo = streamInfo;
    }

    @Nullable
    @Override
    protected Description getDescription() {
        if (streamInfo == null) {
            return null;
        }
        return streamInfo.getDescription();
    }

    @Nullable
    @Override
    protected StreamingService getService() {
        if (streamInfo == null) {
            return null;
        }
        return streamInfo.getService();
    }

    @Override
    protected int getServiceId() {
        if (streamInfo == null) {
            return -1;
        }
        return streamInfo.getServiceId();
    }

    @Nullable
    @Override
    protected String getStreamUrl() {
        if (streamInfo == null) {
            return null;
        }
        return streamInfo.getUrl();
    }

    @Nullable
    @Override
    public List<String> getTags() {
        if (streamInfo == null) {
            return null;
        }
        return streamInfo.getTags();
    }

    @Override
    protected void setupMetadata(final LayoutInflater inflater,
                                 final LinearLayout layout) {
        if (streamInfo != null && streamInfo.getUploadDate() != null) {
            binding.detailUploadDateView.setText(Localization
                    .localizeUploadDate(activity, streamInfo.getUploadDate().offsetDateTime()));
        } else {
            binding.detailUploadDateView.setVisibility(View.GONE);
        }

        if (streamInfo == null) {
            return;
        }

        addMetadataItem(inflater, layout, false, R.string.metadata_category,
                streamInfo.getCategory());

        addMetadataItem(inflater, layout, false, R.string.metadata_licence,
                streamInfo.getLicence());

        addPrivacyMetadataItem(inflater, layout);

        if (streamInfo.getAgeLimit() != NO_AGE_LIMIT) {
            addMetadataItem(inflater, layout, false, R.string.metadata_age_limit,
                    String.valueOf(streamInfo.getAgeLimit()));
        }

        if (streamInfo.getLanguageInfo() != null) {
            addMetadataItem(inflater, layout, false, R.string.metadata_language,
                    streamInfo.getLanguageInfo().getDisplayLanguage(getAppLocale(getContext())));
        }

        addMetadataItem(inflater, layout, true, R.string.metadata_support,
                streamInfo.getSupportInfo());
        addMetadataItem(inflater, layout, true, R.string.metadata_host,
                streamInfo.getHost());

        addImagesMetadataItem(inflater, layout, R.string.metadata_thumbnails,
                streamInfo.getThumbnails());
        addImagesMetadataItem(inflater, layout, R.string.metadata_uploader_avatars,
                streamInfo.getUploaderAvatars());
        addImagesMetadataItem(inflater, layout, R.string.metadata_subchannel_avatars,
                streamInfo.getSubChannelAvatars());
    }

    private void addPrivacyMetadataItem(final LayoutInflater inflater, final LinearLayout layout) {
        if (streamInfo.getPrivacy() != null) {
            @StringRes final int contentRes;
            switch (streamInfo.getPrivacy()) {
                case PUBLIC:
                    contentRes = R.string.metadata_privacy_public;
                    break;
                case UNLISTED:
                    contentRes = R.string.metadata_privacy_unlisted;
                    break;
                case PRIVATE:
                    contentRes = R.string.metadata_privacy_private;
                    break;
                case INTERNAL:
                    contentRes = R.string.metadata_privacy_internal;
                    break;
                case OTHER:
                default:
                    contentRes = 0;
                    break;
            }

            if (contentRes != 0) {
                addMetadataItem(inflater, layout, false, R.string.metadata_privacy,
                        getString(contentRes));
            }
        }
    }
}
