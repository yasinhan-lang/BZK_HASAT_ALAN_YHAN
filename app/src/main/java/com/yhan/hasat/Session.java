package com.yhan.hasat;

import android.content.Context;
import android.location.Location;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Süren ölçümün durumu (uygulama içinde tek). Bütün çağrılar ana iş parçacığından yapılır. */
final class Session {
    private Session() {}

    static final int IDLE = 0, REC = 1, PAUSED = 2;

    interface Listener {
        void onSessionChanged();
        void onMessage(String msg);
    }

    private static final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private static final Handler main = new Handler(Looper.getMainLooper());

    static int state = IDLE;
    static boolean cornerMode;
    static double tabla = 6;
    static long startMs, activeMs, resumedAt;

    /** {enlem, boylam, doğruluk, zaman(sn)} */
    static final List<double[]> track = new ArrayList<>();
    /** {enlem, boylam, doğruluk, örnek sayısı} */
    static final List<double[]> corners = new ArrayList<>();
    /** Projeksiyon ve izin metrik karşılığı (canlı çizim için). */
    static Geo.Proj proj;
    static final List<double[]> trackXY = new ArrayList<>();
    static final List<double[]> cornerXY = new ArrayList<>();
    static double pathLen, maxFromStart;
    static boolean returnNotified;

    // anlık GNSS bilgisi
    static Location last;
    static long lastFixAt;
    static int satsUsed, satsVisible;
    static boolean dualFreq;
    static int rejected;
    private static int jumpCount;

    // köşe ortalaması
    static boolean averaging;
    static long avgUntil;
    static int avgTotalMs;
    private static final List<double[]> avgBuf = new ArrayList<>();

    static double maxAcc = 10;
    static int avgSec = 10;

    static void addListener(Listener l) { listeners.addIfAbsent(l); }
    static void removeListener(Listener l) { listeners.remove(l); }

    static void changed() {
        for (Listener l : listeners) l.onSessionChanged();
    }

    static void message(String m) {
        for (Listener l : listeners) l.onMessage(m);
    }

    // ------------------------------------------------------------------ yaşam döngüsü

    static void begin(Context c, boolean corner) {
        reset();
        cornerMode = corner;
        tabla = Prefs.tabla(c);
        maxAcc = Prefs.maxAcc(c);
        avgSec = Prefs.avgSec(c);
        state = REC;
        startMs = System.currentTimeMillis();
        resumedAt = SystemClock.elapsedRealtime();
        save(c);
        changed();
    }

    static void pause(Context c) {
        if (state != REC) return;
        activeMs += SystemClock.elapsedRealtime() - resumedAt;
        state = PAUSED;
        averaging = false;
        save(c);
        changed();
    }

    static void resume(Context c) {
        if (state != PAUSED) return;
        state = REC;
        resumedAt = SystemClock.elapsedRealtime();
        jumpCount = 0;
        changed();
    }

    static long elapsedMs() {
        long t = activeMs;
        if (state == REC) t += SystemClock.elapsedRealtime() - resumedAt;
        return t;
    }

    static void reset() {
        state = IDLE;
        track.clear();
        corners.clear();
        trackXY.clear();
        cornerXY.clear();
        proj = null;
        pathLen = 0;
        maxFromStart = 0;
        returnNotified = false;
        averaging = false;
        avgBuf.clear();
        activeMs = 0;
        rejected = 0;
        jumpCount = 0;
    }

    // ------------------------------------------------------------------ konum

    /** @param record true ise (kayıt servisi) iz kaydına alınır; false ise yalnızca gösterim. */
    static void onFix(Context c, Location loc, boolean record) {
        last = loc;
        lastFixAt = SystemClock.elapsedRealtime();
        if (record && state == REC) {
            if (!loc.hasAccuracy() || loc.getAccuracy() > maxAcc) {
                rejected++;
            } else {
                if (averaging) avgBuf.add(new double[]{loc.getLatitude(), loc.getLongitude(), loc.getAccuracy()});
                addTrack(c, loc);
            }
            tick(c);
        }
        changed();
    }

    private static void addTrack(Context c, Location loc) {
        double[] q = {loc.getLatitude(), loc.getLongitude(), loc.getAccuracy(), loc.getTime() / 1000.0};
        if (!track.isEmpty()) {
            double[] p = track.get(track.size() - 1);
            float[] r = new float[1];
            Location.distanceBetween(p[0], p[1], q[0], q[1], r);
            double d = r[0], dt = Math.max(0.5, q[3] - p[3]);
            // durunca oluşan titremeyi alma
            if (d < 0.8) return;
            if (loc.hasSpeed() && loc.getSpeed() < 0.4f && d < 3) return;
            // ani sıçrama (yansıma vb.)
            if (d / dt > 15 && jumpCount < 5) { jumpCount++; return; }
            jumpCount = 0;
            pathLen += d;
        }
        if (proj == null) proj = new Geo.Proj(q[0], q[1]);
        track.add(q);
        double[] xy = proj.fwd(q[0], q[1]);
        trackXY.add(xy);
        double fs = Geo.dist(xy, trackXY.get(0));
        maxFromStart = Math.max(maxFromStart, fs);
        if (!cornerMode && !returnNotified && pathLen > 80 && maxFromStart > 40 && fs < Math.max(10, tabla * 1.5)) {
            returnNotified = true;
            Notif.returned(c);
            message("Başlangıç noktasına döndünüz. Tur tamamsa BİTİR'e basın.");
        }
        if (track.size() % 20 == 0) save(c);
    }

    /** Başlangıca olan mesafe (m) ya da -1. */
    static double distToStart() {
        if (trackXY.size() < 2) return -1;
        return Geo.dist(trackXY.get(trackXY.size() - 1), trackXY.get(0));
    }

    // ------------------------------------------------------------------ köşe noktası

    static void startCorner() {
        if (state != REC || averaging) return;
        avgBuf.clear();
        averaging = true;
        avgTotalMs = avgSec * 1000;
        avgUntil = SystemClock.elapsedRealtime() + avgTotalMs;
        changed();
    }

    static void cancelCorner() {
        averaging = false;
        avgBuf.clear();
        changed();
    }

    /** Ortalama süresi dolduysa köşeyi oluştur. Saniyede bir çağrılır. */
    static void tick(Context c) {
        if (!averaging || SystemClock.elapsedRealtime() < avgUntil) return;
        averaging = false;
        if (avgBuf.size() < 3) {
            if (SystemClock.elapsedRealtime() - avgUntil < 20000 && !avgBuf.isEmpty()) {
                // biraz daha bekle
                averaging = true;
                avgUntil = SystemClock.elapsedRealtime() + 3000;
                return;
            }
            avgBuf.clear();
            message("Köşe alınamadı: yeterli hassas konum gelmedi. Tekrar deneyin.");
            changed();
            return;
        }
        double sw = 0, la = 0, lo = 0, acc = 0;
        for (double[] q : avgBuf) {
            double w = 1.0 / Math.max(0.5, q[2] * q[2]);
            sw += w; la += q[0] * w; lo += q[1] * w; acc += q[2];
        }
        double[] k = {la / sw, lo / sw, acc / avgBuf.size(), avgBuf.size()};
        corners.add(k);
        if (proj == null) proj = new Geo.Proj(k[0], k[1]);
        cornerXY.add(proj.fwd(k[0], k[1]));
        avgBuf.clear();
        Notif.beep(c);
        message("Köşe " + corners.size() + " kaydedildi (±" + String.format(java.util.Locale.US, "%.1f", k[2]) + " m).");
        save(c);
        changed();
    }

    static void undoCorner(Context c) {
        if (corners.isEmpty()) return;
        corners.remove(corners.size() - 1);
        cornerXY.remove(cornerXY.size() - 1);
        save(c);
        changed();
    }

    /** Kayıt sırasında yaklaşık alan (m²): iz/köşe çokgeni + tabla payı (Steiner yaklaşımı). */
    static double liveArea(boolean withMargin) {
        List<double[]> p = cornerMode ? cornerXY : trackXY;
        if (p.size() < 3) return 0;
        double a = Geo.quickArea(p);
        if (!withMargin) return a;
        double d = tabla / 2;
        return a + Geo.perimeter(p, true) * d + 4 * d * d;
    }

    // ------------------------------------------------------------------ yarım kalan ölçüm

    static File activeFile(Context c) { return new File(c.getFilesDir(), "aktif.json"); }

    static void save(Context c) {
        try {
            JSONObject o = new JSONObject();
            o.put("kose", cornerMode);
            o.put("tabla", tabla);
            o.put("start", startMs);
            o.put("activeMs", elapsedMs());
            o.put("track", Store.arr(track));
            o.put("corners", Store.arr(corners));
            File f = activeFile(c);
            File tmp = new File(f.getPath() + ".tmp");
            try (FileOutputStream os = new FileOutputStream(tmp)) {
                os.write(o.toString().getBytes(StandardCharsets.UTF_8));
            }
            //noinspection ResultOfMethodCallIgnored
            tmp.renameTo(f);
        } catch (Exception ignored) {
        }
    }

    static void clearSaved(Context c) {
        //noinspection ResultOfMethodCallIgnored
        activeFile(c).delete();
    }

    /** Uygulama kapanmışsa kaydedilmiş yarım ölçümü geri yükle (duraklatılmış olarak). */
    static boolean restore(Context c) {
        File f = activeFile(c);
        if (!f.exists() || state != IDLE) return false;
        try {
            JSONObject o = new JSONObject(Store.read(f));
            reset();
            cornerMode = o.optBoolean("kose");
            tabla = o.optDouble("tabla", Prefs.tabla(c));
            maxAcc = Prefs.maxAcc(c);
            avgSec = Prefs.avgSec(c);
            startMs = o.optLong("start", System.currentTimeMillis());
            activeMs = o.optLong("activeMs");
            Store.fill(o.optJSONArray("track"), track);
            Store.fill(o.optJSONArray("corners"), corners);
            if (track.isEmpty() && corners.isEmpty()) { clearSaved(c); return false; }
            double[] f0 = !track.isEmpty() ? track.get(0) : corners.get(0);
            proj = new Geo.Proj(f0[0], f0[1]);
            for (int i = 0; i < track.size(); i++) {
                double[] xy = proj.fwd(track.get(i)[0], track.get(i)[1]);
                if (i > 0) pathLen += Geo.dist(xy, trackXY.get(i - 1));
                trackXY.add(xy);
                maxFromStart = Math.max(maxFromStart, Geo.dist(xy, trackXY.get(0)));
            }
            for (double[] k : corners) cornerXY.add(proj.fwd(k[0], k[1]));
            state = PAUSED;
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    static void post(Runnable r) { main.post(r); }
}
