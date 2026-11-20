package com.yhan.hasat;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/** Kayıtlı ölçümler listesi. */
public class HistoryActivity extends Activity {

    private LinearLayout list;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);
        root.setFitsSystemWindows(true);
        root.addView(Ui.header(this, "Ölçümler", "Kayıtlı tarla alanları"));
        ScrollView sv = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, 0, 0, Ui.dp(this, 16));
        sv.addView(list);
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        fill();
    }

    private void fill() {
        list.removeAllViews();
        List<String> ids = Store.list(this);
        if (ids.isEmpty()) {
            TextView t = Ui.text(this, "Henüz kayıtlı ölçüm yok.", 15, Ui.SUB, false);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, Ui.dp(this, 40), 0, 0);
            list.addView(t);
        }
        for (String id : ids) {
            Store.Olcum o = Store.load(this, id);
            if (o == null) continue;
            LinearLayout card = Ui.card(this);
            LinearLayout r = Ui.row(this);
            LinearLayout left = new LinearLayout(this);
            left.setOrientation(LinearLayout.VERTICAL);
            left.addView(Ui.text(this, o.name.isEmpty() ? "İsimsiz tarla" : o.name, 16, Ui.INK, true));
            left.addView(Ui.text(this, Fmt.date(o.created) + " · " + (o.corner ? "Köşe" : "İz"), 12.5f, Ui.SUB, false));
            r.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            LinearLayout right = new LinearLayout(this);
            right.setOrientation(LinearLayout.VERTICAL);
            right.setGravity(Gravity.END);
            right.addView(Ui.text(this, o.area >= 0 ? Fmt.donum(o.area) + " dönüm" : "—", 17, Ui.GREEN, true));
            if (o.area >= 0) right.addView(Ui.text(this, Fmt.m2(o.area) + " m²", 12.5f, Ui.SUB, false));
            r.addView(right);
            card.addView(r);
            card.setOnClickListener(v -> startActivity(new Intent(this, ResultActivity.class).putExtra("id", id)));
            list.addView(card, Ui.lp(this, 10));
        }
        list.addView(Ui.footer(this));
    }
}
