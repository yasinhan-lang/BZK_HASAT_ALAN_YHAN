package com.yhan.hasat;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Ortak görünüm yardımcıları (renkler, kartlar, başlık, alt bilgi). */
final class Ui {
    private Ui() {}

    static final int BG = 0xFFFBF6EA, CARD = 0xFFFFFFFF, GREEN = 0xFF1F5C3A, GREEN_S = 0xFFE4F1E8,
            GOLD = 0xFFE0A526, GOLD_S = 0xFFFDF1D6, INK = 0xFF1F2A22, SUB = 0xFF7D857F,
            RED = 0xFFD64545, RED_S = 0xFFFBE4E4, BLUE = 0xFF3D7BF0, LINE = 0xFFEDE6D6;

    static int dp(Activity a, float v) {
        return Math.round(v * a.getResources().getDisplayMetrics().density);
    }

    static GradientDrawable round(Activity a, int color, float r) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(a, r));
        return g;
    }

    static TextView text(Activity a, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(a);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    static LinearLayout card(Activity a) {
        LinearLayout l = new LinearLayout(a);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(a, 16), dp(a, 14), dp(a, 16), dp(a, 14));
        l.setBackground(round(a, CARD, 20));
        l.setElevation(dp(a, 1.5f));
        return l;
    }

    static LinearLayout.LayoutParams lp(Activity a, int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(dp(a, 14), dp(a, top), dp(a, 14), 0);
        return p;
    }

    static TextView button(Activity a, String s, int bg, int fg, float sp) {
        TextView t = text(a, s, sp, fg, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(a, 12), dp(a, 14), dp(a, 12), dp(a, 14));
        t.setBackground(round(a, bg, 16));
        t.setClickable(true);
        t.setFocusable(true);
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

    /** Logo · başlık · dalgalanan bayrak; ince çerçeve içinde, simetrik. */
    static View header(Activity a, String title, String sub) {
        LinearLayout h = row(a);
        h.setPadding(dp(a, 12), dp(a, 8), dp(a, 12), dp(a, 8));
        h.setBackground(round(a, CARD, 0));
        h.setElevation(dp(a, 2));

        LogoView logo = new LogoView(a);
        h.addView(logo, new LinearLayout.LayoutParams(dp(a, 74), dp(a, 46)));

        LinearLayout mid = new LinearLayout(a);
        mid.setOrientation(LinearLayout.VERTICAL);
        mid.setGravity(Gravity.CENTER);
        TextView t = text(a, title, 17, GREEN, true);
        t.setGravity(Gravity.CENTER);
        TextView s = text(a, sub, 11.5f, SUB, false);
        s.setGravity(Gravity.CENTER);
        mid.addView(t);
        mid.addView(s);
        h.addView(mid, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        FlagView flag = new FlagView(a);
        h.addView(flag, new LinearLayout.LayoutParams(dp(a, 74), dp(a, 46)));
        return h;
    }

    static View footer(Activity a) {
        TextView t = text(a, "Tasarım ve Geliştirme: Yasin HAN\n© 2026 Yasin HAN · Tüm hakları saklıdır.", 11, SUB, false);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(a, 12), dp(a, 18), dp(a, 12), dp(a, 22));
        return t;
    }

    /** Etiket + değer satırı. */
    static TextView[] stat(Activity a, LinearLayout parent, String label) {
        LinearLayout r = row(a);
        r.setPadding(0, dp(a, 4), 0, dp(a, 4));
        TextView l = text(a, label, 13.5f, SUB, false);
        TextView v = text(a, "—", 14.5f, INK, true);
        v.setGravity(Gravity.END);
        r.addView(l, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        r.addView(v);
        parent.addView(r);
        return new TextView[]{l, v};
    }
}
