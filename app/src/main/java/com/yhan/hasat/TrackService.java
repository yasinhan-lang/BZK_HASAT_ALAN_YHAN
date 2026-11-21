package com.yhan.hasat;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.location.GnssStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

/** Ekran kapalıyken de GNSS konumlarını kaydeden ön plan servisi. */
public class TrackService extends Service implements LocationListener {

    static final String ACT_START = "basla", ACT_STOP = "dur";
    static boolean running;

    private LocationManager lm;
    private PowerManager.WakeLock wl;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final GnssStatus.Callback gnss = new GnssStatus.Callback() {
        @Override
        public void onSatelliteStatusChanged(GnssStatus s) { Gnss.update(s); }
    };

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            Session.tick(TrackService.this);
            Notif.updateOngoing(TrackService.this);
            h.postDelayed(this, 1000);
        }
    };

    static void start(Context c) {
        Intent i = new Intent(c, TrackService.class).setAction(ACT_START);
        c.startForegroundService(i);
    }

    static void stop(Context c) {
        c.stopService(new Intent(c, TrackService.class));
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @SuppressLint("MissingPermission")
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACT_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        startForeground(Notif.ID_ONGOING, Notif.ongoing(this), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        if (!running) {
            running = true;
            lm = getSystemService(LocationManager.class);
            try {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 500L, 0f, this, Looper.getMainLooper());
                lm.registerGnssStatusCallback(gnss, h);
            } catch (SecurityException e) {
                Session.message("Konum izni yok.");
            }
            PowerManager pm = getSystemService(PowerManager.class);
            wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BzkHasat:kayit");
            wl.acquire(12 * 60 * 60 * 1000L);
            h.post(ticker);
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        h.removeCallbacks(ticker);
        if (lm != null) {
            lm.removeUpdates(this);
            lm.unregisterGnssStatusCallback(gnss);
        }
        if (wl != null && wl.isHeld()) wl.release();
        Session.save(this);
        super.onDestroy();
    }

    @Override
    public void onLocationChanged(Location loc) {
        Session.onFix(this, loc, true);
    }

    @Override public void onProviderEnabled(String p) {}
    @Override public void onProviderDisabled(String p) { Session.message("GPS kapatıldı! Kayıt için konumu açın."); }
    @Override public void onStatusChanged(String p, int s, Bundle b) {}
}
