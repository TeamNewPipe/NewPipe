package org.schabi.newpipe.util.image

import org.junit.Assert.assertEquals
import org.junit.Test
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.Image.HEIGHT_UNKNOWN
import org.schabi.newpipe.extractor.Image.ResolutionLevel
import org.schabi.newpipe.extractor.Image.WIDTH_UNKNOWN
import org.schabi.newpipe.util.image.ImageStrategy.choosePreferredImage
import org.schabi.newpipe.util.image.ImageStrategy.estimatePixelCount

class ImageStrategyTest {

    private fun img(height: Int, width: Int): Image {
        return Image("", height, width, ResolutionLevel.UNKNOWN)
    }

    private fun img(url: String, resolutionLevel: ResolutionLevel): Image {
        return Image(url, HEIGHT_UNKNOWN, WIDTH_UNKNOWN, resolutionLevel)
    }

    private fun img(
        url: String,
        height: Int,
        width: Int,
        resolutionLevel: ResolutionLevel
    ): Image {
        return Image(url, height, width, resolutionLevel)
    }

    private fun assertChoosePreferredImage(
        low: String?,
        medium: String?,
        high: String?,
        images: List<Image>
    ) {
        assertEquals(low, choosePreferredImage(images, PreferredImageQuality.LOW))
        assertEquals(medium, choosePreferredImage(images, PreferredImageQuality.MEDIUM))
        assertEquals(high, choosePreferredImage(images, PreferredImageQuality.HIGH))
    }

    @Test
    fun testEstimatePixelCountAllKnown() {
        assertEquals(20000.0, estimatePixelCount(img(100, 200), 1.0), 0.0)
        assertEquals(20000.0, estimatePixelCount(img(100, 200), 12.0), 0.0)
        assertEquals(100.0, estimatePixelCount(img(100, 1), 12.0), 0.0)
        assertEquals(100.0, estimatePixelCount(img(1, 100), 0.5), 0.0)
    }

    @Test
    fun testEstimatePixelCountHeightUnknown() {
        assertEquals(10000.0, estimatePixelCount(img(HEIGHT_UNKNOWN, 100), 1.0), 0.0)
        assertEquals(20000.0, estimatePixelCount(img(HEIGHT_UNKNOWN, 200), 2.0), 0.0)
        assertEquals(10.0, estimatePixelCount(img(HEIGHT_UNKNOWN, 1), 0.1), 0.0)
        assertEquals(230400.0, estimatePixelCount(img(HEIGHT_UNKNOWN, 640), 16.0 / 9.0), 0.0)
    }

    @Test
    fun testEstimatePixelCountWidthUnknown() {
        assertEquals(10000.0, estimatePixelCount(img(100, WIDTH_UNKNOWN), 1.0), 0.0)
        assertEquals(20000.0, estimatePixelCount(img(200, WIDTH_UNKNOWN), 0.5), 0.0)
        assertEquals(12.0, estimatePixelCount(img(1, WIDTH_UNKNOWN), 12.0), 0.0)
        assertEquals(230400.0, estimatePixelCount(img(360, WIDTH_UNKNOWN), 16.0 / 9.0), 0.0)
    }

    @Test
    fun testEstimatePixelCountAllUnknown() {
        assertEquals(0.0, estimatePixelCount(img(HEIGHT_UNKNOWN, WIDTH_UNKNOWN), 1.0), 0.0)
        assertEquals(0.0, estimatePixelCount(img(HEIGHT_UNKNOWN, WIDTH_UNKNOWN), 12.0), 0.0)
        assertEquals(0.0, estimatePixelCount(img(HEIGHT_UNKNOWN, WIDTH_UNKNOWN), 0.1), 0.0)
        assertEquals(0.0, estimatePixelCount(img(HEIGHT_UNKNOWN, WIDTH_UNKNOWN), 16.0 / 9.0), 0.0)
    }

    @Test
    fun testChoosePreferredImageAllKnown() {
        // the resolution level of the images is more important than the actual resolution
        assertChoosePreferredImage(
            "a",
            "b",
            "c",
            listOf(
                img("a", 1, 1, ResolutionLevel.LOW),
                img("b", 200, 200, ResolutionLevel.MEDIUM),
                img("c", 10000, 10000, ResolutionLevel.HIGH)
            )
        )
        assertChoosePreferredImage(
            "a",
            "b",
            "c",
            listOf(
                img("a", 10000, 10000, ResolutionLevel.LOW),
                img("b", 200, 200, ResolutionLevel.MEDIUM),
                img("c", 1, 1, ResolutionLevel.HIGH)
            )
        )

        assertChoosePreferredImage(
            "b",
            "c",
            "d",
            listOf(
                img("a", 2, 1, ResolutionLevel.LOW),
                img("b", 50, 25, ResolutionLevel.LOW),
                img("c", 200, 100, ResolutionLevel.LOW),
                img("d", 300, 150, ResolutionLevel.LOW)
            )
        )

        assertChoosePreferredImage(
            "c",
            "d",
            "d",
            listOf(
                img("a", 2, 1, ResolutionLevel.MEDIUM),
                img("b", 50, 25, ResolutionLevel.MEDIUM),
                img("c", 60, 30, ResolutionLevel.MEDIUM),
                img("d", 300, 150, ResolutionLevel.MEDIUM)
            )
        )
    }

    @Test
    fun testChoosePreferredImageSomeKnown() {
        // the resolution level of the images is more important than the actual resolution
        assertChoosePreferredImage(
            "a",
            "b",
            "c",
            listOf(
                img("a", 1, WIDTH_UNKNOWN, ResolutionLevel.LOW),
                img("b", HEIGHT_UNKNOWN, 200, ResolutionLevel.MEDIUM),
                img("c", 10000, WIDTH_UNKNOWN, ResolutionLevel.HIGH)
            )
        )
        assertChoosePreferredImage(
            "a",
            "b",
            "c",
            listOf(
                img("a", HEIGHT_UNKNOWN, 10000, ResolutionLevel.LOW),
                img("b", 200, WIDTH_UNKNOWN, ResolutionLevel.MEDIUM),
                img("c", HEIGHT_UNKNOWN, 1, ResolutionLevel.HIGH)
            )
        )

        assertChoosePreferredImage(
            "b",
            "c",
            "d",
            listOf(
                img("a", HEIGHT_UNKNOWN, 1, ResolutionLevel.HIGH),
                img("b", 50, WIDTH_UNKNOWN, ResolutionLevel.HIGH),
                img("c", HEIGHT_UNKNOWN, 120, ResolutionLevel.HIGH),
                img("d", 340, WIDTH_UNKNOWN, ResolutionLevel.HIGH)
            )
        )

        assertChoosePreferredImage(
            "c",
            "d",
            "d",
            listOf(
                img("a", 2, WIDTH_UNKNOWN, ResolutionLevel.MEDIUM),
                img("b", HEIGHT_UNKNOWN, 50, ResolutionLevel.MEDIUM),
                img("c", 60, WIDTH_UNKNOWN, ResolutionLevel.MEDIUM),
                img("d", HEIGHT_UNKNOWN, 340, ResolutionLevel.MEDIUM)
            )
        )
    }

    @Test
    fun testChoosePreferredImageMixed() {
        for (resolutionLevel in RESOLUTION_LEVELS) {
            assertChoosePreferredImage(
                "d",
                "b",
                "c",
                listOf(
                    img("a", ResolutionLevel.UNKNOWN),
                    img("b", 200, 100, resolutionLevel),
                    img("c", 400, WIDTH_UNKNOWN, resolutionLevel),
                    img("d", HEIGHT_UNKNOWN, 50, resolutionLevel),
                    img("e", resolutionLevel)
                )
            )
        }
        for (resolutionLevel in RESOLUTION_LEVELS) {
            assertChoosePreferredImage(
                "b",
                "b",
                "b",
                listOf(
                    img("a", ResolutionLevel.UNKNOWN),
                    img("b", 200, 100, resolutionLevel),
                    img("e", resolutionLevel)
                )
            )
        }
        assertChoosePreferredImage(
            "b",
            "b",
            "e",
            listOf(
                img("a", ResolutionLevel.UNKNOWN),
                img("b", 200, 100, ResolutionLevel.LOW),
                img("e", ResolutionLevel.HIGH)
            )
        )
    }

    @Test
    fun testChoosePreferredImageAllUnknown() {
        assertChoosePreferredImage(
            "b",
            "c",
            "d",
            listOf(
                img("a", ResolutionLevel.UNKNOWN),
                img("b", ResolutionLevel.LOW),
                img("c", ResolutionLevel.MEDIUM),
                img("d", ResolutionLevel.HIGH)
            )
        )
        assertChoosePreferredImage(
            "c",
            "c",
            "d",
            listOf(
                img("a", ResolutionLevel.UNKNOWN),
                img("c", ResolutionLevel.MEDIUM),
                img("d", ResolutionLevel.HIGH)
            )
        )
        assertChoosePreferredImage(
            "b",
            "c",
            "c",
            listOf(
                img("a", ResolutionLevel.UNKNOWN),
                img("b", ResolutionLevel.LOW),
                img("c", ResolutionLevel.MEDIUM)
            )
        )

        // UNKNOWN is avoided as much as possible
        assertChoosePreferredImage(
            "d",
            "d",
            "d",
            listOf(
                img("a", ResolutionLevel.UNKNOWN),
                img("d", ResolutionLevel.HIGH)
            )
        )
        assertChoosePreferredImage(
            "b",
            "b",
            "b",
            listOf(
                img("a", ResolutionLevel.UNKNOWN),
                img("b", ResolutionLevel.LOW)
            )
        )
        assertChoosePreferredImage(
            "a",
            "a",
            "a",
            listOf(
                img("a", ResolutionLevel.UNKNOWN)
            )
        )
    }

    companion object {
        private val RESOLUTION_LEVELS = listOf(
            ResolutionLevel.LOW,
            ResolutionLevel.MEDIUM,
            ResolutionLevel.HIGH
        )
    }
}
