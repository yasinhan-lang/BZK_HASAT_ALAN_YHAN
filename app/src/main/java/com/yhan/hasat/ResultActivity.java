package com.yhan.hasat;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/** Bir ölçümün sonucu: alan, kroki, ayrıntılar, dışa aktarma. */
public class ResultActivity extends Activity {

    private Store.Olcum o;
    private Geo.Result r;

    private TextView nameText, dateText, areaDonum, areaM2, sigmaText, tablaText;
    private Switch marginSw;
    private TextView methodIz, methodKose;
    private LinearLayout methodRow, notesCard, details, tablaRow;
    private FieldView field;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        o = Store.load(this, getIntent().getStringExtra("id"));
        if (o == null) {
            Toast.makeText(this, "Ölçüm bulunamadı", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        setContentView(buildUi());
        recompute();
    }

    private int dp(float v) { return Ui.dp(this, v); }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);
        root.setFitsSystemWindows(true);
        root.addView(Ui.header(this, "SONUÇ"));

        ScrollView sv = new ScrollView(this);
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        sv.addView(c);
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        // ana sonuç
        LinearLayout top = Ui.card(this);
        top.setGravity(Gravity.CENTER_HORIZONTAL);
        nameText = Ui.text(this, "", 18, Ui.INK, true);
        nameText.setGravity(Gravity.CENTER);
        nameText.setOnClickListener(v -> rename());
        top.addView(nameText);
        dateText = Ui.text(this, "", 12.5f, Ui.SUB, false);
        dateText.setGravity(Gravity.CENTER);
        top.addView(dateText);
        TextView lbl = Ui.text(this, "TARLA ALANI", 13, Ui.SUB, true);
        lbl.setPadding(0, dp(12), 0, 0);
        top.addView(lbl);
        LinearLayout ar = Ui.row(this);
        ar.setGravity(Gravity.CENTER);
        areaDonum = Ui.text(this, "", 44, Ui.GREEN, true);
        ar.addView(areaDonum);
        ar.addView(Ui.text(this, " dönüm", 18, Ui.GREEN, true));
        top.addView(ar);
        areaM2 = Ui.text(this, "", 18, Ui.INK, true);
        top.addView(areaM2);
        sigmaText = Ui.text(this, "", 12.5f, Ui.SUB, false);
        sigmaText.setGravity(Gravity.CENTER);
        sigmaText.setPadding(0, dp(4), 0, 0);
        top.addView(sigmaText);
        c.addView(top, Ui.lp(this, 12));

        // ayarlar
        LinearLayout set = Ui.card(this);
        marginSw = new Switch(this);
        marginSw.setText("Biçerdöver tabla payını ekle");
        marginSw.setTextSize(14);
        marginSw.setTextColor(Ui.INK);
        marginSw.setChecked(o.margin);
        marginSw.setOnCheckedChangeListener((v, on) -> { o.margin = on; recompute(); });
        set.addView(marginSw);
        LinearLayout tr = Ui.row(this);
        tablaRow = tr;
        tr.setPadding(0, dp(10), 0, 0);
        tr.addView(Ui.text(this, "Tabla genişliği", 14, Ui.INK, true), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView minus = Ui.button(this, "−", Ui.GOLD_S, Ui.INK, 20);
        TextView plus = Ui.button(this, "+", Ui.GOLD_S, Ui.INK, 20);
        minus.setPadding(dp(18), dp(4), dp(18), dp(4));
        plus.setPadding(dp(18), dp(4), dp(18), dp(4));
        tablaText = Ui.text(this, "", 18, Ui.INK, true);
        tablaText.setGravity(Gravity.CENTER);
        tablaText.setMinWidth(dp(86));
        tablaText.setOnClickListener(v -> editTabla());
        minus.setOnClickListener(v -> { o.tabla = Math.max(0, Math.round((o.tabla - 0.1) * 10) / 10.0); recompute(); });
        plus.setOnClickListener(v -> { o.tabla = Math.min(15, Math.round((o.tabla + 0.1) * 10) / 10.0); recompute(); });
        tr.addView(minus);
        tr.addView(tablaText);
        tr.addView(plus);
        set.addView(tr);

        methodRow = Ui.row(this);
        methodRow.setPadding(0, dp(12), 0, 0);
        methodIz = Ui.button(this, "İz ile hesap", Ui.GREEN, 0xFFFFFFFF, 14);
        methodKose = Ui.button(this, "Köşelerle hesap", Ui.GREEN_S, Ui.GREEN, 14);
        methodIz.setOnClickListener(v -> { o.corner = false; recompute(); });
        methodKose.setOnClickListener(v -> { o.corner = true; recompute(); });
        methodRow.addView(methodIz, Ui.weight(this, 1, 4));
        methodRow.addView(methodKose, Ui.weight(this, 1, 4));
        set.addView(methodRow);
        c.addView(set, Ui.lp(this, 10));

        // kroki
        field = new FieldView(this);
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(330));
        fp.setMargins(dp(14), dp(10), dp(14), 0);
        c.addView(field, fp);
        TextView legend = Ui.text(this, "Sarı: alan sınırı  ·  beyaz: GPS yolu  ·  yeşil nokta: başlangıç", 11.5f, Ui.SUB, false);
        legend.setGravity(Gravity.CENTER);
        legend.setPadding(dp(14), dp(4), dp(14), 0);
        c.addView(legend);

        // uyarılar
        notesCard = Ui.card(this);
        notesCard.setBackground(Ui.round(this, Ui.GOLD_S, 20));
        c.addView(notesCard, Ui.lp(this, 10));

        // ayrıntılar
        details = Ui.card(this);
        c.addView(details, Ui.lp(this, 10));

        // dışa aktarma
        LinearLayout r1 = Ui.row(this);
        TextView share = Ui.button(this, "📤  Paylaş (resim)", Ui.GREEN, 0xFFFFFFFF, 14);
        share.setOnClickListener(v -> shareImage());
        TextView kml = Ui.button(this, "🌍  KML kaydet", Ui.GREEN_S, Ui.GREEN, 14);
        kml.setOnClickListener(v -> exportKml(false));
        r1.addView(share, Ui.weight(this, 1, 4));
        r1.addView(kml, Ui.weight(this, 1, 4));
        c.addView(r1, Ui.lp(this, 12));
        LinearLayout r2 = Ui.row(this);
        TextView txt = Ui.button(this, "📄  Rapor (TXT)", Ui.GREEN_S, Ui.GREEN, 14);
        txt.setOnClickListener(v -> exportTxt());
        TextView ge = Ui.button(this, "🛰  Google Earth", Ui.GREEN_S, Ui.GREEN, 14);
        ge.setOnClickListener(v -> exportKml(true));
        r2.addView(txt, Ui.weight(this, 1, 4));
        r2.addView(ge, Ui.weight(this, 1, 4));
        c.addView(r2, Ui.lp(this, 8));
        TextView del = Ui.button(this, "🗑  Ölçümü sil", Ui.RED_S, Ui.RED, 14);
        del.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setMessage("Bu ölçüm silinsin mi?")
                .setPositiveButton("Sil", (x, y) -> { Store.delete(this, o.id); finish(); })
                .setNegativeButton("Vazgeç", null)
                .show());
        c.addView(del, Ui.lp(this, 8));

        c.addView(Ui.footer(this));
        return root;
    }

    private void recompute() {
        boolean hasTrack = o.track.size() >= 10, hasCorners = o.corners.size() >= 3;
        if (o.corner && !hasCorners && hasTrack) o.corner = false;
        if (!o.corner && !hasTrack && hasCorners) o.corner = true;
        r = Geo.compute(o.track, o.corners, o.corner, o.margin ? o.tabla / 2 : 0);
        o.area = r.ok ? r.fieldArea : -1;
        try { Store.save(this, o); } catch (Exception ignored) { }

        nameText.setText((o.name.isEmpty() ? "İsimsiz tarla" : o.name) + "  ✎");
        dateText.setText(Fmt.date(o.created) + "  ·  " + (o.corner ? "Köşe noktaları" : Store.modeName(o.mode)));
        tablaText.setText(Fmt.num(o.tabla, 1) + " m");
        tablaRow.setVisibility(o.margin ? View.VISIBLE : View.GONE);
        methodRow.setVisibility(hasTrack && hasCorners ? View.VISIBLE : View.GONE);
        methodIz.setBackground(Ui.round(this, o.corner ? Ui.GREEN_S : Ui.GREEN, 16));
        methodIz.setTextColor(o.corner ? Ui.GREEN : 0xFFFFFFFF);
        methodKose.setBackground(Ui.round(this, o.corner ? Ui.GREEN : Ui.GREEN_S, 16));
        methodKose.setTextColor(o.corner ? 0xFFFFFFFF : Ui.GREEN);

        notesCard.removeAllViews();
        details.removeAllViews();
        if (!r.ok) {
            areaDonum.setText("—");
            areaM2.setText("");
            sigmaText.setText(r.error);
            notesCard.setVisibility(View.GONE);
            FieldView.Data d = new FieldView.Data();
            d.path = r.trackXY;
            field.setData(d);
            return;
        }
        areaDonum.setText(Fmt.donum(r.fieldArea));
        areaM2.setText(Fmt.m2(r.fieldArea) + " m²");
        sigmaText.setText("Tahmini hata payı: ± " + Fmt.num(r.sigma, 0) + " m²  (± %"
                + Fmt.num(100 * r.sigma / Math.max(1, r.fieldArea), 1) + ")");

        if (r.notes.isEmpty()) {
            notesCard.setVisibility(View.GONE);
        } else {
            notesCard.setVisibility(View.VISIBLE);
            notesCard.addView(Ui.text(this, "⚠  Dikkat", 14, 0xFF9A6A00, true));
            for (String n : r.notes) {
                TextView t = Ui.text(this, "• " + n, 13, Ui.INK, false);
                t.setPadding(0, dp(4), 0, 0);
                notesCard.addView(t);
            }
        }

        details.addView(Ui.text(this, "Ayrıntılar", 14, Ui.GREEN, true));
        if (r.margin > 0) {
            Ui.stat(this, details, "Makine izi alanı")[1].setText(Fmt.m2(r.izArea) + " m²");
            Ui.stat(this, details, "Tabla payıyla eklenen")[1].setText(Fmt.m2(r.fieldArea - r.izArea) + " m²");
        }
        Ui.stat(this, details, "Tarla çevresi")[1].setText(Fmt.num(r.fieldPerim, 1) + " m");
        if (r.closure >= 0) Ui.stat(this, details, "Kapanma hatası (dağıtıldı)")[1].setText(Fmt.num(r.closure, 1) + " m");
        Ui.stat(this, details, o.corner ? "Köşe sayısı" : "Kullanılan iz noktası")[1].setText(String.valueOf(r.nPoints));
        Ui.stat(this, details, "Ortalama GPS doğruluğu")[1].setText("± " + Fmt.num(r.meanAcc, 1) + " m");
        Ui.stat(this, details, "Hektar")[1].setText(Fmt.num(r.fieldArea / 10000.0, 4) + " ha");

        FieldView.Data d = new FieldView.Data();
        d.path = r.trackXY;
        if (r.margin > 0) d.iz = r.izXY;
        d.closeIz = true;
        d.field = r.fieldXY;
        if (o.corner) for (double[] k : o.corners) d.corners.add(r.proj.fwd(k[0], k[1]));
        field.setData(d);
    }

    private void rename() {
        EditText e = new EditText(this);
        e.setText(o.name);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        LinearLayout box = new LinearLayout(this);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        box.addView(e, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        new AlertDialog.Builder(this)
                .setTitle("Tarla adı / sahibi")
                .setView(box)
                .setPositiveButton("Kaydet", (x, y) -> { o.name = e.getText().toString().trim(); recompute(); })
                .setNegativeButton("Vazgeç", null)
                .show();
    }

    private void editTabla() {
        EditText e = new EditText(this);
        e.setText(Fmt.num(o.tabla, 2));
        e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        LinearLayout box = new LinearLayout(this);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        box.addView(e, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        new AlertDialog.Builder(this)
                .setTitle("Tabla genişliği (m)")
                .setView(box)
                .setPositiveButton("Tamam", (x, y) -> {
                    try { o.tabla = Math.max(0, Math.min(15, Double.parseDouble(e.getText().toString().trim().replace(',', '.')))); }
                    catch (Exception ignored) { }
                    recompute();
                })
                .setNegativeButton("Vazgeç", null)
                .show();
    }

    // ------------------------------------------------------------------ dışa aktarma

    private void exportKml(boolean open) {
        if (!r.ok) return;
        try {
            Uri u = Store.saveText(this, Store.baseName(o) + ".kml", "application/vnd.google-earth.kml+xml", Store.kml(o, r));
            if (open) {
                Intent i = new Intent(Intent.ACTION_VIEW)
                        .setDataAndType(u, "application/vnd.google-earth.kml+xml")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                try {
                    startActivity(i);
                } catch (Exception e) {
                    Toast.makeText(this, "KML kaydedildi; açacak uygulama bulunamadı.", Toast.LENGTH_LONG).show();
                }
            } else {
                Toast.makeText(this, "Kaydedildi: İndirilenler/" + Store.FOLDER, Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Kaydedilemedi: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void exportTxt() {
        if (!r.ok) return;
        try {
            Store.saveText(this, Store.baseName(o) + ".txt", "text/plain", Store.report(o, r));
            Toast.makeText(this, "Kaydedildi: İndirilenler/" + Store.FOLDER, Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Kaydedilemedi: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void shareImage() {
        if (!r.ok) return;
        try {
            Bitmap bmp = renderCard();
            Uri u = Store.savePng(this, Store.baseName(o) + ".png", bmp);
            Intent i = new Intent(Intent.ACTION_SEND)
                    .setType("image/png")
                    .putExtra(Intent.EXTRA_STREAM, u)
                    .putExtra(Intent.EXTRA_TEXT, (o.name.isEmpty() ? "Tarla" : o.name) + ": "
                            + Fmt.donum(r.fieldArea) + " dönüm (" + Fmt.m2(r.fieldArea) + " m²)")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(i, "Paylaş"));
        } catch (Exception e) {
            Toast.makeText(this, "Paylaşılamadı: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /** Paylaşım için tek sayfalık sonuç resmi. */
    private Bitmap renderCard() {
        int W = 1080, H = 1560;
        float k = 2.6f; // resim için "dp"
        Bitmap b = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.drawColor(0xFFFBF6EA);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTextAlign(Paint.Align.CENTER);

        // başlık şeridi
        Paint band = new Paint();
        band.setColor(0xFF1F5C3A);
        c.drawRect(0, 0, W, 150, band);
        p.setColor(0xFFF4C542);
        p.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));
        p.setTextSize(40);
        c.drawText("BOZOKLU HAN", W / 2f, 62, p);
        p.setColor(0xFFFFFFFF);
        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setTextSize(34);
        c.drawText("ALAN ÖLÇÜMÜ", W / 2f, 116, p);

        p.setColor(0xFF1F2A22);
        p.setTextSize(46);
        c.drawText(o.name.isEmpty() ? "Tarla" : o.name, W / 2f, 225, p);
        p.setTypeface(Typeface.DEFAULT);
        p.setColor(0xFF7D857F);
        p.setTextSize(30);
        c.drawText(Fmt.date(o.created) + "  ·  " + (o.corner ? "Köşe noktaları" : Store.modeName(o.mode)), W / 2f, 272, p);

        p.setColor(0xFF1F5C3A);
        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setTextSize(110);
        c.drawText(Fmt.donum(r.fieldArea) + " dönüm", W / 2f, 400, p);
        p.setColor(0xFF1F2A22);
        p.setTextSize(48);
        c.drawText(Fmt.m2(r.fieldArea) + " m²", W / 2f, 468, p);
        p.setTypeface(Typeface.DEFAULT);
        p.setColor(0xFF7D857F);
        p.setTextSize(28);
        String sub = "Tahmini hata ± " + Fmt.num(r.sigma, 0) + " m²";
        if (r.margin > 0) sub += "  ·  tabla " + Fmt.num(o.tabla, 1) + " m";
        sub += "  ·  çevre " + Fmt.num(r.fieldPerim, 0) + " m";
        c.drawText(sub, W / 2f, 516, p);

        FieldView.Data d = new FieldView.Data();
        d.path = r.trackXY;
        if (r.margin > 0) d.iz = r.izXY;
        d.closeIz = true;
        d.field = r.fieldXY;
        if (o.corner) for (double[] q : o.corners) d.corners.add(r.proj.fwd(q[0], q[1]));
        FieldView.render(c, new RectF(50, 560, W - 50, H - 110), d, k);

        p.setColor(0xFF7D857F);
        p.setTextSize(24);
        c.drawText("BozokluHAN ALAN ÖLÇER · © 2026 Yasin HAN · Tüm hakları saklıdır.", W / 2f, H - 50, p);
        return b;
    }
}
