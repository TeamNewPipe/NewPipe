package org.schabi.newpipe.player.helper;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.schabi.newpipe.player.helper.VolumeBoostAudioProcessor.MAXIMUM_VOLUME_BOOST;
import static org.schabi.newpipe.player.helper.VolumeBoostAudioProcessor.MINIMUM_VOLUME_BOOST;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.audio.AudioProcessor;
import com.google.android.exoplayer2.audio.AudioProcessor.AudioFormat;
import com.google.android.exoplayer2.audio.AudioProcessor.UnhandledAudioFormatException;

import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class VolumeBoostAudioProcessorTest {

    private static final int SAMPLE_RATE = 44100;
    private static final int CHANNEL_COUNT = 2;

    private static final AudioFormat PCM_16_BIT =
            new AudioFormat(SAMPLE_RATE, CHANNEL_COUNT, C.ENCODING_PCM_16BIT);
    private static final AudioFormat PCM_FLOAT =
            new AudioFormat(SAMPLE_RATE, CHANNEL_COUNT, C.ENCODING_PCM_FLOAT);

    private static final float FLOAT_TOLERANCE = 0.0001f;

    private static VolumeBoostAudioProcessor processorFor(final AudioFormat audioFormat)
            throws UnhandledAudioFormatException {
        final VolumeBoostAudioProcessor processor = new VolumeBoostAudioProcessor();
        processor.configure(audioFormat);
        // the configuration only takes effect once the processor is flushed, just like when it is
        // used by an actual audio sink
        processor.flush();
        return processor;
    }

    private static ByteBuffer bufferOf(final short... samples) {
        final ByteBuffer buffer = ByteBuffer
                .allocateDirect(samples.length * 2)
                .order(ByteOrder.nativeOrder());
        for (final short sample : samples) {
            buffer.putShort(sample);
        }
        buffer.flip();
        return buffer;
    }

    private static ByteBuffer bufferOf(final float... samples) {
        final ByteBuffer buffer = ByteBuffer
                .allocateDirect(samples.length * 4)
                .order(ByteOrder.nativeOrder());
        for (final float sample : samples) {
            buffer.putFloat(sample);
        }
        buffer.flip();
        return buffer;
    }

    private static short[] shortsOf(final ByteBuffer buffer) {
        final short[] samples = new short[buffer.remaining() / 2];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = buffer.getShort(buffer.position() + i * 2);
        }
        return samples;
    }

    private static float[] floatsOf(final ByteBuffer buffer) {
        final float[] samples = new float[buffer.remaining() / 4];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = buffer.getFloat(buffer.position() + i * 4);
        }
        return samples;
    }

    /**
     * Feeds a square wave, i.e. a signal whose loudness is exactly its amplitude, to the processor
     * and returns the loudest sample it output while processing the last buffer.
     *
     * @param processor the processor to feed, which must be configured for float samples
     * @param amplitude the amplitude of every sample of the signal
     * @param seconds   how many seconds of audio to feed
     * @return the absolute value of the loudest sample of the output of the last buffer
     */
    private static float feedSquareWave(final VolumeBoostAudioProcessor processor,
                                       final float amplitude,
                                       final float seconds) {
        final int framesPerBuffer = 1024;
        final int samplesPerBuffer = framesPerBuffer * CHANNEL_COUNT;
        final int bufferCount = (int) (seconds * SAMPLE_RATE / framesPerBuffer);

        final float[] samples = new float[samplesPerBuffer];
        for (int i = 0; i < samples.length; i++) {
            // alternate the sign every frame, so that the signal has no constant offset
            samples[i] = (i / CHANNEL_COUNT) % 2 == 0 ? amplitude : -amplitude;
        }

        float peak = 0.0f;
        for (int i = 0; i < bufferCount; i++) {
            processor.queueInput(bufferOf(samples));

            peak = 0.0f;
            for (final float sample : floatsOf(processor.getOutput())) {
                peak = Math.max(peak, Math.abs(sample));
            }
        }
        return peak;
    }

    @Test
    public void encodingsWhichCannotBeAmplifiedAreRejected() {
        final VolumeBoostAudioProcessor processor = new VolumeBoostAudioProcessor();
        assertThrows(UnhandledAudioFormatException.class, () -> processor.configure(
                new AudioFormat(SAMPLE_RATE, CHANNEL_COUNT, C.ENCODING_AC3)));
    }

    @Test
    public void volumeBoostIsClampedToTheAllowedRange() {
        final VolumeBoostAudioProcessor processor = new VolumeBoostAudioProcessor();

        processor.setVolumeBoost(100.0f);
        assertEquals(MAXIMUM_VOLUME_BOOST, processor.getVolumeBoost(), FLOAT_TOLERANCE);

        processor.setVolumeBoost(0.0f);
        assertEquals(MINIMUM_VOLUME_BOOST, processor.getVolumeBoost(), FLOAT_TOLERANCE);
    }

    /**
     * The audio pipeline hands over the shared empty buffer whenever there is nothing to process,
     * which used to make the processor copy that buffer onto itself and throw, aborting playback
     * before the first frame was even rendered.
     */
    @Test
    public void emptyInputIsAccepted() throws UnhandledAudioFormatException {
        final VolumeBoostAudioProcessor processor = processorFor(PCM_16_BIT);

        processor.queueInput(AudioProcessor.EMPTY_BUFFER);
        assertFalse(processor.getOutput().hasRemaining());

        processor.setVolumeBoost(2.0f);
        processor.queueInput(AudioProcessor.EMPTY_BUFFER);
        assertFalse(processor.getOutput().hasRemaining());

        processor.setAutomaticVolumeBoost(true);
        processor.queueInput(AudioProcessor.EMPTY_BUFFER);
        assertFalse(processor.getOutput().hasRemaining());
    }

    @Test
    public void samplesArePassedThroughWhenNotBoosting() throws UnhandledAudioFormatException {
        final VolumeBoostAudioProcessor processor = processorFor(PCM_16_BIT);
        final short[] samples = {0, 123, -456, Short.MAX_VALUE, Short.MIN_VALUE};

        processor.queueInput(bufferOf(samples));

        assertArrayEquals(samples, shortsOf(processor.getOutput()));
    }

    @Test
    public void pcm16BitSamplesAreAmplifiedAndClipped() throws UnhandledAudioFormatException {
        final VolumeBoostAudioProcessor processor = processorFor(PCM_16_BIT);
        processor.setVolumeBoost(2.0f);

        processor.queueInput(bufferOf((short) 0, (short) 100, (short) -100, (short) 20000,
                (short) -20000));

        assertArrayEquals(new short[] {0, 200, -200, Short.MAX_VALUE, Short.MIN_VALUE},
                shortsOf(processor.getOutput()));
    }

    @Test
    public void pcmFloatSamplesAreAmplifiedAndClipped() throws UnhandledAudioFormatException {
        final VolumeBoostAudioProcessor processor = processorFor(PCM_FLOAT);
        processor.setVolumeBoost(2.0f);

        processor.queueInput(bufferOf(0.0f, 0.1f, -0.1f, 0.6f, -0.6f));

        assertArrayEquals(new float[] {0.0f, 0.2f, -0.2f, 1.0f, -1.0f},
                floatsOf(processor.getOutput()), FLOAT_TOLERANCE);
    }

    @Test
    public void theWholeInputBufferIsConsumed() throws UnhandledAudioFormatException {
        final VolumeBoostAudioProcessor processor = processorFor(PCM_16_BIT);
        processor.setVolumeBoost(2.0f);

        final ByteBuffer inputBuffer = bufferOf((short) 1, (short) 2, (short) 3);
        processor.queueInput(inputBuffer);

        assertFalse("the pipeline would keep feeding the same buffer forever",
                inputBuffer.hasRemaining());
    }

    @Test
    public void automaticModeAmplifiesQuietAudio() throws UnhandledAudioFormatException {
        final VolumeBoostAudioProcessor processor = processorFor(PCM_FLOAT);
        processor.setAutomaticVolumeBoost(true);

        final float amplitude = 0.02f;
        final float peak = feedSquareWave(processor, amplitude, 3.0f);

        assertTrue("quiet audio should have been amplified, but the peak went from "
                + amplitude + " to " + peak, peak > amplitude * 2.0f);
        assertTrue("the gain should never exceed the maximum volume boost, but the peak went from "
                        + amplitude + " to " + peak,
                peak <= amplitude * MAXIMUM_VOLUME_BOOST + FLOAT_TOLERANCE);
    }

    @Test
    public void automaticModeLeavesLoudAudioAlone() throws UnhandledAudioFormatException {
        final VolumeBoostAudioProcessor processor = processorFor(PCM_FLOAT);
        processor.setAutomaticVolumeBoost(true);

        final float amplitude = 0.95f;
        final float peak = feedSquareWave(processor, amplitude, 3.0f);

        assertEquals("audio which is already loud should not have been amplified",
                amplitude, peak, FLOAT_TOLERANCE);
    }

    @Test
    public void automaticModeStartsOverAfterAFlush() throws UnhandledAudioFormatException {
        final VolumeBoostAudioProcessor processor = processorFor(PCM_FLOAT);
        processor.setAutomaticVolumeBoost(true);

        final float amplitude = 0.02f;
        feedSquareWave(processor, amplitude, 3.0f);
        // seeking, or playing another stream, flushes the processor
        processor.flush();

        final float peak = feedSquareWave(processor, amplitude, 0.05f);
        assertTrue("the gain should have been reset, but the peak went from "
                + amplitude + " to " + peak, peak < amplitude * 1.5f);
    }
}
