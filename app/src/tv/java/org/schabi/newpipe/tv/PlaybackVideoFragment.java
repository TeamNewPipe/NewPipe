package org.schabi.newpipe.tv;

import android.os.Bundle;
import android.support.v17.leanback.app.VideoSupportFragment;
import android.support.v17.leanback.app.VideoSupportFragmentGlueHost;
import android.support.v17.leanback.media.MediaPlayerGlue;
import android.support.v17.leanback.media.PlaybackGlue;

public class PlaybackVideoFragment extends VideoSupportFragment {
    private static final String TAG = "PlaybackVideoFragment";

    private MediaPlayerGlue mMediaPlayerGlue;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Movie movie = (Movie) getActivity()
                .getIntent().getSerializableExtra(DetailsActivity.MOVIE);

        VideoSupportFragmentGlueHost glueHost =
                new VideoSupportFragmentGlueHost(PlaybackVideoFragment.this);

        mMediaPlayerGlue = new MediaPlayerGlue(getActivity());
        mMediaPlayerGlue.setHost(glueHost);
        mMediaPlayerGlue.setMode(MediaPlayerGlue.NO_REPEAT);
        mMediaPlayerGlue.addPlayerCallback(new PlaybackGlue.PlayerCallback() {
            @Override
            public void onPreparedStateChanged(PlaybackGlue glue) {
                if(glue.isPrepared()) {
                    mMediaPlayerGlue.play();
                }
            }
        });
        mMediaPlayerGlue.setTitle(movie.getTitle());
        mMediaPlayerGlue.setArtist(movie.getDescription());
        mMediaPlayerGlue.setVideoUrl(movie.getVideoUrl());
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mMediaPlayerGlue != null) {
            mMediaPlayerGlue.pause();
        }
    }
}