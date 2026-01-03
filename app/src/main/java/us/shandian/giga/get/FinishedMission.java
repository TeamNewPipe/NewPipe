package us.shandian.giga.get;

import androidx.annotation.NonNull;

public class FinishedMission extends Mission {

    public int serviceId = -1;
    public long streamUid = -1;
    public String qualityLabel = null;

    public FinishedMission() {
    }

    public FinishedMission(@NonNull DownloadMission mission) {
        source = mission.source;
        length = mission.length;
        timestamp = mission.timestamp;
        kind = mission.kind;
        storage = mission.storage;
        serviceId = mission.serviceId;
        streamUid = mission.streamUid;
        qualityLabel = mission.qualityLabel;
    }

}
