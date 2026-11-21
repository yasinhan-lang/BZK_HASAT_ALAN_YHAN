package com.yhan.hasat;

import android.content.Context;
import android.location.Location;
import android.os.SystemClock;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Süren ölçümün durumu (uygulama içinde tek). Bütün çağrılar ana iş parçacığından yapılır. */
final class Session {
    private Session() {}

    static final int IDLE = 0, REC = 1, PAUSED = 2;

    interface Listener {
        void onSessionChanged();
        void onMessage(String msg);
        void onFinished(String id);
    }

    private static final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();

    static int state = IDLE;
    static int mode = Prefs.YAYA;
    static boolean cornerMode;
    static double tabla = 6;
    static long startMs, activeMs, resumedAt;
    static boolean autoFinish = true;

    /** {enlem, boylam, doğruluk, zaman(sn)} */
    static final List<double[]> track = new ArrayList<>();
    /** {enlem, boylam, doğruluk, örnek sayısı} */
    static final List<double[]> corners = new ArrayList<>();
    static Geo.Proj proj;
    static final List<double[]> trackXY = new ArrayList<>();
    static final List<double[]> cornerXY = new ArrayList<>();
    static double pathLen, maxFromStart;

    // başlangıç noktası: hareket edene kadar konumların ağırlıklı ortalaması
    private static double anchorW, anchorLa, anchorLo, anchorAcc;
    private static int anchorN;

    // başlangıca dönüş
    static boolean inZone;
    private static double zoneMin;
    private static long zoneSince;
    /** Otomatik bitirilen son ölçüm (ekran kapalıyken bitmişse açılışta gösterilir). */
    static String pendingResultId;

    // anlık GNSS bilgisi
    static Location last;
    static long lastFixAt;
    static int satsUsed, satsVisible;
    static boolean dualFreq;
    static int rejected;
    private static int jumpCount;
    private static final double[] recentAcc = new double[25];
    private static int recentN, recentI;

    // köşe ortalaması
    static boolean averaging;
    static long avgUntil;
    private static final List<double[]> avgBuf = new ArrayList<>();

    static double maxAcc = 8;
    static int avgSec = 10;

    static void addListener(Listener l) { listeners.addIfAbsent(l); }
    static void removeListener(Listener l) { listeners.remove(l); }
    static boolean hasListeners() { return !listeners.isEmpty(); }

    static void changed() { for (Listener l : listeners) l.onSessionChanged(); }
    static void message(String m) { for (Listener l : listeners) l.onMessage(m); }

    /** Tabla payı eklenecek mi (yalnızca biçerdöver). */
    static double margin() { return mode == Prefs.BICERDOVER ? tabla / 2 : 0; }

    // ------------------------------------------------------------------ yaşam döngüsü

    static void begin(Context c, int m) {
        reset();
        mode = m;
        cornerMode = m == Prefs.KOSE;
        tabla = Prefs.tabla(c);
        maxAcc = Prefs.maxAcc(c);
        avgSec = Prefs.avgSec(c);
        autoFinish = Prefs.autoFinish(c);
        state = REC;
        startMs = System.currentTimeMillis();
        resumedAt = SystemClock.elapsedRealtime();
        // hazır, hassas bir anlık konum varsa onu başlangıç olarak hemen al
        if (!cornerMode && last != null && SystemClock.elapsedRealtime() - lastFixAt < 3000
                && last.hasAccuracy() && last.getAccuracy() <= maxAcc) {
            addTrack(c, last);
        }
        save(c);
        changed();
    }

    static void pause(Context c) {
        if (state != REC) return;
        activeMs += SystemClock.elapsedRealtime() - resumedAt;
        state = PAUSED;
        averaging = false;
        inZone = false;
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
        averaging = false;
        avgBuf.clear();
        activeMs = 0;
        rejected = 0;
        jumpCount = 0;
        anchorW = 0;
        anchorN = 0;
        inZone = false;
        recentN = 0;
        recentI = 0;
    }

    // ------------------------------------------------------------------ konum

    /** @param record true ise (kayıt servisi) iz kaydına alınır; false ise yalnızca gösterim. */
    static void onFix(Context c, Location loc, boolean record) {
        last = loc;
        lastFixAt = SystemClock.elapsedRealtime();
        if (record && state == REC) {
            if (!loc.hasAccuracy() || loc.getAccuracy() > maxAcc || worseThanRecent(loc.getAccuracy())) {
                rejected++;
            } else {
                noteAcc(loc.getAccuracy());
                if (averaging) avgBuf.add(new double[]{loc.getLatitude(), loc.getLongitude(), loc.getAccuracy()});
                if (!cornerMode) addTrack(c, loc);
            }
            tick(c);
        }
        changed();
    }

    /** Son konumların ortanca doğruluğunun çok altında kalan (ani bozulan) konumu ele. */
    private static boolean worseThanRecent(double acc) {
        if (recentN < 8) return false;
        double[] a = Arrays.copyOf(recentAcc, recentN);
        Arrays.sort(a);
        double med = a[recentN / 2];
        return acc > Math.max(4.0, med * 2.0);
    }

    private static void noteAcc(double acc) {
        recentAcc[recentI] = acc;
        recentI = (recentI + 1) % recentAcc.length;
        if (recentN < recentAcc.length) recentN++;
    }

    private static void addTrack(Context c, Location loc) {
        double acc = loc.getAccuracy();
        double[] q = {loc.getLatitude(), loc.getLongitude(), acc, loc.getTime() / 1000.0};
        if (proj == null) proj = new Geo.Proj(q[0], q[1]);
        double[] xy = proj.fwd(q[0], q[1]);

        if (!track.isEmpty()) {
            double[] p = track.get(track.size() - 1);
            double[] pxy = trackXY.get(trackXY.size() - 1);
            double d = Geo.dist(pxy, xy), dt = Math.max(0.5, q[3] - p[3]);

            // henüz yola çıkılmadı: başlangıç noktasını ortalamayla iyileştir
            if (track.size() == 1 && d < Math.max(3.0, acc)) {
                double w = 1.0 / Math.max(0.25, acc * acc);
                anchorLa += q[0] * w;
                anchorLo += q[1] * w;
                anchorAcc += acc;
                anchorW += w;
                anchorN++;
                double[] a = {anchorLa / anchorW, anchorLo / anchorW, anchorAcc / anchorN, q[3]};
                track.set(0, a);
                trackXY.set(0, proj.fwd(a[0], a[1]));
                return;
            }
            // durunca oluşan titremeyi alma
            if (d < 0.7) return;
            if (loc.hasSpeed() && loc.getSpeed() < 0.3f && d < 2.5) return;
            // ani sıçrama (yansıma vb.)
            if (d / dt > 15 && jumpCount < 5) { jumpCount++; return; }
            jumpCount = 0;
            pathLen += d;
        } else {
            double w = 1.0 / Math.max(0.25, acc * acc);
            anchorLa = q[0] * w;
            anchorLo = q[1] * w;
            anchorAcc = acc;
            anchorW = w;
            anchorN = 1;
        }
        track.add(q);
        trackXY.add(xy);
        double fs = Geo.dist(xy, trackXY.get(0));
        maxFromStart = Math.max(maxFromStart, fs);
        checkReturn(c, fs, acc);
        if (state == REC && track.size() % 15 == 0) save(c);
    }

    /** Başlangıca dönüş: bölgeye girince en yakın geçişi bekle, sonra bitir. */
    private static void checkReturn(Context c, double fs, double acc) {
        boolean bic = mode == Prefs.BICERDOVER;
        double minPath = bic ? 80 : 25, minAway = bic ? 40 : 8;
        double zone = bic ? Math.max(10, tabla * 1.5) : Math.min(10, Math.max(4, acc * 1.5));
        if (pathLen < minPath || maxFromStart < minAway) return;
        long now = SystemClock.elapsedRealtime();
        if (!inZone) {
            if (fs < zone) {
                inZone = true;
                zoneMin = fs;
                zoneSince = now;
                Notif.beep(c);
                if (!autoFinish) {
                    Notif.returned(c);
                    message("Başlangıç noktasına döndünüz. Alan için BİTİR'e veya ilk noktaya dokunun.");
                }
            }
            return;
        }
        if (!autoFinish) {
            if (fs > zone * 2) inZone = false;
            return;
        }
        zoneMin = Math.min(zoneMin, fs);
        boolean closeEnough = fs < 1.5;
        boolean movingAway = fs > zoneMin + 2.0;
        boolean waited = now - zoneSince > 8000;
        if (closeEnough || movingAway || waited || fs > zone) finish(c, true);
    }

    /** Başlangıca olan mesafe (m) ya da -1. */
    static double distToStart() {
        if (trackXY.size() < 2) return -1;
        return Geo.dist(trackXY.get(trackXY.size() - 1), trackXY.get(0));
    }

    /** Alan hesabı için yeterli veri var mı? */
    static boolean canFinish() {
        return cornerMode ? corners.size() >= 3 : (track.size() >= 8 && pathLen >= 15);
    }

    /** Ölçümü kaydet, servisi durdur, sonucu bildir. */
    static String finish(Context c, boolean auto) {
        if (state == IDLE || !canFinish()) return null;
        try {
            if (state == REC) {
                activeMs += SystemClock.elapsedRealtime() - resumedAt;
                state = PAUSED;
            }
            Store.Olcum o = Store.fromSession(c, "");
            TrackService.stop(c);
            clearSaved(c);
            reset();
            pendingResultId = o.id;
            Notif.finished(c, o, auto);
            for (Listener l : listeners) l.onFinished(o.id);
            changed();
            return o.id;
        } catch (Exception e) {
            message("Kaydedilemedi: " + e.getMessage());
            return null;
        }
    }

    // ------------------------------------------------------------------ köşe noktası

    static void startCorner() {
        if (state != REC || averaging) return;
        avgBuf.clear();
        averaging = true;
        avgUntil = SystemClock.elapsedRealtime() + avgSec * 1000L;
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
                averaging = true;
                avgUntil = SystemClock.elapsedRealtime() + 3000;
                return;
            }
            avgBuf.clear();
            message("Köşe alınamadı: yeterli hassas konum gelmedi. Tekrar deneyin.");
            changed();
            return;
        }
        // en kötü %20'yi at, kalanların ağırlıklı ortalaması
        avgBuf.sort((a, b) -> Double.compare(a[2], b[2]));
        int n = Math.max(3, (int) Math.ceil(avgBuf.size() * 0.8));
        double sw = 0, la = 0, lo = 0, acc = 0;
        for (int i = 0; i < n; i++) {
            double[] q = avgBuf.get(i);
            double w = 1.0 / Math.max(0.25, q[2] * q[2]);
            sw += w; la += q[0] * w; lo += q[1] * w; acc += q[2];
        }
        double[] k = {la / sw, lo / sw, acc / n, n};
        corners.add(k);
        if (proj == null) proj = new Geo.Proj(k[0], k[1]);
        cornerXY.add(proj.fwd(k[0], k[1]));
        avgBuf.clear();
        Notif.beep(c);
        message("Köşe " + corners.size() + " kaydedildi (± " + Fmt.num(k[2], 1) + " m).");
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

    /** Kayıt sırasında yaklaşık alan (m²). */
    static double liveArea() {
        List<double[]> p = cornerMode ? cornerXY : trackXY;
        if (p.size() < 3) return 0;
        double a = Geo.quickArea(p);
        double d = margin();
        if (d <= 0) return a;
        return a + Geo.perimeter(p, true) * d + 4 * d * d;
    }

    // ------------------------------------------------------------------ yarım kalan ölçüm

    static File activeFile(Context c) { return new File(c.getFilesDir(), "aktif.json"); }

    static void save(Context c) {
        try {
            JSONObject o = new JSONObject();
            o.put("mod", mode);
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
            mode = o.has("mod") ? o.optInt("mod") : (o.optBoolean("kose") ? Prefs.KOSE : Prefs.BICERDOVER);
            cornerMode = mode == Prefs.KOSE;
            tabla = o.optDouble("tabla", Prefs.tabla(c));
            maxAcc = Prefs.maxAcc(c);
            avgSec = Prefs.avgSec(c);
            autoFinish = Prefs.autoFinish(c);
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
}
