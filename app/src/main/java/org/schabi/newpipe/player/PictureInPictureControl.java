package org.schabi.newpipe.player;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.PendingIntent;
import android.app.PictureInPictureParams;
import android.app.RemoteAction;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.widget.Toast;

import org.schabi.newpipe.R;
import org.schabi.newpipe.player.helper.PlayerHelper;
import org.schabi.newpipe.player.helper.PlayerHolder;

import java.util.Arrays;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import static android.app.PendingIntent.FLAG_UPDATE_CURRENT;
import static android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE;
import static android.widget.Toast.LENGTH_SHORT;
import static org.schabi.newpipe.player.MainPlayer.ACTION_PLAY_NEXT;
import static org.schabi.newpipe.player.MainPlayer.ACTION_PLAY_PREVIOUS;
import static org.schabi.newpipe.player.helper.PlayerHelper.MinimizeMode.MINIMIZE_ON_EXIT_MODE_PIP;

public interface PictureInPictureControl {

    void onUserLeaveHint(@NonNull Activity activity);

    final class MainPlayerPictureInPicture implements PictureInPictureControl {
        public static final PictureInPictureControl CONTROLLER = new MainPlayerPictureInPicture();
        private static final int BROADCAST_ID = 846221;
        private Boolean pipSupported;
        private Toast pipToast;
        private PictureInPictureParams pipParameters;
        private MainPlayerPictureInPicture() { }

        @Override
        public void onUserLeaveHint(@NonNull final Activity activity) {
            if (!shouldEnterPip(activity)) {
                return;
            }

            if (pipSupported == null) {
                pipSupported = isPipSupported(activity);
            }

            if (!pipSupported) {
                showPipSupportMissing(activity);
                return;
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                enterPictureInPictureMode(activity);
            }
        }

        @RequiresApi(api = Build.VERSION_CODES.O)
        private void enterPictureInPictureMode(@NonNull final Activity activity) {
            if (pipParameters == null) {
                pipParameters = new PictureInPictureParams.Builder()
                        .setActions(buildActions(activity))
                        .build();
            }
            activity.enterPictureInPictureMode(pipParameters);
        }

        private void showPipSupportMissing(@NonNull final Context context) {
            if (pipToast != null) {
                pipToast.cancel();
            }
            pipToast = Toast.makeText(context, R.string.pip_mode_unsupported, LENGTH_SHORT);
            pipToast.show();
        }

        @RequiresApi(api = Build.VERSION_CODES.O)
        @SuppressLint("UnspecifiedImmutableFlag")
        private List<RemoteAction> buildActions(@NonNull final Context context) {
            final Icon playPreviousIcon =
                    Icon.createWithResource(context, R.drawable.exo_icon_previous);
            final PendingIntent playPreviousIntent = PendingIntent.getBroadcast(
                    context, BROADCAST_ID, new Intent(ACTION_PLAY_PREVIOUS), FLAG_UPDATE_CURRENT);
            // title and contentDescription do not accept string resource and are not displayed
            final RemoteAction playPreviousAction = new RemoteAction(
                    playPreviousIcon, "Previous", "Play Previous", playPreviousIntent);

            final Icon playNextIcon = Icon.createWithResource(context, R.drawable.exo_icon_next);
            final PendingIntent playNextIntent = PendingIntent.getBroadcast(
                    context, BROADCAST_ID, new Intent(ACTION_PLAY_NEXT), FLAG_UPDATE_CURRENT);
            final RemoteAction playNextAction = new RemoteAction(
                    playNextIcon, "Next", "Play Next", playNextIntent);

            return Arrays.asList(playPreviousAction, playNextAction);
        }

        /**
         * Checks if PIP is supported on the device:
         * - Android version must be 8.0+
         * - Must be PIP-capable (i.e. RAM is sufficient)
         *
         * @param activity the activity hosting a PIP-entering player
         * @return boolean indicating PIP is supported on this device
         **/
        private static boolean isPipSupported(@NonNull final Activity activity) {
            final PackageManager packageManager = activity.getPackageManager();
            return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    && packageManager.hasSystemFeature(FEATURE_PICTURE_IN_PICTURE);
        }

        /**
         * Determines if the current player state is suitable for entering PIP:
         * - User must enable PIP mode on player minimize
         * - Player must be playing in MainPlayer video mode
         *
         * Not meeting the second criteria will cause PIP to enter when the player ui is cluttered,
         * e.g. when paused.
         *
         * @param activity the activity hosting a PIP-entering player
         * @return boolean indicating if the underlying player is in a good state for entering PIP
         **/
        private static boolean shouldEnterPip(@NonNull final Activity activity) {
            final PlayerHolder player = PlayerHolder.getInstance();
            return MINIMIZE_ON_EXIT_MODE_PIP == PlayerHelper.getMinimizeOnExitAction(activity)
                    && MainPlayer.PlayerType.VIDEO == player.getType()
                    && player.isPlaying();
        }
    }
}
