package org.schabi.newpipe;

import android.content.Intent;
import android.media.AudioManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.NavUtils;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.SearchView;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import org.schabi.newpipe.extractor.ServiceList;

import java.util.Vector;

/**
 * Created by Christian Schabesberger on 02.08.16.
 */

public class MainActivity extends AppCompatActivity {

    private Fragment mainFragment = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        mainFragment = getSupportFragmentManager()
                .findFragmentById(R.id.search_fragment);
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        MenuInflater inflater = getMenuInflater();

        inflater.inflate(R.menu.main_menu, menu);

        mainFragment.onCreateOptionsMenu(menu, inflater);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        switch(id) {
            case android.R.id.home: {
                Intent intent = new Intent(this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                NavUtils.navigateUpTo(this, intent);
                return true;
            }
            case R.id.action_settings: {
                Intent intent = new Intent(this, SettingsActivity.class);
                startActivity(intent);
                return true;
            }
            case R.id.action_show_downloads: {
                Intent intent = new Intent(this, org.schabi.newpipe.download.MainActivity.class);
                startActivity(intent);
                return true;
            }
            default:
                return mainFragment.onOptionsItemSelected(item) ||
                        super.onOptionsItemSelected(item);
        }
    }
}
