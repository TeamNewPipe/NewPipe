package org.schabi.newpipe.youtube;

import androidx.annotation.NonNull;

import org.schabi.newpipe.extractor.services.youtube.YoutubePoTokenResult;
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrSession;
import org.schabi.newpipe.extractor.services.youtube.sabr.exception.SabrAttestationException;

import java.util.Base64;

/** Maintains the PO-token recovery budget for one SABR media acquisition. */
public final class SabrAttestationRetryHandler {
    private static final int MAX_RETRIES = 3;

    @NonNull private final String videoId;
    private int retriesRemaining = MAX_RETRIES;

    public SabrAttestationRetryHandler(@NonNull final String videoId) {
        this.videoId = videoId;
    }

    /** Mints and injects the token for the next retry, or fails after the retry budget is spent. */
    public synchronized void prepareRetry(
            @NonNull final YoutubeSabrSession session,
            @NonNull final SabrAttestationException rejectedTokenError)
            throws SabrAttestationException {
        if (retriesRemaining == 0) {
            LocalDomPoTokenProvider.INSTANCE.invalidate();
            throw new SabrAttestationException(
                    "SABR PO token was rejected after " + MAX_RETRIES
                            + " attestation recovery retries for video=" + videoId,
                    rejectedTokenError);
        }

        final int retryNumber = MAX_RETRIES - retriesRemaining + 1;
        retriesRemaining--;
        final YoutubePoTokenResult tokenResult;
        try {
            tokenResult = LocalDomPoTokenProvider.INSTANCE.getPlayerPoToken(videoId);
            final byte[] token = Base64.getUrlDecoder().decode(tokenResult.getPlayerPoToken());
            if (token.length == 0) {
                throw new IllegalArgumentException("decoded token is empty");
            }
            session.setPoToken(token);
        } catch (final Exception error) {
            throw new SabrAttestationException(
                    "SABR PO token recovery failed on retry " + retryNumber + " of "
                            + MAX_RETRIES + " for video=" + videoId + ": "
                            + error.getMessage(), error);
        }
    }

    /** A media payload proves that the current token is usable and starts a fresh budget. */
    public synchronized void onMediaReceived() {
        retriesRemaining = MAX_RETRIES;
    }
}
