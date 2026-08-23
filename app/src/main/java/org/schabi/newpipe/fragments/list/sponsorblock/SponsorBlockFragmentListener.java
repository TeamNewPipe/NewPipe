package org.schabi.newpipe.fragments.list.sponsorblock;

import org.schabi.newpipe.extractor.sponsorblock.SponsorBlockSegment;

public interface SponsorBlockFragmentListener {
    void onSkippingEnabledChanged(boolean newValue);
    void onRequestNewPendingSegment(int startTime, int endTime);
    void onRequestClearPendingSegment();
    void onRequestSubmitPendingSegment(SponsorBlockSegment newSegment);
    void onSeekToRequested(long positionMillis);
}
