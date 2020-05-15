package org.schabi.newpipe.util;

import java.util.ArrayList;

public class SponsorTimeInfo {
    public ArrayList<TimeFrame> timeFrames = new ArrayList<>();

    public int getSponsorEndTimeFromProgress(final int progress) {
        if (timeFrames == null) {
            return 0;
        }

        for (TimeFrame t : timeFrames) {
            if (progress < t.startTime) {
                continue;
            }

            if (progress > t.endTime) {
                continue;
            }

            return (int) Math.ceil((t.endTime));
        }

        return 0;
    }
}
