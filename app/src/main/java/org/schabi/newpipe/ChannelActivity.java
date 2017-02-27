package org.schabi.newpipe;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.nostra13.universalimageloader.core.ImageLoader;

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
import org.schabi.newpipe.settings.SettingsActivity;
import org.schabi.newpipe.util.NavStack;
import java.io.IOException;
import static android.os.Build.VERSION.SDK_INT;
import org.schabi.newpipe.util.ThemeHelper;


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

    private int serviceId = -1;
    private String channelUrl = "";
    private int pageNumber = 0;
    private boolean hasNextPage = true;
    private boolean isLoading = false;

    private ImageLoader imageLoader = ImageLoader.getInstance();
    private InfoListAdapter infoListAdapter = null;

    private String subS = "";

    ProgressBar progressBar = null;
    ImageView channelBanner = null;
    ImageView avatarView = null;
    TextView titleView = null;
    TextView subscirberView = null;
    Button subscriberButton = null;
    View subscriberLayout = null;

    View header = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeHelper.setTheme(this, true);
        setContentView(R.layout.activity_channel);
        rootView = findViewById(android.R.id.content);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowTitleEnabled(true);

        infoListAdapter = new InfoListAdapter(this, rootView);
        RecyclerView recyclerView = (RecyclerView) findViewById(R.id.channel_streams_view);
        final LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);
        header = getLayoutInflater().inflate(R.layout.channel_header, recyclerView, false);
        infoListAdapter.setHeader(header);
        recyclerView.setAdapter(infoListAdapter);
        infoListAdapter.setOnStreamInfoItemSelectedListener(
                new InfoItemBuilder.OnInfoItemSelectedListener() {
            @Override
            public void selected(String url, int serviceId) {
                NavStack.getInstance()
                        .openDetailActivity(ChannelActivity.this, url, serviceId);
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

        subS = getString(R.string.subscriber);

        progressBar = (ProgressBar) findViewById(R.id.progressBar);
        channelBanner = (ImageView) header.findViewById(R.id.channel_banner_image);
        avatarView = (ImageView) header.findViewById(R.id.channel_avatar_view);
        titleView = (TextView) header.findViewById(R.id.channel_title_view);
        subscirberView = (TextView) header.findViewById(R.id.channel_subscriber_view);
        subscriberButton = (Button) header.findViewById(R.id.channel_subscribe_button);
        subscriberLayout = header.findViewById(R.id.channel_subscriber_layout);

        if(savedInstanceState == null) {
            handleIntent(getIntent());
        } else {
            channelUrl = savedInstanceState.getString(NavStack.URL);
            serviceId = savedInstanceState.getInt(NavStack.SERVICE_ID);
            NavStack.getInstance()
                    .restoreSavedInstanceState(savedInstanceState);
        }

    }

    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent i) {
        channelUrl = i.getStringExtra(NavStack.URL);
        serviceId = i.getIntExtra(NavStack.SERVICE_ID, -1);
        requestData(false);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(NavStack.URL, channelUrl);
        outState.putInt(NavStack.SERVICE_ID, serviceId);
        NavStack.getInstance()
                .onSaveInstanceState(outState);
    }

    private void updateUi(final ChannelInfo info) {
        findViewById(R.id.channel_header_layout).setVisibility(View.VISIBLE);
        progressBar.setVisibility(View.GONE);

        if(info.channel_name != null && !info.channel_name.isEmpty()) {
            getSupportActionBar().setTitle(info.channel_name);
            titleView.setText(info.channel_name);
        }

        if(info.banner_url != null && !info.banner_url.isEmpty()) {
            imageLoader.displayImage(info.banner_url, channelBanner,
                   new ImageErrorLoadingListener(this, rootView ,info.service_id));
        }

        if(info.avatar_url != null && !info.avatar_url.isEmpty()) {
            avatarView.setVisibility(View.VISIBLE);
            imageLoader.displayImage(info.avatar_url, avatarView,
                    new ImageErrorLoadingListener(this, rootView ,info.service_id));
        }

        if(info.subscriberCount != -1) {
            subscirberView.setText(buildSubscriberString(info.subscriberCount));
        }

        if((info.feed_url != null && !info.feed_url.isEmpty()) ||
                (info.subscriberCount != -1)) {
            subscriberLayout.setVisibility(View.VISIBLE);
        }

        if(info.feed_url != null && !info.feed_url.isEmpty()) {
            subscriberButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Log.d(TAG, info.feed_url);
                    Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(info.feed_url));
                    startActivity(i);
                }
            });
        } else {
            subscriberButton.setVisibility(View.INVISIBLE);
        }

    }

    private void addVideos(final ChannelInfo info) {
        infoListAdapter.addInfoItemList(info.related_streams);
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

        if(!onlyVideos) {
            //delete already displayed content
            progressBar.setVisibility(View.VISIBLE);
            infoListAdapter.clearSteamItemList();
            if (SDK_INT >= 21) {
                channelBanner.setImageDrawable(getDrawable(R.drawable.channel_banner));
                avatarView.setImageDrawable(getDrawable(R.drawable.buddy));
                subscriberLayout.setVisibility(View.GONE);
                titleView.setText("");
                getSupportActionBar().setTitle("");
            }
        }


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
                    String name = "none";
                    if(service != null) {
                        name = service.getServiceInfo().name;
                    }
                    ErrorActivity.reportError(h, ChannelActivity.this, ex, VideoItemDetailFragment.class, null,
                            ErrorActivity.ErrorInfo.make(ErrorActivity.REQUESTED_CHANNEL,
                                    name, channelUrl, R.string.parsing_error));
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

    @Override
    public void onBackPressed() {
        try {
            NavStack.getInstance()
                    .navBack(this);
        } catch (Exception e) {
            ErrorActivity.reportUiError(this, e);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.menu_channel, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        super.onOptionsItemSelected(item);
        switch(item.getItemId()) {
            case R.id.action_settings: {
                Intent intent = new Intent(this, SettingsActivity.class);
                startActivity(intent);
                return true;
            }
            case R.id.menu_item_openInBrowser: {
                Intent intent = new Intent();
                intent.setAction(Intent.ACTION_VIEW);
                intent.setData(Uri.parse(channelUrl));

                startActivity(Intent.createChooser(intent, getString(R.string.choose_browser)));
            }
            case R.id.menu_item_share:
                Intent intent = new Intent();
                intent.setAction(Intent.ACTION_SEND);
                intent.putExtra(Intent.EXTRA_TEXT, channelUrl);
                intent.setType("text/plain");
                startActivity(Intent.createChooser(intent, getString(R.string.share_dialog_title)));
            case android.R.id.home:
                NavStack.getInstance().openMainActivity(this);
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private String buildSubscriberString(long count) {
        String out = "";
        if(count >= 1000000000){
            out += Long.toString((count/1000000000)%1000)+".";
        }
        if(count>=1000000){
            out += Long.toString((count/1000000)%1000) + ".";
        }
        if(count>=1000){
            out += Long.toString((count/1000)%1000)+".";
        }
        out += Long.toString(count%1000) + " " + subS;
        return out;
    }
}
