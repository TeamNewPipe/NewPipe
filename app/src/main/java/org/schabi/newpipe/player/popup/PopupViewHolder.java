package org.schabi.newpipe.player.popup;

import android.graphics.Color;
import android.graphics.PorterDuff;
import android.os.Build;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import com.devbrackets.android.exomedia.ui.widget.EMVideoView;

import org.schabi.newpipe.R;

public class PopupViewHolder {
    private View rootView;
    private EMVideoView videoView;
    private View loadingPanel;
    private ImageView endScreen;
    private ImageView controlAnimationView;
    private LinearLayout controlsRoot;
    private SeekBar playbackSeekBar;
    private TextView playbackCurrentTime;
    private TextView playbackEndTime;

    public PopupViewHolder(View rootView) {
        if (rootView == null) return;
        this.rootView = rootView;
        this.videoView = (EMVideoView) rootView.findViewById(R.id.popupVideoView);
        this.loadingPanel = rootView.findViewById(R.id.loadingPanel);
        this.endScreen = (ImageView) rootView.findViewById(R.id.endScreen);
        this.controlAnimationView = (ImageView) rootView.findViewById(R.id.controlAnimationView);
        this.controlsRoot = (LinearLayout) rootView.findViewById(R.id.playbackControlRoot);
        this.playbackSeekBar = (SeekBar) rootView.findViewById(R.id.playbackSeekBar);
        this.playbackCurrentTime = (TextView) rootView.findViewById(R.id.playbackCurrentTime);
        this.playbackEndTime = (TextView) rootView.findViewById(R.id.playbackEndTime);
        doModifications();
    }

    private void doModifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) playbackSeekBar.getThumb().setColorFilter(Color.RED, PorterDuff.Mode.SRC_IN);
        playbackSeekBar.getProgressDrawable().setColorFilter(Color.RED, PorterDuff.Mode.MULTIPLY);
    }

    public boolean isControlsVisible() {
        return controlsRoot != null && controlsRoot.getVisibility() == View.VISIBLE;
    }

    public boolean isVisible(View view) {
        return view != null && view.getVisibility() == View.VISIBLE;
    }

    ///////////////////////////////////////////////////////////////////////////
    // GETTERS
    ///////////////////////////////////////////////////////////////////////////

    public View getRootView() {
        return rootView;
    }

    public EMVideoView getVideoView() {
        return videoView;
    }

    public View getLoadingPanel() {
        return loadingPanel;
    }

    public ImageView getEndScreen() {
        return endScreen;
    }

    public ImageView getControlAnimationView() {
        return controlAnimationView;
    }

    public LinearLayout getControlsRoot() {
        return controlsRoot;
    }

    public SeekBar getPlaybackSeekBar() {
        return playbackSeekBar;
    }

    public TextView getPlaybackCurrentTime() {
        return playbackCurrentTime;
    }

    public TextView getPlaybackEndTime() {
        return playbackEndTime;
    }
}
