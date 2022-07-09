package org.schabi.newpipe.util;

import android.content.Context;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.schabi.newpipe.DownloaderImpl;
import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.Stream;
import org.schabi.newpipe.extractor.stream.SubtitlesStream;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import us.shandian.giga.util.Utility;

/**
 * A list adapter for a list of {@link Stream streams}.
 * It currently supports {@link VideoStream}, {@link AudioStream} and {@link SubtitlesStream}.
 *
 * @param <T> the primary stream type's class extending {@link Stream}
 * @param <U> the secondary stream type's class extending {@link Stream}
 */
public class StreamItemAdapter<T extends Stream, U extends Stream> extends BaseAdapter {
    private final Context context;

    private final StreamSizeWrapper<T> streamsWrapper;
    private final SparseArray<SecondaryStreamHelper<U>> secondaryStreams;

    /**
     * Indicates that at least one of the primary streams is an instance of {@link VideoStream},
     * has no audio ({@link VideoStream#isVideoOnly()} returns true) and has no secondary stream
     * associated with it.
     */
    private final boolean hasAnyVideoOnlyStreamWithNoSecondaryStream;

    public StreamItemAdapter(final Context context, final StreamSizeWrapper<T> streamsWrapper,
                             final SparseArray<SecondaryStreamHelper<U>> secondaryStreams) {
        this.context = context;
        this.streamsWrapper = streamsWrapper;
        this.secondaryStreams = secondaryStreams;

        this.hasAnyVideoOnlyStreamWithNoSecondaryStream =
                checkHasAnyVideoOnlyStreamWithNoSecondaryStream();
    }

    public StreamItemAdapter(final Context context, final StreamSizeWrapper<T> streamsWrapper) {
        this(context, streamsWrapper, null);
    }

    public List<T> getAll() {
        return streamsWrapper.getStreamsList();
    }

    public SparseArray<SecondaryStreamHelper<U>> getAllSecondary() {
        return secondaryStreams;
    }

    @Override
    public int getCount() {
        return streamsWrapper.getStreamsList().size();
    }

    @Override
    public T getItem(final int position) {
        return streamsWrapper.getStreamsList().get(position);
    }

    @Override
    public long getItemId(final int position) {
        return position;
    }

    @Override
    public View getDropDownView(final int position,
                                final View convertView,
                                final ViewGroup parent) {
        return getCustomView(position, convertView, parent, true);
    }

    @Override
    public View getView(final int position, final View convertView, final ViewGroup parent) {
        return getCustomView(((Spinner) parent).getSelectedItemPosition(),
                convertView, parent, false);
    }

    @NonNull
    private View getCustomView(final int position,
                               final View view,
                               final ViewGroup parent,
                               final boolean isDropdownItem) {
        View convertView = view;
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(
                    R.layout.stream_quality_item, parent, false);
        }

        final ImageView woSoundIconView = convertView.findViewById(R.id.wo_sound_icon);
        final TextView formatNameView = convertView.findViewById(R.id.stream_format_name);
        final TextView qualityView = convertView.findViewById(R.id.stream_quality);
        final TextView sizeView = convertView.findViewById(R.id.stream_size);

        final T stream = getItem(position);
        final MediaFormat mediaFormat = stream.getFormat();

        int woSoundIconVisibility = View.GONE;
        String qualityString;

        if (stream instanceof VideoStream) {
            final VideoStream videoStream = ((VideoStream) stream);
            qualityString = videoStream.getResolution();

            if (hasAnyVideoOnlyStreamWithNoSecondaryStream) {
                if (videoStream.isVideoOnly()) {
                    woSoundIconVisibility = hasSecondaryStream(position)
                            // It has a secondary stream associated with it, so check if it's a
                            // dropdown view so it doesn't look out of place (missing margin)
                            // compared to those that don't.
                            ? (isDropdownItem ? View.INVISIBLE : View.GONE)
                            // It doesn't have a secondary stream, icon is visible no matter what.
                            : View.VISIBLE;
                } else if (isDropdownItem) {
                    woSoundIconVisibility = View.INVISIBLE;
                }
            }
        } else if (stream instanceof AudioStream) {
            final AudioStream audioStream = ((AudioStream) stream);
            if (audioStream.getAverageBitrate() > 0) {
                qualityString = audioStream.getAverageBitrate() + "kbps";
            } else if (mediaFormat != null) {
                qualityString = mediaFormat.getName();
            } else {
                qualityString = context.getString(R.string.unknown_quality);
            }
        } else if (stream instanceof SubtitlesStream) {
            qualityString = ((SubtitlesStream) stream).getDisplayLanguageName();
            if (((SubtitlesStream) stream).isAutoGenerated()) {
                qualityString += " (" + context.getString(R.string.caption_auto_generated) + ")";
            }
        } else {
            if (mediaFormat == null) {
                qualityString = context.getString(R.string.unknown_quality);
            } else {
                qualityString = mediaFormat.getSuffix();
            }
        }

        if (streamsWrapper.getSizeInBytes(position) > 0) {
            final SecondaryStreamHelper<U> secondary = secondaryStreams == null ? null
                    : secondaryStreams.get(position);
            if (secondary != null) {
                final long size = secondary.getSizeInBytes()
                        + streamsWrapper.getSizeInBytes(position);
                sizeView.setText(Utility.formatBytes(size));
            } else {
                sizeView.setText(streamsWrapper.getFormattedSize(position));
            }
            sizeView.setVisibility(View.VISIBLE);
        } else {
            sizeView.setVisibility(View.GONE);
        }

        if (stream instanceof SubtitlesStream) {
            formatNameView.setText(((SubtitlesStream) stream).getLanguageTag());
        } else {
            if (mediaFormat == null) {
                formatNameView.setText(context.getString(R.string.unknown_format));
            } else if (mediaFormat == MediaFormat.WEBMA_OPUS) {
                // noinspection AndroidLintSetTextI18n
                formatNameView.setText("opus");
            } else {
                formatNameView.setText(mediaFormat.getName());
            }
        }

        qualityView.setText(qualityString);
        woSoundIconView.setVisibility(woSoundIconVisibility);

        return convertView;
    }

    /**
     * @param position which primary stream to check.
     * @return whether the primary stream at position has a secondary stream associated with it.
     */
    private boolean hasSecondaryStream(final int position) {
        return secondaryStreams != null && secondaryStreams.get(position) != null;
    }

    /**
     * @return if there are any video-only streams with no secondary stream associated with them.
     * @see #hasAnyVideoOnlyStreamWithNoSecondaryStream
     */
    private boolean checkHasAnyVideoOnlyStreamWithNoSecondaryStream() {
        for (int i = 0; i < streamsWrapper.getStreamsList().size(); i++) {
            final T stream = streamsWrapper.getStreamsList().get(i);
            if (stream instanceof VideoStream) {
                final boolean videoOnly = ((VideoStream) stream).isVideoOnly();
                if (videoOnly && !hasSecondaryStream(i)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * A wrapper class that includes a way of storing the stream sizes.
     *
     * @param <T> the stream type's class extending {@link Stream}
     */
    public static class StreamSizeWrapper<T extends Stream> implements Serializable {
        private static final StreamSizeWrapper<Stream> EMPTY = new StreamSizeWrapper<>(
                Collections.emptyList(), null);
        private final List<T> streamsList;
        private final long[] streamSizes;
        private final String unknownSize;

        public StreamSizeWrapper(final List<T> sL, final Context context) {
            this.streamsList = sL != null
                    ? sL
                    : Collections.emptyList();
            this.streamSizes = new long[streamsList.size()];
            this.unknownSize = context == null
                    ? "--.-" : context.getString(R.string.unknown_content);

            Arrays.fill(streamSizes, -2);
        }

        /**
         * Helper method to fetch the sizes of all the streams in a wrapper.
         *
         * @param <X> the stream type's class extending {@link Stream}
         * @param streamsWrapper the wrapper
         * @return a {@link Single} that returns a boolean indicating if any elements were changed
         */
        @NonNull
        public static <X extends Stream> Single<Boolean> fetchSizeForWrapper(
                final StreamSizeWrapper<X> streamsWrapper) {
            final Callable<Boolean> fetchAndSet = () -> {
                boolean hasChanged = false;
                for (final X stream : streamsWrapper.getStreamsList()) {
                    if (streamsWrapper.getSizeInBytes(stream) > -2) {
                        continue;
                    }

                    final long contentLength = DownloaderImpl.getInstance().getContentLength(
                            stream.getContent());
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

        public static <X extends Stream> StreamSizeWrapper<X> empty() {
            //noinspection unchecked
            return (StreamSizeWrapper<X>) EMPTY;
        }

        public List<T> getStreamsList() {
            return streamsList;
        }

        public long getSizeInBytes(final int streamIndex) {
            return streamSizes[streamIndex];
        }

        public long getSizeInBytes(final T stream) {
            return streamSizes[streamsList.indexOf(stream)];
        }

        public String getFormattedSize(final int streamIndex) {
            return formatSize(getSizeInBytes(streamIndex));
        }

        public String getFormattedSize(final T stream) {
            return formatSize(getSizeInBytes(stream));
        }

        private String formatSize(final long size) {
            if (size > -1) {
                return Utility.formatBytes(size);
            }
            return unknownSize;
        }

        public void setSize(final int streamIndex, final long sizeInBytes) {
            streamSizes[streamIndex] = sizeInBytes;
        }

        public void setSize(final T stream, final long sizeInBytes) {
            streamSizes[streamsList.indexOf(stream)] = sizeInBytes;
        }
    }
}
