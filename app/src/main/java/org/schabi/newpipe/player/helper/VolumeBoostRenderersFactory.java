package org.schabi.newpipe.player.helper;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.audio.AudioProcessor;
import com.google.android.exoplayer2.audio.AudioSink;
import com.google.android.exoplayer2.audio.DefaultAudioSink;

/**
 * A {@link DefaultRenderersFactory} which adds a {@link VolumeBoostAudioProcessor} to the audio
 * sink, so that the volume of quiet streams can be amplified beyond the system volume.
 *
 * <p>
 * The audio sink is rebuilt every time a new player is created, therefore the currently wanted
 * gain is kept here and forwarded to the audio processor which is in use at the moment.
 * </p>
 */
public class VolumeBoostRenderersFactory extends DefaultRenderersFactory {

    private float volumeBoost = VolumeBoostAudioProcessor.MINIMUM_VOLUME_BOOST;
    private boolean automaticVolumeBoost = false;

    @Nullable
    private VolumeBoostAudioProcessor volumeBoostAudioProcessor;

    public VolumeBoostRenderersFactory(@NonNull final Context context) {
        super(context);
    }

    public float getVolumeBoost() {
        return volumeBoost;
    }

    /**
     * Applies the provided gain to the audio sink currently in use, if any, and remembers it so
     * that audio sinks built later on also use it.
     *
     * @param newVolumeBoost the gain to apply to the decoded audio samples
     */
    public void setVolumeBoost(final float newVolumeBoost) {
        volumeBoost = newVolumeBoost;
        if (volumeBoostAudioProcessor != null) {
            volumeBoostAudioProcessor.setVolumeBoost(newVolumeBoost);
        }
    }

    public boolean isAutomaticVolumeBoost() {
        return automaticVolumeBoost;
    }

    /**
     * Enables or disables the automatic gain of the audio sink currently in use, if any, and
     * remembers the choice so that audio sinks built later on also use it.
     *
     * @param newAutomaticVolumeBoost whether the gain should be computed from the loudness of the
     *                                stream instead of being the one set by the user
     */
    public void setAutomaticVolumeBoost(final boolean newAutomaticVolumeBoost) {
        automaticVolumeBoost = newAutomaticVolumeBoost;
        if (volumeBoostAudioProcessor != null) {
            volumeBoostAudioProcessor.setAutomaticVolumeBoost(newAutomaticVolumeBoost);
        }
    }

    @Nullable
    @Override
    protected AudioSink buildAudioSink(@NonNull final Context context,
                                       final boolean enableFloatOutput,
                                       final boolean enableAudioTrackPlaybackParams,
                                       final boolean enableOffload) {
        // audio processors may not be shared between audio sinks, so build a new one every time
        volumeBoostAudioProcessor = new VolumeBoostAudioProcessor();
        volumeBoostAudioProcessor.setVolumeBoost(volumeBoost);
        volumeBoostAudioProcessor.setAutomaticVolumeBoost(automaticVolumeBoost);

        return new DefaultAudioSink.Builder(context)
                .setAudioProcessors(new AudioProcessor[] {volumeBoostAudioProcessor})
                .setEnableFloatOutput(enableFloatOutput)
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .setOffloadMode(enableOffload
                        ? DefaultAudioSink.OFFLOAD_MODE_ENABLED_GAPLESS_REQUIRED
                        : DefaultAudioSink.OFFLOAD_MODE_DISABLED)
                .build();
    }
}
