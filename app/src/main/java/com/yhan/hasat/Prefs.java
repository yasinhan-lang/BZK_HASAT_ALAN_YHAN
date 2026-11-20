package com.yhan.hasat;

import android.content.Context;
import android.content.SharedPreferences;

/** Ayarlar. */
final class Prefs {
    private Prefs() {}

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("ayarlar", Context.MODE_PRIVATE);
    }

    /** Biçerdöver tabla genişliği (m). */
    static double tabla(Context c) { return sp(c).getFloat("tabla", 6.0f); }
    static void tabla(Context c, double v) { sp(c).edit().putFloat("tabla", (float) v).apply(); }

    /** Bu değerden kötü doğruluktaki konumlar kaydedilmez (m). */
    static double maxAcc(Context c) { return sp(c).getFloat("maxAcc", 10f); }
    static void maxAcc(Context c, double v) { sp(c).edit().putFloat("maxAcc", (float) v).apply(); }

    /** Köşe noktasında ortalama alma süresi (sn). */
    static int avgSec(Context c) { return sp(c).getInt("avgSec", 10); }
    static void avgSec(Context c, int v) { sp(c).edit().putInt("avgSec", v).apply(); }

    /** Son seçilen yöntem: true = köşe noktaları. */
    static boolean cornerMode(Context c) { return sp(c).getBoolean("kose", false); }
    static void cornerMode(Context c, boolean v) { sp(c).edit().putBoolean("kose", v).apply(); }
}
