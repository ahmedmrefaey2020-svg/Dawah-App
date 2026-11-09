package com.dawa.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import java.io.File;

/** خدمة أمامية بتشغّل ملف المؤذن المحمّل (أو أذان التطبيق الافتراضي) والتطبيق مقفول. */
public class AdhanService extends Service {
    static final String CH = "dawa_adhan_playing", CH_FALLBACK = "dawa_adhan_fallback_v1";
    static final String ACT_STOP = "com.dawa.app.ADHAN_STOP";
    private static final int NID = 7101;

    private MediaPlayer mp;
    private PowerManager.WakeLock wl;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable timeout = this::finish;

    static void play(Context c, String title, String body, String file, float vol) {
        Intent s = new Intent(c, AdhanService.class).putExtra("title", title).putExtra("body", body)
                .putExtra("file", file).putExtra("vol", vol);
        ContextCompat.startForegroundService(c, s);
    }

    static void stop(Context c) { c.stopService(new Intent(c, AdhanService.class)); }

    @Override public IBinder onBind(Intent i) { return null; }

    @Override
    public int onStartCommand(Intent in, int flags, int startId) {
        if (in != null && ACT_STOP.equals(in.getAction())) { finish(); return START_NOT_STICKY; }
        String title = in == null ? "الأذان" : in.getStringExtra("title");
        String body = in == null ? "" : in.getStringExtra("body");
        String file = in == null ? "" : in.getStringExtra("file");
        float vol = in == null ? 0.9f : in.getFloatExtra("vol", 0.9f);

        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CH) == null) {
            NotificationChannel ch = new NotificationChannel(CH, "تشغيل الأذان", NotificationManager.IMPORTANCE_HIGH);
            ch.setSound(null, null); // الصوت بيشغّله MediaPlayer مش الإشعار
            nm.createNotificationChannel(ch);
        }
        Intent open = getPackageManager().getLaunchIntentForPackage(getPackageName());
        int imm = Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0;
        PendingIntent openPi = PendingIntent.getActivity(this, 0, open, imm | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stopPi = PendingIntent.getService(this, 1,
                new Intent(this, AdhanService.class).setAction(ACT_STOP), imm | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new NotificationCompat.Builder(this, CH)
                .setSmallIcon(getApplicationInfo().icon) // الأفضل تحط أيقونة إشعار بيضاء ic_stat_adhan
                .setContentTitle(title == null ? "الأذان" : title).setContentText(body)
                .setContentIntent(openPi).setOngoing(true).setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .addAction(0, "إيقاف", stopPi).build();
        if (Build.VERSION.SDK_INT >= 29) startForeground(NID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        else startForeground(NID, n);

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (wl == null) {
            wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "dawa:adhan");
            wl.acquire(8 * 60 * 1000L);
        }
        h.removeCallbacks(timeout);
        h.postDelayed(timeout, 8 * 60 * 1000L);
        startPlayback(file, vol);
        return START_NOT_STICKY;
    }

    private void startPlayback(String file, float vol) {
        releasePlayer();
        try {
            mp = new MediaPlayer();
            mp.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
            File f = (file != null && !file.isEmpty() && file.indexOf('/') < 0 && file.indexOf("..") < 0)
                    ? new File(DawaAdhanPlugin.adhanDir(this), file) : null;
            if (f != null && f.length() > 5000) {
                mp.setDataSource(f.getAbsolutePath()); // الملف المحمّل على الجهاز
            } else {
                int res = getResources().getIdentifier("adhan", "raw", getPackageName()); // الأذان الافتراضي res/raw/adhan.mp3
                if (res != 0) {
                    AssetFileDescriptor afd = getResources().openRawResourceFd(res);
                    mp.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
                    afd.close();
                } else {
                    mp.setDataSource(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM));
                }
            }
            mp.setVolume(vol, vol);
            mp.setOnCompletionListener(m -> finish());
            mp.setOnErrorListener((m, w, e) -> { finish(); return true; });
            mp.prepare();
            mp.start();
        } catch (Exception e) {
            finish();
        }
    }

    private void releasePlayer() {
        if (mp != null) { try { mp.stop(); } catch (Exception ignored) {} mp.release(); mp = null; }
    }

    private void finish() {
        h.removeCallbacks(timeout);
        releasePlayer();
        if (wl != null && wl.isHeld()) wl.release();
        wl = null;
        stopForeground(true);
        stopSelf();
    }

    @Override public void onDestroy() { finish(); super.onDestroy(); }

    /** احتياطي: إشعار عادي بصوت الأذان الافتراضي لو الخدمة اتمنعت. */
    static void notifyFallback(Context c, String title, String body) {
        try {
            NotificationManager nm = (NotificationManager) c.getSystemService(NOTIFICATION_SERVICE);
            int res = c.getResources().getIdentifier("adhan", "raw", c.getPackageName());
            Uri snd = res != 0 ? Uri.parse("android.resource://" + c.getPackageName() + "/" + res) : null;
            if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CH_FALLBACK) == null) {
                NotificationChannel ch = new NotificationChannel(CH_FALLBACK, "الأذان", NotificationManager.IMPORTANCE_HIGH);
                if (snd != null) ch.setSound(snd, new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build());
                nm.createNotificationChannel(ch);
            }
            Intent open = c.getPackageManager().getLaunchIntentForPackage(c.getPackageName());
            PendingIntent pi = PendingIntent.getActivity(c, 0, open,
                    (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0) | PendingIntent.FLAG_UPDATE_CURRENT);
            nm.notify(NID + 1, new NotificationCompat.Builder(c, CH_FALLBACK).setSmallIcon(c.getApplicationInfo().icon)
                    .setContentTitle(title == null ? "الأذان" : title).setContentText(body).setContentIntent(pi)
                    .setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_HIGH).build());
        } catch (Exception ignored) {
        }
    }
}
