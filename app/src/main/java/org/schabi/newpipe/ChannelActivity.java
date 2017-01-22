package org.schabi.newpipe;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.design.widget.CollapsingToolbarLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.nostra13.universalimageloader.core.ImageLoader;

import org.schabi.newpipe.detail.VideoItemDetailActivity;
import org.schabi.newpipe.detail.VideoItemDetailFragment;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.channel.ChannelExtractor;
import org.schabi.newpipe.extractor.channel.ChannelInfo;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.info_list.InfoItemBuilder;
import org.schabi.newpipe.info_list.InfoListAdapter;
import org.schabi.newpipe.report.ErrorActivity;

import java.io.IOException;
import java.util.Objects;

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


    private static final String TAG = ChannelActivity.class.toString();
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
        if (Objects.equals(PreferenceManager.getDefaultSharedPreferences(this)
                .getString("theme", getResources().getString(R.string.light_theme_title)), getResources().getString(R.string.dark_theme_title))) {
            setTheme(R.style.DarkTheme_NoActionBar);
        }
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_channel);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        rootView = findViewById(R.id.rootView);
        setSupportActionBar(toolbar);
        Intent i = getIntent();
        channelUrl = i.getStringExtra(CHANNEL_URL);
        serviceId = i.getIntExtra(SERVICE_ID, -1);

        infoListAdapter = new InfoListAdapter(this, rootView);
        RecyclerView recyclerView = (RecyclerView) findViewById(R.id.channel_streams_view);
        final LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(infoListAdapter);
        infoListAdapter.setOnItemSelectedListener(new InfoItemBuilder.OnItemSelectedListener() {
            @Override
            public void selected(String url) {
                Intent detailIntent = new Intent(ChannelActivity.this, VideoItemDetailActivity.class);
                detailIntent.putExtra(VideoItemDetailFragment.VIDEO_URL, url);
                detailIntent.putExtra(
                        VideoItemDetailFragment.STREAMING_SERVICE, serviceId);
                startActivity(detailIntent);
            }
        });

        // detect if list has ben scrolled to the bottom
        recyclerView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                int pastVisiblesItems, visibleItemCount, totalItemCount;
                super.onScrolled(recyclerView, dx, dy);
                if(dy > 0) //check for scroll down
                {
                    visibleItemCount = layoutManager.getChildCount();
                    totalItemCount = layoutManager.getItemCount();
                    pastVisiblesItems = layoutManager.findFirstVisibleItemPosition();

                    if ( (visibleItemCount + pastVisiblesItems) >= totalItemCount
                            && !isLoading
                            && hasNextPage)
                    {
                        pageNumber++;
                        requestData(true);
                    }
                }
            }
        });

        requestData(false);
    }



    private void updateUi(final ChannelInfo info) {
        CollapsingToolbarLayout ctl = (CollapsingToolbarLayout) findViewById(R.id.channel_toolbar_layout);
        ProgressBar progressBar = (ProgressBar) findViewById(R.id.progressBar);
        ImageView channelBanner = (ImageView) findViewById(R.id.channel_banner_image);
        final FloatingActionButton feedButton = (FloatingActionButton) findViewById(R.id.channel_rss_fab);
        ImageView avatarView = (ImageView) findViewById(R.id.channel_avatar_view);
        ImageView haloView = (ImageView) findViewById(R.id.channel_avatar_halo);

        progressBar.setVisibility(View.GONE);

        if(info.channel_name != null && !info.channel_name.isEmpty()) {
            ctl.setTitle(info.channel_name);
        }

        if(info.banner_url != null && !info.banner_url.isEmpty()) {
            imageLoader.displayImage(info.banner_url, channelBanner,
                    new ImageErrorLoadingListener(this, rootView ,info.service_id));
        }

        if(info.avatar_url != null && !info.avatar_url.isEmpty()) {
            avatarView.setVisibility(View.VISIBLE);
            haloView.setVisibility(View.VISIBLE);
            imageLoader.displayImage(info.avatar_url, avatarView,
                    new ImageErrorLoadingListener(this, rootView ,info.service_id));
        }

        if(info.feed_url != null && !info.feed_url.isEmpty()) {
            feedButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Log.d(TAG, info.feed_url);
                    Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(info.feed_url));
                    startActivity(i);
                }
            });
        } else {
            feedButton.setVisibility(View.GONE);
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
        Thread channelExtractorThread = new Thread(new Runnable() {
            Handler h = new Handler();

            @Override
            public void run() {
                StreamingService service = null;
                try {
                    service = NewPipe.getService(serviceId);
                    ChannelExtractor extractor = service.getChannelExtractorInstance(
                            channelUrl, pageNumber);

                    final ChannelInfo info = ChannelInfo.getInfo(extractor);


                    h.post(new Runnable() {
                        @Override
                        public void run() {
                            isLoading = false;
                            if(!onlyVideos) {
                                updateUi(info);
                            }
                            hasNextPage = info.hasNextPage;
                            addVideos(info);
                        }
                    });

                    // look for non critical errors during extraction
                    if(info != null &&
                            !info.errors.isEmpty()) {
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
                } catch(IOException ioe) {
                    postNewErrorToast(h, R.string.network_error);
                    ioe.printStackTrace();
                } catch(ParsingException pe) {
                    ErrorActivity.reportError(h, ChannelActivity.this, pe, VideoItemDetailFragment.class, null,
                            ErrorActivity.ErrorInfo.make(ErrorActivity.REQUESTED_CHANNEL,
                                    service.getServiceInfo().name, channelUrl, R.string.parsing_error));
                    h.post(new Runnable() {
                        @Override
                        public void run() {
                            ChannelActivity.this.finish();
                        }
                    });
                    pe.printStackTrace();
                } catch(ExtractionException ex) {
                    ErrorActivity.reportError(h, ChannelActivity.this, ex, VideoItemDetailFragment.class, null,
                            ErrorActivity.ErrorInfo.make(ErrorActivity.REQUESTED_CHANNEL,
                                    service.getServiceInfo().name, channelUrl, R.string.parsing_error));
                    h.post(new Runnable() {
                        @Override
                        public void run() {
                            ChannelActivity.this.finish();
                        }
                    });
                    ex.printStackTrace();
                } catch(Exception e) {
                    ErrorActivity.reportError(h, ChannelActivity.this, e, VideoItemDetailFragment.class, null,
                            ErrorActivity.ErrorInfo.make(ErrorActivity.REQUESTED_CHANNEL,
                                    service.getServiceInfo().name, channelUrl, R.string.general_error));
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

        channelExtractorThread.start();
    }

}
