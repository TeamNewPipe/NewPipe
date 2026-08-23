package org.schabi.newpipe.player;

import static org.schabi.newpipe.player.Player.PLAYER_TYPE;

import android.content.Intent;

public enum PlayerType {
    MAIN,
    AUDIO,
    POPUP;

    /**
     * @return an integer representing this {@link PlayerType}, to be used to save it in intents
     * @see #retrieveFromIntent(Intent) Use retrieveFromIntent() to retrieve and convert player type
     *                                  integers from an intent
     */
    public int valueForIntent() {
        return ordinal();
    }

    /**
     * @param intent the intent to retrieve a player type from
     * @return the player type integer retrieved from the intent, converted back into a {@link
     *         PlayerType}, or {@link PlayerType#MAIN} if there is no player type extra in the
     *         intent
     * @throws ArrayIndexOutOfBoundsException if the intent contains an invalid player type integer
     * @see #valueForIntent() Use valueForIntent() to obtain valid player type integers
     */
    public static PlayerType retrieveFromIntent(final Intent intent) {
        return values()[intent.getIntExtra(PLAYER_TYPE, MAIN.valueForIntent())];
    }
}
