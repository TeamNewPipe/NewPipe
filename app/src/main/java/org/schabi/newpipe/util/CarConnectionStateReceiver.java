package org.schabi.newpipe.util;
import android.content.*;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import androidx.car.app.connection.CarConnection;
import org.schabi.newpipe.player.PlayerBinderInterface;
import org.schabi.newpipe.player.mediasession.PlayerServiceInterface;

public class CarConnectionStateReceiver extends BroadcastReceiver {

    private static final String TAG = "CarConnectionReceiver";
    
    private static volatile boolean isCarConnected = false;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && CarConnection.ACTION_CAR_CONNECTION_UPDATED.equals(intent.getAction())) {
            int connectionState = intent.getIntExtra(CarConnection.CAR_CONNECTION_STATE, -1);

            boolean isConnected = (connectionState != CarConnection.CONNECTION_TYPE_NOT_CONNECTED);

            Log.d(TAG, "Android Auto connection state changed. Is connected: " + isConnected);
            shutdownOldService(context); // shutdown old Service or phone and auto are interacting to different service
            setCarConnectionState(isConnected);
        }
    }

    public static boolean isCarConnected() {
        Log.i(TAG, String.valueOf(isCarConnected));
        return isCarConnected;
    }

    
    public static void setCarConnectionState(boolean connected) {
        if (isCarConnected != connected) {
            isCarConnected = connected;
        }
    }

    private static void shutdownOldService(Context context) {
        ServiceConnection connection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                // 获取服务引用
                PlayerBinderInterface binder = (PlayerBinderInterface) service;
                PlayerServiceInterface binderService = binder.getService();
                binderService.stopService();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {}
        };
        Intent serviceIntent = new Intent(context, DeviceUtils.getPlayerServiceClass());
        context.bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE);
    }
}
