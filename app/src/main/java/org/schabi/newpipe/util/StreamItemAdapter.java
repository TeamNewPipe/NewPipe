package org.schabi.newpipe.util;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;

import org.schabi.newpipe.Downloader;
import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.Stream;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;

import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;
import us.shandian.giga.util.Utility;

/**
 * A list adapter for a list of {@link Stream streams}, currently supporting {@link VideoStream} and {@link AudioStream}.
 */
public class StreamItemAdapter<T extends Stream> extends BaseAdapter {
    private final Context context;

    private final StreamSizeWrapper<T> streamsWrapper;
    private final boolean showIconNoAudio;

    public StreamItemAdapter(Context context, StreamSizeWrapper<T> streamsWrapper, boolean showIconNoAudio) {
        this.context = context;
        this.streamsWrapper = streamsWrapper;
        this.showIconNoAudio = showIconNoAudio;
    }

    public StreamItemAdapter(Context context, StreamSizeWrapper<T> streamsWrapper) {
        this(context, streamsWrapper, false);
    }

    public List<T> getAll() {
        return streamsWrapper.getStreamsList();
    }

    @Override
    public int getCount() {
        return streamsWrapper.getStreamsList().size();
    }

    @Override
    public T getItem(int position) {
        return streamsWrapper.getStreamsList().get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getDropDownView(int position, View convertView, ViewGroup parent) {
        return getCustomView(position, convertView, parent, true);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        return getCustomView(((Spinner) parent).getSelectedItemPosition(), convertView, parent, false);
    }

    private View getCustomView(int position, View convertView, ViewGroup parent, boolean isDropdownItem) {
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.stream_quality_item, parent, false);
        }

        final ImageView woSoundIconView = convertView.findViewById(R.id.wo_sound_icon);
        final TextView formatNameView = convertView.findViewById(R.id.stream_format_name);
        final TextView qualityView = convertView.findViewById(R.id.stream_quality);
        final TextView sizeView = convertView.findViewById(R.id.stream_size);

        final T stream = getItem(position);

        int woSoundIconVisibility = View.GONE;
        String qualityString;

        if (stream instanceof VideoStream) {
            qualityString = ((VideoStream) stream).getResolution();

            if (!showIconNoAudio) {
                woSoundIconVisibility = View.GONE;
            } else if (((VideoStream) stream).isVideoOnly()) {
                woSoundIconVisibility = View.VISIBLE;
            } else if (isDropdownItem) {
                woSoundIconVisibility = View.INVISIBLE;
            }
        } else if (stream instanceof AudioStream) {
            qualityString = ((AudioStream) stream).getAverageBitrate() + "kbps";
        } else {
            qualityString = stream.getFormat().getSuffix();
        }

        if (streamsWrapper.getSizeInBytes(position) > 0) {
            sizeView.setText(streamsWrapper.getFormattedSize(position));
            sizeView.setVisibility(View.VISIBLE);
        } else {
            sizeView.setVisibility(View.GONE);
        }

        formatNameView.setText(stream.getFormat().getName());
        qualityView.setText(qualityString);
        woSoundIconView.setVisibility(woSoundIconVisibility);

        return convertView;
    }

    /**
     * A wrapper class that includes a way of storing the stream sizes.
     */
    public static class StreamSizeWrapper<T extends Stream> implements Serializable {
        private static final StreamSizeWrapper<Stream> EMPTY = new StreamSizeWrapper<>(Collections.emptyList());
        private final List<T> streamsList;
        private final long[] streamSizes;

        public StreamSizeWrapper(List<T> streamsList) {
            this.streamsList = streamsList;
            this.streamSizes = new long[streamsList.size()];

            for (int i = 0; i < streamSizes.length; i++) streamSizes[i] = -1;
        }

        /**
         * Helper method to fetch the sizes of all the streams in a wrapper.
         *
         * @param streamsWrapper the wrapper
         * @return a {@link Single} that returns a boolean indicating if any elements were changed
         */
        public static <X extends Stream> Single<Boolean> fetchSizeForWrapper(StreamSizeWrapper<X> streamsWrapper) {
            final Callable<Boolean> fetchAndSet = () -> {
                boolean hasChanged = false;
                for (X stream : streamsWrapper.getStreamsList()) {
                    if (streamsWrapper.getSizeInBytes(stream) > 0) {
                        continue;
                    }

                    final long contentLength = Downloader.getInstance().getContentLength(stream.getUrl());
                    streamsWrapper.setSize(stream, contentLength);
                    hasChanged = true;
                }
                return hasChanged;
            };

            return Single.fromCallable(fetchAndSet)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .onErrorReturnItem(true);
        }

        public List<T> getStreamsList() {
            return streamsList;
        }

        public long getSizeInBytes(int streamIndex) {
            return streamSizes[streamIndex];
        }

        public long getSizeInBytes(T stream) {
            return streamSizes[streamsList.indexOf(stream)];
        }

        public String getFormattedSize(int streamIndex) {
            return Utility.formatBytes(getSizeInBytes(streamIndex));
        }

        public String getFormattedSize(T stream) {
            return Utility.formatBytes(getSizeInBytes(stream));
        }

        public void setSize(int streamIndex, long sizeInBytes) {
            streamSizes[streamIndex] = sizeInBytes;
        }

        public void setSize(T stream, long sizeInBytes) {
            streamSizes[streamsList.indexOf(stream)] = sizeInBytes;
        }

        public static <X extends Stream> StreamSizeWrapper<X> empty() {
            //noinspection unchecked
            return (StreamSizeWrapper<X>) EMPTY;
        }
    }
}