package com.yhan.hasat;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;

/** Bildirim, titreşim ve ses yardımcıları. */
final class Notif {
    private Notif() {}

    static final String CH_ON = "olcum_suruyor", CH_ALERT = "olcum_uyari";
    static final int ID_ONGOING = 5101, ID_ALERT = 5102;
    private static long lastUpdate;

    static void ensure(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;
        NotificationChannel a = new NotificationChannel(CH_ON, "Ölçüm sürüyor", NotificationManager.IMPORTANCE_LOW);
        a.setShowBadge(false);
        NotificationChannel b = new NotificationChannel(CH_ALERT, "Başlangıca dönüş uyarısı", NotificationManager.IMPORTANCE_HIGH);
        b.enableVibration(true);
        b.setVibrationPattern(new long[]{0, 400, 200, 400, 200, 400});
        nm.createNotificationChannel(a);
        nm.createNotificationChannel(b);
    }

    static PendingIntent open(Context c) {
        Intent i = new Intent(c, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(c, 0, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    static Notification ongoing(Context c) {
        ensure(c);
        String txt;
        if (Session.state == Session.PAUSED) {
            txt = "Duraklatıldı";
        } else if (Session.cornerMode) {
            txt = Session.corners.size() + " köşe · " + Fmt.donum(Session.liveArea(false)) + " dönüm";
        } else {
            txt = Fmt.m(Session.pathLen) + " yol · ≈ " + Fmt.donum(Session.liveArea(true)) + " dönüm";
        }
        return new Notification.Builder(c, CH_ON)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("Bzk.Hasat · alan ölçülüyor")
                .setContentText(txt)
                .setColor(0xFF1F5C3A)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(open(c))
                .build();
    }

    static void updateOngoing(Context c) {
        long now = SystemClock.elapsedRealtime();
        if (now - lastUpdate < 5000) return;
        lastUpdate = now;
        if (!allowed(c)) return;
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm != null) nm.notify(ID_ONGOING, ongoing(c));
    }

    static boolean allowed(Context c) {
        return c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                || android.os.Build.VERSION.SDK_INT < 33;
    }

    static void returned(Context c) {
        vibrate(c, new long[]{0, 400, 200, 400, 200, 400});
        tone(ToneGenerator.TONE_PROP_ACK);
        if (!allowed(c)) return;
        ensure(c);
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;
        nm.notify(ID_ALERT, new Notification.Builder(c, CH_ALERT)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("Başlangıç noktasına döndünüz")
                .setContentText("Tur tamamsa BİTİR'e basın.")
                .setColor(0xFFE0A526)
                .setAutoCancel(true)
                .setContentIntent(open(c))
                .build());
    }

    static void beep(Context c) {
        vibrate(c, new long[]{0, 150});
        tone(ToneGenerator.TONE_PROP_BEEP);
    }

    static void vibrate(Context c, long[] pattern) {
        try {
            Vibrator v = c.getSystemService(Vibrator.class);
            if (v != null && v.hasVibrator()) v.vibrate(VibrationEffect.createWaveform(pattern, -1));
        } catch (Exception ignored) {
        }
    }

    static void tone(int t) {
        try {
            ToneGenerator g = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90);
            g.startTone(t, 300);
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(g::release, 600);
        } catch (Exception ignored) {
        }
    }
}
