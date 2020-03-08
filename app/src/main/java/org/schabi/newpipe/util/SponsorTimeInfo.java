package org.schabi.newpipe.util;

import java.util.ArrayList;

public class SponsorTimeInfo {
    public ArrayList<TimeFrame> timeFrames = new ArrayList<>();

    public int getSponsorEndTimeFromProgress(int progress) {
        if (timeFrames == null) {
            return 0;
        }

        for (TimeFrame timeFrames : timeFrames) {
            if (progress < timeFrames.startTime) {
                continue;
            }

            if (progress > timeFrames.endTime) {
                continue;
            }

            return (int) Math.ceil((timeFrames.endTime));
        }

        return 0;
    }
}