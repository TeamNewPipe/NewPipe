package org.schabi.newpipe.playlist.events;


public class QualityEvent implements PlayQueueEvent {
    final private int streamIndex;
    final private int oldQualityIndex;
    final private int newQualityIndex;

    @Override
    public PlayQueueEventType type() {
        return PlayQueueEventType.QUALITY;
    }

    public QualityEvent(final int streamIndex, final int oldQualityIndex, final int newQualityIndex) {
        this.streamIndex = streamIndex;
        this.oldQualityIndex = oldQualityIndex;
        this.newQualityIndex = newQualityIndex;
    }

    public int getStreamIndex() {
        return streamIndex;
    }

    public int getOldQualityIndex() {
        return oldQualityIndex;
    }

    public int getNewQualityIndex() {
        return newQualityIndex;
    }
}
