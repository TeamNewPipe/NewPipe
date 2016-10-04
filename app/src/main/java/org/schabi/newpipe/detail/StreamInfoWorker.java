package org.schabi.newpipe.detail;

import android.app.Activity;
import android.os.Handler;
import android.util.Log;
import android.view.View;

import org.schabi.newpipe.Downloader;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.stream_info.StreamExtractor;
import org.schabi.newpipe.extractor.stream_info.StreamInfo;
import org.schabi.newpipe.report.ErrorActivity;
import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.services.youtube.YoutubeStreamExtractor;

import java.io.IOException;

/**
 * Created by Christian Schabesberger on 02.08.16.
 *
 * Copyright (C) Christian Schabesberger 2016 <chris.schabesberger@mailbox.org>
 * StreamInfoWorker.java is part of NewPipe.
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

public class StreamInfoWorker {

    private static final String TAG = StreamInfoWorker.class.toString();

    public interface OnStreamInfoReceivedListener {
        void onReceive(StreamInfo info);
        void onError(int messageId);
        void onBlockedByGemaError();
        void onContentErrorWithMessage(int messageId);
        void onContentError();
    }

    private class StreamExtractorRunnable implements Runnable {
        private final Handler h = new Handler();
        private StreamExtractor streamExtractor;
        private final int serviceId;
        private final String videoUrl;
        private Activity a;

        public StreamExtractorRunnable(Activity a, String videoUrl, int serviceId) {
            this.serviceId = serviceId;
            this.videoUrl = videoUrl;
            this.a = a;
        }

        @Override
        public void run() {
            StreamInfo streamInfo = null;
            StreamingService service = null;
            try {
                service = NewPipe.getService(serviceId);
            } catch (Exception e) {
                e.printStackTrace();
                ErrorActivity.reportError(h, a, e, VideoItemDetailFragment.class, null,
                        ErrorActivity.ErrorInfo.make(ErrorActivity.REQUESTED_STREAM,
                                "", videoUrl, R.string.could_not_get_stream));
                return;
            }
            try {
                streamExtractor = service.getExtractorInstance(videoUrl);
                streamInfo = StreamInfo.getVideoInfo(streamExtractor);

                final StreamInfo info = streamInfo;
                h.post(new Runnable() {
                    @Override
                    public void run() {
                        onStreamInfoReceivedListener.onReceive(info);
                    }
                });

                // look for errors during extraction
                // this if statement only covers extra information.
                // if these are not available or caused an error, they are just not available
                // but don't render the stream information unusalbe.
                if(streamInfo != null &&
                        !streamInfo.errors.isEmpty()) {
                    Log.e(TAG, "OCCURRED ERRORS DURING EXTRACTION:");
                    for (Throwable e : streamInfo.errors) {
                        e.printStackTrace();
                        Log.e(TAG, "------");
                    }

                    View rootView = a != null ? a.findViewById(R.id.video_item_detail) : null;
                    ErrorActivity.reportError(h, a,
                            streamInfo.errors, null, rootView,
                            ErrorActivity.ErrorInfo.make(ErrorActivity.REQUESTED_STREAM,
                                    service.getServiceInfo().name, videoUrl, 0 /* no message for the user */));
                }

                // These errors render the stream information unusable.
            } catch (IOException e) {
                h.post(new Runnable() {
                    @Override
                    public void run() {
                        onStreamInfoReceivedListener.onError(R.string.network_error);
                    }
                });
                e.printStackTrace();
            }
            // custom service related exceptions
            catch (YoutubeStreamExtractor.DecryptException de) {
                h.post(new Runnable() {
                    @Override
                    public void run() {
                        onStreamInfoReceivedListener.onError(R.string.youtube_signature_decryption_error);
                    }
                });
                de.printStackTrace();
            } catch (YoutubeStreamExtractor.GemaException ge) {
                h.post(new Runnable() {
                    @Override
                    public void run() {
                        onStreamInfoReceivedListener.onBlockedByGemaError();
                    }
                });
            } catch(YoutubeStreamExtractor.LiveStreamException e) {
                h.post(new Runnable() {
                    @Override
                    public void run() {
                        onStreamInfoReceivedListener
                                .onContentErrorWithMessage(R.string.live_streams_not_supported);
                    }
                });
            }
            // ----------------------------------------
            catch(StreamExtractor.ContentNotAvailableException e) {
                h.post(new Runnable() {
                    @Override
                    public void run() {
                        onStreamInfoReceivedListener
                                .onContentError();
                    }
                });
                e.printStackTrace();
            } catch(StreamInfo.StreamExctractException e) {
                if(!streamInfo.errors.isEmpty()) {
                    // !!! if this case ever kicks in someone gets kicked out !!!
                    ErrorActivity.reportError(h, a, e, VideoItemDetailFragment.class, null,
                            ErrorActivity.ErrorInfo.make(ErrorActivity.REQUESTED_STREAM,
                                    service.getServiceInfo().name, videoUrl, R.string.could_not_get_stream));
                } else {
                    ErrorActivity.reportError(h, a, streamInfo.errors, VideoItemDetailFragment.class, null,
                            ErrorActivity.ErrorInfo.make(ErrorActivity.REQUESTED_STREAM,
                                    service.getServiceInfo().name, videoUrl, R.string.could_not_get_stream));
                }
                h.post(new Runnable() {
                    @Override
                    public void run() {
                        a.finish();
                    }
                });
                e.printStackTrace();
            } catch (ParsingException e) {
                ErrorActivity.reportError(h, a, e, VideoItemDetailFragment.class, null,
                        ErrorActivity.ErrorInfo.make(ErrorActivity.REQUESTED_STREAM,
                                service.getServiceInfo().name, videoUrl, R.string.parsing_error));
                h.post(new Runnable() {
                    @Override
                    public void run() {
                        a.finish();
                    }
                });
                e.printStackTrace();
            } catch(Exception e) {
                ErrorActivity.reportError(h, a, e, VideoItemDetailFragment.class, null,
                        ErrorActivity.ErrorInfo.make(ErrorActivity.REQUESTED_STREAM,
                                service.getServiceInfo().name, videoUrl, R.string.general_error));
                h.post(new Runnable() {
                    @Override
                    public void run() {
                        a.finish();
                    }
                });
                e.printStackTrace();
            }
        }
    }

    private static StreamInfoWorker streamInfoWorker = null;
    private StreamExtractorRunnable runnable = null;
    private OnStreamInfoReceivedListener onStreamInfoReceivedListener = null;

    private StreamInfoWorker() {

    }

    public static StreamInfoWorker getInstance() {
        return streamInfoWorker == null ? (streamInfoWorker = new StreamInfoWorker()) : streamInfoWorker;
    }

    public void search(int serviceId, String url, Activity a) {
        runnable = new StreamExtractorRunnable(a, url, serviceId);
        Thread thread = new Thread(runnable);
        thread.start();
    }

    public void setOnStreamInfoReceivedListener(
            OnStreamInfoReceivedListener onStreamInfoReceivedListener) {
        this.onStreamInfoReceivedListener = onStreamInfoReceivedListener;
    }
}
