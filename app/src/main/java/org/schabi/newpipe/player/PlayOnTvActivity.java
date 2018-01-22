package org.schabi.newpipe.player;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.schabi.newpipe.R;
import org.schabi.newpipe.dial.DialService;

import java.util.List;

import de.w3is.jdial.model.DialServer;

public class PlayOnTvActivity extends Activity {

    public static final String VIDEO_ID = "video_id";

    private static final boolean DEBUG = BasePlayer.DEBUG;
    private static final String TAG = ".TvPlayer";
    private static final String STATE_INTENT = "tv_player_state_intent";

    private ImageButton refreshButton;
    protected ProgressBar progressBar;

    private ListView serverList;
    private ServerListAdapter serverListAdapter;

    private BroadcastReceiver serverListReceiver;
    private BroadcastReceiver videoStartedReceiver;

    private String videoId;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (DEBUG) Log.d(TAG, "onCreate() called with: savedInstanceState = [" + savedInstanceState + "]");

        final Intent intent;
        if (savedInstanceState != null && savedInstanceState.getParcelable(STATE_INTENT) != null) {
            intent = savedInstanceState.getParcelable(STATE_INTENT);
        } else {
            intent = getIntent();
        }

        if (intent == null) {
            Toast.makeText(this, R.string.general_error, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        setContentView(R.layout.activity_play_on_tv);

        initView();
        initBroadcastReceiver();
        getVideoInfoFromIntent(intent);

        sendRefreshIntent(false);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        Intent intent = new Intent(getApplicationContext(), PlayOnTvActivity.class)
                .putExtra(VIDEO_ID, videoId);
        outState.putParcelable(STATE_INTENT, intent);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        if (DEBUG) Log.d(TAG, "onNewIntent() called with: intent = [" + intent + "]");
        super.onNewIntent(intent);
        getVideoInfoFromIntent(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        LocalBroadcastManager broadcastManager = LocalBroadcastManager.getInstance(this);
        broadcastManager.unregisterReceiver(serverListReceiver);
        broadcastManager.unregisterReceiver(videoStartedReceiver);
    }

    private void sendRefreshIntent(boolean clearCache) {
        if (DEBUG) Log.d(TAG, "sendRefreshIntent() called with: clearCache = [" + clearCache + "]");

        Intent discoveryIntent = new Intent(this, DialService.class);
        discoveryIntent.setAction(DialService.ACTION_DISCOVERY);
        discoveryIntent.putExtra(DialService.EXTRA_CLEAR_CACHE, clearCache);
        startService(discoveryIntent);

        showLoadAnimation();
    }

    private void getVideoInfoFromIntent(Intent intent) {

        videoId = intent.getStringExtra(VIDEO_ID);
    }

    private void initView() {

        refreshButton = findViewById(R.id.refreshButton);
        refreshButton.setOnClickListener(view -> sendRefreshIntent(true));

        serverListAdapter = new ServerListAdapter(this, R.layout.list_tv_item);

        serverList = findViewById(R.id.serverList);
        serverList.setClickable(true);
        serverList.setAdapter(serverListAdapter);
        serverList.setOnItemClickListener(onServerItemClick());

        progressBar = findViewById(R.id.progressBar);
    }

    private AdapterView.OnItemClickListener onServerItemClick() {

        return (parent, view, position, id) -> {

            DialServer selectedServer = (DialServer) serverList.getItemAtPosition(position);

            if(DEBUG) Log.d(TAG, "Play video on tv [ server: "+ selectedServer.getServerDescription() +", videoId: " + videoId + "]");

            Intent startIntent = new Intent(this, DialService.class);
            startIntent.setAction(DialService.ACTION_START);
            startIntent.putExtra(DialService.EXTRA_DIAL_SERVER, selectedServer);
            startIntent.putExtra(DialService.EXTRA_VIDEO_ID, videoId);

            startService(startIntent);
            showLoadAnimation();
        };
    }

    private void initBroadcastReceiver() {

        serverListReceiver = createServerListReceiver();
        videoStartedReceiver = createVideoStartedReceiver();

        LocalBroadcastManager broadcastManager = LocalBroadcastManager.getInstance(this);

        broadcastManager.registerReceiver(serverListReceiver, new IntentFilter(DialService.ACTION_DISCOVERY));
        broadcastManager.registerReceiver(videoStartedReceiver, new IntentFilter(DialService.ACTION_START));
    }

    private BroadcastReceiver createServerListReceiver() {

        return new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {

                    List<DialServer> discoveredServers = (List<DialServer>) intent.getSerializableExtra(DialService.EXTRA_DIAL_SERVERS);
                    showInList(discoveredServers);
                    endLoadAnimation();
            }
        };
    }

    private BroadcastReceiver createVideoStartedReceiver() {

        return new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {

                endLoadAnimation();

                if (intent.getBooleanExtra(DialService.EXTRA_VIDEO_STARTED, false)) {
                    finish();
                }
            }
        };
    }

    private void showInList(List<DialServer> discoveredServers) {

        this.serverListAdapter.clear();
        this.serverListAdapter.addAll(discoveredServers);
    }

    private void showLoadAnimation() {

        refreshButton.setClickable(false);
        progressBar.setVisibility(View.VISIBLE);
    }

    private void endLoadAnimation() {

        refreshButton.setClickable(true);
        progressBar.setVisibility(View.GONE);
    }

    private class ServerListAdapter extends ArrayAdapter<DialServer> {

        ServerListAdapter(@NonNull Context context, int resource) {
            super(context, resource);
        }

        @NonNull
        @Override
        public View getView(int position, View convertView, @NonNull ViewGroup parent) {

            DialServer server = getItem(position);

            if (convertView == null) {
                convertView = LayoutInflater.from(getContext()).inflate(R.layout.list_tv_item, parent, false);
            }

            if (server != null) {

                TextView tvName = convertView.findViewById(R.id.itemTvName);
                TextView tvDescription = convertView.findViewById(R.id.itemTvDescription);

                tvName.setText(server.getFriendlyName());
                tvDescription.setText(server.getServerDescription());
            }

            return convertView;
        }
    }
}
