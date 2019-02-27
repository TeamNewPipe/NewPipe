package us.shandian.giga.get;

public class FinishedMission extends  Mission {

    public FinishedMission() {
    }

    public FinishedMission(DownloadMission mission) {
        source = mission.source;
        length = mission.length;// ¿or mission.done?
        timestamp = mission.timestamp;
        name = mission.name;
        location = mission.location;
        kind = mission.kind;
    }
}
