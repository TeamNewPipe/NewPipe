package org.schabi.newpipe.util.image;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.schabi.newpipe.extractor.Image;

import java.util.Comparator;
import java.util.List;

public final class ImageStrategy {

    // the height thresholds also used by the extractor (TODO move them to the extractor)
    private static final int LOW_MEDIUM = 175;
    private static final int MEDIUM_HIGH = 720;

    private static PreferredImageQuality preferredImageQuality = PreferredImageQuality.MEDIUM;

    private ImageStrategy() {
    }

    public static void setPreferredImageQuality(final PreferredImageQuality preferredImageQuality) {
        ImageStrategy.preferredImageQuality = preferredImageQuality;
    }

    public static boolean shouldLoadImages() {
        return preferredImageQuality != PreferredImageQuality.NONE;
    }


    private static double estimatePixelCount(final Image image,
                                             final double widthOverHeight,
                                             final boolean unknownsLast) {
        if (image.getHeight() == Image.HEIGHT_UNKNOWN) {
            if (image.getWidth() == Image.WIDTH_UNKNOWN) {
                switch (image.getEstimatedResolutionLevel()) {
                    case LOW:
                        return unknownsLast
                                ? (LOW_MEDIUM - 1) * (LOW_MEDIUM - 1) * widthOverHeight
                                : 0;
                    case MEDIUM:
                        return unknownsLast
                                ? (MEDIUM_HIGH - 1) * (MEDIUM_HIGH - 1) * widthOverHeight
                                : LOW_MEDIUM * LOW_MEDIUM * widthOverHeight;
                    case HIGH:
                        return unknownsLast
                                ? 1e20 // less than 1e21 to prefer over fully unknown image sizes
                                : MEDIUM_HIGH * MEDIUM_HIGH * widthOverHeight;
                    default:
                    case UNKNOWN:
                        // images whose size is completely unknown will be avoided when possible
                        return unknownsLast ? 1e21 : -1;
                }

            } else {
                return image.getWidth() * image.getWidth() / widthOverHeight;
            }

        } else if (image.getWidth() == Image.WIDTH_UNKNOWN) {
            return image.getHeight() * image.getHeight() * widthOverHeight;

        } else {
            return image.getHeight() * image.getWidth();
        }
    }

    @Nullable
    public static String choosePreferredImage(@NonNull final List<Image> images) {
        if (preferredImageQuality == PreferredImageQuality.NONE) {
            return null; // do not load images
        }

        final double widthOverHeight = images.stream()
                .filter(image -> image.getHeight() != Image.HEIGHT_UNKNOWN
                        && image.getWidth() != Image.WIDTH_UNKNOWN)
                .mapToDouble(image -> ((double) image.getWidth()) / image.getHeight())
                .findFirst()
                .orElse(1.0);

        final Comparator<Image> comparator;
        switch (preferredImageQuality) {
            case LOW:
                comparator = Comparator.comparingDouble(
                        image -> estimatePixelCount(image, widthOverHeight, true));
                break;
            default:
            case MEDIUM:
                comparator = Comparator.comparingDouble(image -> {
                    final double pixelCount = estimatePixelCount(image, widthOverHeight, true);
                    final double mediumHeight = (LOW_MEDIUM + MEDIUM_HIGH) / 2.0;
                    return Math.abs(pixelCount - mediumHeight * mediumHeight * widthOverHeight);
                });
                break;
            case HIGH:
                comparator = Comparator.<Image>comparingDouble(
                        image -> estimatePixelCount(image, widthOverHeight, false))
                        .reversed();
                break;
        }

        return images.stream()
                .min(comparator)
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
