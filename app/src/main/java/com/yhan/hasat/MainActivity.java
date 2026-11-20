package com.yhan.hasat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.GnssStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity implements Session.Listener, LocationListener {

    private static final int REQ_START = 11;

    private final Handler h = new Handler(Looper.getMainLooper());
    private LocationManager lm;
    private boolean listening;
    private boolean pendingCorner;

    private TextView accText, satText, gnssHint;
    private TextView modeIz, modeKose, tablaText;
    private LinearLayout setupCard, liveCard, buttons;
    private TextView liveArea, liveM2, liveTitle;
    private TextView[] sTime, sPath, sCount, sStart, sRej;
    private TextView avgText;
    private FieldView field;
    private TextView helpText;

    private final GnssStatus.Callback gnss = new GnssStatus.Callback() {
        @Override
        public void onSatelliteStatusChanged(GnssStatus s) { Gnss.update(s); }
    };

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            Session.tick(MainActivity.this);
            refresh();
            h.postDelayed(this, 1000);
        }
    };

    // ------------------------------------------------------------------ yaşam döngüsü

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Notif.ensure(this);
        lm = getSystemService(LocationManager.class);
        setContentView(buildUi());
        if (Session.state == Session.IDLE && Session.restore(this)) {
            h.postDelayed(this::askRestored, 400);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Session.addListener(this);
        startListening();
        h.post(ticker);
        rebuildButtons();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Session.removeListener(this);
        stopListening();
        h.removeCallbacks(ticker);
    }

    @Override
    public void onSessionChanged() { refresh(); }

    private int lastButtonsKey = -1;

    @Override
    public void onMessage(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    // ------------------------------------------------------------------ konum (yalnızca gösterim)

    private boolean hasLoc() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    @SuppressLint("MissingPermission")
    private void startListening() {
        if (listening || !hasLoc()) return;
        try {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, this, Looper.getMainLooper());
            lm.registerGnssStatusCallback(gnss, h);
            listening = true;
        } catch (Exception ignored) {
        }
    }

    private void stopListening() {
        if (!listening) return;
        lm.removeUpdates(this);
        lm.unregisterGnssStatusCallback(gnss);
        listening = false;
    }

    @Override
    public void onLocationChanged(Location loc) {
        // kayıt servisi çalışıyorsa konumu o işler; burada yalnızca ekran için
        if (!TrackService.running) Session.onFix(this, loc, false);
    }

    @Override public void onProviderEnabled(String p) {}
    @Override public void onProviderDisabled(String p) {}
    @Override public void onStatusChanged(String p, int s, Bundle b) {}

    // ------------------------------------------------------------------ arayüz

    private int dp(float v) { return Ui.dp(this, v); }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);
        root.setFitsSystemWindows(true);
        root.addView(Ui.header(this, "Hasat Alan Ölçer", "BozokluHAN · biçilen tarlanın alanı"));

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(0, 0, 0, dp(4));
        sv.addView(c);
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        // GNSS durumu
        LinearLayout g = Ui.card(this);
        LinearLayout gr = Ui.row(this);
        LinearLayout gl = new LinearLayout(this);
        gl.setOrientation(LinearLayout.VERTICAL);
        gl.addView(Ui.text(this, "Konum doğruluğu", 12.5f, Ui.SUB, false));
        accText = Ui.text(this, "—", 28, Ui.INK, true);
        gl.addView(accText);
        gr.addView(gl, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        satText = Ui.text(this, "", 13, Ui.INK, false);
        satText.setGravity(Gravity.END);
        gr.addView(satText);
        g.addView(gr);
        gnssHint = Ui.text(this, "", 12.5f, Ui.SUB, false);
        gnssHint.setPadding(0, dp(4), 0, 0);
        g.addView(gnssHint);
        c.addView(g, Ui.lp(this, 12));

        // yöntem + tabla
        setupCard = Ui.card(this);
        setupCard.addView(Ui.text(this, "Ölçüm yöntemi", 13, Ui.SUB, true));
        LinearLayout mr = Ui.row(this);
        mr.setPadding(0, dp(8), 0, dp(4));
        modeIz = Ui.button(this, "İZ\n(kenar turu)", Ui.GREEN, 0xFFFFFFFF, 14);
        modeKose = Ui.button(this, "KÖŞE\nNOKTALARI", Ui.GREEN_S, Ui.GREEN, 14);
        modeIz.setOnClickListener(v -> { Prefs.cornerMode(this, false); refresh(); });
        modeKose.setOnClickListener(v -> { Prefs.cornerMode(this, true); refresh(); });
        mr.addView(modeIz, Ui.weight(this, 1, 4));
        mr.addView(modeKose, Ui.weight(this, 1, 4));
        setupCard.addView(mr);

        LinearLayout tr = Ui.row(this);
        tr.setPadding(0, dp(10), 0, 0);
        TextView tl = Ui.text(this, "Tabla genişliği", 14, Ui.INK, true);
        tr.addView(tl, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView minus = Ui.button(this, "−", Ui.GOLD_S, Ui.INK, 20);
        TextView plus = Ui.button(this, "+", Ui.GOLD_S, Ui.INK, 20);
        minus.setPadding(dp(18), dp(4), dp(18), dp(4));
        plus.setPadding(dp(18), dp(4), dp(18), dp(4));
        tablaText = Ui.text(this, "", 18, Ui.INK, true);
        tablaText.setGravity(Gravity.CENTER);
        tablaText.setMinWidth(dp(86));
        tablaText.setOnClickListener(v -> editTabla());
        minus.setOnClickListener(v -> { Prefs.tabla(this, Math.max(0, Math.round((Prefs.tabla(this) - 0.1) * 10) / 10.0)); refresh(); });
        plus.setOnClickListener(v -> { Prefs.tabla(this, Math.min(15, Math.round((Prefs.tabla(this) + 0.1) * 10) / 10.0)); refresh(); });
        tr.addView(minus);
        tr.addView(tablaText);
        tr.addView(plus);
        setupCard.addView(tr);
        TextView th = Ui.text(this, "Tarla sınırı, makinenin izinden tabla genişliğinin yarısı kadar dışarıdadır; hesapta bu pay eklenir. Değere dokunarak elle yazabilirsiniz.", 12, Ui.SUB, false);
        th.setPadding(0, dp(6), 0, 0);
        setupCard.addView(th);
        c.addView(setupCard, Ui.lp(this, 10));

        // kroki
        field = new FieldView(this);
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(300));
        fp.setMargins(dp(14), dp(10), dp(14), 0);
        c.addView(field, fp);

        // canlı değerler
        liveCard = Ui.card(this);
        liveTitle = Ui.text(this, "Anlık alan (tabla payı dahil, yaklaşık)", 12.5f, Ui.SUB, false);
        liveCard.addView(liveTitle);
        LinearLayout ar = Ui.row(this);
        liveArea = Ui.text(this, "0,000", 34, Ui.GREEN, true);
        ar.addView(liveArea);
        TextView du = Ui.text(this, "  dönüm", 16, Ui.GREEN, true);
        ar.addView(du);
        liveCard.addView(ar);
        liveM2 = Ui.text(this, "", 13.5f, Ui.INK, false);
        liveCard.addView(liveM2);
        View sep = new View(this);
        sep.setBackgroundColor(Ui.LINE);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        sl.setMargins(0, dp(8), 0, dp(6));
        liveCard.addView(sep, sl);
        sTime = Ui.stat(this, liveCard, "Süre");
        sPath = Ui.stat(this, liveCard, "Kat edilen yol");
        sCount = Ui.stat(this, liveCard, "Kayıtlı nokta");
        sStart = Ui.stat(this, liveCard, "Başlangıca uzaklık");
        sRej = Ui.stat(this, liveCard, "Doğruluğu düşük, alınmayan");
        avgText = Ui.text(this, "", 15, Ui.GOLD, true);
        avgText.setGravity(Gravity.CENTER);
        avgText.setPadding(0, dp(8), 0, 0);
        liveCard.addView(avgText);
        c.addView(liveCard, Ui.lp(this, 10));

        // düğmeler
        buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.VERTICAL);
        c.addView(buttons, Ui.lp(this, 12));

        // kullanım
        LinearLayout help = Ui.card(this);
        help.addView(Ui.text(this, "Nasıl kullanılır?", 14, Ui.GREEN, true));
        helpText = Ui.text(this, "", 13, Ui.INK, false);
        helpText.setPadding(0, dp(6), 0, 0);
        helpText.setLineSpacing(dp(2), 1);
        help.addView(helpText);
        c.addView(help, Ui.lp(this, 12));

        LinearLayout links = Ui.row(this);
        TextView hist = Ui.button(this, "📋  Ölçümler", Ui.CARD, Ui.GREEN, 14);
        TextView set = Ui.button(this, "⚙  Ayarlar", Ui.CARD, Ui.GREEN, 14);
        hist.setOnClickListener(v -> startActivity(new Intent(this, HistoryActivity.class)));
        set.setOnClickListener(v -> settings());
        links.addView(hist, Ui.weight(this, 1, 4));
        links.addView(set, Ui.weight(this, 1, 4));
        c.addView(links, Ui.lp(this, 12));

        c.addView(Ui.footer(this));
        return root;
    }

    private void rebuildButtons() {
        int key = Session.state * 10 + (Session.cornerMode ? 1 : 0);
        if (key == lastButtonsKey) return;
        lastButtonsKey = key;
        buttons.removeAllViews();
        if (Session.state == Session.IDLE) {
            TextView start = Ui.button(this, "▶  BAŞLAT", Ui.GREEN, 0xFFFFFFFF, 20);
            start.setPadding(dp(12), dp(20), dp(12), dp(20));
            start.setOnClickListener(v -> requestStart(Prefs.cornerMode(this)));
            buttons.addView(start);
            return;
        }
        if (Session.cornerMode && Session.state == Session.REC) {
            TextView add = Ui.button(this, "📍  KÖŞE EKLE", Ui.GOLD, 0xFFFFFFFF, 20);
            add.setPadding(dp(12), dp(20), dp(12), dp(20));
            add.setOnClickListener(v -> {
                if (Session.averaging) Session.cancelCorner(); else Session.startCorner();
            });
            buttons.addView(add);
            LinearLayout r0 = Ui.row(this);
            r0.setPadding(0, dp(8), 0, 0);
            TextView undo = Ui.button(this, "↶  Son köşeyi sil", Ui.GOLD_S, Ui.INK, 14);
            undo.setOnClickListener(v -> confirm("Son köşe silinsin mi?", () -> Session.undoCorner(this)));
            r0.addView(undo, Ui.weight(this, 1, 0));
            buttons.addView(r0);
        }
        LinearLayout r = Ui.row(this);
        r.setPadding(0, dp(8), 0, 0);
        if (Session.state == Session.REC) {
            TextView pause = Ui.button(this, "⏸  DURAKLAT", Ui.GOLD_S, Ui.INK, 16);
            pause.setOnClickListener(v -> Session.pause(this));
            r.addView(pause, Ui.weight(this, 1, 4));
        } else {
            TextView res = Ui.button(this, "▶  DEVAM", Ui.GREEN_S, Ui.GREEN, 16);
            res.setOnClickListener(v -> resumeRec());
            r.addView(res, Ui.weight(this, 1, 4));
        }
        TextView fin = Ui.button(this, "■  BİTİR", Ui.RED, 0xFFFFFFFF, 16);
        fin.setOnClickListener(v -> finishDialog());
        r.addView(fin, Ui.weight(this, 1, 4));
        buttons.addView(r);
    }

    private void refresh() {
        if (accText == null) return;
        rebuildButtons();
        boolean idle = Session.state == Session.IDLE;
        boolean corner = idle ? Prefs.cornerMode(this) : Session.cornerMode;
        getWindow().getDecorView().post(() -> {
            if (Session.state != Session.IDLE) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        });

        // GNSS
        Location l = Session.last;
        boolean fresh = l != null && SystemClock.elapsedRealtime() - Session.lastFixAt < 5000;
        if (!hasLoc()) {
            accText.setText("İzin yok");
            accText.setTextColor(Ui.RED);
            gnssHint.setText("BAŞLAT'a basınca konum izni istenecek.");
        } else if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            accText.setText("GPS kapalı");
            accText.setTextColor(Ui.RED);
            gnssHint.setText("Telefonun Konum ayarını açın.");
        } else if (!fresh) {
            accText.setText("Aranıyor…");
            accText.setTextColor(Ui.GOLD);
            gnssHint.setText("Uydu sinyali bekleniyor. Açık havada 1–2 dakika sürebilir. İnternet gerekmez.");
        } else {
            float a = l.getAccuracy();
            accText.setText("± " + Fmt.num(a, 1) + " m");
            accText.setTextColor(a <= 3 ? 0xFF22A565 : a <= Prefs.maxAcc(this) * 0.6 ? Ui.GOLD : Ui.RED);
            gnssHint.setText(a <= 3 ? "Çok iyi. Ölçüme hazır."
                    : a <= Prefs.maxAcc(this) ? "Kullanılabilir. Biraz beklerseniz doğruluk artar."
                    : "Zayıf! ± " + Fmt.num(Prefs.maxAcc(this), 0) + " m'den kötü konumlar kaydedilmez.");
        }
        String sat = "Uydu " + Session.satsUsed + " / " + Session.satsVisible;
        sat += Session.dualFreq ? "\nÇift frekans ✓" : "\nTek frekans";
        if (fresh && l.hasSpeed()) sat += "\n" + Fmt.num(l.getSpeed() * 3.6, 1) + " km/sa";
        satText.setText(sat);

        // ayar kartı
        setupCard.setVisibility(idle ? View.VISIBLE : View.GONE);
        modeIz.setBackground(Ui.round(this, corner ? Ui.GREEN_S : Ui.GREEN, 16));
        modeIz.setTextColor(corner ? Ui.GREEN : 0xFFFFFFFF);
        modeKose.setBackground(Ui.round(this, corner ? Ui.GREEN : Ui.GREEN_S, 16));
        modeKose.setTextColor(corner ? 0xFFFFFFFF : Ui.GREEN);
        tablaText.setText(Fmt.num(Prefs.tabla(this), 1) + " m");

        // kroki
        FieldView.Data d = new FieldView.Data();
        d.path = new ArrayList<>(Session.trackXY);
        d.corners = new ArrayList<>(Session.cornerXY);
        if (!Session.cornerMode && Session.trackXY.size() >= 3) {
            d.iz = d.path;
            d.closeIz = true;
        }
        if (fresh && Session.proj != null) {
            d.current = Session.proj.fwd(l.getLatitude(), l.getLongitude());
            d.currentAcc = l.getAccuracy();
        }
        if (idle) d.label = corner ? "Köşe modunda her köşede KÖŞE EKLE'ye basın" : "BAŞLAT'a basın, tarlanın etrafını dönün";
        field.setData(d);

        // canlı
        liveCard.setVisibility(idle ? View.GONE : View.VISIBLE);
        if (!idle) {
            double a = Session.liveArea(true);
            liveArea.setText(Fmt.donum(a));
            liveM2.setText(Fmt.m2(a) + " m²   ·   tabla " + Fmt.num(Session.tabla, 1) + " m"
                    + (Session.state == Session.PAUSED ? "   ·   DURAKLATILDI" : ""));
            sTime[1].setText(Fmt.time(Session.elapsedMs()));
            sPath[1].setText(Fmt.m(Session.pathLen));
            sCount[0].setText(Session.cornerMode ? "Köşe sayısı" : "Kayıtlı nokta");
            sCount[1].setText(Session.cornerMode ? String.valueOf(Session.corners.size()) : String.valueOf(Session.track.size()));
            double ds = Session.distToStart();
            sStart[1].setText(ds < 0 ? "—" : Fmt.num(ds, 0) + " m");
            sStart[1].setTextColor(!Session.cornerMode && Session.returnNotified ? 0xFF22A565 : Ui.INK);
            sRej[1].setText(String.valueOf(Session.rejected));
            if (Session.averaging) {
                long left = Math.max(0, Session.avgUntil - SystemClock.elapsedRealtime());
                avgText.setText("⏳ Köşe " + (Session.corners.size() + 1) + " ölçülüyor… " + ((left + 999) / 1000)
                        + " sn  —  kıpırdamayın (iptal için tekrar basın)");
                avgText.setVisibility(View.VISIBLE);
            } else {
                avgText.setVisibility(View.GONE);
            }
        }

        helpText.setText(corner
                ? "1) Telefonu ön cama, gökyüzünü görecek şekilde sabitleyin.\n"
                + "2) Doğruluk ± 3–5 m'ye inince BAŞLAT'a basın.\n"
                + "3) Her tarla köşesinde durun, KÖŞE EKLE'ye basın ve " + Prefs.avgSec(this) + " sn kıpırdamayın.\n"
                + "4) Köşeleri tarlanın etrafında sırayla alın (atlamadan).\n"
                + "5) Bitince BİTİR. Yaya olarak tam köşede ölçtüyseniz sonuç ekranında \"tabla payı\"nı kapatın."
                : "1) Telefonu ön cama, gökyüzünü görecek şekilde sabitleyin.\n"
                + "2) Tabla genişliğini doğru girin.\n"
                + "3) Doğruluk ± 3–5 m'ye inince, biçerdöver ilk kenar turuna başlarken BAŞLAT'a basın.\n"
                + "4) Tablanın dış ucu tarla sınırını takip edecek şekilde tarlanın etrafını tam bir tur dönün.\n"
                + "5) Başlangıç noktasına dönünce telefon titrer; BİTİR'e basın. Biraz fazla gitmek sorun değil, fazlalık otomatik kırpılır.");
    }

    // ------------------------------------------------------------------ işlemler

    private void requestStart(boolean corner) {
        pendingCorner = corner;
        List<String> need = new ArrayList<>();
        if (!hasLoc()) {
            need.add(Manifest.permission.ACCESS_FINE_LOCATION);
            need.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            need.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (!need.isEmpty()) {
            requestPermissions(need.toArray(new String[0]), REQ_START);
            return;
        }
        doStart();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        if (code != REQ_START) return;
        if (hasLoc()) {
            startListening();
            doStart();
        } else {
            new AlertDialog.Builder(this)
                    .setTitle("Konum izni gerekli")
                    .setMessage("Alan ölçmek için \"Hassas konum\" izni verilmelidir.")
                    .setPositiveButton("Ayarları aç", (x, y) -> startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:" + getPackageName()))))
                    .setNegativeButton("Kapat", null)
                    .show();
        }
    }

    private void doStart() {
        if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            new AlertDialog.Builder(this)
                    .setTitle("Konum kapalı")
                    .setMessage("Telefonun Konum (GPS) ayarını açın.")
                    .setPositiveButton("Aç", (x, y) -> startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)))
                    .setNegativeButton("Vazgeç", null)
                    .show();
            return;
        }
        Location l = Session.last;
        boolean good = l != null && SystemClock.elapsedRealtime() - Session.lastFixAt < 5000
                && l.getAccuracy() <= Prefs.maxAcc(this);
        if (!good) {
            new AlertDialog.Builder(this)
                    .setTitle("Konum henüz hassas değil")
                    .setMessage("Uydu doğruluğu henüz yeterli değil. Yine de başlatırsanız düşük doğruluktaki konumlar kaydedilmez; "
                            + "doğruluk iyileşene kadar iz eksik kalabilir.")
                    .setPositiveButton("Yine de başlat", (x, y) -> reallyStart())
                    .setNegativeButton("Bekle", null)
                    .show();
            return;
        }
        reallyStart();
    }

    private void reallyStart() {
        Session.begin(this, pendingCorner);
        TrackService.start(this);
        askBattery();
        refresh();
    }

    private void resumeRec() {
        if (!hasLoc()) { requestStart(Session.cornerMode); return; }
        if (!TrackService.running) TrackService.start(this);
        Session.resume(this);
    }

    private void askBattery() {
        PowerManager pm = getSystemService(PowerManager.class);
        SharedPreferences sp = getSharedPreferences("ayarlar", MODE_PRIVATE);
        if (pm == null || pm.isIgnoringBatteryOptimizations(getPackageName()) || sp.getBoolean("pilSoruldu", false)) return;
        sp.edit().putBoolean("pilSoruldu", true).apply();
        new AlertDialog.Builder(this)
                .setTitle("Pil kısıtlaması")
                .setMessage("Ekran kapalıyken kaydın kesilmemesi için Bzk.Hasat'ın pil kısıtlamasını kaldırmanız önerilir (Xiaomi telefonlarda önemli).")
                .setPositiveButton("Kaldır", (x, y) -> openBattery())
                .setNegativeButton("Sonra", null)
                .show();
    }

    @SuppressLint("BatteryLife")
    private void openBattery() {
        try {
            startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName())));
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
        }
    }

    private void finishDialog() {
        int n = Session.cornerMode ? Session.corners.size() : Session.track.size();
        if ((Session.cornerMode && n < 3) || (!Session.cornerMode && n < 10)) {
            new AlertDialog.Builder(this)
                    .setTitle("Ölçüm yetersiz")
                    .setMessage(Session.cornerMode ? "Alan için en az 3 köşe gerekli." : "Henüz yeterli iz kaydedilmedi.")
                    .setPositiveButton("Devam et", null)
                    .setNegativeButton("Ölçümü sil", (x, y) -> discard())
                    .show();
            return;
        }
        EditText name = new EditText(this);
        name.setHint("Tarla adı / sahibi (isteğe bağlı)");
        name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        LinearLayout box = new LinearLayout(this);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        box.addView(name, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        new AlertDialog.Builder(this)
                .setTitle("Ölçümü bitir")
                .setView(box)
                .setPositiveButton("Kaydet ve hesapla", (x, y) -> saveAndShow(name.getText().toString()))
                .setNegativeButton("Vazgeç", null)
                .setNeutralButton("Sil", (x, y) -> confirm("Bu ölçüm kaydedilmeden silinsin mi?", this::discard))
                .show();
    }

    private void saveAndShow(String name) {
        try {
            Store.Olcum o = Store.fromSession(this, name.trim());
            TrackService.stop(this);
            Session.clearSaved(this);
            Session.reset();
            lastButtonsKey = -1;
            refresh();
            startActivity(new Intent(this, ResultActivity.class).putExtra("id", o.id));
        } catch (Exception e) {
            Toast.makeText(this, "Kaydedilemedi: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void discard() {
        TrackService.stop(this);
        Session.clearSaved(this);
        Session.reset();
        lastButtonsKey = -1;
        refresh();
    }

    private void askRestored() {
        int n = Session.cornerMode ? Session.corners.size() : Session.track.size();
        new AlertDialog.Builder(this)
                .setTitle("Yarım kalan ölçüm")
                .setMessage("Daha önce başlatılmış, bitirilmemiş bir ölçüm bulundu ("
                        + n + (Session.cornerMode ? " köşe" : " nokta") + ", " + Fmt.date(Session.startMs) + ").")
                .setPositiveButton("Devam et", (x, y) -> resumeRec())
                .setNeutralButton("Bitir ve hesapla", (x, y) -> finishDialog())
                .setNegativeButton("Sil", (x, y) -> confirm("Yarım ölçüm silinsin mi?", this::discard))
                .setCancelable(false)
                .show();
        refresh();
    }

    private void confirm(String msg, Runnable yes) {
        new AlertDialog.Builder(this)
                .setMessage(msg)
                .setPositiveButton("Evet", (x, y) -> yes.run())
                .setNegativeButton("Hayır", null)
                .show();
    }

    private EditText numField(String hint, String val) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setText(val);
        e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        return e;
    }

    private static double parse(String s, double def) {
        try { return Double.parseDouble(s.trim().replace(',', '.')); } catch (Exception e) { return def; }
    }

    private void editTabla() {
        EditText e = numField("metre", Fmt.num(Prefs.tabla(this), 2));
        LinearLayout box = new LinearLayout(this);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        box.addView(e, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        new AlertDialog.Builder(this)
                .setTitle("Tabla genişliği (m)")
                .setView(box)
                .setPositiveButton("Tamam", (x, y) -> {
                    Prefs.tabla(this, Math.max(0, Math.min(15, parse(e.getText().toString(), Prefs.tabla(this)))));
                    refresh();
                })
                .setNegativeButton("Vazgeç", null)
                .show();
    }

    private void settings() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(22), dp(8), dp(22), 0);
        box.addView(Ui.text(this, "Tabla genişliği (m)", 13, Ui.SUB, false));
        EditText t = numField("6", Fmt.num(Prefs.tabla(this), 2));
        box.addView(t);
        box.addView(Ui.text(this, "En kötü kabul edilen doğruluk (± m)", 13, Ui.SUB, false));
        EditText a = numField("10", Fmt.num(Prefs.maxAcc(this), 0));
        box.addView(a);
        box.addView(Ui.text(this, "Köşede ortalama süresi (sn)", 13, Ui.SUB, false));
        EditText s = numField("10", String.valueOf(Prefs.avgSec(this)));
        box.addView(s);
        TextView bat = Ui.button(this, "🔋  Pil kısıtlamasını kaldır", Ui.GOLD_S, Ui.INK, 14);
        bat.setOnClickListener(v -> openBattery());
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bp.setMargins(0, dp(12), 0, 0);
        box.addView(bat, bp);
        new AlertDialog.Builder(this)
                .setTitle("Ayarlar")
                .setView(box)
                .setPositiveButton("Kaydet", (x, y) -> {
                    Prefs.tabla(this, Math.max(0, Math.min(15, parse(t.getText().toString(), Prefs.tabla(this)))));
                    Prefs.maxAcc(this, Math.max(2, Math.min(50, parse(a.getText().toString(), Prefs.maxAcc(this)))));
                    Prefs.avgSec(this, (int) Math.max(3, Math.min(60, parse(s.getText().toString(), Prefs.avgSec(this)))));
                    refresh();
                })
                .setNegativeButton("Vazgeç", null)
                .show();
    }
}
