package us.shandian.giga.get;

import org.schabi.newpipe.extractor.stream.DeliveryMethod;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.Stream;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.util.ArrayList;
import java.util.List;

public final class SabrDownloadStreamHelper {
    private SabrDownloadStreamHelper() {
    }

    public static boolean containsSabrResource(final String[] deliveryMethods,
                                               final MissionRecoveryInfo[] recoveryInfo) {
        if (deliveryMethods != null) {
            for (final String deliveryMethod : deliveryMethods) {
                if (DeliveryMethod.SABR.name().equals(deliveryMethod)) {
                    return true;
                }
            }
        }
        if (recoveryInfo != null) {
            for (final MissionRecoveryInfo recovery : recoveryInfo) {
                if (recovery != null && recovery.isSabr()) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean containsSabrStream(final Stream selectedStream,
                                             final Stream secondaryStream) {
        return isSabr(selectedStream) || isSabr(secondaryStream);
    }

    public static List<AudioStream> audioStreamsForVideo(final List<AudioStream> audioStreams,
                                                         final VideoStream videoStream) {
        if (audioStreams == null || audioStreams.isEmpty()) {
            return audioStreams;
        }

        final List<AudioStream> result = new ArrayList<>();
        for (final AudioStream audioStream : audioStreams) {
            if (isSabr(audioStream) == isSabr(videoStream)) {
                result.add(audioStream);
            }
        }
        return result;
    }

    public static boolean isCompatibleSecondaryStream(final Stream selectedStream,
                                                      final Stream secondaryStream) {
        if (secondaryStream == null) {
            return true;
        }
        if (!isSabr(selectedStream) && !isSabr(secondaryStream)) {
            return true;
        }
        return isSabr(selectedStream) && isSabr(secondaryStream);
    }

    private static boolean isSabr(final Stream stream) {
        return stream != null && stream.getDeliveryMethod() == DeliveryMethod.SABR;
    }

}
