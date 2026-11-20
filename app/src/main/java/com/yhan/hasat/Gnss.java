package com.yhan.hasat;

import android.location.GnssStatus;

/** Uydu durumu: kullanılan / görünen uydu sayısı, çift frekans (L5/E5a) var mı. */
final class Gnss {
    private Gnss() {}

    static void update(GnssStatus s) {
        int used = 0, vis = s.getSatelliteCount();
        boolean dual = false;
        for (int i = 0; i < vis; i++) {
            if (s.usedInFix(i)) {
                used++;
                if (s.hasCarrierFrequencyHz(i)) {
                    float f = s.getCarrierFrequencyHz(i);
                    if (f > 1.1e9f && f < 1.2e9f) dual = true; // L5 / E5a 1176 MHz
                }
            }
        }
        Session.satsUsed = used;
        Session.satsVisible = vis;
        Session.dualFreq = dual;
    }
}
