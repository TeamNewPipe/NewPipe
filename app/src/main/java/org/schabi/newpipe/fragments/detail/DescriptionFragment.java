package org.schabi.newpipe.fragments.detail;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.widget.TooltipCompat;
import androidx.core.text.HtmlCompat;

import com.google.android.material.chip.Chip;

import org.schabi.newpipe.BaseFragment;
import org.schabi.newpipe.R;
import org.schabi.newpipe.databinding.FragmentDescriptionBinding;
import org.schabi.newpipe.databinding.ItemMetadataBinding;
import org.schabi.newpipe.databinding.ItemMetadataTagsBinding;
import org.schabi.newpipe.extractor.stream.Description;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.util.Localization;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.external_communication.ShareUtils;
import org.schabi.newpipe.util.external_communication.TextLinkifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import icepick.State;
import io.reactivex.rxjava3.disposables.CompositeDisposable;

import static android.text.TextUtils.isEmpty;
import static org.schabi.newpipe.extractor.stream.StreamExtractor.NO_AGE_LIMIT;
import static org.schabi.newpipe.extractor.utils.Utils.isBlank;

public class DescriptionFragment extends BaseFragment {

    @State
    StreamInfo streamInfo = null;
    final CompositeDisposable descriptionDisposables = new CompositeDisposable();
    FragmentDescriptionBinding binding;

    public DescriptionFragment() {
    }

    public DescriptionFragment(final StreamInfo streamInfo) {
        this.streamInfo = streamInfo;
    }

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        binding = FragmentDescriptionBinding.inflate(inflater, container, false);
        if (streamInfo != null) {
            setupUploadDate();
            setupDescription();
            setupMetadata(inflater, binding.detailMetadataLayout);
        }
        return binding.getRoot();
    }

    @Override
    public void onDestroy() {
        descriptionDisposables.clear();
        super.onDestroy();
    }


    private void setupUploadDate() {
        if (streamInfo.getUploadDate() != null) {
            binding.detailUploadDateView.setText(Localization
                    .localizeUploadDate(activity, streamInfo.getUploadDate().offsetDateTime()));
        } else {
            binding.detailUploadDateView.setVisibility(View.GONE);
        }
    }


    private void setupDescription() {
        final Description description = streamInfo.getDescription();
        if (description == null || isEmpty(description.getContent())
                || description == Description.emptyDescription) {
            binding.detailDescriptionView.setVisibility(View.GONE);
            binding.detailSelectDescriptionButton.setVisibility(View.GONE);
            return;
        }

        // start with disabled state. This also loads description content (!)
        disableDescriptionSelection();

        binding.detailSelectDescriptionButton.setOnClickListener(v -> {
            if (binding.detailDescriptionNoteView.getVisibility() == View.VISIBLE) {
                disableDescriptionSelection();
            } else {
                // enable selection only when button is clicked to prevent flickering
                enableDescriptionSelection();
            }
        });
    }

    private void enableDescriptionSelection() {
        binding.detailDescriptionNoteView.setVisibility(View.VISIBLE);
        binding.detailDescriptionView.setTextIsSelectable(true);

        final String buttonLabel = getString(R.string.description_select_disable);
        binding.detailSelectDescriptionButton.setContentDescription(buttonLabel);
        TooltipCompat.setTooltipText(binding.detailSelectDescriptionButton, buttonLabel);
        binding.detailSelectDescriptionButton.setImageResource(R.drawable.ic_close);
    }

    private void disableDescriptionSelection() {
        // show description content again, otherwise some links are not clickable
        loadDescriptionContent();

        binding.detailDescriptionNoteView.setVisibility(View.GONE);
        binding.detailDescriptionView.setTextIsSelectable(false);

        final String buttonLabel = getString(R.string.description_select_enable);
        binding.detailSelectDescriptionButton.setContentDescription(buttonLabel);
        TooltipCompat.setTooltipText(binding.detailSelectDescriptionButton, buttonLabel);
        binding.detailSelectDescriptionButton.setImageResource(R.drawable.ic_select_all);
    }

    private void loadDescriptionContent() {
        final Description description = streamInfo.getDescription();
        switch (description.getType()) {
            case Description.HTML:
                TextLinkifier.createLinksFromHtmlBlock(binding.detailDescriptionView,
                        description.getContent(), HtmlCompat.FROM_HTML_MODE_LEGACY, streamInfo,
                        descriptionDisposables);
                break;
            case Description.MARKDOWN:
                TextLinkifier.createLinksFromMarkdownText(binding.detailDescriptionView,
                        description.getContent(), streamInfo, descriptionDisposables);
                break;
            case Description.PLAIN_TEXT: default:
                TextLinkifier.createLinksFromPlainText(binding.detailDescriptionView,
                        description.getContent(), streamInfo, descriptionDisposables);
                break;
        }
    }


    private void setupMetadata(final LayoutInflater inflater,
                               final LinearLayout layout) {
        addMetadataItem(inflater, layout, false,
                R.string.metadata_category, streamInfo.getCategory());

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

        addTagsMetadataItem(inflater, layout);
    }

    private void addMetadataItem(final LayoutInflater inflater,
                                 final LinearLayout layout,
                                 final boolean linkifyContent,
                                 @StringRes final int type,
                                 @Nullable final String content) {
        if (isBlank(content)) {
            return;
        }

        final ItemMetadataBinding itemBinding
                = ItemMetadataBinding.inflate(inflater, layout, false);

        itemBinding.metadataTypeView.setText(type);
        itemBinding.metadataTypeView.setOnLongClickListener(v -> {
            ShareUtils.copyToClipboard(requireContext(), content);
            return true;
        });

        if (linkifyContent) {
            TextLinkifier.createLinksFromPlainText(itemBinding.metadataContentView, content, null,
                    descriptionDisposables);
        } else {
            itemBinding.metadataContentView.setText(content);
        }

        layout.addView(itemBinding.getRoot());
    }

    private void addTagsMetadataItem(final LayoutInflater inflater, final LinearLayout layout) {
        if (streamInfo.getTags() != null && !streamInfo.getTags().isEmpty()) {
            final ItemMetadataTagsBinding itemBinding
                    = ItemMetadataTagsBinding.inflate(inflater, layout, false);

            final List<String> tags = new ArrayList<>(streamInfo.getTags());
            Collections.sort(tags);
            for (final String tag : tags) {
                final Chip chip = (Chip) inflater.inflate(R.layout.chip,
                        itemBinding.metadataTagsChips, false);
                chip.setText(tag);
                chip.setOnClickListener(this::onTagClick);
                chip.setOnLongClickListener(this::onTagLongClick);
                itemBinding.metadataTagsChips.addView(chip);
            }

            layout.addView(itemBinding.getRoot());
        }
    }

    private void onTagClick(final View chip) {
        if (getParentFragment() != null) {
            NavigationHelper.openSearchFragment(getParentFragment().getParentFragmentManager(),
                    streamInfo.getServiceId(), ((Chip) chip).getText().toString());
        }
    }

    private boolean onTagLongClick(final View chip) {
        ShareUtils.copyToClipboard(requireContext(), ((Chip) chip).getText().toString());
        return true;
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
                        R.string.metadata_privacy, getString(contentRes));
            }
        }
    }
}
