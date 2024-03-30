package org.schabi.newpipe.util

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import androidx.annotation.VisibleForTesting
import androidx.collection.SparseArrayCompat
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.DownloaderImpl
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.Stream
import org.schabi.newpipe.extractor.stream.SubtitlesStream
import org.schabi.newpipe.extractor.stream.VideoStream
import org.schabi.newpipe.extractor.utils.Utils
import us.shandian.giga.util.Utility
import java.io.Serializable
import java.util.Arrays
import java.util.concurrent.Callable
import java.util.function.Predicate
import java.util.stream.Collectors

/**
 * A list adapter for a list of [streams][Stream].
 * It currently supports [VideoStream], [AudioStream] and [SubtitlesStream].
 *
 * @param <T> the primary stream type's class extending [Stream]
 * @param <U> the secondary stream type's class extending [Stream]
</U></T> */
class StreamItemAdapter<T : Stream?, U : Stream?>(
        private val streamsWrapper: StreamInfoWrapper<T>,
        val allSecondary: SparseArrayCompat<SecondaryStreamHelper<U>?>
) : BaseAdapter() {

    /**
     * Indicates that at least one of the primary streams is an instance of [VideoStream],
     * has no audio ([VideoStream.isVideoOnly] returns true) and has no secondary stream
     * associated with it.
     */
    private val hasAnyVideoOnlyStreamWithNoSecondaryStream: Boolean

    init {
        hasAnyVideoOnlyStreamWithNoSecondaryStream = checkHasAnyVideoOnlyStreamWithNoSecondaryStream()
    }

    constructor(streamsWrapper: StreamInfoWrapper<T>?) : this(streamsWrapper, SparseArrayCompat<SecondaryStreamHelper<U>?>(0))

    val all: List<T>
        get() {
            return streamsWrapper.streamsList
        }

    public override fun getCount(): Int {
        return streamsWrapper.streamsList.size
    }

    public override fun getItem(position: Int): T {
        return streamsWrapper.streamsList.get(position)
    }

    public override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    public override fun getDropDownView(position: Int,
                                        convertView: View,
                                        parent: ViewGroup): View {
        return getCustomView(position, convertView, parent, true)
    }

    public override fun getView(position: Int, convertView: View, parent: ViewGroup): View {
        return getCustomView((parent as Spinner).getSelectedItemPosition(),
                convertView, parent, false)
    }

    private fun getCustomView(position: Int,
                              view: View,
                              parent: ViewGroup,
                              isDropdownItem: Boolean): View {
        val context: Context = parent.getContext()
        var convertView: View = view
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(
                    R.layout.stream_quality_item, parent, false)
        }
        val woSoundIconView: ImageView = convertView.findViewById(R.id.wo_sound_icon)
        val formatNameView: TextView = convertView.findViewById(R.id.stream_format_name)
        val qualityView: TextView = convertView.findViewById(R.id.stream_quality)
        val sizeView: TextView = convertView.findViewById(R.id.stream_size)
        val stream: T = getItem(position)
        val mediaFormat: MediaFormat? = streamsWrapper.getFormat(position)
        var woSoundIconVisibility: Int = View.GONE
        var qualityString: String?
        if (stream is VideoStream) {
            val videoStream: VideoStream = stream
            qualityString = videoStream.getResolution()
            if (hasAnyVideoOnlyStreamWithNoSecondaryStream) {
                if (videoStream.isVideoOnly()) {
                    woSoundIconVisibility = if (allSecondary.get(position) != null // It has a secondary stream associated with it, so check if it's a
                    // dropdown view so it doesn't look out of place (missing margin)
                    // compared to those that don't.
                    ) (if (isDropdownItem) View.INVISIBLE else View.GONE) // It doesn't have a secondary stream, icon is visible no matter what.
                    else View.VISIBLE
                } else if (isDropdownItem) {
                    woSoundIconVisibility = View.INVISIBLE
                }
            }
        } else if (stream is AudioStream) {
            val audioStream: AudioStream = stream
            if (audioStream.getAverageBitrate() > 0) {
                qualityString = audioStream.getAverageBitrate().toString() + "kbps"
            } else {
                qualityString = context.getString(R.string.unknown_quality)
            }
        } else if (stream is SubtitlesStream) {
            qualityString = (stream as SubtitlesStream).getDisplayLanguageName()
            if ((stream as SubtitlesStream).isAutoGenerated()) {
                qualityString += " (" + context.getString(R.string.caption_auto_generated) + ")"
            }
        } else {
            if (mediaFormat == null) {
                qualityString = context.getString(R.string.unknown_quality)
            } else {
                qualityString = mediaFormat.getSuffix()
            }
        }
        if (streamsWrapper.getSizeInBytes(position) > 0) {
            val secondary: SecondaryStreamHelper<U>? = allSecondary.get(position)
            if (secondary != null) {
                val size: Long = (secondary.getSizeInBytes()
                        + streamsWrapper.getSizeInBytes(position))
                sizeView.setText(Utility.formatBytes(size))
            } else {
                sizeView.setText(streamsWrapper.getFormattedSize(position))
            }
            sizeView.setVisibility(View.VISIBLE)
        } else {
            sizeView.setVisibility(View.GONE)
        }
        if (stream is SubtitlesStream) {
            formatNameView.setText((stream as SubtitlesStream).getLanguageTag())
        } else {
            if (mediaFormat == null) {
                formatNameView.setText(context.getString(R.string.unknown_format))
            } else if (mediaFormat == MediaFormat.WEBMA_OPUS) {
                // noinspection AndroidLintSetTextI18n
                formatNameView.setText("opus")
            } else {
                formatNameView.setText(mediaFormat.getName())
            }
        }
        qualityView.setText(qualityString)
        woSoundIconView.setVisibility(woSoundIconVisibility)
        return convertView
    }

    /**
     * @return if there are any video-only streams with no secondary stream associated with them.
     * @see .hasAnyVideoOnlyStreamWithNoSecondaryStream
     */
    private fun checkHasAnyVideoOnlyStreamWithNoSecondaryStream(): Boolean {
        for (i in streamsWrapper.streamsList.indices) {
            val stream: T = streamsWrapper.streamsList.get(i)
            if (stream is VideoStream) {
                val videoOnly: Boolean = (stream as VideoStream).isVideoOnly()
                if (videoOnly && allSecondary.get(i) == null) {
                    return true
                }
            }
        }
        return false
    }

    /**
     * A wrapper class that includes a way of storing the stream sizes.
     *
     * @param <T> the stream type's class extending [Stream]
    </T> */
    class StreamInfoWrapper<T : Stream?>(streamList: List<T>,
                                         context: Context?) : Serializable {
        val streamsList: List<T?>
        private val streamSizes: LongArray
        private val streamFormats: Array<MediaFormat?>
        private val unknownSize: String

        init {
            streamsList = streamList
            streamSizes = LongArray(streamsList.size)
            unknownSize = if (context == null) "--.-" else context.getString(R.string.unknown_content)
            streamFormats = arrayOfNulls(streamsList.size)
            resetInfo()
        }

        fun resetInfo() {
            Arrays.fill(streamSizes, SIZE_UNSET.toLong())
            for (i in streamsList.indices) {
                streamFormats.get(i) = if (streamsList.get(i) == null // test for invalid streams
                ) null else streamsList.get(i)!!.getFormat()
            }
        }

        fun getSizeInBytes(streamIndex: Int): Long {
            return streamSizes.get(streamIndex)
        }

        fun getSizeInBytes(stream: T): Long {
            return streamSizes.get(streamsList.indexOf(stream))
        }

        fun getFormattedSize(streamIndex: Int): String? {
            return formatSize(getSizeInBytes(streamIndex))
        }

        private fun formatSize(size: Long): String? {
            if (size > -1) {
                return Utility.formatBytes(size)
            }
            return unknownSize
        }

        fun setSize(stream: T, sizeInBytes: Long) {
            streamSizes.get(streamsList.indexOf(stream)) = sizeInBytes
        }

        fun getFormat(streamIndex: Int): MediaFormat? {
            return streamFormats.get(streamIndex)
        }

        fun setFormat(stream: T, format: MediaFormat?) {
            streamFormats.get(streamsList.indexOf(stream)) = format
        }

        companion object {
            private val EMPTY: StreamInfoWrapper<Stream> = StreamInfoWrapper(emptyList(), null)
            private val SIZE_UNSET: Int = -2

            /**
             * Helper method to fetch the sizes and missing media formats
             * of all the streams in a wrapper.
             *
             * @param <X> the stream type's class extending [Stream]
             * @param streamsWrapper the wrapper
             * @return a [Single] that returns a boolean indicating if any elements were changed
            </X> */
            fun <X : Stream?> fetchMoreInfoForWrapper(
                    streamsWrapper: StreamInfoWrapper<X>?): Single<Boolean> {
                val fetchAndSet: Callable<Boolean> = Callable<Boolean>({
                    var hasChanged: Boolean = false
                    for (stream: X in streamsWrapper!!.streamsList) {
                        val changeSize: Boolean = streamsWrapper.getSizeInBytes(stream) <= SIZE_UNSET
                        val changeFormat: Boolean = stream!!.getFormat() == null
                        if (!changeSize && !changeFormat) {
                            continue
                        }
                        val response: Response = DownloaderImpl.Companion.getInstance()
                                .head(stream.getContent())
                        if (changeSize) {
                            val contentLength: String? = response.getHeader("Content-Length")
                            if (!Utils.isNullOrEmpty(contentLength)) {
                                streamsWrapper.setSize(stream, contentLength!!.toLong())
                                hasChanged = true
                            }
                        }
                        if (changeFormat) {
                            hasChanged = (retrieveMediaFormat(stream, streamsWrapper, response)
                                    || hasChanged)
                        }
                    }
                    hasChanged
                })
                return Single.fromCallable(fetchAndSet)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .onErrorReturnItem(true)
            }

            /**
             * Try to retrieve the [MediaFormat] for a stream from the request headers.
             *
             * @param <X>            the stream type to get the [MediaFormat] for
             * @param stream         the stream to find the [MediaFormat] for
             * @param streamsWrapper the wrapper to store the found [MediaFormat] in
             * @param response       the response of the head request for the given stream
             * @return `true` if the media format could be retrieved; `false` otherwise
            </X> */
            @VisibleForTesting
            fun <X : Stream?> retrieveMediaFormat(
                    stream: X,
                    streamsWrapper: StreamInfoWrapper<X>,
                    response: Response): Boolean {
                return (retrieveMediaFormatFromFileTypeHeaders(stream, streamsWrapper, response)
                        || retrieveMediaFormatFromContentDispositionHeader(
                        stream, streamsWrapper, response)
                        || retrieveMediaFormatFromContentTypeHeader(stream, streamsWrapper, response))
            }

            @VisibleForTesting
            fun <X : Stream?> retrieveMediaFormatFromFileTypeHeaders(
                    stream: X,
                    streamsWrapper: StreamInfoWrapper<X>,
                    response: Response): Boolean {
                // try to use additional headers from CDNs or servers,
                // e.g. x-amz-meta-file-type (e.g. for SoundCloud)
                val keys: List<String> = response.responseHeaders().keys.stream()
                        .filter(Predicate({ k: String -> k.endsWith("file-type") })).collect(Collectors.toList())
                if (!keys.isEmpty()) {
                    for (key: String? in keys) {
                        val suffix: String? = response.getHeader(key)
                        val format: MediaFormat? = MediaFormat.getFromSuffix(suffix)
                        if (format != null) {
                            streamsWrapper.setFormat(stream, format)
                            return true
                        }
                    }
                }
                return false
            }

            /**
             *
             * Retrieve a [MediaFormat] from a HTTP Content-Disposition header
             * for a stream and store the info in a wrapper.
             * @see  [
             * mdn Web Docs for the HTTP Content-Disposition Header](https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Content-Disposition)
             *
             * @param stream the stream to get the [MediaFormat] for
             * @param streamsWrapper the wrapper to store the [MediaFormat] in
             * @param response the response to get the Content-Disposition header from
             * @return `true` if the [MediaFormat] could be retrieved from the response;
             * otherwise `false`
             * @param <X>
            </X> */
            @VisibleForTesting
            fun <X : Stream?> retrieveMediaFormatFromContentDispositionHeader(
                    stream: X,
                    streamsWrapper: StreamInfoWrapper<X>,
                    response: Response): Boolean {
                // parse the Content-Disposition header,
                // see
                // there can be two filename directives
                var contentDisposition: String? = response.getHeader("Content-Disposition")
                if (contentDisposition == null) {
                    return false
                }
                try {
                    contentDisposition = Utils.decodeUrlUtf8(contentDisposition)
                    val parts: Array<String> = contentDisposition.split(";".toRegex()).dropLastWhile({ it.isEmpty() }).toTypedArray()
                    for (part: String in parts) {
                        val fileName: String
                        part = part.trim({ it <= ' ' })

                        // extract the filename
                        if (part.startsWith("filename=")) {
                            // remove directive and decode
                            fileName = Utils.decodeUrlUtf8(part.substring(9))
                        } else if (part.startsWith("filename*=")) {
                            fileName = Utils.decodeUrlUtf8(part.substring(10))
                        } else {
                            continue
                        }

                        // extract the file extension / suffix
                        val p: Array<String> = fileName.split("\\.".toRegex()).dropLastWhile({ it.isEmpty() }).toTypedArray()
                        var suffix: String = p.get(p.size - 1)
                        if (suffix.endsWith("\"") || suffix.endsWith("'")) {
                            // remove trailing quotes if present, end index is exclusive
                            suffix = suffix.substring(0, suffix.length - 1)
                        }

                        // get the corresponding media format
                        val format: MediaFormat? = MediaFormat.getFromSuffix(suffix)
                        if (format != null) {
                            streamsWrapper.setFormat(stream, format)
                            return true
                        }
                    }
                } catch (ignored: Exception) {
                    // fail silently
                }
                return false
            }

            @VisibleForTesting
            fun <X : Stream?> retrieveMediaFormatFromContentTypeHeader(
                    stream: X,
                    streamsWrapper: StreamInfoWrapper<X>,
                    response: Response): Boolean {
                // try to get the format by content type
                // some mime types are not unique for every format, those are omitted
                val contentTypeHeader: String? = response.getHeader("Content-Type")
                if (contentTypeHeader == null) {
                    return false
                }
                var foundFormat: MediaFormat? = null
                for (format: MediaFormat in MediaFormat.getAllFromMimeType(contentTypeHeader)) {
                    if (foundFormat == null) {
                        foundFormat = format
                    } else if (foundFormat.id != format.id) {
                        return false
                    }
                }
                if (foundFormat != null) {
                    streamsWrapper.setFormat(stream, foundFormat)
                    return true
                }
                return false
            }

            fun <X : Stream?> empty(): StreamInfoWrapper<X> {
                return EMPTY as StreamInfoWrapper<X>
            }
        }
    }
}
