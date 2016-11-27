package org.schabi.newpipe;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.design.widget.CollapsingToolbarLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.getbase.floatingactionbutton.FloatingActionButton;
import com.getbase.floatingactionbutton.FloatingActionsMenu;
import com.nostra13.universalimageloader.core.ImageLoader;

import org.schabi.newpipe.detail.VideoItemDetailFragment;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.channel.ChannelExtractor;
import org.schabi.newpipe.extractor.channel.ChannelInfo;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.services.youtube.YoutubePlayListUrlIdHandler;
import org.schabi.newpipe.extractor.stream_info.StreamPreviewInfo;
import org.schabi.newpipe.info_list.InfoItemBuilder;
import org.schabi.newpipe.info_list.InfoListAdapter;
import org.schabi.newpipe.info_list.ItemDialog;
import org.schabi.newpipe.playList.PlayListDataSource;
import org.schabi.newpipe.playList.PlayListDataSource.PLAYLIST_SYSTEM;
import org.schabi.newpipe.playList.QueueManager;
import org.schabi.newpipe.report.ErrorActivity;

import java.io.IOException;
import java.util.List;

/**
 * Copyright (C) Christian Schabesberger 2016 <chris.schabesberger@mailbox.org>
 * PlaylistExternalActivity.java is part of NewPipe.
 *
 * NewPipe is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NewPipe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NewPipe.  If not, see <http://www.gnu.org/licenses/>.
 */

public class PlaylistExternalActivity extends AppCompatActivity {

    private static final String TAG = PlaylistExternalActivity.class.toString();
    private View rootView = null;

    // intent const
    public static final String CHANNEL_URL = "channel_url";
    public static final String SERVICE_ID = "service_id";

    private int serviceId = -1;

    private String channelUrl = "";
    private int pageNumber = 0;
    private boolean hasNextPage = true;
    private boolean isLoading = false;

    private ImageLoader imageLoader = ImageLoader.getInstance();
    private InfoListAdapter infoListAdapter = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_playlist_external);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        rootView = findViewById(R.id.rootView);
        setSupportActionBar(toolbar);
        try {
            extractInfo(getIntent());
        } catch (ExtractionException e) {
            e.printStackTrace();
        }
        infoListAdapter = new InfoListAdapter(this, rootView);
        RecyclerView recyclerView = (RecyclerView) findViewById(R.id.channel_streams_view);
        final LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(infoListAdapter);

        infoListAdapter.setOnItemSelectedListener(new InfoItemBuilder.OnItemSelectedListener() {
            @Override
            public void selected(View view, StreamPreviewInfo url) {
                IntentRunner.lunchIntentVideoDetail(PlaylistExternalActivity.this, url.webpage_url, url.service_id, PLAYLIST_SYSTEM.NOT_IN_PLAYLIST_ID, url.position);
            }
        });
        infoListAdapter.setOnPlayListActionListener(new InfoItemBuilder.OnPlayListActionListener() {
            @Override
            public void selected(View view, StreamPreviewInfo streamPreviewInfo) {
                final ItemDialog itemDialog = new ItemDialog(PlaylistExternalActivity.this);
                itemDialog.showSettingDialog(view, streamPreviewInfo, PLAYLIST_SYSTEM.NOT_IN_PLAYLIST_ID, new Runnable() {
                    @Override
                    public void run() {
                        infoListAdapter.clearSteamItemList();
                        pageNumber = 0;
                        requestData(true);
                    }
                });
            }
        });

        // detect if list has ben scrolled to the bottom
        recyclerView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                int pastVisiblesItems, visibleItemCount, totalItemCount;
                super.onScrolled(recyclerView, dx, dy);
                if(dy > 0) {//check for scroll down
                    visibleItemCount = layoutManager.getChildCount();
                    totalItemCount = layoutManager.getItemCount();
                    pastVisiblesItems = layoutManager.findFirstVisibleItemPosition();

                    if ((visibleItemCount + pastVisiblesItems) >= totalItemCount
                            && !isLoading
                            && hasNextPage) {
                        pageNumber++;
                        requestData(true);
                    }
                }
            }
        });

        requestData(false);
    }

    private void initFloatingActionButtonMenu(final ChannelInfo info) {

        final FloatingActionsMenu floatingActionsMenu = (FloatingActionsMenu) findViewById(R.id.multiple_actions_menu);
        final String channel_name = info.channel_name;
        final List<StreamPreviewInfo> relatedStreams = info.related_streams;

        final FloatingActionButton actionRecordToLocalPlaylist = (FloatingActionButton) findViewById(R.id.action_record_to_local_playlist);
        actionRecordToLocalPlaylist.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                floatingActionsMenu.collapse();
                final AlertDialog.Builder alertDialog = new AlertDialog.Builder(PlaylistExternalActivity.this)
                        .setTitle(R.string.save_to_local_playlist)
                        .setMessage(channel_name)
                        .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                // first load all videos
                                recordAllVideosOnPlayList(channel_name, relatedStreams);
                            }
                        })
                        .setNegativeButton(R.string.cancel, null);
                alertDialog.show();
            }
        });

        final FloatingActionButton actionAddToQueue = (FloatingActionButton) findViewById(R.id.action_add_to_queue);
        actionAddToQueue.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                floatingActionsMenu.collapse();
                final AlertDialog.Builder alertDialog = new AlertDialog.Builder(PlaylistExternalActivity.this)
                        .setTitle(R.string.add_playlist_to_queue)
                        .setMessage(channel_name)
                        .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                recordToPlayList(PLAYLIST_SYSTEM.QUEUE_ID, relatedStreams);
                            }
                        })
                        .setNegativeButton(R.string.cancel, null);
                alertDialog.show();
            }
        });

        final FloatingActionButton actionAddToQueueAndPlay = (FloatingActionButton) findViewById(R.id.action_replace_queue);
        actionAddToQueueAndPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                floatingActionsMenu.collapse();
                final AlertDialog.Builder alertDialog = new AlertDialog.Builder(PlaylistExternalActivity.this)
                        .setTitle(R.string.replace_queue_by_playlist)
                        .setMessage(channel_name)
                        .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                final QueueManager queueManager = new QueueManager(getApplicationContext());
                                queueManager.clearQueue();
                                recordToPlayList(PLAYLIST_SYSTEM.QUEUE_ID, relatedStreams);
                                queueManager.lunchInBackgroundQueue();
                            }
                        })
                        .setNegativeButton(R.string.cancel, null);
                alertDialog.show();
            }
        });

        final View backgroundOpac = findViewById(R.id.floating_menu_background);
        backgroundOpac.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                floatingActionsMenu.collapse();
            }
        });
        floatingActionsMenu.setOnFloatingActionsMenuUpdateListener(new FloatingActionsMenu.OnFloatingActionsMenuUpdateListener() {
            @Override
            public void onMenuExpanded() {
                backgroundOpac.setVisibility(View.VISIBLE);
            }

            @Override
            public void onMenuCollapsed() {
                backgroundOpac.setVisibility(View.GONE);
            }
        });
    }


    private void extractInfo(final Intent intent) throws ExtractionException {
        if(intent != null) {
            final YoutubePlayListUrlIdHandler youtubePlayListUrlIdHandler = new YoutubePlayListUrlIdHandler();
            final String youtubePlayListLink = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (youtubePlayListUrlIdHandler.acceptUrl(youtubePlayListLink)) {
                channelUrl = youtubePlayListUrlIdHandler.cleanUrl(youtubePlayListLink);
                serviceId = NewPipe.getIdOfService("Youtube");
            }
        }
    }


    private void updateUi(final ChannelInfo info) {
        CollapsingToolbarLayout ctl = (CollapsingToolbarLayout) findViewById(R.id.channel_toolbar_layout);
        ProgressBar progressBar = (ProgressBar) findViewById(R.id.progressBar);
        ImageView channelBanner = (ImageView) findViewById(R.id.channel_banner_image);
        ImageView avatarView = (ImageView) findViewById(R.id.channel_avatar_view);
        ImageView haloView = (ImageView) findViewById(R.id.channel_avatar_halo);

        progressBar.setVisibility(View.GONE);

        if(!TextUtils.isEmpty(info.channel_name)) {
            ctl.setTitle(info.channel_name);
        }

        if(!TextUtils.isEmpty(info.banner_url)) {
            imageLoader.displayImage(info.banner_url, channelBanner,
                    new ImageErrorLoadingListener(this, rootView ,info.service_id));
        }

        if(!TextUtils.isEmpty(info.avatar_url)) {
            avatarView.setVisibility(View.VISIBLE);
            haloView.setVisibility(View.VISIBLE);
            imageLoader.displayImage(info.avatar_url, avatarView,
                    new ImageErrorLoadingListener(this, rootView ,info.service_id));
        }

        if(!TextUtils.isEmpty(channelUrl)) {
            initFloatingActionButtonMenu(info);
        }
    }

    private void recordAllVideosOnPlayList(final String channel_name, final List<StreamPreviewInfo> related_streams) {
        PlayListDataSource playListDataSource = new PlayListDataSource(this);
        int playListId = playListDataSource.getPlayListId(channel_name);
        if(playListId < 0) {
            playListId = playListDataSource.createPlayList(channel_name).get_id();
        }
        recordToPlayList(playListId, related_streams);
    }

    private void addVideos(int playlist_id, final ChannelInfo info) {
        infoListAdapter.addStreamItemList(info.related_streams);
        // if a playlist is defined
        recordToPlayList(playlist_id, info.related_streams);
    }

    private void recordToPlayList(final int playlist_id, final List<StreamPreviewInfo> related_streams) {
        if(playlist_id != PLAYLIST_SYSTEM.NOT_IN_PLAYLIST_ID) {
            PlayListDataSource playListDataSource = new PlayListDataSource(this);
            for (final StreamPreviewInfo streamPreviewInfo : related_streams) {
                playListDataSource.addEntryToPlayList(playlist_id, streamPreviewInfo);
            }
            if(hasNextPage) {
                pageNumber++;
                Toast.makeText(this, "Fetch from playlist " + channelUrl + " - page " + pageNumber,
                        Toast.LENGTH_SHORT).show();
                requestData(true);
            } else {
                Toast.makeText(this, "PlayList " + channelUrl + " successfully fetched !",
                        Toast.LENGTH_SHORT).show();
                IntentRunner.lunchLocalPlayList(this, playlist_id);
            }
        }
    }

    private void postNewErrorToast(Handler h, final int stringResource) {
        h.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(PlaylistExternalActivity.this,
                        stringResource, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void requestData(final boolean onlyVideos) {
        // start processing
        isLoading = true;
        Log.d(TAG, String.format("Loading more data for %s %d", channelUrl, pageNumber));
        Thread t = new Thread(new Runnable() {
            Handler h = new Handler();
            StreamingService service;
            @Override
            public void run() {
                try {
                    service = NewPipe.getService(serviceId);
                    final ChannelExtractor extractor = service.getPlayListExtractorInstance(channelUrl, pageNumber);
                    final ChannelInfo info = ChannelInfo.getInfo(extractor);

                    h.post(new Runnable() {
                        @Override
                        public void run() {
                            isLoading = false;
                            if (!onlyVideos) {
                                updateUi(info);
                            }
                            hasNextPage = info.hasNextPage;
                            addVideos(PLAYLIST_SYSTEM.NOT_IN_PLAYLIST_ID, info);
                        }
                    });

                    // look for non critical errors during extraction
                    if (info != null && !info.errors.isEmpty()) {
                        Log.e(TAG, "OCCURRED ERRORS DURING EXTRACTION:");
                        for (Throwable e : info.errors) {
                            e.printStackTrace();
                            Log.e(TAG, "------");
                        }

                        View rootView = findViewById(android.R.id.content);
                        ErrorActivity.reportError(h, PlaylistExternalActivity.this,
                                info.errors, null, rootView,
                                ErrorActivity.ErrorInfo.make(ErrorActivity.REQUESTED_CHANNEL,
                                        service.getServiceInfo().name, channelUrl, 0 /* no message for the user */));
                    }
                } catch (IOException ioe) {
                    postNewErrorToast(h, R.string.network_error);
                    ioe.printStackTrace();
                } catch (Exception e) {
                    if (service != null && service.getServiceInfo() != null) {
                        ErrorActivity.reportError(h, PlaylistExternalActivity.this, e, VideoItemDetailFragment.class, null,
                                ErrorActivity.ErrorInfo.make(ErrorActivity.REQUESTED_CHANNEL,
                                        service.getServiceInfo().name, channelUrl, R.string.general_error));
                    }
                    h.post(new Runnable() {
                        @Override
                        public void run() {
                            PlaylistExternalActivity.this.finish();
                        }
                    });
                    e.printStackTrace();
                }
            }
        });
        t.start();
    }

}
