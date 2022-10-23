package org.schabi.newpipe.fragments.list.channel;

import static org.schabi.newpipe.extractor.stream.StreamExtractor.UNKNOWN_SUBSCRIBER_COUNT;
import static org.schabi.newpipe.extractor.utils.Utils.isBlank;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.google.android.material.chip.Chip;

import org.schabi.newpipe.BaseFragment;
import org.schabi.newpipe.R;
import org.schabi.newpipe.databinding.FragmentChannelInfoBinding;
import org.schabi.newpipe.databinding.ItemMetadataBinding;
import org.schabi.newpipe.databinding.ItemMetadataTagsBinding;
import org.schabi.newpipe.extractor.channel.ChannelInfo;
import org.schabi.newpipe.util.Localization;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.external_communication.ShareUtils;
import org.schabi.newpipe.util.external_communication.TextLinkifier;

import java.util.List;

import icepick.State;
import io.reactivex.rxjava3.disposables.CompositeDisposable;

public class ChannelInfoFragment extends BaseFragment {
    @State
    protected ChannelInfo channelInfo;

    private final CompositeDisposable disposables = new CompositeDisposable();
    private FragmentChannelInfoBinding binding;

    public static ChannelInfoFragment getInstance(final ChannelInfo channelInfo) {
        final ChannelInfoFragment fragment = new ChannelInfoFragment();
        fragment.channelInfo = channelInfo;
        return fragment;
    }

    public ChannelInfoFragment() {
        super();
    }

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             final Bundle savedInstanceState) {
        binding = FragmentChannelInfoBinding.inflate(inflater, container, false);
        loadDescription();
        setupMetadata(inflater, binding.detailMetadataLayout);
        return binding.getRoot();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        disposables.clear();
    }

    private void loadDescription() {
        final String description = channelInfo.getDescription();

        if (description == null || description.isEmpty()) {
            binding.descriptionTitle.setVisibility(View.GONE);
            binding.descriptionView.setVisibility(View.GONE);
        } else {
            TextLinkifier.createLinksFromPlainText(
                    binding.descriptionView, description, null, disposables);
        }
    }

    private void setupMetadata(final LayoutInflater inflater,
                               final LinearLayout layout) {
        final Context context = getContext();

        if (channelInfo.getSubscriberCount() != UNKNOWN_SUBSCRIBER_COUNT) {
            addMetadataItem(inflater, layout, R.string.metadata_subscribers,
                    Localization.localizeNumber(context, channelInfo.getSubscriberCount()));
        }

        addTagsMetadataItem(inflater, layout);
    }

    private void addMetadataItem(final LayoutInflater inflater,
                                 final LinearLayout layout,
                                 @StringRes final int type,
                                 @Nullable final String content) {
        if (isBlank(content)) {
            return;
        }

        final ItemMetadataBinding itemBinding =
                ItemMetadataBinding.inflate(inflater, layout, false);

        itemBinding.metadataTypeView.setText(type);
        itemBinding.metadataTypeView.setOnLongClickListener(v -> {
            ShareUtils.copyToClipboard(requireContext(), content);
            return true;
        });

        itemBinding.metadataContentView.setText(content);

        layout.addView(itemBinding.getRoot());
    }

    private void addTagsMetadataItem(final LayoutInflater inflater, final LinearLayout layout) {
        final List<String> tags = channelInfo.getTags();

        if (!tags.isEmpty()) {
            final var itemBinding = ItemMetadataTagsBinding.inflate(inflater, layout, false);

            tags.stream().sorted(String.CASE_INSENSITIVE_ORDER).forEach(tag -> {
                final Chip chip = (Chip) inflater.inflate(R.layout.chip,
                        itemBinding.metadataTagsChips, false);
                chip.setText(tag);
                chip.setOnClickListener(this::onTagClick);
                chip.setOnLongClickListener(this::onTagLongClick);
                itemBinding.metadataTagsChips.addView(chip);
            });

            layout.addView(itemBinding.getRoot());
        }
    }

    private void onTagClick(final View chip) {
        if (getParentFragment() != null) {
            NavigationHelper.openSearchFragment(getParentFragment().getParentFragmentManager(),
                    channelInfo.getServiceId(), ((Chip) chip).getText().toString());
        }
    }

    private boolean onTagLongClick(final View chip) {
        ShareUtils.copyToClipboard(requireContext(), ((Chip) chip).getText().toString());
        return true;
    }
}
