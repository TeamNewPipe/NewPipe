package org.schabi.newpipe.dial;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import java.io.Serializable;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import de.w3is.jdial.DialClient;
import de.w3is.jdial.Discovery;
import de.w3is.jdial.model.Application;
import de.w3is.jdial.model.DialClientException;
import de.w3is.jdial.model.DialContent;
import de.w3is.jdial.model.DialServer;
import de.w3is.jdial.protocol.ProtocolFactory;
import de.w3is.jdial.protocol.ProtocolFactoryImpl;

public class DialService extends IntentService {

    public static final String ACTION_DISCOVERY = "dial.intent.action.discovery";
    public static final String ACTION_START = "dial.intent.action.start";

    public static final String EXTRA_CLEAR_CACHE = "dial.intent.extra.clearCache";
    public static final String EXTRA_DIAL_SERVERS = "dial.intent.extra.servers";
    public static final String EXTRA_DIAL_SERVER = "dial.intent.extra.server";
    public static final String EXTRA_VIDEO_ID = "dial.intent.extra.videoId";
    public static final String EXTRA_VIDEO_STARTED = "dial.intent.extra.videoStarted";

    private static final String SERVICE_NAME = "dialService";
    private static final String TAG = ".DialService";

    private final Discovery discovery;
    private final DialClient dialClient;
    private WifiManager wifiManager;

    private List<DialServer> dialServerCache = new ArrayList<>();

    public DialService() {
        super(SERVICE_NAME);

        ProtocolFactory protocolFactory = new ProtocolFactoryImpl(true);
        discovery = new Discovery(protocolFactory);
        dialClient = new DialClient(protocolFactory);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        wifiManager = (WifiManager) getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {

        if (intent == null) {
            return;
        }

        if (ACTION_DISCOVERY.equals(intent.getAction())) {

            discoverServers(intent);

        } else if (ACTION_START.equals(intent.getAction())) {

            startVideo(intent);
        }
    }

    private void startVideo(Intent intent) {

        String videoId = intent.getStringExtra(EXTRA_VIDEO_ID);
        DialServer dialServer = (DialServer) intent.getSerializableExtra(EXTRA_DIAL_SERVER);

        if (videoId == null || dialServer == null || videoId.isEmpty()) {

            Log.e(TAG, "Not all extras were set");
            sendStartActionResponse(false);
            return;
        }

        boolean success = false;

        try {

            URL instanceURL = dialClient.connectTo(dialServer)
                    .startApplication(Application.YOUTUBE, createVideoStartContent(videoId));

            success = instanceURL != null;

        } catch (DialClientException e) {

            Log.e(TAG, "Error while launching video on tv [ dialServer: " + dialServer.getServerDescription() + ", videoId: " + videoId + "]", e);
        }

        sendStartActionResponse(success);
    }

    private void discoverServers(Intent intent) {

        if (intent.getBooleanExtra(EXTRA_CLEAR_CACHE, false)) {

            dialServerCache.clear();
        }

        if (dialServerCache.isEmpty()) {

            dialServerCache.addAll(discoverYoutubeServer());
        }

        Intent resultIntent = new Intent(ACTION_DISCOVERY);
        resultIntent.putExtra(EXTRA_DIAL_SERVERS, (Serializable) dialServerCache);
        LocalBroadcastManager.getInstance(this).sendBroadcast(resultIntent);
    }

    private List<DialServer> discoverYoutubeServer() {

        WifiManager.MulticastLock multicastLock = wifiManager.createMulticastLock(TAG);
        multicastLock.acquire();

        List<DialServer> allServers = discovery.discover();

        multicastLock.release();

        List<DialServer> youtubeServers = new ArrayList<>();

        for (DialServer server : allServers) {

            if (supportsYoutube(server)) {

                youtubeServers.add(server);
            }
        }

        return youtubeServers;
    }

    private boolean supportsYoutube(DialServer server) {

        return dialClient.connectTo(server).supportsApplication(Application.YOUTUBE);
    }

    private DialContent createVideoStartContent(final String videoId) {

        return new DialContent() {
            @Override
            public String getContentType() {
                return "application/x-www-form-urlencoded; encoding=UTF-8";
            }

            @Override
            public byte[] getData() {
                return String.format("v=%s", videoId).getBytes(Charset.forName("UTF-8"));
            }
        };
    }

    private void sendStartActionResponse(boolean success) {

        Intent resultIntent = new Intent(ACTION_START);
        resultIntent.putExtra(EXTRA_VIDEO_STARTED, success);
        LocalBroadcastManager.getInstance(this).sendBroadcast(resultIntent);
    }
}
