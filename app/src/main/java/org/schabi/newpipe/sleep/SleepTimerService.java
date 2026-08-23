package org.schabi.newpipe.sleep;

import android.annotation.SuppressLint;
import android.app.*;
import android.content.Intent;
import android.os.Build;
import android.os.CountDownTimer;
import android.os.IBinder;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import org.schabi.newpipe.R;

import static org.schabi.newpipe.player.PlayerService.ACTION_CLOSE;

public class SleepTimerService extends Service {

    public String CHANNEL_ID;
    private CountDownTimer countDownTimer;

    public static final String ACTION_START_TIMER = "org.schabi.newpipe.sleep.action.START_TIMER";
    public static final String ACTION_STOP_TIMER = "org.schabi.newpipe.sleep.action.STOP_TIMER";


    @Override
    public void onCreate() {
        super.onCreate();
        CHANNEL_ID = getString(R.string.notification_channel_id);
        createNotificationChannel();
    }

    @SuppressLint("SimpleDateFormat")
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent.getAction();
        if (ACTION_START_TIMER.equals(action)) {
            long timeInMillis = intent.getIntExtra("timeInMillis", 900000);
            startForeground(1, getNotification(getFormattedTime(timeInMillis)));

            if (countDownTimer != null) {
                countDownTimer.cancel();
                countDownTimer = null;
            }
            countDownTimer = new CountDownTimer(timeInMillis, 1000) {
                public void onTick(long millisUntilFinished) {
                    Notification updatedNotification = getNotification(getFormattedTime(millisUntilFinished));
                    NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                    notificationManager.notify(1, updatedNotification); // Use the same ID that you used for startForeground
                }

                public void onFinish() {
                    Intent intent = new Intent(ACTION_CLOSE);
                    sendBroadcast(intent);
                    stopSelf();
                }
            }.start();
        } else if (ACTION_STOP_TIMER.equals(action)) {
            if (countDownTimer != null) {
                countDownTimer.cancel();
                countDownTimer = null;
            }
            stopForeground(true);
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    private Notification getNotification(String text) {
        // Intent for the stop action
        Intent stopIntent = new Intent(this, TimerStopReceiver.class);
        PendingIntent stopPendingIntent = PendingIntent.getBroadcast(this, 0, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Build the notification
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Sleep Timer")
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_pipeplay)
                .addAction(R.drawable.ic_pipeplay, "Stop", stopPendingIntent) // Use your stop icon
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .build();
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Sleep Timer Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );

            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(serviceChannel);
        }
    }

    private String getFormattedTime(long millis) {
        long hours = millis / 3600000;
        long minutes = (millis % 3600000) / 60000;
        long seconds = ((millis % 3600000) % 60000) / 1000;

        // Format the remaining time as a string
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }
}
