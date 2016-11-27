package org.schabi.newpipe;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.design.widget.CollapsingToolbarLayout;
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

import com.getbase.floatingactionbutton.FloatingActionButton;
import com.getbase.floatingactionbutton.FloatingActionsMenu;
import com.nostra13.universalimageloader.core.ImageLoader;

import org.schabi.newpipe.detail.VideoItemDetailFragment;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.channel.ChannelExtractor;
import org.schabi.newpipe.extractor.channel.ChannelInfo;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.stream_info.StreamPreviewInfo;
import org.schabi.newpipe.info_list.InfoItemBuilder;
import org.schabi.newpipe.info_list.InfoListAdapter;
import org.schabi.newpipe.info_list.ItemDialog;
import org.schabi.newpipe.info_list.SimpleItemTouchHelperCallback;
import org.schabi.newpipe.playList.PlayListDataSource;
import org.schabi.newpipe.playList.PlayListDataSource.PLAYLIST_SYSTEM;
import org.schabi.newpipe.playList.QueueManager;
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

public class PlaylistLocalActivity extends AppCompatActivity {

    private static final String TAG = PlaylistLocalActivity.class.toString();
    private View rootView = null;

    // intent const

    private int playListId = PLAYLIST_SYSTEM.QUEUE_ID;
    private StreamingService service;

    private int pageNumber = 0;
    private boolean hasNextPage = true;
    private boolean isLoading = false;

    private ImageLoader imageLoader = ImageLoader.getInstance();
    private InfoListAdapter infoListAdapter = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_playlist_local);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        rootView = findViewById(R.id.rootView);
        setSupportActionBar(toolbar);
        Intent i = getIntent();
        playListId = initPlayList(i);
        infoListAdapter = new InfoListAdapter(this, rootView);
        RecyclerView recyclerView = (RecyclerView) findViewById(R.id.channel_streams_view);
        final LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(infoListAdapter);
        initRemoveItemFrom(recyclerView);
        infoListAdapter.setOnItemSelectedListener(new InfoItemBuilder.OnItemSelectedListener() {
            @Override
            public void selected(View view, StreamPreviewInfo url) {
                IntentRunner.lunchIntentVideoDetail(PlaylistLocalActivity.this, url.webpage_url, url.service_id, playListId, url.position);
            }
        });
        infoListAdapter.setOnPlayListActionListener(new InfoItemBuilder.OnPlayListActionListener() {
            @Override
            public void selected(View view, StreamPreviewInfo streamPreviewInfo) {
                final ItemDialog itemDialog = new ItemDialog(PlaylistLocalActivity.this);
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
        initService();
        requestData(false);
    }
    private void initService() {
        try {
            service = NewPipe.getService(NewPipe.getIdOfService("Youtube"));
        } catch (ExtractionException e) {
            Log.e(TAG, e.getMessage(), e);
        }
    }
    private int initPlayList(final Intent i) {
        return i.getIntExtra(SearchInfoItemFragment.PLAYLIST_ID, PLAYLIST_SYSTEM.QUEUE_ID);
    }

    private void initRemoveItemFrom(final RecyclerView recyclerView) {
        final ItemTouchHelper.Callback callback = new SimpleItemTouchHelperCallback(infoListAdapter);
        final ItemTouchHelper touchHelper = new ItemTouchHelper(callback);
        touchHelper.attachToRecyclerView(recyclerView);
        infoListAdapter.setOnItemDeleteListener(new InfoListAdapter.ItemListener() {
            @Override
            public void deletedItem(final StreamPreviewInfo deletedItem) {
                new AsyncTask<Void, Void, Void>() {
                    @Override
                    protected Void doInBackground(Void... voids) {
                        PlayListDataSource playListDatasource = new PlayListDataSource(PlaylistLocalActivity.this);
                        playListDatasource.deleteEntryFromPlayList(playListId, deletedItem.position);
                        return null;
                    }
                }.execute();
            }

            @Override
            public boolean moveItem(final int fromPosition, final int toPosition) {
                if (PLAYLIST_SYSTEM.HISTORIC_ID == playListId) {
                    return false;
                } else {
                    PlayListDataSource playListDataSource = new PlayListDataSource(PlaylistLocalActivity.this);
                    // update in database
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
                    // update in memory
                    infoListAdapter.notifyItemMoved(fromPosition, toPosition);
                    return true;
                }
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


    private void updateUi(final ChannelInfo info) {
        CollapsingToolbarLayout ctl = (CollapsingToolbarLayout) findViewById(R.id.channel_toolbar_layout);
        ProgressBar progressBar = (ProgressBar) findViewById(R.id.progressBar);
        ImageView channelBanner = (ImageView) findViewById(R.id.channel_banner_image);

        progressBar.setVisibility(View.GONE);

        if(!TextUtils.isEmpty(info.channel_name)) {
            ctl.setTitle(info.channel_name);
        }

        if(!TextUtils.isEmpty(info.banner_url)) {
            imageLoader.displayImage(info.banner_url, channelBanner,
                    new ImageErrorLoadingListener(this, rootView ,info.service_id));
        }

        if(info.related_streams != null && !info.related_streams.isEmpty()) {
            initFloatingActionButtonMenu(info);
        }
    }


    private void initFloatingActionButtonMenu(final ChannelInfo info) {

        final FloatingActionsMenu floatingActionsMenu = (FloatingActionsMenu) findViewById(R.id.multiple_actions_menu);
        final String channel_name = info.channel_name;

        final FloatingActionButton actionRecordToLocalPlaylist = (FloatingActionButton) findViewById(R.id.action_record_to_local_playlist);
        actionRecordToLocalPlaylist.setVisibility(View.GONE);

        final FloatingActionButton actionAddToQueue = (FloatingActionButton) findViewById(R.id.action_add_to_queue);
        actionAddToQueue.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                floatingActionsMenu.collapse();
                final AlertDialog.Builder alertDialog = new AlertDialog.Builder(PlaylistLocalActivity.this)
                        .setTitle(R.string.add_playlist_to_queue)
                        .setMessage(channel_name)
                        .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                final QueueManager queueManager = new QueueManager(getApplicationContext());
                                queueManager.addToQueue(playListId);
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
                final AlertDialog.Builder alertDialog = new AlertDialog.Builder(PlaylistLocalActivity.this)
                        .setTitle(R.string.replace_queue_by_playlist)
                        .setMessage(channel_name)
                        .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                final QueueManager queueManager = new QueueManager(getApplicationContext());
                                queueManager.replaceQueue(playListId);
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


    private void addVideos(final ChannelInfo info) {
        infoListAdapter.addStreamItemList(info.related_streams);
    }

    private void postNewErrorToast(Handler h, final int stringResource) {
        h.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(PlaylistLocalActivity.this,
                        stringResource, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void requestData(final boolean onlyVideos) {
        // start processing
        isLoading = true;
        final Thread t = new Thread(new Runnable() {
            final Handler h = new Handler();

            @Override
            public void run() {
                try {
                    final ChannelExtractor extractor;
                    extractor = PLAYLIST_SYSTEM.QUEUE_ID == playListId ?
                        service.getQueueExtractorInstance(PlaylistLocalActivity.this, pageNumber) :
                        service.getLocalPlayListExtractorInstance(PlaylistLocalActivity.this,
                                playListId, pageNumber);

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
                        ErrorActivity.reportError(h, PlaylistLocalActivity.this,
                                info.errors, null, rootView,
                                ErrorActivity.ErrorInfo.make(ErrorActivity.REQUESTED_CHANNEL,
                                        service.getServiceInfo().name, extractor.getChannelName(), 0 /* no message for the user */));
                    }
                } catch (IOException ioe) {
                    postNewErrorToast(h, R.string.network_error);
                    ioe.printStackTrace();
                } catch (Exception e) {
                    if (service != null && service.getServiceInfo() != null) {
                        ErrorActivity.reportError(h, PlaylistLocalActivity.this, e, VideoItemDetailFragment.class, null,
                                ErrorActivity.ErrorInfo.make(ErrorActivity.REQUESTED_CHANNEL,
                                        service.getServiceInfo().name, String.valueOf(playListId), R.string.general_error));
                    }
                    h.post(new Runnable() {
                        @Override
                        public void run() {
                            PlaylistLocalActivity.this.finish();
                        }
                    });
                    e.printStackTrace();
                }
            }
        });
        t.start();
    }

}
