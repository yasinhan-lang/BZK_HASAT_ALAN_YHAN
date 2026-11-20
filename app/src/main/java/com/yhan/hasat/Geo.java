package com.yhan.hasat;

import java.util.ArrayList;
import java.util.List;

/**
 * Alan hesabının bütün matematiği (Android'e bağımlı değil).
 *
 * - WGS84/GRS80 enlem-boylam → tarlanın ortasından geçen yerel Transversal Merkatör (k0 = 1),
 *   böylece alan elipsoit üzerindeki yatay alana (kadastro alanına) eşit çıkar.
 * - İz modunda: tur kapanması bulunur, kapanma hatası kenarlara dağıtılır (pusula kuralı).
 * - Köşe modunda: ortalanmış köşe noktaları sırayla birleştirilir.
 * - Tabla payı: makine merkezinden geçen iz, tabla genişliğinin yarısı kadar dışa ötelenir.
 * - Alan: Gauss (koordinatlarla alan) formülü.
 */
public final class Geo {
    private Geo() {}

    // ------------------------------------------------------------------ projeksiyon

    /** Yerel Transversal Merkatör, GRS80, k0 = 1; dilim ortası ve başlangıç enlemi tarlanın ortası. */
    public static final class Proj {
        static final double A = 6378137.0, F = 1 / 298.257222101;
        static final double E2 = F * (2 - F), EP2 = E2 / (1 - E2);
        final double lat0, lon0, m0;

        public Proj(double lat0Deg, double lon0Deg) {
            lat0 = Math.toRadians(lat0Deg);
            lon0 = Math.toRadians(lon0Deg);
            m0 = merid(lat0);
        }

        static double merid(double p) {
            double e4 = E2 * E2, e6 = e4 * E2;
            return A * ((1 - E2 / 4 - 3 * e4 / 64 - 5 * e6 / 256) * p
                    - (3 * E2 / 8 + 3 * e4 / 32 + 45 * e6 / 1024) * Math.sin(2 * p)
                    + (15 * e4 / 256 + 45 * e6 / 1024) * Math.sin(4 * p)
                    - (35 * e6 / 3072) * Math.sin(6 * p));
        }

        /** enlem, boylam (derece) → {x doğu, y kuzey} metre */
        public double[] fwd(double latDeg, double lonDeg) {
            double p = Math.toRadians(latDeg), l = Math.toRadians(lonDeg);
            double s = Math.sin(p), c = Math.cos(p), t = Math.tan(p);
            double n = A / Math.sqrt(1 - E2 * s * s);
            double T = t * t, C = EP2 * c * c, a = (l - lon0) * c;
            double a2 = a * a, a3 = a2 * a, a4 = a3 * a, a5 = a4 * a, a6 = a5 * a;
            double x = n * (a + (1 - T + C) * a3 / 6 + (5 - 18 * T + T * T + 72 * C - 58 * EP2) * a5 / 120);
            double y = merid(p) - m0 + n * t * (a2 / 2 + (5 - T + 9 * C + 4 * C * C) * a4 / 24
                    + (61 - 58 * T + T * T + 600 * C - 330 * EP2) * a6 / 720);
            return new double[]{x, y};
        }

        /** {x, y} metre → {enlem, boylam} derece */
        public double[] inv(double x, double y) {
            double e4 = E2 * E2, e6 = e4 * E2;
            double m = m0 + y;
            double mu = m / (A * (1 - E2 / 4 - 3 * e4 / 64 - 5 * e6 / 256));
            double sq = Math.sqrt(1 - E2), e1 = (1 - sq) / (1 + sq);
            double e12 = e1 * e1, e13 = e12 * e1, e14 = e13 * e1;
            double p1 = mu + (3 * e1 / 2 - 27 * e13 / 32) * Math.sin(2 * mu)
                    + (21 * e12 / 16 - 55 * e14 / 32) * Math.sin(4 * mu)
                    + (151 * e13 / 96) * Math.sin(6 * mu)
                    + (1097 * e14 / 512) * Math.sin(8 * mu);
            double s = Math.sin(p1), c = Math.cos(p1), t = Math.tan(p1);
            double C1 = EP2 * c * c, T1 = t * t;
            double n1 = A / Math.sqrt(1 - E2 * s * s);
            double r1 = A * (1 - E2) / Math.pow(1 - E2 * s * s, 1.5);
            double d = x / n1, d2 = d * d, d3 = d2 * d, d4 = d3 * d, d5 = d4 * d, d6 = d5 * d;
            double lat = p1 - (n1 * t / r1) * (d2 / 2 - (5 + 3 * T1 + 10 * C1 - 4 * C1 * C1 - 9 * EP2) * d4 / 24
                    + (61 + 90 * T1 + 298 * C1 + 45 * T1 * T1 - 252 * EP2 - 3 * C1 * C1) * d6 / 720);
            double lon = lon0 + (d - (1 + 2 * T1 + C1) * d3 / 6
                    + (5 - 2 * C1 + 28 * T1 - 3 * C1 * C1 + 8 * EP2 + 24 * T1 * T1) * d5 / 120) / c;
            return new double[]{Math.toDegrees(lat), Math.toDegrees(lon)};
        }
    }

    // ------------------------------------------------------------------ temel geometri

    /** İşaretli alan (saat yönü tersine pozitif). Çokgen kapalı kabul edilir, son nokta ilkini tekrar etmez. */
    public static double signedArea(List<double[]> p) {
        int n = p.size();
        if (n < 3) return 0;
        double s = 0;
        for (int i = 0; i < n; i++) {
            double[] a = p.get(i), b = p.get((i + 1) % n);
            s += a[0] * b[1] - b[0] * a[1];
        }
        return s / 2;
    }

    public static double perimeter(List<double[]> p, boolean closed) {
        double s = 0;
        int n = p.size();
        for (int i = 0; i + 1 < n; i++) s += dist(p.get(i), p.get(i + 1));
        if (closed && n > 2) s += dist(p.get(n - 1), p.get(0));
        return s;
    }

    public static double dist(double[] a, double[] b) {
        return Math.hypot(a[0] - b[0], a[1] - b[1]);
    }

    static double segDist(double[] p, double[] a, double[] b) {
        double dx = b[0] - a[0], dy = b[1] - a[1];
        double l2 = dx * dx + dy * dy;
        if (l2 == 0) return dist(p, a);
        double t = ((p[0] - a[0]) * dx + (p[1] - a[1]) * dy) / l2;
        t = Math.max(0, Math.min(1, t));
        return Math.hypot(p[0] - (a[0] + t * dx), p[1] - (a[1] + t * dy));
    }

    /** Douglas-Peucker sadeleştirme (açık çizgi). */
    static List<double[]> simplify(List<double[]> p, double tol) {
        int n = p.size();
        if (n < 3) return new ArrayList<>(p);
        boolean[] keep = new boolean[n];
        keep[0] = keep[n - 1] = true;
        int[] stack = new int[2 * n + 4];
        int sp = 0;
        stack[sp++] = 0;
        stack[sp++] = n - 1;
        while (sp > 0) {
            int b = stack[--sp], a = stack[--sp];
            double best = -1;
            int bi = -1;
            for (int i = a + 1; i < b; i++) {
                double d = segDist(p.get(i), p.get(a), p.get(b));
                if (d > best) { best = d; bi = i; }
            }
            if (bi >= 0 && best > tol) {
                keep[bi] = true;
                stack[sp++] = a; stack[sp++] = bi;
                stack[sp++] = bi; stack[sp++] = b;
            }
        }
        List<double[]> out = new ArrayList<>();
        for (int i = 0; i < n; i++) if (keep[i]) out.add(p.get(i));
        return out;
    }

    /** Kapalı çokgeni sadeleştir: en uzak iki noktadan ikiye bölünüp ayrı ayrı sadeleştirilir. */
    static List<double[]> simplifyClosed(List<double[]> p, double tol) {
        int n = p.size();
        if (n < 5) return new ArrayList<>(p);
        int far = 0;
        double fd = -1;
        for (int i = 1; i < n; i++) {
            double d = dist(p.get(0), p.get(i));
            if (d > fd) { fd = d; far = i; }
        }
        List<double[]> a = new ArrayList<>(p.subList(0, far + 1));
        List<double[]> b = new ArrayList<>(p.subList(far, n));
        b.add(p.get(0));
        List<double[]> sa = simplify(a, tol), sb = simplify(b, tol);
        List<double[]> out = new ArrayList<>(sa);
        for (int i = 1; i < sb.size() - 1; i++) out.add(sb.get(i));
        return out.size() >= 3 ? out : new ArrayList<>(p);
    }

    /** Neredeyse aynı yerdeki ardışık noktaları ve düz çizgi üzerindeki noktaları temizle. */
    static List<double[]> dedupe(List<double[]> p, double minD) {
        List<double[]> out = new ArrayList<>();
        for (double[] q : p) {
            if (out.isEmpty() || dist(out.get(out.size() - 1), q) >= minD) out.add(q);
        }
        while (out.size() > 3 && dist(out.get(0), out.get(out.size() - 1)) < minD) out.remove(out.size() - 1);
        return out;
    }

    /**
     * Saat yönü tersine (CCW) çokgeni d metre dışa ötele (köşeler sivri / gönye birleşimli).
     * Çok sivri köşelerde gönye boyu 4d ile sınırlanır (pah kırılır).
     */
    static List<double[]> offsetOut(List<double[]> p, double d) {
        int n = p.size();
        List<double[]> out = new ArrayList<>();
        if (n < 3 || d == 0) { out.addAll(p); return out; }
        for (int i = 0; i < n; i++) {
            double[] prev = p.get((i - 1 + n) % n), cur = p.get(i), next = p.get((i + 1) % n);
            double[] n1 = rightNormal(prev, cur), n2 = rightNormal(cur, next);
            double dot = n1[0] * n2[0] + n1[1] * n2[1];
            double cross = n1[0] * n2[1] - n1[1] * n2[0];
            if (1 + dot < 1e-9) { // geri dönüş: iki ayrı nokta
                out.add(new double[]{cur[0] + n1[0] * d, cur[1] + n1[1] * d});
                out.add(new double[]{cur[0] + n2[0] * d, cur[1] + n2[1] * d});
                continue;
            }
            double k = d / (1 + dot);
            double mx = (n1[0] + n2[0]) * k, my = (n1[1] + n2[1]) * k;
            double miter = Math.hypot(mx, my);
            boolean convex = cross > 0; // CCW çokgende dışbükey köşede yön (ve normal) sola döner
            if (miter > 4 * Math.abs(d) && convex) {
                out.add(new double[]{cur[0] + n1[0] * d, cur[1] + n1[1] * d});
                out.add(new double[]{cur[0] + n2[0] * d, cur[1] + n2[1] * d});
            } else if (miter > 4 * Math.abs(d)) {
                double r = 4 * Math.abs(d) / miter;
                out.add(new double[]{cur[0] + mx * r, cur[1] + my * r});
            } else {
                out.add(new double[]{cur[0] + mx, cur[1] + my});
            }
        }
        return out;
    }

    /** a→b doğrultusunun sağ birim normali (CCW çokgende dış taraf). */
    static double[] rightNormal(double[] a, double[] b) {
        double dx = b[0] - a[0], dy = b[1] - a[1];
        double l = Math.hypot(dx, dy);
        if (l == 0) return new double[]{0, 0};
        return new double[]{dy / l, -dx / l};
    }

    static List<double[]> ccw(List<double[]> p) {
        List<double[]> q = new ArrayList<>(p);
        if (signedArea(q) < 0) java.util.Collections.reverse(q);
        return q;
    }

    /** Kenarlar birbirini kesiyor mu (komşu olmayan kenarlar)? */
    static boolean selfIntersects(List<double[]> p) {
        int n = p.size();
        if (n < 4 || n > 4000) return false;
        for (int i = 0; i < n; i++) {
            double[] a = p.get(i), b = p.get((i + 1) % n);
            for (int j = i + 2; j < n; j++) {
                if (i == 0 && j == n - 1) continue;
                double[] c = p.get(j), e = p.get((j + 1) % n);
                if (segX(a, b, c, e)) return true;
            }
        }
        return false;
    }

    static boolean segX(double[] a, double[] b, double[] c, double[] d) {
        double d1 = orient(c, d, a), d2 = orient(c, d, b), d3 = orient(a, b, c), d4 = orient(a, b, d);
        return ((d1 > 0) != (d2 > 0)) && ((d3 > 0) != (d4 > 0)) && d1 != 0 && d2 != 0 && d3 != 0 && d4 != 0;
    }

    static double orient(double[] a, double[] b, double[] c) {
        return (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0]);
    }

    // ------------------------------------------------------------------ tur kapanması

    static final class Loop {
        List<double[]> poly;   // kapanma hatası dağıtılmış çokgen
        double gap;            // kapanma hatası (m)
        int iStart, iEnd;
    }

    /**
     * İzde başlangıca dönülen yeri bul: ilk %25'lik kısımdaki bir nokta ile son %45'lik kısımdaki
     * bir nokta arasındaki en kısa mesafe. Aradaki kısım tur kabul edilir; kalan kapanma açıklığı
     * kat edilen yol oranında kenarlara dağıtılır (poligon dengelemesindeki pusula kuralı).
     */
    static Loop closeLoop(List<double[]> t) {
        int n = t.size();
        double[] s = new double[n];
        for (int i = 1; i < n; i++) s[i] = s[i - 1] + dist(t.get(i - 1), t.get(i));
        double L = s[n - 1];
        int aMax = 0;
        while (aMax + 1 < n && s[aMax + 1] <= Math.min(0.25 * L, 250)) aMax++;
        int bMin = n - 1;
        while (bMin - 1 > aMax + 2 && s[bMin - 1] >= 0.55 * L) bMin--;
        int bi = 0, bj = n - 1;
        double best = Double.MAX_VALUE;
        for (int i = 0; i <= aMax; i++) {
            double[] a = t.get(i);
            for (int j = bMin; j < n; j++) {
                double d = dist(a, t.get(j));
                if (d < best) { best = d; bi = i; bj = j; }
            }
        }
        Loop lp = new Loop();
        lp.iStart = bi;
        lp.iEnd = bj;
        lp.gap = best;
        double[] g = t.get(bj), f = t.get(bi);
        double gx = g[0] - f[0], gy = g[1] - f[1];
        double span = s[bj] - s[bi];
        List<double[]> poly = new ArrayList<>();
        for (int k = bi; k < bj; k++) {
            double r = span > 0 ? (s[k] - s[bi]) / span : 0;
            double[] q = t.get(k);
            poly.add(new double[]{q[0] - gx * r, q[1] - gy * r});
        }
        lp.poly = poly;
        return lp;
    }

    // ------------------------------------------------------------------ ana hesap

    public static final class Result {
        public boolean ok;
        public String error;
        public double izArea, fieldArea, izPerim, fieldPerim, closure = -1, sigma, meanAcc, margin;
        public int nPoints;
        public boolean crossed;
        public final List<String> notes = new ArrayList<>();
        public List<double[]> trackXY = new ArrayList<>(), izXY = new ArrayList<>(), fieldXY = new ArrayList<>();
        public List<double[]> fieldLL = new ArrayList<>(), izLL = new ArrayList<>();
        public Proj proj;
    }

    /**
     * @param track  {enlem, boylam, doğruluk} iz noktaları
     * @param corners {enlem, boylam, doğruluk} köşe noktaları
     * @param useCorners köşe modu
     * @param margin  dışa öteleme (m) — genelde tabla genişliğinin yarısı; 0 ise öteleme yok
     */
    public static Result compute(List<double[]> track, List<double[]> corners, boolean useCorners, double margin) {
        Result r = new Result();
        r.margin = margin;
        List<double[]> src = useCorners ? corners : track;
        if (src == null || src.size() < 3) {
            r.error = useCorners ? "En az 3 köşe noktası gerekli." : "Yeterli iz noktası yok.";
            return r;
        }
        double la = 0, lo = 0, acc = 0;
        for (double[] q : src) { la += q[0]; lo += q[1]; acc += q[2]; }
        la /= src.size(); lo /= src.size();
        r.meanAcc = acc / src.size();
        r.proj = new Proj(la, lo);
        if (track != null) for (double[] q : track) r.trackXY.add(r.proj.fwd(q[0], q[1]));

        List<double[]> poly;
        double sigma = Math.max(0.5, r.meanAcc * 0.7); // Android doğruluğu %68 yarıçapı → eksen başına yaklaşık σ
        if (useCorners) {
            poly = new ArrayList<>();
            for (double[] q : corners) poly.add(r.proj.fwd(q[0], q[1]));
            poly = dedupe(poly, 0.05);
            if (poly.size() < 3) { r.error = "Köşe noktaları üst üste."; return r; }
            // σ_A² = Σ ¼ σ² |P(i+1) − P(i−1)|²
            double v = 0;
            int n = poly.size();
            for (int i = 0; i < n; i++) {
                double[] a = poly.get((i - 1 + n) % n), b = poly.get((i + 1) % n);
                double si = Math.max(0.5, corners.get(Math.min(i, corners.size() - 1))[2] * 0.7);
                double dd = dist(a, b);
                v += 0.25 * si * si * dd * dd;
            }
            r.sigma = Math.sqrt(v);
            r.nPoints = n;
        } else {
            List<double[]> t = dedupe(r.trackXY, 0.3);
            double L = perimeter(t, false);
            if (t.size() < 10 || L < 40) {
                r.error = "İz çok kısa (" + Math.round(L) + " m). Tarlanın etrafını tam dönün.";
                return r;
            }
            Loop lp = closeLoop(t);
            r.closure = lp.gap;
            poly = dedupe(lp.poly, 0.3);
            if (poly.size() < 3) { r.error = "Tur oluşturulamadı."; return r; }
            double P = perimeter(poly, true);
            r.sigma = sigma * Math.sqrt(20 * P); // ~20 m'lik parçalar bağımsız kabul edilir
            r.nPoints = poly.size();
            if (lp.gap > Math.max(15, 3 * margin)) {
                r.notes.add("Tur tam kapanmamış görünüyor: başlangıca en yakın mesafe "
                        + Math.round(lp.gap) + " m. Alan eksik olabilir.");
            }
        }

        poly = ccw(poly);
        r.izXY = poly;
        r.izArea = Math.abs(signedArea(poly));
        r.izPerim = perimeter(poly, true);
        r.crossed = selfIntersects(simplifyClosed(poly, 2.5));
        if (r.crossed) r.notes.add("İz kendini kesiyor (çapraz geçiş var). Sonucu krokide kontrol edin.");

        if (margin > 0) {
            List<double[]> simp = ccw(simplifyClosed(poly, useCorners ? 0 : 1.0));
            List<double[]> off = offsetOut(simp, margin);
            double simpArea = Math.abs(signedArea(simp));
            // sadeleştirmenin alan farkını geri ekle → iz alanı ile tutarlı kalsın
            r.fieldArea = Math.abs(signedArea(off)) + (r.izArea - simpArea);
            r.fieldXY = off;
            r.fieldPerim = perimeter(off, true);
            // sürücünün kenara uzaklığındaki belirsizlik (±0,3 m)
            r.sigma = Math.sqrt(r.sigma * r.sigma + Math.pow(0.3 * r.fieldPerim, 2));
        } else {
            r.fieldArea = r.izArea;
            r.fieldXY = poly;
            r.fieldPerim = r.izPerim;
        }
        for (double[] q : r.fieldXY) r.fieldLL.add(r.proj.inv(q[0], q[1]));
        for (double[] q : r.izXY) r.izLL.add(r.proj.inv(q[0], q[1]));
        r.ok = true;
        return r;
    }

    /** Kayıt sırasında canlı gösterim için hızlı yaklaşık alan (iz başlangıca bağlanarak). */
    public static double quickArea(List<double[]> xy) {
        return Math.abs(signedArea(xy));
    }
}
