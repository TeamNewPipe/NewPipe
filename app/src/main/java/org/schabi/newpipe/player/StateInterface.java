package org.schabi.newpipe.player;

public interface StateInterface {
    int STATE_LOADING = 123;
    int STATE_PLAYING = 124;
    int STATE_BUFFERING = 125;
    int STATE_PAUSED = 126;
    int STATE_PAUSED_SEEK = 127;
    int STATE_COMPLETED = 128;

    void changeState(int state);

    void onLoading();
    void onPlaying();
    void onBuffering();
    void onPaused();
    void onPausedSeek();
    void onCompleted();
}