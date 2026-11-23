package org.schabi.newpipe.player.ui;

import android.text.TextUtils;
import android.view.HapticFeedbackConstants;
import android.view.View;

import androidx.annotation.NonNull;

import org.schabi.newpipe.databinding.PlayerBinding;
import org.schabi.newpipe.extractor.stream.StreamSegment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Keeps chapter markers and the seek preview in sync with the current stream. */
final class ChapterSeekBarController {
    private List<StreamSegment> chapters = Collections.emptyList();
    private int lastSeekChapterIndex = -1;

    void setChapters(@NonNull final PlayerBinding binding,
                     @NonNull final List<StreamSegment> newChapters) {
        chapters = new ArrayList<>(newChapters);
        binding.playbackSeekBar.setChapters(chapters);
        hideTitle(binding);
    }

    void clear(@NonNull final PlayerBinding binding) {
        setChapters(binding, Collections.emptyList());
    }

    void onProgressChanged(@NonNull final PlayerBinding binding, final int progress) {
        updateTitle(binding, progress, true);
    }

    void onStartTrackingTouch(@NonNull final PlayerBinding binding, final int progress) {
        lastSeekChapterIndex = -1;
        updateTitle(binding, progress, false);
    }

    void hideTitle(@NonNull final PlayerBinding binding) {
        binding.currentChapterTitle.setVisibility(View.GONE);
        lastSeekChapterIndex = -1;
    }

    private void updateTitle(@NonNull final PlayerBinding binding, final int progress,
                             final boolean allowHaptics) {
        int chapterIndex = -1;
        for (int i = 0; i < chapters.size(); i++) {
            if (org.schabi.newpipe.download.ChapterTimes.startMillis(chapters.get(i)) > progress) {
                break;
            }
            chapterIndex = i;
        }

        if (allowHaptics && lastSeekChapterIndex >= 0
                && chapterIndex != lastSeekChapterIndex) {
            binding.playbackSeekBar.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        }
        lastSeekChapterIndex = chapterIndex;

        final String title = chapterIndex >= 0 ? chapters.get(chapterIndex).getTitle() : null;
        if (TextUtils.isEmpty(title)) {
            binding.currentChapterTitle.setVisibility(View.GONE);
        } else {
            binding.currentChapterTitle.setText(title);
            binding.currentChapterTitle.setVisibility(View.VISIBLE);
        }
    }
}
