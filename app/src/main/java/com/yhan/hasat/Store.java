package com.yhan.hasat;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Ölçümlerin kaydı ve dışa aktarma (İndirilenler/BozokluHAN Alan Olcer). */
final class Store {
    private Store() {}

    static final String FOLDER = "BozokluHAN Alan Olcer";

    /** Kayıtlı bir ölçüm. */
    static final class Olcum {
        String id, name = "";
        long created;
        boolean corner, margin = true;
        int mode = Prefs.YAYA;
        double tabla = 6, area = -1;
        final List<double[]> track = new ArrayList<>();
        final List<double[]> corners = new ArrayList<>();
    }

    // ------------------------------------------------------------------ JSON yardımcıları

    static JSONArray arr(List<double[]> l) throws JSONException {
        JSONArray a = new JSONArray();
        for (double[] q : l) {
            JSONArray r = new JSONArray();
            for (double v : q) r.put(v);
            a.put(r);
        }
        return a;
    }

    static void fill(JSONArray a, List<double[]> out) {
        if (a == null) return;
        for (int i = 0; i < a.length(); i++) {
            JSONArray r = a.optJSONArray(i);
            if (r == null) continue;
            double[] q = new double[r.length()];
            for (int k = 0; k < q.length; k++) q[k] = r.optDouble(k);
            out.add(q);
        }
    }

    static String read(File f) throws Exception {
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] b = new byte[(int) f.length()];
            int o = 0;
            while (o < b.length) {
                int n = in.read(b, o, b.length - o);
                if (n < 0) break;
                o += n;
            }
            return new String(b, 0, o, StandardCharsets.UTF_8);
        }
    }

    // ------------------------------------------------------------------ ölçümler

    static File dir(Context c) {
        File d = new File(c.getFilesDir(), "olcumler");
        //noinspection ResultOfMethodCallIgnored
        d.mkdirs();
        return d;
    }

    static void save(Context c, Olcum o) throws Exception {
        JSONObject j = new JSONObject();
        j.put("id", o.id);
        j.put("ad", o.name);
        j.put("tarih", o.created);
        j.put("kose", o.corner);
        j.put("mod", o.mode);
        j.put("pay", o.margin);
        j.put("tabla", o.tabla);
        j.put("alan", o.area);
        j.put("track", arr(o.track));
        j.put("corners", arr(o.corners));
        File f = new File(dir(c), o.id + ".json");
        File tmp = new File(f.getPath() + ".tmp");
        try (FileOutputStream os = new FileOutputStream(tmp)) {
            os.write(j.toString().getBytes(StandardCharsets.UTF_8));
        }
        //noinspection ResultOfMethodCallIgnored
        tmp.renameTo(f);
    }

    static Olcum load(Context c, String id) {
        try {
            JSONObject j = new JSONObject(read(new File(dir(c), id + ".json")));
            Olcum o = new Olcum();
            o.id = j.optString("id", id);
            o.name = j.optString("ad", "");
            o.created = j.optLong("tarih");
            o.corner = j.optBoolean("kose");
            o.mode = j.optInt("mod", o.corner ? Prefs.KOSE : Prefs.BICERDOVER);
            o.margin = j.optBoolean("pay", true);
            o.tabla = j.optDouble("tabla", 6);
            o.area = j.optDouble("alan", -1);
            fill(j.optJSONArray("track"), o.track);
            fill(j.optJSONArray("corners"), o.corners);
            return o;
        } catch (Exception e) {
            return null;
        }
    }

    /** Yeniden eskiye ölçüm kimlikleri. */
    static List<String> list(Context c) {
        List<String> ids = new ArrayList<>();
        File[] fs = dir(c).listFiles();
        if (fs == null) return ids;
        for (File f : fs) {
            String n = f.getName();
            if (n.endsWith(".json")) ids.add(n.substring(0, n.length() - 5));
        }
        String[] a = ids.toArray(new String[0]);
        Arrays.sort(a);
        ids.clear();
        for (int i = a.length - 1; i >= 0; i--) ids.add(a[i]);
        return ids;
    }

    static void delete(Context c, String id) {
        //noinspection ResultOfMethodCallIgnored
        new File(dir(c), id + ".json").delete();
    }

    /** Süren oturumdan yeni ölçüm oluştur ve kaydet. */
    static Olcum fromSession(Context c, String name) throws Exception {
        Olcum o = new Olcum();
        o.created = Session.startMs;
        o.id = Fmt.fileStamp(o.created) + "_" + Long.toString(System.currentTimeMillis() % 100000, 36);
        o.name = name;
        o.corner = Session.cornerMode;
        o.mode = Session.mode;
        o.margin = Session.mode == Prefs.BICERDOVER;
        o.tabla = Session.tabla;
        o.track.addAll(Session.track);
        o.corners.addAll(Session.corners);
        Geo.Result r = Geo.compute(o.track, o.corners, o.corner, o.margin ? o.tabla / 2 : 0);
        o.area = r.ok ? r.fieldArea : -1;
        save(c, o);
        return o;
    }

    // ------------------------------------------------------------------ dışa aktarma

    private static String safe(String s) {
        String t = s.trim().replaceAll("[\\\\/:*?\"<>|\\n\\r\\t]", "_");
        return t.isEmpty() ? "Tarla" : t;
    }

    static String baseName(Olcum o) {
        return safe(o.name.isEmpty() ? "Tarla" : o.name) + "_" + Fmt.fileStamp(o.created);
    }

    private static Uri create(Context c, String fileName, String mime) {
        ContentValues v = new ContentValues();
        v.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
        v.put(MediaStore.MediaColumns.MIME_TYPE, mime);
        v.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" + FOLDER);
        return c.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
    }

    static Uri saveText(Context c, String fileName, String mime, String text) throws Exception {
        Uri u = create(c, fileName, mime);
        if (u == null) throw new Exception("Dosya oluşturulamadı");
        ContentResolver cr = c.getContentResolver();
        try (OutputStream os = cr.openOutputStream(u)) {
            if (os == null) throw new Exception("Dosya açılamadı");
            os.write(text.getBytes(StandardCharsets.UTF_8));
        }
        return u;
    }

    static Uri savePng(Context c, String fileName, Bitmap b) throws Exception {
        Uri u = create(c, fileName, "image/png");
        if (u == null) throw new Exception("Dosya oluşturulamadı");
        try (OutputStream os = c.getContentResolver().openOutputStream(u)) {
            if (os == null) throw new Exception("Dosya açılamadı");
            b.compress(Bitmap.CompressFormat.PNG, 100, os);
        }
        return u;
    }

    static String modeName(int m) {
        return m == Prefs.KOSE ? "Köşe noktaları" : m == Prefs.BICERDOVER ? "Biçerdöver (kenar turu)" : "Yaya (yürüyerek)";
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static void coords(StringBuilder sb, List<double[]> ll, boolean close) {
        for (double[] q : ll) sb.append(String.format(Locale.US, "%.9f,%.9f,0 ", q[1], q[0]));
        if (close && !ll.isEmpty()) sb.append(String.format(Locale.US, "%.9f,%.9f,0", ll.get(0)[1], ll.get(0)[0]));
    }

    static String kml(Olcum o, Geo.Result r) {
        String title = o.name.isEmpty() ? "Tarla" : o.name;
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
          .append("<kml xmlns=\"http://www.opengis.net/kml/2.2\"><Document>\n")
          .append("<name>").append(esc(title)).append(" - ").append(Fmt.donum(r.fieldArea)).append(" dönüm</name>\n")
          .append("<Style id=\"tarla\"><LineStyle><color>ff26a5e0</color><width>3</width></LineStyle>")
          .append("<PolyStyle><color>4026a5e0</color></PolyStyle></Style>\n")
          .append("<Style id=\"iz\"><LineStyle><color>ff3a5c1f</color><width>2</width></LineStyle>")
          .append("<PolyStyle><fill>0</fill></PolyStyle></Style>\n")
          .append("<Style id=\"yol\"><LineStyle><color>a0ffffff</color><width>1.5</width></LineStyle></Style>\n");
        sb.append("<Placemark><name>Tarla sınırı · ").append(Fmt.m2(r.fieldArea)).append(" m²</name>")
          .append("<description>").append(esc(Fmt.donum(r.fieldArea) + " dönüm · " + Fmt.date(o.created))).append("</description>")
          .append("<styleUrl>#tarla</styleUrl><Polygon><outerBoundaryIs><LinearRing><coordinates>");
        coords(sb, r.fieldLL, true);
        sb.append("</coordinates></LinearRing></outerBoundaryIs></Polygon></Placemark>\n");
        if (r.margin > 0) {
            sb.append("<Placemark><name>Makine izi (tabla payı hariç) · ").append(Fmt.m2(r.izArea)).append(" m²</name>")
              .append("<styleUrl>#iz</styleUrl><Polygon><outerBoundaryIs><LinearRing><coordinates>");
            coords(sb, r.izLL, true);
            sb.append("</coordinates></LinearRing></outerBoundaryIs></Polygon></Placemark>\n");
        }
        if (o.track.size() > 1) {
            sb.append("<Placemark><name>GPS yolu</name><styleUrl>#yol</styleUrl><LineString><coordinates>");
            coords(sb, o.track, false);
            sb.append("</coordinates></LineString></Placemark>\n");
        }
        for (int i = 0; i < o.corners.size(); i++) {
            double[] k = o.corners.get(i);
            sb.append(String.format(Locale.US, "<Placemark><name>K%d</name><Point><coordinates>%.9f,%.9f,0</coordinates></Point></Placemark>\n",
                    i + 1, k[1], k[0]));
        }
        sb.append("</Document></kml>\n");
        return sb.toString();
    }

    static String report(Olcum o, Geo.Result r) {
        StringBuilder sb = new StringBuilder();
        sb.append("BozokluHAN ALAN ÖLÇER\r\n");
        sb.append("=====================\r\n");
        sb.append("Tarla          : ").append(o.name.isEmpty() ? "-" : o.name).append("\r\n");
        sb.append("Tarih          : ").append(Fmt.date(o.created)).append("\r\n");
        sb.append("Yöntem         : ").append(modeName(o.mode)).append("\r\n");
        sb.append("TARLA ALANI    : ").append(Fmt.m2(r.fieldArea)).append(" m²  =  ").append(Fmt.donum(r.fieldArea)).append(" dönüm\r\n");
        sb.append("Tahmini hata   : ± ").append(Fmt.num(r.sigma, 0)).append(" m²\r\n");
        sb.append("Makine izi alanı: ").append(Fmt.m2(r.izArea)).append(" m²\r\n");
        sb.append("Tabla payı     : ").append(r.margin > 0 ? Fmt.num(r.margin, 2) + " m (tabla " + Fmt.num(o.tabla, 2) + " m)" : "eklenmedi").append("\r\n");
        sb.append("Çevre          : ").append(Fmt.num(r.fieldPerim, 1)).append(" m\r\n");
        if (r.closure >= 0) sb.append("Kapanma hatası : ").append(Fmt.num(r.closure, 1)).append(" m\r\n");
        sb.append("Ort. doğruluk  : ± ").append(Fmt.num(r.meanAcc, 1)).append(" m\r\n");
        for (String n : r.notes) sb.append("Not: ").append(n).append("\r\n");
        sb.append("\r\nTarla sınırı köşe/nokta koordinatları (WGS84 enlem, boylam):\r\n");
        for (int i = 0; i < r.fieldLL.size(); i++) {
            double[] q = r.fieldLL.get(i);
            sb.append(String.format(Locale.US, "%d\t%.8f\t%.8f\r\n", i + 1, q[0], q[1]));
        }
        if (!o.corners.isEmpty()) {
            sb.append("\r\nÖlçülen köşe noktaları (enlem, boylam, ±m, örnek):\r\n");
            for (int i = 0; i < o.corners.size(); i++) {
                double[] k = o.corners.get(i);
                sb.append(String.format(Locale.US, "K%d\t%.8f\t%.8f\t%.1f\t%d\r\n", i + 1, k[0], k[1], k[2], (int) k[3]));
            }
        }
        sb.append("\r\n© 2026 Yasin HAN · Tüm hakları saklıdır.\r\n");
        return sb.toString();
    }
}
