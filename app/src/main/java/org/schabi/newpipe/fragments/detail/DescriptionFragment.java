package org.schabi.newpipe.fragments.detail;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.text.HtmlCompat;

import org.schabi.newpipe.BaseFragment;
import org.schabi.newpipe.R;
import org.schabi.newpipe.databinding.FragmentDescriptionBinding;
import org.schabi.newpipe.databinding.ItemMetadataBinding;
import org.schabi.newpipe.extractor.stream.Description;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.util.Localization;
import org.schabi.newpipe.util.TextLinkifier;

import icepick.State;
import io.reactivex.rxjava3.disposables.Disposable;

import static android.text.TextUtils.isEmpty;
import static org.schabi.newpipe.extractor.stream.StreamExtractor.NO_AGE_LIMIT;
import static org.schabi.newpipe.extractor.utils.Utils.isBlank;

public class DescriptionFragment extends BaseFragment {

    @State
    StreamInfo streamInfo = null;
    @Nullable
    Disposable descriptionDisposable = null;

    public DescriptionFragment() {
    }

    public DescriptionFragment(final StreamInfo streamInfo) {
        this.streamInfo = streamInfo;
    }

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        final FragmentDescriptionBinding binding =
                FragmentDescriptionBinding.inflate(inflater, container, false);
        if (streamInfo != null) {
            setupUploadDate(binding.detailUploadDateView);
            setupDescription(binding.detailDescriptionView);
            setupMetadata(inflater, binding.detailMetadataLayout);
        }
        return binding.getRoot();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (descriptionDisposable != null) {
            descriptionDisposable.dispose();
        }
    }

    private void setupUploadDate(final TextView uploadDateTextView) {
        if (streamInfo.getUploadDate() != null) {
            uploadDateTextView.setText(Localization
                    .localizeUploadDate(activity, streamInfo.getUploadDate().offsetDateTime()));
        } else {
            uploadDateTextView.setVisibility(View.GONE);
        }
    }

    private void setupDescription(final TextView descriptionTextView) {
        final Description description = streamInfo.getDescription();
        if (description == null || isEmpty(description.getContent())
                || description == Description.emptyDescription) {
            descriptionTextView.setVisibility(View.GONE);
            return;
        }

        switch (description.getType()) {
            case Description.HTML:
                descriptionDisposable = TextLinkifier.createLinksFromHtmlBlock(requireContext(),
                        description.getContent(), descriptionTextView,
                        HtmlCompat.FROM_HTML_MODE_LEGACY);
                break;
            case Description.MARKDOWN:
                descriptionDisposable = TextLinkifier.createLinksFromMarkdownText(requireContext(),
                        description.getContent(), descriptionTextView);
                break;
            case Description.PLAIN_TEXT: default:
                descriptionDisposable = TextLinkifier.createLinksFromPlainText(requireContext(),
                        description.getContent(), descriptionTextView);
                break;
        }
    }

    private void setupMetadata(final LayoutInflater inflater,
                               final LinearLayout layout) {
        addMetadataItem(inflater, layout, false,
                R.string.metadata_category, streamInfo.getCategory());

        addTagsMetadataItem(inflater, layout);

        addMetadataItem(inflater, layout, false,
                R.string.metadata_licence, streamInfo.getLicence());

        addPrivacyMetadataItem(inflater, layout);

        if (streamInfo.getAgeLimit() != NO_AGE_LIMIT) {
            addMetadataItem(inflater, layout, false,
                    R.string.metadata_age_limit, String.valueOf(streamInfo.getAgeLimit()));
        }

        if (streamInfo.getLanguageInfo() != null) {
            addMetadataItem(inflater, layout, false,
                    R.string.metadata_language, streamInfo.getLanguageInfo().getDisplayLanguage());
        }

        addMetadataItem(inflater, layout, true,
                R.string.metadata_support, streamInfo.getSupportInfo());
        addMetadataItem(inflater, layout, true,
                R.string.metadata_host, streamInfo.getHost());
        addMetadataItem(inflater, layout, true,
                R.string.metadata_thumbnail_url, streamInfo.getThumbnailUrl());
    }

    private void addMetadataItem(final LayoutInflater inflater,
                                 final LinearLayout layout,
                                 final boolean linkifyContent,
                                 @StringRes final int type,
                                 @Nullable final String content) {
        if (isBlank(content)) {
            return;
        }

        final ItemMetadataBinding binding = ItemMetadataBinding.inflate(inflater, layout, false);
        binding.metadataTypeView.setText(type);

        if (linkifyContent) {
            TextLinkifier.createLinksFromPlainText(layout.getContext(), content,
                    binding.metadataContentView);
        } else {
            binding.metadataContentView.setText(content);
        }

        layout.addView(binding.getRoot());
    }

    private void addTagsMetadataItem(final LayoutInflater inflater, final LinearLayout layout) {
        if (streamInfo.getTags() != null && !streamInfo.getTags().isEmpty()) {
            final StringBuilder tags = new StringBuilder();
            for (int i = 0; i < streamInfo.getTags().size(); ++i) {
                if (i != 0) {
                    tags.append(", ");
                }
                tags.append(streamInfo.getTags().get(i));
            }

            addMetadataItem(inflater, layout, false, R.string.metadata_tags, tags.toString());
        }
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
                case OTHER: default:
                    contentRes = 0;
                    break;
            }

            if (contentRes != 0) {
                addMetadataItem(inflater, layout, false,
                        R.string.metadata_privacy, layout.getContext().getString(contentRes));
            }
        }
    }
}
