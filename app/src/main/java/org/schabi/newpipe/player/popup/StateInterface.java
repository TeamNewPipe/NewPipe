package org.schabi.newpipe.player.popup;

public interface StateInterface {
    int STATE_LOADING = 123;
    int STATE_PLAYING = 125;
    int STATE_PAUSED = 126;
    int STATE_PAUSED_SEEK = 127;
    int STATE_COMPLETED = 128;

    void changeState(int state);

    void onLoading();
    void onPlaying();
    void onPaused();
    void onPausedSeek();
    void onCompleted();
}