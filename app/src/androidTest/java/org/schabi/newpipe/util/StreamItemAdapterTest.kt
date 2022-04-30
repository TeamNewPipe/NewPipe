package org.schabi.newpipe.util

import android.content.Context
import android.util.SparseArray
import android.view.View
import android.view.View.GONE
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import android.widget.Spinner
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.internal.runner.junit4.statement.UiThreadStatement
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.Stream
import org.schabi.newpipe.extractor.stream.SubtitlesStream
import org.schabi.newpipe.extractor.stream.VideoStream

@MediumTest
@RunWith(AndroidJUnit4::class)
class StreamItemAdapterTest {
    private lateinit var context: Context
    private lateinit var spinner: Spinner

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        UiThreadStatement.runOnUiThread {
            spinner = Spinner(context)
        }
    }

    @Test
    fun videoStreams_noSecondaryStream() {
        val adapter = StreamItemAdapter<VideoStream, AudioStream>(
            context,
            getVideoStreams(true, true, true, true),
            null
        )

        spinner.adapter = adapter
        assertIconVisibility(spinner, 0, VISIBLE, VISIBLE)
        assertIconVisibility(spinner, 1, VISIBLE, VISIBLE)
        assertIconVisibility(spinner, 2, VISIBLE, VISIBLE)
        assertIconVisibility(spinner, 3, VISIBLE, VISIBLE)
    }

    @Test
    fun videoStreams_hasSecondaryStream() {
        val adapter = StreamItemAdapter(
            context,
            getVideoStreams(false, true, false, true),
            getAudioStreams(false, true, false, true)
        )

        spinner.adapter = adapter
        assertIconVisibility(spinner, 0, GONE, GONE)
        assertIconVisibility(spinner, 1, GONE, GONE)
        assertIconVisibility(spinner, 2, GONE, GONE)
        assertIconVisibility(spinner, 3, GONE, GONE)
    }

    @Test
    fun videoStreams_Mixed() {
        val adapter = StreamItemAdapter(
            context,
            getVideoStreams(true, true, true, true, true, false, true, true),
            getAudioStreams(false, true, false, false, false, true, true, true)
        )

        spinner.adapter = adapter
        assertIconVisibility(spinner, 0, VISIBLE, VISIBLE)
        assertIconVisibility(spinner, 1, GONE, INVISIBLE)
        assertIconVisibility(spinner, 2, VISIBLE, VISIBLE)
        assertIconVisibility(spinner, 3, VISIBLE, VISIBLE)
        assertIconVisibility(spinner, 4, VISIBLE, VISIBLE)
        assertIconVisibility(spinner, 5, GONE, INVISIBLE)
        assertIconVisibility(spinner, 6, GONE, INVISIBLE)
        assertIconVisibility(spinner, 7, GONE, INVISIBLE)
    }

    @Test
    fun subtitleStreams_noIcon() {
        val adapter = StreamItemAdapter<SubtitlesStream, Stream>(
            context,
            StreamItemAdapter.StreamSizeWrapper(
                (0 until 5).map {
                    SubtitlesStream(MediaFormat.SRT, "pt-BR", "https://example.com", false)
                },
                context
            ),
            null
        )
        spinner.adapter = adapter
        for (i in 0 until spinner.count) {
            assertIconVisibility(spinner, i, GONE, GONE)
        }
    }

    @Test
    fun audioStreams_noIcon() {
        val adapter = StreamItemAdapter<AudioStream, Stream>(
            context,
            StreamItemAdapter.StreamSizeWrapper(
                (0 until 5).map { AudioStream("https://example.com/$it", MediaFormat.OPUS, 192) },
                context
            ),
            null
        )
        spinner.adapter = adapter
        for (i in 0 until spinner.count) {
            assertIconVisibility(spinner, i, GONE, GONE)
        }
    }

    /**
     * @return a list of video streams, in which their video only property mirrors the provided
     * [videoOnly] vararg.
     */
    private fun getVideoStreams(vararg videoOnly: Boolean) =
        StreamItemAdapter.StreamSizeWrapper(
            videoOnly.map {
                VideoStream("https://example.com", MediaFormat.MPEG_4, "720p", it)
            },
            context
        )

    /**
     * @return a list of audio streams, containing valid and null elements mirroring the provided
     * [shouldBeValid] vararg.
     */
    private fun getAudioStreams(vararg shouldBeValid: Boolean) =
        getSecondaryStreamsFromList(
            shouldBeValid.map {
                if (it) AudioStream("https://example.com", MediaFormat.OPUS, 192)
                else null
            }
        )

    /**
     * Checks whether the item at [position] in the [spinner] has the correct icon visibility when
     * it is shown in normal mode (selected) and in dropdown mode (user is choosing one of a list).
     */
    private fun assertIconVisibility(
        spinner: Spinner,
        position: Int,
        normalVisibility: Int,
        dropDownVisibility: Int
    ) {
        spinner.setSelection(position)
        spinner.adapter.getView(position, null, spinner).run {
            Assert.assertEquals(
                "normal visibility (pos=[$position]) is not correct",
                findViewById<View>(R.id.wo_sound_icon).visibility,
                normalVisibility,
            )
        }
        spinner.adapter.getDropDownView(position, null, spinner).run {
            Assert.assertEquals(
                "drop down visibility (pos=[$position]) is not correct",
                findViewById<View>(R.id.wo_sound_icon).visibility,
                dropDownVisibility
            )
        }
    }

    /**
     * Helper function that builds a secondary stream list.
     */
    private fun <T : Stream> getSecondaryStreamsFromList(streams: List<T?>) =
        SparseArray<SecondaryStreamHelper<T>?>(streams.size).apply {
            streams.forEachIndexed { index, stream ->
                val secondaryStreamHelper: SecondaryStreamHelper<T>? = stream?.let {
                    SecondaryStreamHelper(
                        StreamItemAdapter.StreamSizeWrapper(streams, context),
                        it
                    )
                }
                put(index, secondaryStreamHelper)
            }
        }
}
