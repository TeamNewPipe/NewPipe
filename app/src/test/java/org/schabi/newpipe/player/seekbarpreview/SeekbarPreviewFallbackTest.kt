package org.schabi.newpipe.player.seekbarpreview

import android.graphics.Bitmap
import coil3.request.Disposable
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.schabi.newpipe.extractor.stream.Frameset

@RunWith(RobolectricTestRunner::class)
class SeekbarPreviewFallbackTest {
    @Test
    fun failedHighQualityPageFallsBackToLowerQualityWithoutExtraExtraction() {
        val urls = mutableListOf<String>()
        val callbacks = mutableListOf<java.util.function.Consumer<Bitmap>>()
        val holder = SeekbarPreviewThumbnailHolder { _, url, callback ->
            urls.add(url)
            callbacks.add(callback)
            mock(Disposable::class.java)
        }
        val high = Frameset(listOf("high"), 4, 4, 1, 1000, 1, 1)
        val low = Frameset(listOf("low"), 2, 2, 1, 1000, 1, 1)
        holder.resetFrom(RuntimeEnvironment.getApplication(), listOf(high, low), 0)
        val result = AtomicReference<Bitmap>()
        holder.requestBitmapAt(0) { result.set(it) }
        assertEquals(listOf("high"), urls)
        callbacks[0].accept(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888))
        assertEquals(listOf("high", "low"), urls)
        callbacks[1].accept(Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888))
        assertNotNull(result.get())
        assertEquals(2, result.get().width)
    }
}
