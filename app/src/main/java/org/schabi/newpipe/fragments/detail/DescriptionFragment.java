package org.schabi.newpipe.fragments.detail;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.text.HtmlCompat;

import org.schabi.newpipe.BaseFragment;
import org.schabi.newpipe.databinding.FragmentDescriptionBinding;
import org.schabi.newpipe.extractor.stream.Description;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.util.Localization;
import org.schabi.newpipe.util.TextLinkifier;

import icepick.State;
import io.reactivex.rxjava3.disposables.Disposable;

import static android.text.TextUtils.isEmpty;

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
            descriptionTextView.setText("");
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
}
