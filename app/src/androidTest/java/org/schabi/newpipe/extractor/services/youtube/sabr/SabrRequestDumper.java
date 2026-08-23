package org.schabi.newpipe.extractor.services.youtube.sabr;

import org.schabi.newpipe.extractor.services.youtube.sabr.exception.SabrProtocolException;
import org.schabi.newpipe.extractor.services.youtube.sabr.protocol.SabrProto;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Test-only diagnostics for SABR request-shape experiments.
 */
public final class SabrRequestDumper {
    private SabrRequestDumper() {
    }

    @Nonnull
    public static String summarize(@Nonnull final byte[] requestBody) {
        try {
            return summarizeRequest(requestBody);
        } catch (final Exception e) {
            return "undecodableRequest(bytes=" + requestBody.length + ')';
        }
    }

    @Nonnull
    private static String summarizeRequest(@Nonnull final byte[] requestBody)
            throws SabrProtocolException {
        final List<SabrProto.Field> fields = SabrProto.readFields(requestBody);
        String clientAbrState = "null";
        final List<String> selectedFormats = new ArrayList<>();
        final List<String> bufferedRanges = new ArrayList<>();
        long topLevelPlayerTimeMs = -1;
        int ustreamerConfigBytes = -1;
        final List<String> preferredAudioFormats = new ArrayList<>();
        final List<String> preferredVideoFormats = new ArrayList<>();
        final List<String> preferredSubtitleFormats = new ArrayList<>();
        String streamerContext = "null";
        int field1000Count = 0;
        final List<String> unknownFields = new ArrayList<>();

        for (final SabrProto.Field field : fields) {
            switch (field.getNumber()) {
                case 1:
                    clientAbrState = describeClientAbrState(field.getBytes());
                    break;
                case 2:
                    selectedFormats.add(describeFormatId(field.getBytes()));
                    break;
                case 3:
                    bufferedRanges.add(describeBufferedRange(field.getBytes()));
                    break;
                case 4:
                    topLevelPlayerTimeMs = field.getVarint();
                    break;
                case 5:
                    ustreamerConfigBytes = field.getBytes().length;
                    break;
                case 16:
                    preferredAudioFormats.add(describeFormatId(field.getBytes()));
                    break;
                case 17:
                    preferredVideoFormats.add(describeFormatId(field.getBytes()));
                    break;
                case 18:
                    preferredSubtitleFormats.add(describeFormatId(field.getBytes()));
                    break;
                case 19:
                    streamerContext = describeStreamerContext(field.getBytes());
                    break;
                case 1000:
                    field1000Count++;
                    break;
                default:
                    unknownFields.add(describeUnknownField(field));
                    break;
            }
        }

        return "bytes=" + requestBody.length
                + "; fields=" + describeFieldCounts(fields)
                + "; clientAbr={" + clientAbrState + '}'
                + "; selected=" + selectedFormats
                + "; ranges=" + bufferedRanges
                + "; topPlayerTimeMs=" + topLevelPlayerTimeMs
                + "; ustreamer=bytes(" + ustreamerConfigBytes + ')'
                + "; prefAudio=" + preferredAudioFormats
                + "; prefVideo=" + preferredVideoFormats
                + "; prefSub=" + preferredSubtitleFormats
                + "; streamer={" + streamerContext + '}'
                + "; field1000=" + field1000Count
                + "; unknown=" + unknownFields;
    }

    @Nonnull
    private static String describeClientAbrState(@Nonnull final byte[] data)
            throws SabrProtocolException {
        final List<String> values = new ArrayList<>();
        for (final SabrProto.Field field : SabrProto.readFields(data)) {
            final String name = clientAbrStateFieldName(field.getNumber());
            if (field.getNumber() == 35 && field.getWireType() == SabrProto.WIRE_FIXED32) {
                values.add(name + '=' + String.format(Locale.ROOT, "%.3f",
                        Float.intBitsToFloat(SabrProto.asFixed32LittleEndian(field.getBytes()))));
            } else if (field.getNumber() == 72
                    && field.getWireType() == SabrProto.WIRE_LENGTH_DELIMITED) {
                values.add(name + "={" + SabrProto.summarizeFields(field.getBytes()) + '}');
            } else if (field.getNumber() == 79
                    && field.getWireType() == SabrProto.WIRE_LENGTH_DELIMITED) {
                values.add(name + "={" + describePlaybackAuthorization(field.getBytes()) + '}');
            } else if (field.getNumber() == 69
                    && field.getWireType() == SabrProto.WIRE_LENGTH_DELIMITED) {
                values.add(name + "=present(len=" + field.getBytes().length + ')');
            } else if (field.getWireType() == SabrProto.WIRE_LENGTH_DELIMITED) {
                values.add(name + "=bytes(" + field.getBytes().length + ')');
            } else if (isBoolClientAbrStateField(field.getNumber())) {
                values.add(name + '=' + (field.getVarint() != 0));
            } else {
                values.add(name + '=' + field.getVarint());
            }
        }
        return join(values);
    }

    @Nonnull
    private static String describeBufferedRange(@Nonnull final byte[] data)
            throws SabrProtocolException {
        String formatId = "format:null";
        long startTimeMs = -1;
        long durationMs = -1;
        int startSegmentIndex = -1;
        int endSegmentIndex = -1;
        String timeRange = "null";
        final List<String> unknown = new ArrayList<>();

        for (final SabrProto.Field field : SabrProto.readFields(data)) {
            switch (field.getNumber()) {
                case 1:
                    formatId = describeFormatId(field.getBytes());
                    break;
                case 2:
                    startTimeMs = field.getVarint();
                    break;
                case 3:
                    durationMs = field.getVarint();
                    break;
                case 4:
                    startSegmentIndex = (int) field.getVarint();
                    break;
                case 5:
                    endSegmentIndex = (int) field.getVarint();
                    break;
                case 6:
                    timeRange = describeTimeRange(field.getBytes());
                    break;
                default:
                    unknown.add(describeUnknownField(field));
                    break;
            }
        }

        return formatId + ":seq=" + startSegmentIndex + '-' + endSegmentIndex
                + ":time=" + startTimeMs + '+' + durationMs
                + ":tr=" + timeRange
                + (unknown.isEmpty() ? "" : ":unknown=" + unknown);
    }

    @Nonnull
    private static String describeTimeRange(@Nonnull final byte[] data)
            throws SabrProtocolException {
        long startTicks = -1;
        long durationTicks = -1;
        int timescale = -1;
        for (final SabrProto.Field field : SabrProto.readFields(data)) {
            if (field.getNumber() == 1) {
                startTicks = field.getVarint();
            } else if (field.getNumber() == 2) {
                durationTicks = field.getVarint();
            } else if (field.getNumber() == 3) {
                timescale = (int) field.getVarint();
            }
        }
        return startTicks + "+" + durationTicks + '@' + timescale;
    }

    @Nonnull
    private static String describeStreamerContext(@Nonnull final byte[] data)
            throws SabrProtocolException {
        String clientInfo = "null";
        int poTokenBytes = -1;
        String playbackCookie = "null";
        int field4Bytes = -1;
        final List<String> contexts = new ArrayList<>();
        final List<Long> unsentContexts = new ArrayList<>();
        int field7Bytes = -1;
        int field8Bytes = -1;
        final List<String> unknown = new ArrayList<>();

        for (final SabrProto.Field field : SabrProto.readFields(data)) {
            switch (field.getNumber()) {
                case 1:
                    clientInfo = describeClientInfo(field.getBytes());
                    break;
                case 2:
                    poTokenBytes = field.getBytes().length;
                    break;
                case 3:
                    playbackCookie = describePlaybackCookie(field.getBytes());
                    break;
                case 4:
                    field4Bytes = field.getBytes().length;
                    break;
                case 5:
                    contexts.add(describeSabrContext(field.getBytes()));
                    break;
                case 6:
                    if (field.getWireType() == SabrProto.WIRE_VARINT) {
                        unsentContexts.add(field.getVarint());
                    } else {
                        unsentContexts.addAll(readRawVarints(field.getBytes()));
                    }
                    break;
                case 7:
                    field7Bytes = field.getBytes().length;
                    break;
                case 8:
                    field8Bytes = field.getBytes().length;
                    break;
                default:
                    unknown.add(describeUnknownField(field));
                    break;
            }
        }

        return "client=" + clientInfo
                + ", poToken=bytes(" + poTokenBytes + ')'
                + ", playbackCookie=" + playbackCookie
                + ", field4=bytes(" + field4Bytes + ')'
                + ", contexts=" + contexts
                + ", unsent=" + unsentContexts
                + ", field7=bytes(" + field7Bytes + ')'
                + ", field8=bytes(" + field8Bytes + ')'
                + (unknown.isEmpty() ? "" : ", unknown=" + unknown);
    }

    @Nonnull
    private static String describeClientInfo(@Nonnull final byte[] data)
            throws SabrProtocolException {
        final List<String> values = new ArrayList<>();
        for (final SabrProto.Field field : SabrProto.readFields(data)) {
            switch (field.getNumber()) {
                case 16:
                    values.add("clientName=" + field.getVarint());
                    break;
                case 17:
                    values.add("clientVersion=" + field.getString());
                    break;
                case 18:
                    values.add("osName=" + field.getString());
                    break;
                case 19:
                    values.add("osVersion=" + field.getString());
                    break;
                case 21:
                    values.add("acceptLanguage=" + field.getString());
                    break;
                case 22:
                    values.add("acceptRegion=" + field.getString());
                    break;
                default:
                    values.add(describeUnknownField(field));
                    break;
            }
        }
        return '{' + join(values) + '}';
    }

    @Nonnull
    private static String describeSabrContext(@Nonnull final byte[] data)
            throws SabrProtocolException {
        int type = -1;
        int valueBytes = -1;
        for (final SabrProto.Field field : SabrProto.readFields(data)) {
            if (field.getNumber() == 1 && field.getWireType() == SabrProto.WIRE_VARINT) {
                type = (int) field.getVarint();
            } else if (field.getNumber() == 2
                    && field.getWireType() == SabrProto.WIRE_LENGTH_DELIMITED) {
                valueBytes = field.getBytes().length;
            }
        }
        return "type=" + type + "/bytes=" + valueBytes;
    }

    @Nonnull
    private static String describePlaybackCookie(@Nonnull final byte[] data) {
        try {
            return "bytes(" + data.length + "):" + SabrProto.summarizeFields(data);
        } catch (final Exception e) {
            return "bytes(" + data.length + ")";
        }
    }

    @Nonnull
    private static String describePlaybackAuthorization(@Nonnull final byte[] data) {
        try {
            int authorizedFormats = 0;
            int licenseConstraintBytes = -1;
            final List<String> unknown = new ArrayList<>();
            for (final SabrProto.Field field : SabrProto.readFields(data)) {
                if (field.getNumber() == 1
                        && field.getWireType() == SabrProto.WIRE_LENGTH_DELIMITED) {
                    authorizedFormats++;
                } else if (field.getNumber() == 2
                        && field.getWireType() == SabrProto.WIRE_LENGTH_DELIMITED) {
                    licenseConstraintBytes = field.getBytes().length;
                } else {
                    unknown.add(describeUnknownField(field));
                }
            }
            return "authorized=" + authorizedFormats
                    + ", licenseConstraint=bytes(" + licenseConstraintBytes + ')'
                    + (unknown.isEmpty() ? "" : ", unknown=" + unknown);
        } catch (final Exception e) {
            return "bytes(" + data.length + ')';
        }
    }

    @Nonnull
    private static String describeFormatId(@Nonnull final byte[] data) {
        try {
            int itag = -1;
            long lastModified = -1;
            int xtagsLength = -1;
            for (final SabrProto.Field field : SabrProto.readFields(data)) {
                if (field.getNumber() == 1 && field.getWireType() == SabrProto.WIRE_VARINT) {
                    itag = (int) field.getVarint();
                } else if (field.getNumber() == 2
                        && field.getWireType() == SabrProto.WIRE_VARINT) {
                    lastModified = field.getVarint();
                } else if (field.getNumber() == 3
                        && field.getWireType() == SabrProto.WIRE_LENGTH_DELIMITED) {
                    xtagsLength = field.getBytes().length;
                }
            }
            if (itag < 0) {
                return "bytes(" + data.length + ')';
            }
            return "itag:" + itag
                    + (lastModified >= 0 ? "+lm=" + lastModified : "")
                    + (xtagsLength >= 0 ? "+xtagsLen=" + xtagsLength : "");
        } catch (final Exception e) {
            return "bytes(" + data.length + ')';
        }
    }

    @Nonnull
    private static String describeFieldCounts(@Nonnull final List<SabrProto.Field> fields) {
        final Map<Integer, Integer> counts = new LinkedHashMap<>();
        for (final SabrProto.Field field : fields) {
            final Integer count = counts.get(field.getNumber());
            counts.put(field.getNumber(), count == null ? 1 : count + 1);
        }
        final List<String> values = new ArrayList<>();
        for (final Map.Entry<Integer, Integer> entry : counts.entrySet()) {
            values.add(entry.getKey() + "x" + entry.getValue());
        }
        return values.toString();
    }

    @Nonnull
    private static String describeUnknownField(@Nonnull final SabrProto.Field field)
            throws SabrProtocolException {
        if (field.getWireType() == SabrProto.WIRE_VARINT) {
            return field.getNumber() + "=" + field.getVarint();
        }
        return field.getNumber() + "=bytes(" + field.getBytes().length + ')';
    }

    @Nonnull
    private static List<Long> readRawVarints(@Nonnull final byte[] data)
            throws SabrProtocolException {
        final List<Long> values = new ArrayList<>();
        int offset = 0;
        while (offset < data.length) {
            long result = 0;
            int shift = 0;
            while (shift < 64) {
                if (offset >= data.length) {
                    throw new SabrProtocolException("Unexpected EOF in packed varint");
                }
                final int current = data[offset++] & 0xff;
                result |= (long) (current & 0x7f) << shift;
                if ((current & 0x80) == 0) {
                    values.add(result);
                    break;
                }
                shift += 7;
            }
            if (shift >= 64) {
                throw new SabrProtocolException("Packed varint is too long");
            }
        }
        return values;
    }

    @Nonnull
    private static String clientAbrStateFieldName(final int fieldNumber) {
        switch (fieldNumber) {
            case 13:
                return "timeSinceLastManualFormatSelectionMs";
            case 14:
                return "lastManualDirection";
            case 16:
                return "lastManualSelectedResolution";
            case 17:
                return "detailedNetworkType";
            case 18:
                return "clientViewportWidth";
            case 19:
                return "clientViewportHeight";
            case 20:
                return "clientBitrateCapBytesPerSec";
            case 21:
                return "stickyResolution";
            case 22:
                return "clientViewportIsFlexible";
            case 23:
                return "bandwidthEstimate";
            case 24:
                return "minAudioQuality";
            case 25:
                return "maxAudioQuality";
            case 26:
                return "videoQualitySetting";
            case 27:
                return "audioRoute";
            case 28:
                return "playerTimeMs";
            case 29:
                return "timeSinceLastSeek";
            case 30:
                return "dataSaverMode";
            case 32:
                return "networkMeteredState";
            case 34:
                return "visibility";
            case 35:
                return "playbackRate";
            case 36:
                return "elapsedWallTimeMs";
            case 38:
                return "mediaCapabilities";
            case 39:
                return "timeSinceLastActionMs";
            case 40:
                return "enabledTrackTypesBitfield";
            case 43:
                return "maxPacingRate";
            case 44:
                return "playerState";
            case 46:
                return "drcEnabled";
            case 48:
                return "field48";
            case 50:
                return "field50";
            case 51:
                return "field51";
            case 54:
                return "sabrReportRequestCancellationInfo";
            case 55:
                return "field55";
            case 56:
                return "disableStreamingXhr";
            case 57:
                return "field57";
            case 58:
                return "preferVp9";
            case 59:
                return "av1QualityThreshold";
            case 60:
                return "field60";
            case 61:
                return "isPrefetch";
            case 62:
                return "sabrSupportQualityConstraints";
            case 63:
                return "sabrLicenseConstraint";
            case 64:
                return "allowProximaLiveLatency";
            case 66:
                return "sabrForceProxima";
            case 67:
                return "field67";
            case 68:
                return "sabrForceMaxNetworkInterruptionDurationMs";
            case 69:
                return "audioTrackId";
            case 71:
                return "field71";
            case 72:
                return "field72";
            case 73:
                return "field73";
            case 74:
                return "field74";
            case 75:
                return "field75";
            case 76:
                return "enableVoiceBoost";
            case 79:
                return "playbackAuthorization";
            case 80:
                return "field80";
            default:
                return "field" + fieldNumber;
        }
    }

    private static boolean isBoolClientAbrStateField(final int fieldNumber) {
        return fieldNumber == 22 || fieldNumber == 30 || fieldNumber == 46
                || fieldNumber == 56 || fieldNumber == 58 || fieldNumber == 61
                || fieldNumber == 62 || fieldNumber == 71;
    }

    @Nonnull
    private static String join(@Nonnull final List<String> values) {
        final StringBuilder builder = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(values.get(i));
        }
        return builder.toString();
    }
}
