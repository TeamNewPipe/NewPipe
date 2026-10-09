package org.schabi.newpipe.player.helper;

import androidx.core.math.MathUtils;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.audio.BaseAudioProcessor;

import java.nio.ByteBuffer;

/**
 * An {@link com.google.android.exoplayer2.audio.AudioProcessor} which amplifies the decoded PCM
 * samples by a gain factor, allowing playback to become louder than the maximum volume the system
 * would normally allow.
 *
 * <p>
 * Samples exceeding the range representable by the current encoding are clipped, which is why
 * boosting too much introduces distortion. This is the same trade-off other players (e.g. VLC)
 * make for their volume boost feature.
 * </p>
 *
 * <p>
 * The gain is either chosen by the user, or, when the automatic mode is enabled, continuously
 * computed from the loudness of the stream itself: quiet passages are amplified up to
 * {@link #MAXIMUM_VOLUME_BOOST} while already loud ones are left untouched. The automatic gain
 * follows the signal with a fast attack and a slow release, so that a sudden loud passage is
 * brought back down almost immediately (avoiding clipping) while the amplification of a quiet
 * passage ramps up smoothly instead of pumping.
 * </p>
 *
 * <p>
 * The processor is always active, even when the gain is {@link #MINIMUM_VOLUME_BOOST}, so that the
 * gain can be changed while playing without having to reconfigure the audio sink.
 * </p>
 */
public final class VolumeBoostAudioProcessor extends BaseAudioProcessor {

    /**
     * No amplification at all, i.e. the original volume of the stream.
     */
    public static final float MINIMUM_VOLUME_BOOST = 1.00f;

    /**
     * The loudest amplification allowed, i.e. three times the original volume.
     */
    public static final float MAXIMUM_VOLUME_BOOST = 3.00f;

    private static final int PCM_16_BIT_SAMPLE_SIZE = 2;
    private static final int PCM_FLOAT_SAMPLE_SIZE = 4;

    /**
     * The loudness the automatic mode tries to reach, as a root mean square of samples normalized
     * to {@code [-1, 1]}. This is roughly -16 dBFS, which is about as loud as a well mastered
     * stream, and is only ever reached by amplifying, never by attenuating.
     */
    private static final float AUTOMATIC_TARGET_LOUDNESS = 0.15f;

    /**
     * The automatic gain is reduced so that the loudest sample of the buffer being processed stays
     * below this value, leaving a bit of headroom to limit clipping.
     */
    private static final float AUTOMATIC_PEAK_CEILING = 0.98f;

    /**
     * Buffers quieter than this are considered silence, and leave the automatic gain untouched
     * instead of making it jump to the maximum.
     */
    private static final float AUTOMATIC_SILENCE_LOUDNESS = 0.001f;

    /**
     * How long the automatic gain takes to (mostly) reach a lower gain, in seconds.
     */
    private static final float AUTOMATIC_ATTACK_SECONDS = 0.05f;

    /**
     * How long the automatic gain takes to (mostly) reach a higher gain, in seconds.
     */
    private static final float AUTOMATIC_RELEASE_SECONDS = 2.0f;

    /**
     * Read from the audio processing thread, written from the main thread.
     */
    private volatile float volumeBoost = MINIMUM_VOLUME_BOOST;

    /**
     * Read from the audio processing thread, written from the main thread.
     */
    private volatile boolean automaticVolumeBoost = false;

    /**
     * The gain currently applied by the automatic mode. Only touched by the audio processing
     * thread, except when it is reset while the processor is flushed.
     */
    private float automaticGain = MINIMUM_VOLUME_BOOST;

    private float attackCoefficient = 1.0f;
    private float releaseCoefficient = 1.0f;

    public float getVolumeBoost() {
        return volumeBoost;
    }

    public void setVolumeBoost(final float newVolumeBoost) {
        volumeBoost = MathUtils.clamp(newVolumeBoost, MINIMUM_VOLUME_BOOST, MAXIMUM_VOLUME_BOOST);
    }

    public boolean isAutomaticVolumeBoost() {
        return automaticVolumeBoost;
    }

    public void setAutomaticVolumeBoost(final boolean newAutomaticVolumeBoost) {
        automaticVolumeBoost = newAutomaticVolumeBoost;
    }

    @Override
    protected AudioFormat onConfigure(final AudioFormat inputAudioFormat)
            throws UnhandledAudioFormatException {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT
                && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
            // let the sink know that other encodings (e.g. passthrough) can't be amplified
            throw new UnhandledAudioFormatException(inputAudioFormat);
        }

        // the gain is smoothed once per sample, so the time constants depend on how many samples
        // (not frames) of this format make up a second
        final float samplesPerSecond =
                (float) inputAudioFormat.sampleRate * inputAudioFormat.channelCount;
        attackCoefficient = smoothingCoefficient(AUTOMATIC_ATTACK_SECONDS, samplesPerSecond);
        releaseCoefficient = smoothingCoefficient(AUTOMATIC_RELEASE_SECONDS, samplesPerSecond);

        return inputAudioFormat;
    }

    @Override
    protected void onFlush() {
        // the next buffer may come from a completely different position or stream, so start over
        automaticGain = MINIMUM_VOLUME_BOOST;
    }

    @Override
    public void queueInput(final ByteBuffer inputBuffer) {
        final int limit = inputBuffer.limit();
        final int position = inputBuffer.position();

        if (position == limit) {
            // The audio pipeline hands over the shared empty buffer when there is nothing to
            // process, and that is the very same buffer replaceOutputBuffer() returns for an empty
            // output, so going any further would make that buffer be copied onto itself.
            replaceOutputBuffer(0).flip();
            return;
        }

        final ByteBuffer buffer = replaceOutputBuffer(limit - position);
        final boolean automatic = automaticVolumeBoost;
        final float gain = volumeBoost;
        final boolean pcm16Bit = inputAudioFormat.encoding == C.ENCODING_PCM_16BIT;

        if (!automatic && gain == MINIMUM_VOLUME_BOOST) {
            buffer.put(inputBuffer);
            buffer.flip();
            return;
        }

        if (automatic) {
            amplifyAutomatically(inputBuffer, buffer, position, limit, pcm16Bit);
        } else if (pcm16Bit) {
            for (int i = position; i < limit; i += PCM_16_BIT_SAMPLE_SIZE) {
                buffer.putShort(amplify(inputBuffer.getShort(i), gain));
            }
        } else {
            for (int i = position; i < limit; i += PCM_FLOAT_SAMPLE_SIZE) {
                buffer.putFloat(amplify(inputBuffer.getFloat(i), gain));
            }
        }

        inputBuffer.position(limit);
        buffer.flip();
    }

    private void amplifyAutomatically(final ByteBuffer inputBuffer,
                                      final ByteBuffer outputBuffer,
                                      final int position,
                                      final int limit,
                                      final boolean pcm16Bit) {
        final int sampleSize = pcm16Bit ? PCM_16_BIT_SAMPLE_SIZE : PCM_FLOAT_SAMPLE_SIZE;
        final float targetGain = calculateAutomaticGain(inputBuffer, position, limit, pcm16Bit);
        // reaching a lower gain quickly keeps a sudden loud passage from being clipped, while
        // reaching a higher gain slowly keeps the amplification from audibly pumping
        final float coefficient =
                targetGain < automaticGain ? attackCoefficient : releaseCoefficient;

        for (int i = position; i < limit; i += sampleSize) {
            automaticGain += (targetGain - automaticGain) * coefficient;
            if (pcm16Bit) {
                outputBuffer.putShort(amplify(inputBuffer.getShort(i), automaticGain));
            } else {
                outputBuffer.putFloat(amplify(inputBuffer.getFloat(i), automaticGain));
            }
        }
    }

    /**
     * Computes the gain that would bring the provided buffer to {@link #AUTOMATIC_TARGET_LOUDNESS}
     * without pushing its loudest sample past {@link #AUTOMATIC_PEAK_CEILING}.
     *
     * @param inputBuffer the buffer about to be processed
     * @param position    the index of the first byte to take into account
     * @param limit       the index after the last byte to take into account
     * @param pcm16Bit    whether the buffer holds 16 bit samples instead of float ones
     * @return the gain to converge to, always within the allowed volume boost range
     */
    private float calculateAutomaticGain(final ByteBuffer inputBuffer,
                                         final int position,
                                         final int limit,
                                         final boolean pcm16Bit) {
        final int sampleSize = pcm16Bit ? PCM_16_BIT_SAMPLE_SIZE : PCM_FLOAT_SAMPLE_SIZE;
        double sumOfSquares = 0.0;
        float peak = 0.0f;
        int sampleCount = 0;

        for (int i = position; i < limit; i += sampleSize) {
            final float sample = pcm16Bit
                    ? inputBuffer.getShort(i) / (float) -Short.MIN_VALUE
                    : inputBuffer.getFloat(i);
            sumOfSquares += (double) sample * sample;
            peak = Math.max(peak, Math.abs(sample));
            sampleCount++;
        }

        final float loudness = sampleCount == 0
                ? 0.0f : (float) Math.sqrt(sumOfSquares / sampleCount);
        if (loudness < AUTOMATIC_SILENCE_LOUDNESS) {
            // there is nothing to measure, so keep whatever gain is currently being applied
            return automaticGain;
        }

        float gain = AUTOMATIC_TARGET_LOUDNESS / loudness;
        if (peak > 0.0f) {
            gain = Math.min(gain, AUTOMATIC_PEAK_CEILING / peak);
        }
        return MathUtils.clamp(gain, MINIMUM_VOLUME_BOOST, MAXIMUM_VOLUME_BOOST);
    }

    /**
     * @param seconds          the time the smoothed value takes to cover about 63% of the distance
     *                         to the value it converges to
     * @param samplesPerSecond how many samples are smoothed every second
     * @return the factor of the remaining distance to cover with every single sample
     */
    private static float smoothingCoefficient(final float seconds, final float samplesPerSecond) {
        return (float) (1.0 - Math.exp(-1.0 / (seconds * samplesPerSecond)));
    }

    private static short amplify(final short sample, final float gain) {
        return (short) MathUtils.clamp(Math.round(sample * gain),
                Short.MIN_VALUE, Short.MAX_VALUE);
    }

    private static float amplify(final float sample, final float gain) {
        return MathUtils.clamp(sample * gain, -1.0f, 1.0f);
    }
}
