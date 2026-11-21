package com.yhan.hasat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
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
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity implements Session.Listener, LocationListener {

    private static final int REQ_START = 11, REQ_LOC = 12;

    private final Handler h = new Handler(Looper.getMainLooper());
    private LocationManager lm;
    private boolean listening, resumed;
    private int pendingMode;

    private View accDot;
    private TextView accText, satText, gnssHint;
    private LinearLayout setupCard, tablaRow, liveCard, controls;
    private TextView[] modeSegs;
    private TextView tablaText, liveArea, liveM2, avgText, mapHint, areaChip;
    private TextView tTime, tPath, tCount, tStart, tCountLbl;
    private FieldView field;
    private int lastControlsKey = -1;
    private AlertDialog locDialog;
    private TextView locText;

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
        seedLastKnown();
        if (Session.state == Session.IDLE && Session.restore(this)) {
            h.postDelayed(this::askRestored, 400);
        } else if (!hasLoc()) {
            // konumu baştan isteyelim ki BAŞLAT'a basıldığında uydu hazır olsun
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOC);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        Session.addListener(this);
        startListening();
        h.post(ticker);
        if (Session.state == Session.IDLE && Session.pendingResultId != null) openResult(Session.pendingResultId);
    }

    @Override
    protected void onPause() {
        super.onPause();
        resumed = false;
        Session.removeListener(this);
        stopListening();
        h.removeCallbacks(ticker);
    }

    @Override
    public void onSessionChanged() { refresh(); }

    @Override
    public void onMessage(String msg) { Toast.makeText(this, msg, Toast.LENGTH_LONG).show(); }

    @Override
    public void onFinished(String id) {
        lastControlsKey = -1;
        if (resumed) openResult(id);
    }

    private void openResult(String id) {
        Session.pendingResultId = null;
        startActivity(new Intent(this, ResultActivity.class).putExtra("id", id));
    }

    // ------------------------------------------------------------------ konum (gösterim)

    private boolean hasLoc() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    @SuppressLint("MissingPermission")
    private void seedLastKnown() {
        if (!hasLoc() || Session.last != null) return;
        try {
            Location l = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (l != null) Session.last = l;
        } catch (Exception ignored) {
        }
    }

    @SuppressLint("MissingPermission")
    private void startListening() {
        if (listening || !hasLoc()) return;
        try {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 500L, 0f, this, Looper.getMainLooper());
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
        if (!TrackService.running) Session.onFix(this, loc, false);
        if (locDialog != null && locDialog.isShowing()) updateLocDialog();
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
        root.addView(Ui.header(this, "ALAN ÖLÇER"));

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setVerticalScrollBarEnabled(false);
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        sv.addView(c);
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        // ---- GNSS durumu
        LinearLayout g = Ui.card(this);
        LinearLayout gr = Ui.row(this);
        accDot = new View(this);
        accDot.setBackground(Ui.round(this, Ui.SUB, 8));
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(dp(12), dp(12));
        dl.setMargins(0, 0, dp(10), 0);
        gr.addView(accDot, dl);
        accText = Ui.text(this, "—", 24, Ui.INK, true);
        gr.addView(accText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        satText = Ui.text(this, "", 12.5f, Ui.SUB, false);
        satText.setGravity(Gravity.END);
        satText.setLineSpacing(dp(2), 1);
        gr.addView(satText);
        g.addView(gr);
        gnssHint = Ui.text(this, "", 12.5f, Ui.SUB, false);
        gnssHint.setPadding(dp(22), dp(6), 0, 0);
        g.addView(gnssHint);
        c.addView(g, Ui.lp(this, 14));

        // ---- yöntem
        setupCard = Ui.card(this);
        modeSegs = Ui.segments(this, setupCard, new String[]{"🚶  YAYA", "🚜  BİÇERDÖVER", "📍  KÖŞE"});
        for (int i = 0; i < modeSegs.length; i++) {
            final int m = i;
            modeSegs[i].setOnClickListener(v -> { Prefs.mode(this, m); refresh(); });
        }
        tablaRow = Ui.row(this);
        tablaRow.setPadding(dp(4), dp(14), dp(4), dp(2));
        tablaRow.addView(Ui.text(this, "Tabla genişliği", 14.5f, Ui.INK, false), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView minus = Ui.button(this, "−", Ui.BG, Ui.INK, 20);
        TextView plus = Ui.button(this, "+", Ui.BG, Ui.INK, 20);
        minus.setPadding(dp(18), dp(4), dp(18), dp(6));
        plus.setPadding(dp(18), dp(4), dp(18), dp(6));
        tablaText = Ui.text(this, "", 18, Ui.INK, true);
        tablaText.setGravity(Gravity.CENTER);
        tablaText.setMinWidth(dp(84));
        tablaText.setOnClickListener(v -> editTabla());
        minus.setOnClickListener(v -> { Prefs.tabla(this, Math.max(0, Math.round((Prefs.tabla(this) - 0.1) * 10) / 10.0)); refresh(); });
        plus.setOnClickListener(v -> { Prefs.tabla(this, Math.min(15, Math.round((Prefs.tabla(this) + 0.1) * 10) / 10.0)); refresh(); });
        tablaRow.addView(minus);
        tablaRow.addView(tablaText);
        tablaRow.addView(plus);
        setupCard.addView(tablaRow);
        c.addView(setupCard, Ui.lp(this, 12));

        // ---- kroki + üstündeki düğmeler
        FrameLayout map = new FrameLayout(this);
        field = new FieldView(this);
        field.setOnStartTap(this::onStartTapped);
        map.addView(field, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        areaChip = Ui.text(this, "", 14, 0xFFFFFFFF, true);
        areaChip.setPadding(dp(12), dp(7), dp(12), dp(7));
        areaChip.setBackground(Ui.round(this, 0x99000000, 14));
        FrameLayout.LayoutParams ap = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
        ap.setMargins(dp(12), dp(12), 0, 0);
        map.addView(areaChip, ap);
        TextView getLoc = Ui.text(this, "◎  Konum Getir", 13.5f, Ui.GREEN, false);
        getLoc.setTypeface(Ui.MED);
        getLoc.setPadding(dp(14), dp(10), dp(14), dp(10));
        getLoc.setBackground(Ui.ripple(Ui.round(this, 0xF2FFFFFF, 20), 0x33000000));
        getLoc.setElevation(dp(4));
        getLoc.setOnClickListener(v -> showLocation());
        FrameLayout.LayoutParams gp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.END);
        gp.setMargins(0, 0, dp(12), dp(12));
        map.addView(getLoc, gp);
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(340));
        mp.setMargins(dp(16), dp(12), dp(16), 0);
        c.addView(map, mp);
        mapHint = Ui.text(this, "", 12, Ui.SUB, false);
        mapHint.setGravity(Gravity.CENTER);
        mapHint.setPadding(dp(16), dp(8), dp(16), 0);
        c.addView(mapHint);

        // ---- canlı alan
        liveCard = Ui.card(this);
        liveCard.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView lt = Ui.text(this, "ANLIK ALAN", 11.5f, Ui.SUB, true);
        lt.setLetterSpacing(0.15f);
        liveCard.addView(lt);
        LinearLayout ar = Ui.row(this);
        ar.setGravity(Gravity.CENTER);
        ar.setPadding(0, dp(6), 0, 0);
        liveArea = Ui.text(this, "0,000", 40, Ui.GREEN, true);
        ar.addView(liveArea);
        TextView du = Ui.text(this, "  dönüm", 16, Ui.GREEN, false);
        du.setTypeface(Ui.MED);
        ar.addView(du);
        liveCard.addView(ar);
        liveM2 = Ui.text(this, "", 14, Ui.INK, false);
        liveM2.setPadding(0, dp(4), 0, dp(12));
        liveCard.addView(liveM2);
        LinearLayout tiles = Ui.row(this);
        Object[] a1 = Ui.tile(this, "Süre"), a2 = Ui.tile(this, "Yol"), a3 = Ui.tile(this, "Nokta"), a4 = Ui.tile(this, "Başlangıca");
        tTime = (TextView) a1[1]; tPath = (TextView) a2[1]; tCount = (TextView) a3[1]; tStart = (TextView) a4[1];
        tCountLbl = (TextView) a3[2];
        for (Object[] t : new Object[][]{a1, a2, a3, a4}) tiles.addView((View) t[0], Ui.weight(this, 1, 3));
        liveCard.addView(tiles, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        avgText = Ui.text(this, "", 14.5f, Ui.GOLD, true);
        avgText.setGravity(Gravity.CENTER);
        avgText.setPadding(0, dp(12), 0, 0);
        liveCard.addView(avgText);
        c.addView(liveCard, Ui.lp(this, 12));

        // ---- kontrol düğmeleri
        controls = Ui.row(this);
        controls.setGravity(Gravity.CENTER);
        controls.setPadding(0, dp(18), 0, dp(6));
        c.addView(controls, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // ---- bağlantılar
        LinearLayout links = Ui.row(this);
        TextView hist = Ui.button(this, "ÖLÇÜMLER", Ui.CARD, Ui.GREEN, 14);
        TextView set = Ui.button(this, "AYARLAR", Ui.CARD, Ui.GREEN, 14);
        hist.setElevation(dp(1));
        set.setElevation(dp(1));
        hist.setOnClickListener(v -> startActivity(new Intent(this, HistoryActivity.class)));
        set.setOnClickListener(v -> settings());
        links.addView(hist, Ui.weight(this, 1, 5));
        links.addView(set, Ui.weight(this, 1, 5));
        c.addView(links, Ui.lp(this, 16));

        c.addView(Ui.footer(this));
        return root;
    }

    private View spacer(int wDp) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(wDp), 1));
        return v;
    }

    private LinearLayout labeled(TextView circle, String label) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setGravity(Gravity.CENTER_HORIZONTAL);
        l.addView(circle);
        TextView t = Ui.text(this, label, 12, Ui.SUB, false);
        t.setTypeface(Ui.MED);
        t.setPadding(0, dp(8), 0, 0);
        l.addView(t);
        return l;
    }

    private void rebuildControls() {
        int key = Session.state * 10 + (Session.cornerMode ? 1 : 0);
        if (key == lastControlsKey) return;
        lastControlsKey = key;
        controls.removeAllViews();
        if (Session.state == Session.IDLE) {
            TextView start = Ui.circle(this, "BAŞLAT", Ui.OK, Ui.GREEN_D, 17, 116);
            start.setOnClickListener(v -> requestStart(Prefs.mode(this)));
            controls.addView(start);
            return;
        }
        if (Session.state == Session.REC) {
            TextView pause = Ui.circle(this, "❚❚", 0xFFF2C14E, 0xFFD9971A, 20, 68);
            pause.setOnClickListener(v -> Session.pause(this));
            controls.addView(labeled(pause, "Duraklat"));
        } else {
            TextView res = Ui.circle(this, "▶", Ui.OK, Ui.GREEN_D, 22, 68);
            res.setOnClickListener(v -> resumeRec());
            controls.addView(labeled(res, "Devam"));
        }
        if (Session.cornerMode && Session.state == Session.REC) {
            controls.addView(spacer(18));
            TextView add = Ui.circle(this, "KÖŞE\nEKLE", 0xFF4F8CF7, 0xFF2D5FC4, 15, 104);
            add.setOnClickListener(v -> { if (Session.averaging) Session.cancelCorner(); else Session.startCorner(); });
            add.setOnLongClickListener(v -> { confirm("Son köşe silinsin mi?", () -> Session.undoCorner(this)); return true; });
            controls.addView(labeled(add, "Uzun bas: son köşeyi sil"));
        }
        controls.addView(spacer(18));
        TextView fin = Ui.circle(this, "■", 0xFFE35D5D, 0xFFB83535, 22, 68);
        fin.setOnClickListener(v -> finishNow());
        controls.addView(labeled(fin, "Bitir · Alan"));
    }

    private void refresh() {
        if (accText == null) return;
        rebuildControls();
        boolean idle = Session.state == Session.IDLE;
        int mode = idle ? Prefs.mode(this) : Session.mode;
        if (idle) getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // GNSS
        Location l = Session.last;
        boolean fresh = l != null && SystemClock.elapsedRealtime() - Session.lastFixAt < 5000;
        double maxAcc = Prefs.maxAcc(this);
        int dot;
        if (!hasLoc()) {
            accText.setText("Konum izni yok");
            gnssHint.setText("BAŞLAT'a basınca izin istenecek.");
            dot = Ui.RED;
        } else if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            accText.setText("GPS kapalı");
            gnssHint.setText("Telefonun Konum ayarını açın.");
            dot = Ui.RED;
        } else if (!fresh) {
            accText.setText("Uydu aranıyor…");
            gnssHint.setText("Açık havada birkaç saniye – 1 dakika. İnternet gerekmez.");
            dot = Ui.GOLD;
        } else {
            float a = l.getAccuracy();
            accText.setText("± " + Fmt.num(a, 1) + " m");
            dot = a <= 3 ? Ui.OK : a <= maxAcc ? Ui.GOLD : Ui.RED;
            gnssHint.setText(a <= 3 ? "Çok iyi · ölçüme hazır" : a <= maxAcc ? "Kullanılabilir · birkaç saniye beklerseniz iyileşir"
                    : "Zayıf · ± " + Fmt.num(maxAcc, 0) + " m'den kötü konum kaydedilmez");
        }
        accDot.setBackground(Ui.round(this, dot, 8));
        String sat = "Uydu " + Session.satsUsed + " / " + Session.satsVisible + "\n" + (Session.dualFreq ? "L1 + L5 ✓" : "L1");
        if (fresh && l.hasSpeed() && l.getSpeed() > 0.3f) sat += " · " + Fmt.num(l.getSpeed() * 3.6, 1) + " km/sa";
        satText.setText(sat);

        // yöntem
        setupCard.setVisibility(idle ? View.VISIBLE : View.GONE);
        Ui.select(this, modeSegs, mode);
        tablaRow.setVisibility(mode == Prefs.BICERDOVER ? View.VISIBLE : View.GONE);
        tablaText.setText(Fmt.num(Prefs.tabla(this), 1) + " m");

        // kroki
        FieldView.Data d = new FieldView.Data();
        Geo.Proj pj = Session.proj;
        if (pj == null && fresh) pj = new Geo.Proj(l.getLatitude(), l.getLongitude());
        d.path = new ArrayList<>(Session.trackXY);
        d.corners = new ArrayList<>(Session.cornerXY);
        if (!Session.cornerMode && Session.trackXY.size() >= 3) { d.iz = d.path; d.closeIz = true; }
        if (fresh && pj != null) {
            d.current = pj.fwd(l.getLatitude(), l.getLongitude());
            d.currentAcc = l.getAccuracy();
        }
        d.highlightStart = !idle && Session.canFinish();
        if (idle) d.label = fresh ? null : "Konum bekleniyor…";
        field.setData(d);

        if (idle) {
            mapHint.setText(mode == Prefs.KOSE ? "Her köşede KÖŞE EKLE'ye basın; ilk köşeye dokununca alan hesaplanır."
                    : "Başlangıç noktasına dönünce alan kendiliğinden hesaplanır.");
            areaChip.setVisibility(View.GONE);
        } else {
            mapHint.setText(Session.canFinish() ? "Bitirmek için yeşil başlangıç noktasına dokunun." : "");
            areaChip.setVisibility(View.VISIBLE);
            areaChip.setText(Fmt.donum(Session.liveArea()) + " dönüm");
        }

        // canlı
        liveCard.setVisibility(idle ? View.GONE : View.VISIBLE);
        if (!idle) {
            double a = Session.liveArea();
            liveArea.setText(Fmt.donum(a));
            String m2 = Fmt.m2(a) + " m²";
            if (Session.mode == Prefs.BICERDOVER) m2 += "  ·  tabla " + Fmt.num(Session.tabla, 1) + " m";
            if (Session.state == Session.PAUSED) m2 += "  ·  DURAKLATILDI";
            liveM2.setText(m2);
            tTime.setText(Fmt.time(Session.elapsedMs()));
            tPath.setText(Fmt.m(Session.pathLen));
            tCountLbl.setText(Session.cornerMode ? "Köşe" : "Nokta");
            tCount.setText(String.valueOf(Session.cornerMode ? Session.corners.size() : Session.track.size()));
            double ds = Session.distToStart();
            tStart.setText(ds < 0 ? "—" : Fmt.num(ds, 0) + " m");
            tStart.setTextColor(Session.inZone ? Ui.OK : Ui.INK);
            if (Session.averaging) {
                long left = Math.max(0, Session.avgUntil - SystemClock.elapsedRealtime());
                avgText.setText("Köşe " + (Session.corners.size() + 1) + " ölçülüyor… " + ((left + 999) / 1000) + " sn · kıpırdamayın");
                avgText.setVisibility(View.VISIBLE);
            } else {
                avgText.setVisibility(View.GONE);
            }
        }
    }

    // ------------------------------------------------------------------ işlemler

    private void onStartTapped() {
        if (Session.state == Session.IDLE) return;
        if (!Session.canFinish()) {
            Toast.makeText(this, "Alan için önce etrafını dolaşın.", Toast.LENGTH_SHORT).show();
            return;
        }
        Session.finish(this, false);
    }

    private void finishNow() {
        if (Session.canFinish()) {
            Session.finish(this, false);
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Ölçüm yetersiz")
                .setMessage(Session.cornerMode ? "Alan için en az 3 köşe gerekli." : "Henüz yeterli iz kaydedilmedi.")
                .setPositiveButton("Devam et", null)
                .setNegativeButton("Ölçümü sil", (x, y) -> discard())
                .show();
    }

    private void requestStart(int mode) {
        pendingMode = mode;
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
        if (hasLoc()) {
            seedLastKnown();
            startListening();
            if (code == REQ_START) doStart();
        } else if (code == REQ_START) {
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
                    .setMessage("Doğruluk yeterli olana kadar konumlar kaydedilmez. Birkaç saniye beklemeniz önerilir.")
                    .setPositiveButton("Yine de başlat", (x, y) -> reallyStart())
                    .setNegativeButton("Bekle", null)
                    .show();
            return;
        }
        reallyStart();
    }

    private void reallyStart() {
        Session.begin(this, pendingMode);
        TrackService.start(this);
        askBattery();
        refresh();
    }

    private void resumeRec() {
        if (!hasLoc()) { requestStart(Session.mode); return; }
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
                .setMessage("Ekran kapalıyken ölçümün kesilmemesi için pil kısıtlamasını kaldırmanız önerilir (Xiaomi'de önemli).")
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

    private void discard() {
        TrackService.stop(this);
        Session.clearSaved(this);
        Session.reset();
        lastControlsKey = -1;
        refresh();
    }

    private void askRestored() {
        int n = Session.cornerMode ? Session.corners.size() : Session.track.size();
        new AlertDialog.Builder(this)
                .setTitle("Yarım kalan ölçüm")
                .setMessage("Bitirilmemiş bir ölçüm bulundu (" + n + (Session.cornerMode ? " köşe" : " nokta") + ", "
                        + Fmt.date(Session.startMs) + ").")
                .setPositiveButton("Devam et", (x, y) -> resumeRec())
                .setNeutralButton("Alanı hesapla", (x, y) -> finishNow())
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

    // ------------------------------------------------------------------ Konum Getir

    private void showLocation() {
        if (!hasLoc()) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOC);
            return;
        }
        startListening();
        locText = Ui.text(this, "", 15, Ui.INK, false);
        locText.setLineSpacing(dp(4), 1);
        locText.setTextIsSelectable(true);
        locText.setPadding(dp(24), dp(12), dp(24), 0);
        locDialog = new AlertDialog.Builder(this)
                .setTitle("◎  Anlık Konum")
                .setView(locText)
                .setPositiveButton("Kopyala", null)
                .setNeutralButton("Haritada aç", null)
                .setNegativeButton("Kapat", null)
                .create();
        locDialog.setOnShowListener(x -> {
            locDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                Location l = Session.last;
                if (l == null) return;
                ClipboardManager cm = getSystemService(ClipboardManager.class);
                cm.setPrimaryClip(ClipData.newPlainText("Konum", String.format(Locale.US, "%.8f, %.8f", l.getLatitude(), l.getLongitude())));
                Toast.makeText(this, "Koordinat kopyalandı", Toast.LENGTH_SHORT).show();
            });
            locDialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                Location l = Session.last;
                if (l == null) return;
                String q = String.format(Locale.US, "%.8f,%.8f", l.getLatitude(), l.getLongitude());
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("geo:" + q + "?q=" + q)));
                } catch (Exception e) {
                    Toast.makeText(this, "Harita uygulaması bulunamadı", Toast.LENGTH_SHORT).show();
                }
            });
        });
        locDialog.show();
        updateLocDialog();
        h.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (locDialog == null || !locDialog.isShowing()) return;
                updateLocDialog();
                h.postDelayed(this, 1000);
            }
        }, 1000);
    }

    private void updateLocDialog() {
        if (locText == null) return;
        Location l = Session.last;
        boolean fresh = l != null && SystemClock.elapsedRealtime() - Session.lastFixAt < 5000;
        if (l == null) {
            locText.setText("Uydu sinyali bekleniyor…");
            return;
        }
        double la = l.getLatitude(), lo = l.getLongitude();
        int dom = (int) (Math.round(lo / 3.0) * 3);
        double[] tm = new Geo.Proj(0, dom).fwd(la, lo);
        StringBuilder sb = new StringBuilder();
        sb.append("Enlem   ").append(String.format(Locale.US, "%.8f°", la)).append('\n');
        sb.append("Boylam  ").append(String.format(Locale.US, "%.8f°", lo)).append('\n');
        sb.append("         ").append(dms(la, "K", "G")).append("  ").append(dms(lo, "D", "B")).append("\n\n");
        sb.append("TM 3° · DOM ").append(dom).append('\n');
        sb.append("Y = ").append(String.format(Locale.US, "%.2f", tm[0] + 500000)).append('\n');
        sb.append("X = ").append(String.format(Locale.US, "%.2f", tm[1])).append("\n\n");
        if (l.hasAltitude()) sb.append("Yükseklik (elipsoit)  ").append(Fmt.num(l.getAltitude(), 1)).append(" m\n");
        sb.append("Doğruluk  ± ").append(Fmt.num(l.getAccuracy(), 1)).append(" m");
        sb.append("   ·   Uydu ").append(Session.satsUsed).append('/').append(Session.satsVisible);
        if (!fresh) sb.append("\n(son bilinen konum – güncelleniyor…)");
        locText.setText(sb.toString());
    }

    private static String dms(double v, String pos, String neg) {
        String h = v >= 0 ? pos : neg;
        v = Math.abs(v);
        int d = (int) v;
        double mf = (v - d) * 60;
        int m = (int) mf;
        double s = (mf - m) * 60;
        return String.format(Locale.US, "%d°%02d'%05.2f\"%s", d, m, s, h);
    }

    // ------------------------------------------------------------------ ayarlar

    private EditText numField(String val) {
        EditText e = new EditText(this);
        e.setText(val);
        e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        return e;
    }

    private static double parse(String s, double def) {
        try { return Double.parseDouble(s.trim().replace(',', '.')); } catch (Exception e) { return def; }
    }

    private void editTabla() {
        EditText e = numField(Fmt.num(Prefs.tabla(this), 2));
        LinearLayout box = new LinearLayout(this);
        box.setPadding(dp(22), dp(8), dp(22), 0);
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
        box.setPadding(dp(24), dp(10), dp(24), 0);
        Switch auto = new Switch(this);
        auto.setText("Başlangıca dönünce alanı kendiliğinden hesapla");
        auto.setTextSize(14);
        auto.setChecked(Prefs.autoFinish(this));
        box.addView(auto);
        TextView l1 = Ui.text(this, "Tabla genişliği (m)", 13, Ui.SUB, false);
        l1.setPadding(0, dp(14), 0, 0);
        box.addView(l1);
        EditText t = numField(Fmt.num(Prefs.tabla(this), 2));
        box.addView(t);
        TextView l2 = Ui.text(this, "En kötü kabul edilen doğruluk (± m)", 13, Ui.SUB, false);
        l2.setPadding(0, dp(10), 0, 0);
        box.addView(l2);
        EditText a = numField(Fmt.num(Prefs.maxAcc(this), 0));
        box.addView(a);
        TextView l3 = Ui.text(this, "Köşede ortalama süresi (sn)", 13, Ui.SUB, false);
        l3.setPadding(0, dp(10), 0, 0);
        box.addView(l3);
        EditText s = numField(String.valueOf(Prefs.avgSec(this)));
        box.addView(s);
        TextView bat = Ui.button(this, "Pil kısıtlamasını kaldır", Ui.GOLD_S, Ui.INK, 14);
        bat.setOnClickListener(v -> openBattery());
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bp.setMargins(0, dp(14), 0, 0);
        box.addView(bat, bp);
        ScrollView sv = new ScrollView(this);
        sv.addView(box);
        new AlertDialog.Builder(this)
                .setTitle("AYARLAR")
                .setView(sv)
                .setPositiveButton("Kaydet", (x, y) -> {
                    Prefs.autoFinish(this, auto.isChecked());
                    Prefs.tabla(this, Math.max(0, Math.min(15, parse(t.getText().toString(), Prefs.tabla(this)))));
                    Prefs.maxAcc(this, Math.max(2, Math.min(50, parse(a.getText().toString(), Prefs.maxAcc(this)))));
                    Prefs.avgSec(this, (int) Math.max(3, Math.min(60, parse(s.getText().toString(), Prefs.avgSec(this)))));
                    refresh();
                })
                .setNegativeButton("Vazgeç", null)
                .show();
    }
}
