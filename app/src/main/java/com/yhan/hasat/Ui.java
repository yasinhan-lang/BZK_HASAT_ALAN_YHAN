package com.yhan.hasat;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Ortak görünüm: renkler, kartlar, düğmeler, başlık, alt bilgi. */
final class Ui {
    private Ui() {}

    static final int BG = 0xFFF2F4EF, CARD = 0xFFFFFFFF, GREEN = 0xFF1E5A3A, GREEN_D = 0xFF15412A, GREEN_S = 0xFFE3EFE6,
            GOLD = 0xFFE0A526, GOLD_S = 0xFFFBF0D5, INK = 0xFF18221B, SUB = 0xFF7A847D,
            RED = 0xFFD64545, RED_S = 0xFFFBE6E6, OK = 0xFF22A565, LINE = 0xFFE7EBE4, BLUE = 0xFF3D7BF0;

    static final Typeface MED = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    static final Typeface BOLD = Typeface.create("sans-serif", Typeface.BOLD);
    static final Typeface LIGHT = Typeface.create("sans-serif-light", Typeface.NORMAL);

    static int dp(Activity a, float v) {
        return Math.round(v * a.getResources().getDisplayMetrics().density);
    }

    static GradientDrawable round(Activity a, int color, float r) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(a, r));
        return g;
    }

    static GradientDrawable gradient(Activity a, int c1, int c2, float r) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{c1, c2});
        g.setCornerRadius(dp(a, r));
        return g;
    }

    static Drawable ripple(Drawable content, int rippleColor) {
        return new RippleDrawable(ColorStateList.valueOf(rippleColor), content, null);
    }

    static TextView text(Activity a, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(a);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(bold ? BOLD : Typeface.DEFAULT);
        t.setIncludeFontPadding(false);
        return t;
    }

    static LinearLayout card(Activity a) {
        LinearLayout l = new LinearLayout(a);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(a, 18), dp(a, 16), dp(a, 18), dp(a, 16));
        l.setBackground(round(a, CARD, 24));
        l.setElevation(dp(a, 1));
        return l;
    }

    static LinearLayout.LayoutParams lp(Activity a, int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(dp(a, 16), dp(a, top), dp(a, 16), 0);
        return p;
    }

    static TextView button(Activity a, String s, int bg, int fg, float sp) {
        TextView t = text(a, s, sp, fg, false);
        t.setTypeface(MED);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(a, 12), dp(a, 15), dp(a, 12), dp(a, 15));
        t.setBackground(ripple(round(a, bg, 18), 0x33000000));
        t.setClickable(true);
        t.setFocusable(true);
        t.setLetterSpacing(0.03f);
        return t;
    }

    /** Yuvarlak büyük düğme. */
    static TextView circle(Activity a, String s, int c1, int c2, float sp, int sizeDp) {
        TextView t = text(a, s, sp, 0xFFFFFFFF, true);
        t.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{c1, c2});
        g.setShape(GradientDrawable.OVAL);
        t.setBackground(ripple(g, 0x44FFFFFF));
        t.setElevation(dp(a, 6));
        t.setClickable(true);
        t.setLayoutParams(new LinearLayout.LayoutParams(dp(a, sizeDp), dp(a, sizeDp)));
        return t;
    }

    static LinearLayout row(Activity a) {
        LinearLayout r = new LinearLayout(a);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        return r;
    }

    static LinearLayout.LayoutParams weight(Activity a, float w, int marginDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, w);
        p.setMargins(dp(a, marginDp), 0, dp(a, marginDp), 0);
        return p;
    }

    /** Logo · başlık · dalgalanan bayrak; simetrik. */
    static View header(Activity a, String title) {
        LinearLayout h = row(a);
        h.setPadding(dp(a, 14), dp(a, 10), dp(a, 14), dp(a, 10));
        h.setBackgroundColor(CARD);
        h.setElevation(dp(a, 2));

        LogoView logo = new LogoView(a);
        h.addView(logo, new LinearLayout.LayoutParams(dp(a, 72), dp(a, 44)));

        TextView t = text(a, title, 20, GREEN, true);
        t.setGravity(Gravity.CENTER);
        t.setLetterSpacing(0.12f);
        h.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        FlagView flag = new FlagView(a);
        h.addView(flag, new LinearLayout.LayoutParams(dp(a, 72), dp(a, 44)));
        return h;
    }

    static View footer(Activity a) {
        TextView t = text(a, "Tasarım ve Geliştirme\nYasin HAN\n© 2026 Yasin HAN · Tüm hakları saklıdır.", 11, SUB, false);
        t.setGravity(Gravity.CENTER);
        t.setLineSpacing(dp(a, 3), 1);
        t.setPadding(dp(a, 12), dp(a, 22), dp(a, 12), dp(a, 26));
        return t;
    }

    /** Etiket + değer satırı. */
    static TextView[] stat(Activity a, LinearLayout parent, String label) {
        LinearLayout r = row(a);
        r.setPadding(0, dp(a, 7), 0, dp(a, 7));
        TextView l = text(a, label, 14, SUB, false);
        TextView v = text(a, "—", 15, INK, false);
        v.setTypeface(MED);
        v.setGravity(Gravity.END);
        r.addView(l, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        r.addView(v);
        parent.addView(r);
        return new TextView[]{l, v};
    }

    /** Küçük bilgi kutucuğu: üstte değer, altta etiket. Döndürülen: {kutu, değer, etiket}. */
    static Object[] tile(Activity a, String label) {
        LinearLayout t = new LinearLayout(a);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(a, 8), dp(a, 12), dp(a, 8), dp(a, 12));
        t.setBackground(round(a, BG, 16));
        TextView v = text(a, "—", 18, INK, true);
        v.setGravity(Gravity.CENTER);
        TextView l = text(a, label, 11.5f, SUB, false);
        l.setGravity(Gravity.CENTER);
        l.setPadding(0, dp(a, 5), 0, 0);
        t.addView(v);
        t.addView(l);
        return new Object[]{t, v, l};
    }

    /** Seçmeli düğme grubu (segment). */
    static TextView[] segments(Activity a, LinearLayout parent, String[] labels) {
        LinearLayout box = row(a);
        box.setPadding(dp(a, 4), dp(a, 4), dp(a, 4), dp(a, 4));
        box.setBackground(round(a, BG, 18));
        TextView[] out = new TextView[labels.length];
        for (int i = 0; i < labels.length; i++) {
            TextView t = text(a, labels[i], 13, SUB, false);
            t.setTypeface(MED);
            t.setGravity(Gravity.CENTER);
            t.setPadding(dp(a, 4), dp(a, 11), dp(a, 4), dp(a, 11));
            t.setClickable(true);
            box.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            out[i] = t;
        }
        parent.addView(box);
        return out;
    }

    static void select(Activity a, TextView[] segs, int sel) {
        for (int i = 0; i < segs.length; i++) {
            boolean on = i == sel;
            segs[i].setBackground(on ? gradient(a, GREEN, GREEN_D, 14) : null);
            segs[i].setTextColor(on ? 0xFFFFFFFF : SUB);
            segs[i].setElevation(on ? dp(a, 2) : 0);
        }
    }
}
