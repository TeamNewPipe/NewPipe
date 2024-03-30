package us.shandian.giga.get

class FinishedMission : Mission {
    constructor()
    constructor(mission: DownloadMission) {
        source = mission.source
        length = mission.length
        timestamp = mission.timestamp
        kind = mission.kind
        storage = mission.storage
    }
}
