package org.schabi.newpipe.sleep;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class TimerStopReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        // Stop the service when the broadcast is received
        Intent serviceIntent = new Intent(context, SleepTimerService.class);
        context.stopService(serviceIntent);
    }
}
