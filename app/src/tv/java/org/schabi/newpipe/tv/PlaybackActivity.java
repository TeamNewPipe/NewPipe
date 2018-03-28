package org.schabi.newpipe.tv;

import android.os.Bundle;
import android.support.v4.app.FragmentActivity;

public class PlaybackActivity extends FragmentActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(android.R.id.content, new PlaybackVideoFragment())
                    .commit();
        }
    }
}