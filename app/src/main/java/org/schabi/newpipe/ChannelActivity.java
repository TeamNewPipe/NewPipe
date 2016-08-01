package org.schabi.newpipe;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.design.widget.CollapsingToolbarLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.assist.FailReason;
import com.nostra13.universalimageloader.core.listener.ImageLoadingListener;

import org.schabi.newpipe.extractor.ChannelExtractor;
import org.schabi.newpipe.extractor.ChannelInfo;
import org.schabi.newpipe.extractor.ExtractionException;
import org.schabi.newpipe.extractor.ParsingException;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.StreamingService;

import java.io.IOException;

public class ChannelActivity extends AppCompatActivity {

    private static final String TAG = ChannelActivity.class.toString();
    private View rootView = null;

    class FailedThumbnailListener implements ImageLoadingListener {

        int serviceId = -1;

        public FailedThumbnailListener(int serviceId) {
            this.serviceId= serviceId;
        }


        @Override
        public void onLoadingStarted(String imageUri, View view) {
        }

        @Override
        public void onLoadingFailed(String imageUri, View view, FailReason failReason) {
            ErrorActivity.reportError(ChannelActivity.this,
                    failReason.getCause(), null, rootView,
                    ErrorActivity.ErrorInfo.make(ErrorActivity.LOAD_IMAGE,
                            ServiceList.getNameOfService(serviceId), imageUri,
                            R.string.could_not_load_image));
        }

        @Override
        public void onLoadingComplete(String imageUri, View view, Bitmap loadedImage) {
        }

        @Override
        public void onLoadingCancelled(String imageUri, View view) {
        }
    }

    // intent const
    public static final String CHANNEL_URL = "channel_url";
    public static final String SERVICE_ID = "service_id";

    private int serviceId = -1;
    private String channelUrl = "";

    private ImageLoader imageLoader = ImageLoader.getInstance();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_channel);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        rootView = findViewById(R.id.rootView);
        setSupportActionBar(toolbar);
        Intent i = getIntent();
        channelUrl = i.getStringExtra(CHANNEL_URL);
        serviceId = i.getIntExtra(SERVICE_ID, -1);

        // start processing
        Thread channelExtractorThread = new Thread(new Runnable() {
            Handler h = new Handler();

            @Override
            public void run() {
                try {
                    StreamingService service = ServiceList.getService(serviceId);
                    ChannelExtractor extractor = service.getChannelExtractorInstance(
                            channelUrl, new Downloader());

                    final ChannelInfo info = ChannelInfo.getInfo(extractor, new Downloader());


                    h.post(new Runnable() {
                        @Override
                        public void run() {
                            updateUi(info);
                        }
                    });
                } catch(IOException ioe) {
                    postNewErrorToast(h, R.string.network_error);
                    ioe.printStackTrace();
                } catch(ParsingException pe) {
                    pe.printStackTrace();
                } catch(ExtractionException ex) {
                    ex.printStackTrace();
                } catch(Exception e) {
                    e.printStackTrace();
                }
            }
        });

        channelExtractorThread.start();
    }



    private void updateUi(final ChannelInfo info) {
        CollapsingToolbarLayout ctl = (CollapsingToolbarLayout) findViewById(R.id.channel_toolbar_layout);
        ProgressBar progressBar = (ProgressBar) findViewById(R.id.progressBar);
        ImageView channelBanner = (ImageView) findViewById(R.id.channel_banner_image);
        View channelContentView = (View) findViewById(R.id.channel_content_view);
        FloatingActionButton feedButton = (FloatingActionButton) findViewById(R.id.channel_rss_fab);

        progressBar.setVisibility(View.GONE);
        channelContentView.setVisibility(View.VISIBLE);

        if(info.channel_name != null && !info.channel_name.isEmpty()) {
            ctl.setTitle(info.channel_name);
        }

        if(info.banner_url != null && !info.banner_url.isEmpty()) {
            imageLoader.displayImage(info.banner_url, channelBanner,
                    new FailedThumbnailListener(info.service_id));
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

    private void postNewErrorToast(Handler h, final int stringResource) {
        h.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(ChannelActivity.this,
                        stringResource, Toast.LENGTH_LONG).show();
            }
        });
    }


}
