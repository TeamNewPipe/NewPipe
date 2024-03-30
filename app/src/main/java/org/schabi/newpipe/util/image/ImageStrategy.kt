package org.schabi.newpipe.util.image

import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.Image.ResolutionLevel
import kotlin.math.abs

object ImageStrategy {
    // when preferredImageQuality is LOW or MEDIUM, images are sorted by how close their preferred
    // image quality is to these values (H stands for "Height")
    private const val BEST_LOW_H = 75
    private const val BEST_MEDIUM_H = 250
    private var preferredImageQuality = PreferredImageQuality.MEDIUM
    fun setPreferredImageQuality(preferredImageQuality: PreferredImageQuality) {
        ImageStrategy.preferredImageQuality = preferredImageQuality
    }

    fun shouldLoadImages(): Boolean {
        return preferredImageQuality != PreferredImageQuality.NONE
    }

    @JvmStatic
    fun estimatePixelCount(image: Image, widthOverHeight: Double): Double {
        return if (image.height == Image.HEIGHT_UNKNOWN) {
            if (image.width == Image.WIDTH_UNKNOWN) {
                // images whose size is completely unknown will be in their own subgroups, so
                // any one of them will do, hence returning the same value for all of them
                0
            } else {
                image.width * image.width / widthOverHeight
            }
        } else if (image.width == Image.WIDTH_UNKNOWN) {
            image.height * image.height * widthOverHeight
        } else {
            (image.height * image.width).toDouble()
        }
    }

    /**
     * [.choosePreferredImage] contains the description for this function's logic.
     *
     * @param images         the images from which to choose
     * @param nonNoneQuality the preferred quality (must NOT be [PreferredImageQuality.NONE])
     * @return the chosen preferred image, or [null] if the list is empty
     * @see .choosePreferredImage
     */
    fun choosePreferredImage(images: List<Image?>,
                             nonNoneQuality: PreferredImageQuality): String? {
        // this will be used to estimate the pixel count for images where only one of height or
        // width are known
        val widthOverHeight = images.stream()
                .filter { image: Image? ->
                    (image!!.height != Image.HEIGHT_UNKNOWN
                            && image.width != Image.WIDTH_UNKNOWN)
                }
                .mapToDouble { image: Image? -> image!!.width.toDouble() / image.height }
                .findFirst()
                .orElse(1.0)
        val preferredLevel = nonNoneQuality.toResolutionLevel()
        val initialComparator = Comparator // the first step splits the images into groups of resolution levels
                .comparingInt { i: Image ->
                    if (i.estimatedResolutionLevel == ResolutionLevel.UNKNOWN) {
                        return@comparingInt 3 // avoid unknowns as much as possible
                    } else if (i.estimatedResolutionLevel == preferredLevel) {
                        return@comparingInt 0 // prefer a matching resolution level
                    } else if (i.estimatedResolutionLevel == ResolutionLevel.MEDIUM) {
                        return@comparingInt 1 // the preferredLevel is only 1 "step" away (either HIGH or LOW)
                    } else {
                        return@comparingInt 2 // the preferredLevel is the furthest away possible (2 "steps")
                    }
                } // then each level's group is further split into two subgroups, one with known image
                // size (which is also the preferred subgroup) and the other without
                .thenComparing { image: Image -> image.height == Image.HEIGHT_UNKNOWN && image.width == Image.WIDTH_UNKNOWN }

        // The third step chooses, within each subgroup with known image size, the best image based
        // on how close its size is to BEST_LOW_H or BEST_MEDIUM_H (with proper units). Subgroups
        // without known image size will be left untouched since estimatePixelCount always returns
        // the same number for those.
        val finalComparator = when (nonNoneQuality) {
            PreferredImageQuality.NONE -> initialComparator
            PreferredImageQuality.LOW -> initialComparator.thenComparingDouble { image: Image ->
                val pixelCount = estimatePixelCount(image, widthOverHeight)
                abs(pixelCount - BEST_LOW_H * BEST_LOW_H * widthOverHeight)
            }

            PreferredImageQuality.MEDIUM -> initialComparator.thenComparingDouble { image: Image ->
                val pixelCount = estimatePixelCount(image, widthOverHeight)
                abs(pixelCount - BEST_MEDIUM_H * BEST_MEDIUM_H * widthOverHeight)
            }

            PreferredImageQuality.HIGH -> initialComparator.thenComparingDouble // this is reversed with a - so that the highest resolution is chosen
            { i: Image -> -estimatePixelCount(i, widthOverHeight) }
        }
        return images.stream() // using "min" basically means "take the first group, then take the first subgroup,
                // then choose the best image, while ignoring all other groups and subgroups"
                .min(finalComparator)
                .map { obj: Image? -> obj!!.url }
                .orElse(null)
    }

    /**
     * Chooses an image amongst the provided list based on the user preference previously set with
     * [.setPreferredImageQuality]. `null` will be returned in
     * case the list is empty or the user preference is to not show images.
     * <br></br>
     * These properties will be preferred, from most to least important:
     *
     *  1. The image's [Image.getEstimatedResolutionLevel] is not unknown and is close
     * to [.preferredImageQuality]
     *  1. At least one of the image's width or height are known
     *  1. The highest resolution image is finally chosen if the user's preference is [     ][PreferredImageQuality.HIGH], otherwise the chosen image is the one that has the height
     * closest to [.BEST_LOW_H] or [.BEST_MEDIUM_H]
     *
     * <br></br>
     * Use [.imageListToDbUrl] if the URL is going to be saved to the database, to avoid
     * saving nothing in case at the moment of saving the user preference is to not show images.
     *
     * @param images the images from which to choose
     * @return the chosen preferred image, or [null] if the list is empty or the user disabled
     * images
     * @see .imageListToDbUrl
     */
    fun choosePreferredImage(images: List<Image?>): String? {
        return if (preferredImageQuality == PreferredImageQuality.NONE) {
            null // do not load images
        } else choosePreferredImage(images, preferredImageQuality)
    }

    /**
     * Like [.choosePreferredImage], except that if [.preferredImageQuality] is
     * [PreferredImageQuality.NONE] an image will be chosen anyway (with preferred quality
     * [PreferredImageQuality.MEDIUM].
     * <br></br>
     * To go back to a list of images (obviously with just the one chosen image) from a URL saved in
     * the database use [.dbUrlToImageList].
     *
     * @param images the images from which to choose
     * @return the chosen preferred image, or [null] if the list is empty
     * @see .choosePreferredImage
     * @see .dbUrlToImageList
     */
    fun imageListToDbUrl(images: List<Image?>): String? {
        val quality: PreferredImageQuality
        quality = if (preferredImageQuality == PreferredImageQuality.NONE) {
            PreferredImageQuality.MEDIUM
        } else {
            preferredImageQuality
        }
        return choosePreferredImage(images, quality)
    }

    /**
     * Wraps the URL (coming from the database) in a `List<Image>` so that it is usable
     * seamlessly in all of the places where the extractor would return a list of images, including
     * allowing to build info objects based on database objects.
     * <br></br>
     * To obtain a url to save to the database from a list of images use [ ][.imageListToDbUrl].
     *
     * @param url the URL to wrap coming from the database, or `null` to get an empty list
     * @return a list containing just one [Image] wrapping the provided URL, with unknown
     * image size fields, or an empty list if the URL is `null`
     * @see .imageListToDbUrl
     */
    fun dbUrlToImageList(url: String?): List<Image> {
        return if (url == null) {
            listOf()
        } else {
            java.util.List.of(Image(url, -1, -1, ResolutionLevel.UNKNOWN))
        }
    }
}
