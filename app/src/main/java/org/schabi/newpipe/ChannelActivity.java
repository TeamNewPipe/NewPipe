package org.schabi.newpipe;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.design.widget.CollapsingToolbarLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

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
import org.schabi.newpipe.info_list.SimpleItemTouchHelperCallback;
import org.schabi.newpipe.playList.PlayListDataSource;
import org.schabi.newpipe.playList.PlayListDataSource.PLAYLIST_SYSTEM;
import org.schabi.newpipe.playList.QueueManager;
import org.schabi.newpipe.player.LunchAudioTrack;
import org.schabi.newpipe.report.ErrorActivity;
import org.schabi.newpipe.search_fragment.SearchInfoItemFragment;

import java.io.IOException;
import java.util.Collections;

/**
 * Copyright (C) Christian Schabesberger 2016 <chris.schabesberger@mailbox.org>
 * ChannelActivity.java is part of NewPipe.
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

public class ChannelActivity extends AppCompatActivity {

    private static final int CONTENT_CHANNEL = 0;
    private static final int CONTENT_PLAYLIST_EXTERNAL = 1;
    private static final int CONTENT_PLAYLIST_INTENAL = 2;
    private static final int CONTENT_QUEUE = 3;
    private int currentContent = CONTENT_QUEUE;

    private static final String TAG = ChannelActivity.class.toString();
    private View rootView = null;

    // intent const
    public static final String CHANNEL_URL = "channel_url";
    public static final String SERVICE_ID = "service_id";

    private int playListId = PLAYLIST_SYSTEM.NOT_IN_PLAYLIST_ID;
    private int serviceId = -1;
    private StreamingService service;

    private String channelUrl = "";
    private int pageNumber = 0;
    private boolean hasNextPage = true;
    private boolean isLoading = false;

    private ImageLoader imageLoader = ImageLoader.getInstance();
    private InfoListAdapter infoListAdapter = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_channel);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        rootView = findViewById(R.id.rootView);
        setSupportActionBar(toolbar);
        Intent i = getIntent();
        serviceId = initService(i);
        playListId = initPlayList(i);
        channelUrl = getChannelUrl(i);
        extractYTPlayList(i);
        currentContent = computeMode();
        infoListAdapter = new InfoListAdapter(this, rootView);
        RecyclerView recyclerView = (RecyclerView) findViewById(R.id.channel_streams_view);
        final LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(infoListAdapter);
        initRemoveItemFrom(recyclerView);
        infoListAdapter.setOnItemSelectedListener(new InfoItemBuilder.OnItemSelectedListener() {
            @Override
            public void selected(View view, String url, int positionInList) {
               IntentRunner.lunchIntentVideoDetail(ChannelActivity.this, url, serviceId, playListId, positionInList + 1);
            }
        });
        infoListAdapter.setOnPlayListActionListener(new InfoItemBuilder.OnPlayListActionListener() {
            @Override
            public void selected(View view, StreamPreviewInfo streamPreviewInfo, int positionInList) {
                final ItemDialog itemDialog = new ItemDialog(ChannelActivity.this);
                itemDialog.showSettingDialog(view, streamPreviewInfo, playListId, new Runnable() {
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

    private int computeMode() {
        if(PLAYLIST_SYSTEM.NOT_IN_PLAYLIST_ID != playListId) {
            if(PLAYLIST_SYSTEM.QUEUE_ID == playListId) {
                return CONTENT_QUEUE;
            }
            return CONTENT_PLAYLIST_INTENAL;
        } else if (service != null && !TextUtils.isEmpty(channelUrl)) {
            if (service.getChannelUrlIdHandlerInstance().acceptUrl(channelUrl)) {
                return CONTENT_CHANNEL;
            } else if (service.getPlaylistUrlIdHandlerInstance().acceptUrl(channelUrl)) {
                return CONTENT_PLAYLIST_EXTERNAL;
            }
        }
        return CONTENT_QUEUE;
    }

    private String getChannelUrl(Intent i) {
        final String channelUrl = i.getStringExtra(CHANNEL_URL);
        return channelUrl == null ? "" : channelUrl;
    }

    private int initPlayList(final Intent i) {
        return i.getIntExtra(SearchInfoItemFragment.PLAYLIST_ID, PLAYLIST_SYSTEM.NOT_IN_PLAYLIST_ID);
    }

    private int initService(final Intent i) {
        try {
            final int serviceId = i.getIntExtra(SERVICE_ID, NewPipe.getIdOfService("Youtube"));
            service = NewPipe.getService(serviceId);
            return serviceId;
        } catch (ExtractionException e) {
            return -1;
        }
    }

    private void initRemoveItemFrom(final RecyclerView recyclerView) {
        final ItemTouchHelper.Callback callback = new SimpleItemTouchHelperCallback(infoListAdapter);
        final ItemTouchHelper touchHelper = new ItemTouchHelper(callback);
        touchHelper.attachToRecyclerView(recyclerView);
        infoListAdapter.setOnItemDeleteListener(new InfoListAdapter.ItemListener() {
            @Override
            public void deletedItem(final int position, final StreamPreviewInfo deletedItem) {
                new AsyncTask<Void, Void, Void>() {
                    @Override
                    protected Void doInBackground(Void... voids) {
                        if(CONTENT_PLAYLIST_INTENAL == currentContent) {
                            PlayListDataSource playListDatasource = new PlayListDataSource(ChannelActivity.this);
                            playListDatasource.deleteEntryFromPlayList(playListId, deletedItem.id, deletedItem.service_id);
                        } else if(CONTENT_QUEUE == currentContent) {
                            new QueueManager(ChannelActivity.this).remoteItemAt(position);
                        }
                        return null;
                    }
                }.execute();
            }

            @Override
            public void moveItem(int fromPosition, int toPosition) {
                PlayListDataSource playListDataSource = new PlayListDataSource(ChannelActivity.this);
                // update in database
                // update in memory
                if (fromPosition < toPosition) {
                    for (int i = fromPosition; i < toPosition; i++) {
                        final int j = i + 1;
                        swapItemOnStreamList(playListDataSource, i, j);
                    }
                } else {
                    for (int i = fromPosition; i > toPosition; i--) {
                        final int j = i - 1;
                        swapItemOnStreamList(playListDataSource, i, j);
                    }
                }
                infoListAdapter.notifyItemMoved(fromPosition, toPosition);
            }

            private void swapItemOnStreamList(PlayListDataSource playListDataSource, int i, int j) {
                // on database
                if(playListId != PLAYLIST_SYSTEM.NOT_IN_PLAYLIST_ID) {
                    final StreamPreviewInfo from = infoListAdapter.getStreamList().get(i);
                    final StreamPreviewInfo to = infoListAdapter.getStreamList().get(j);
                    final int positionTo = to.position;
                    final int positionFrom = from.position;

                    playListDataSource.updatePosition(playListId, from, positionTo);
                    playListDataSource.updatePosition(playListId, to, positionFrom);
                }
                // on cache list
                Collections.swap(infoListAdapter.getStreamList(), i, j);
            }
        });
    }

    private void extractYTPlayList(final Intent i) {
        if(i != null) {
            final YoutubePlayListUrlIdHandler youtubePlayListUrlIdHandler = new YoutubePlayListUrlIdHandler();
            final String youtubePlayListLink = i.getStringExtra(Intent.EXTRA_TEXT);
            if (youtubePlayListUrlIdHandler.acceptUrl(youtubePlayListLink)) {
                channelUrl = youtubePlayListLink;
            }
            if (youtubePlayListUrlIdHandler.acceptUrl(channelUrl)) {
                try {
                    channelUrl = youtubePlayListUrlIdHandler.cleanUrl(channelUrl);
                } catch (ExtractionException e) {
                    Log.e(TAG, e.getMessage(), e);
                }
            }
        }
    }


    private void updateUi(final ChannelInfo info) {
        CollapsingToolbarLayout ctl = (CollapsingToolbarLayout) findViewById(R.id.channel_toolbar_layout);
        ProgressBar progressBar = (ProgressBar) findViewById(R.id.progressBar);
        ImageView channelBanner = (ImageView) findViewById(R.id.channel_banner_image);
        FloatingActionButton feedButton = (FloatingActionButton) findViewById(R.id.channel_rss_fab);
        FloatingActionButton playQueueButton = (FloatingActionButton) findViewById(R.id.channel_replace_queue);
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

        if(!TextUtils.isEmpty(info.feed_url)) {
            feedButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Log.d(TAG, info.feed_url);
                    Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(info.feed_url));
                    startActivity(i);
                }
            });
        } else if(!TextUtils.isEmpty(channelUrl)){
            feedButton.setImageResource(R.drawable.nnf_ic_create_new_folder_white_24dp);
            feedButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    AlertDialog.Builder alertDialog = new AlertDialog.Builder(ChannelActivity.this)
                            .setTitle(R.string.save_to_local_playlist)
                            .setMessage(info.channel_name)
                            .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    final PlayListDataSource playListDataSource = new PlayListDataSource(ChannelActivity.this);
                                    int id = playListDataSource.getPlayListId(info.channel_name);
                                    if(id < 0) {
                                        id = playListDataSource.createPlayList(info.channel_name).get_id();
                                    }
                                    if(id > -1) {
                                        for (final StreamPreviewInfo infos : info.related_streams) {
                                            playListDataSource.addEntryToPlayList(id, infos);
                                        }
                                        Toast.makeText(ChannelActivity.this, "Success", Toast.LENGTH_SHORT).show();
                                    }
                                }
                            })
                            .setNegativeButton(R.string.cancel, null);
                    alertDialog.show();
                }
            });
        } else {
            feedButton.setVisibility(View.GONE);
        }
        if(info.related_streams != null && !info.related_streams.isEmpty()) {
            playQueueButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    final StreamPreviewInfo streamPreviewInfo = infoListAdapter.getStreamList().get(0);
                    final LunchAudioTrack lunchAudioTrack = new LunchAudioTrack(getApplicationContext(), streamPreviewInfo, playListId);
                    lunchAudioTrack.process(false);
                    new AsyncTask<Void, Void, Void>() {
                        @Override
                        protected Void doInBackground(Void... voids) {
                            new QueueManager(ChannelActivity.this).replaceQueue(infoListAdapter.getStreamList());
                            return null;
                        }
                    }.execute();
                }
            });
        }
    }

    private void addVideos(final ChannelInfo info) {
        infoListAdapter.addStreamItemList(info.related_streams);
    }

    private void postNewErrorToast(Handler h, final int stringResource) {
        h.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(ChannelActivity.this,
                        stringResource, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void requestData(final boolean onlyVideos) {
        // start processing
        isLoading = true;
        Thread t = new Thread(new Runnable() {
            Handler h = new Handler();

            @Override
            public void run() {
                try {
                    final ChannelExtractor extractor;
                    switch (currentContent) {
                        case CONTENT_CHANNEL:
                            extractor = service.getChannelExtractorInstance(channelUrl, pageNumber);
                            break;
                        case CONTENT_PLAYLIST_EXTERNAL:
                            extractor = service.getPlayListExtractorInstance(channelUrl, pageNumber);
                            break;
                        case CONTENT_PLAYLIST_INTENAL:
                            extractor = service.getLocalPlayListExtractorInstance(ChannelActivity.this, playListId, pageNumber);
                            break;
                        case CONTENT_QUEUE:
                            extractor = service.getQueueExtractorInstance(ChannelActivity.this, pageNumber);
                            break;
                        default:
                            extractor = service.getQueueExtractorInstance(ChannelActivity.this, pageNumber);
                            break;
                    }
                    final ChannelInfo info = ChannelInfo.getInfo(extractor);

                    h.post(new Runnable() {
                        @Override
                        public void run() {
                            isLoading = false;
                            if (!onlyVideos) {
                                updateUi(info);
                            }
                            hasNextPage = info.hasNextPage;
                            addVideos(info);
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
                        ErrorActivity.reportError(h, ChannelActivity.this,
                                info.errors, null, rootView,
                                ErrorActivity.ErrorInfo.make(ErrorActivity.REQUESTED_CHANNEL,
                                        service.getServiceInfo().name, channelUrl, 0 /* no message for the user */));
                    }
                } catch (IOException ioe) {
                    postNewErrorToast(h, R.string.network_error);
                    ioe.printStackTrace();
                } catch (Exception e) {
                    if (service != null && service.getServiceInfo() != null) {
                        ErrorActivity.reportError(h, ChannelActivity.this, e, VideoItemDetailFragment.class, null,
                                ErrorActivity.ErrorInfo.make(ErrorActivity.REQUESTED_CHANNEL,
                                        service.getServiceInfo().name, channelUrl, R.string.general_error));
                    }
                    h.post(new Runnable() {
                        @Override
                        public void run() {
                            ChannelActivity.this.finish();
                        }
                    });
                    e.printStackTrace();
                }
            }
        });
        t.start();
    }

}
