package com.yhan.hasat;

import android.content.Context;
import android.content.SharedPreferences;

/** Ayarlar. */
final class Prefs {
    private Prefs() {}

    /** Ölçüm yöntemi. */
    static final int YAYA = 0, BICERDOVER = 1, KOSE = 2;

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("ayarlar", Context.MODE_PRIVATE);
    }

    static int mode(Context c) { return sp(c).getInt("mod", YAYA); }
    static void mode(Context c, int v) { sp(c).edit().putInt("mod", v).apply(); }

    /** Biçerdöver tabla genişliği (m). */
    static double tabla(Context c) { return sp(c).getFloat("tabla", 6.0f); }
    static void tabla(Context c, double v) { sp(c).edit().putFloat("tabla", (float) v).apply(); }

    /** Bu değerden kötü doğruluktaki konumlar kaydedilmez (m). */
    static double maxAcc(Context c) { return sp(c).getFloat("maxAcc", 8f); }
    static void maxAcc(Context c, double v) { sp(c).edit().putFloat("maxAcc", (float) v).apply(); }

    /** Köşe noktasında ortalama alma süresi (sn). */
    static int avgSec(Context c) { return sp(c).getInt("avgSec", 10); }
    static void avgSec(Context c, int v) { sp(c).edit().putInt("avgSec", v).apply(); }

    /** Başlangıç noktasına dönünce ölçümü kendiliğinden bitir. */
    static boolean autoFinish(Context c) { return sp(c).getBoolean("otoBitir", true); }
    static void autoFinish(Context c, boolean v) { sp(c).edit().putBoolean("otoBitir", v).apply(); }
}
