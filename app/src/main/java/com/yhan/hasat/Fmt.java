package com.yhan.hasat;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Türkçe sayı / tarih biçimleri. */
final class Fmt {
    private Fmt() {}

    static final Locale TR = new Locale("tr", "TR");
    private static final DecimalFormatSymbols SYM = DecimalFormatSymbols.getInstance(TR);

    static String num(double v, int dec) {
        StringBuilder p = new StringBuilder("#,##0");
        if (dec > 0) { p.append('.'); for (int i = 0; i < dec; i++) p.append('0'); }
        return new DecimalFormat(p.toString(), SYM).format(v);
    }

    /** m² → "12.345,6" */
    static String m2(double v) { return num(v, 1); }

    /** m² → dönüm (1 dönüm = 1000 m²) "12,346" */
    static String donum(double m2) { return num(m2 / 1000.0, 3); }

    static String m(double v) {
        return v >= 1000 ? num(v / 1000.0, 2) + " km" : num(v, 0) + " m";
    }

    static String time(long ms) {
        long s = ms / 1000;
        return String.format(Locale.US, "%d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60);
    }

    static String date(long ms) {
        return new SimpleDateFormat("dd.MM.yyyy HH:mm", TR).format(new Date(ms));
    }

    static String fileStamp(long ms) {
        return new SimpleDateFormat("yyyyMMdd_HHmm", TR).format(new Date(ms));
    }
}
