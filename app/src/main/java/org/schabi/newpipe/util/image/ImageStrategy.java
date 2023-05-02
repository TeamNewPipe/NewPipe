package org.schabi.newpipe.util.image;

import static org.schabi.newpipe.extractor.Image.HEIGHT_UNKNOWN;
import static org.schabi.newpipe.extractor.Image.WIDTH_UNKNOWN;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.schabi.newpipe.extractor.Image;

import java.util.Comparator;
import java.util.List;

public final class ImageStrategy {

    // when preferredImageQuality is LOW or MEDIUM, images are sorted by how close their preferred
    // image quality is to these values (H stands for "Height")
    private static final int BEST_LOW_H = 75;
    private static final int BEST_MEDIUM_H = 250;

    private static PreferredImageQuality preferredImageQuality = PreferredImageQuality.MEDIUM;

    private ImageStrategy() {
    }

    public static void setPreferredImageQuality(final PreferredImageQuality preferredImageQuality) {
        ImageStrategy.preferredImageQuality = preferredImageQuality;
    }

    public static boolean shouldLoadImages() {
        return preferredImageQuality != PreferredImageQuality.NONE;
    }


    static double estimatePixelCount(final Image image, final double widthOverHeight) {
        if (image.getHeight() == HEIGHT_UNKNOWN) {
            if (image.getWidth() == WIDTH_UNKNOWN) {
                // images whose size is completely unknown will be in their own subgroups, so
                // any one of them will do, hence returning the same value for all of them
                return 0;
            } else {
                return image.getWidth() * image.getWidth() / widthOverHeight;
            }
        } else if (image.getWidth() == WIDTH_UNKNOWN) {
            return image.getHeight() * image.getHeight() * widthOverHeight;
        } else {
            return image.getHeight() * image.getWidth();
        }
    }

    /**
     * Chooses an image amongst the provided list based on the user preference previously set with
     * {@link #setPreferredImageQuality(PreferredImageQuality)}. {@code null} will be returned in
     * case the list is empty or the user preference is to not show images.
     * <br>
     * These properties will be preferred, from most to least important:
     * <ol>
     *     <li>The image's {@link Image#getEstimatedResolutionLevel()} is not unknown and is close
     *     to {@link #preferredImageQuality}</li>
     *     <li>At least one of the image's width or height are known</li>
     *     <li>The highest resolution image is finally chosen if the user's preference is {@link
     *     PreferredImageQuality#HIGH}, otherwise the chosen image is the one that has the closest
     *     height to {@link #BEST_LOW_H} or {@link #BEST_MEDIUM_H}</li>
     * </ol>
     *
     * @param images the images from which to choose
     * @return the chosen preferred image, or {@link null} if the list is empty or the user disabled
     *         images
     */
    @Nullable
    public static String choosePreferredImage(@NonNull final List<Image> images) {
        if (preferredImageQuality == PreferredImageQuality.NONE) {
            return null; // do not load images
        }

        // this will be used to estimate the pixel count for images where only one of height or
        // width are known
        final double widthOverHeight = images.stream()
                .filter(image -> image.getHeight() != HEIGHT_UNKNOWN
                        && image.getWidth() != WIDTH_UNKNOWN)
                .mapToDouble(image -> ((double) image.getWidth()) / image.getHeight())
                .findFirst()
                .orElse(1.0);

        final Image.ResolutionLevel preferredLevel = preferredImageQuality.toResolutionLevel();
        final Comparator<Image> initialComparator = Comparator
                // the first step splits the images into groups of resolution levels
                .<Image>comparingInt(i -> {
                    if (i.getEstimatedResolutionLevel() == Image.ResolutionLevel.UNKNOWN) {
                        return 3; // avoid unknowns as much as possible
                    } else if (i.getEstimatedResolutionLevel() == preferredLevel) {
                        return 0; // prefer a matching resolution level
                    } else if (i.getEstimatedResolutionLevel() == Image.ResolutionLevel.MEDIUM) {
                        return 1; // the preferredLevel is only 1 "step" away (either HIGH or LOW)
                    } else {
                        return 2; // the preferredLevel is the furthest away possible (2 "steps")
                    }
                })
                // then each level's group is further split into two subgroups, one with known image
                // size (which is also the preferred subgroup) and the other without
                .thenComparing(image ->
                        image.getHeight() == HEIGHT_UNKNOWN && image.getWidth() == WIDTH_UNKNOWN);

        // The third step chooses, within each subgroup with known image size, the best image based
        // on how close its size is to BEST_LOW_H or BEST_MEDIUM_H (with proper units). Subgroups
        // without known image size will be left untouched since estimatePixelCount always returns
        // the same number for those.
        final Comparator<Image> finalComparator = switch (preferredImageQuality) {
            case NONE -> initialComparator; // unreachable
            case LOW -> initialComparator.thenComparingDouble(image -> {
                final double pixelCount = estimatePixelCount(image, widthOverHeight);
                return Math.abs(pixelCount - BEST_LOW_H * BEST_LOW_H * widthOverHeight);
            });
            case MEDIUM -> initialComparator.thenComparingDouble(image -> {
                final double pixelCount = estimatePixelCount(image, widthOverHeight);
                return Math.abs(pixelCount - BEST_MEDIUM_H * BEST_MEDIUM_H * widthOverHeight);
            });
            case HIGH -> initialComparator.thenComparingDouble(
                    // this is reversed with a - so that the highest resolution is chosen
                    i -> -estimatePixelCount(i, widthOverHeight));
        };

        return images.stream()
                // using "min" basically means "take the first group, then take the first subgroup,
                // then choose the best image, while ignoring all other groups and subgroups"
                .min(finalComparator)
                .map(Image::getUrl)
                .orElse(null);
    }

    @NonNull
    public static List<Image> urlToImageList(@Nullable final String url) {
        if (url == null) {
            return List.of();
        } else {
            return List.of(new Image(url, -1, -1, Image.ResolutionLevel.UNKNOWN));
        }
    }
}
