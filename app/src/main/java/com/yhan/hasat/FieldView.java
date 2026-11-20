package com.yhan.hasat;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/** Tarla krokisi: GPS yolu, makine izi çokgeni, tarla sınırı, köşeler, anlık konum. Kuzey yukarı. */
public class FieldView extends View {

    /** Çizilecek veriler (metre, yerel projeksiyon). */
    static final class Data {
        List<double[]> path = new ArrayList<>();
        List<double[]> iz = new ArrayList<>();
        List<double[]> field = new ArrayList<>();
        List<double[]> corners = new ArrayList<>();
        double[] current;
        double currentAcc;
        boolean closeIz;
        String label;
    }

    private Data data = new Data();
    private final float d;

    public FieldView(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
    }

    void setData(Data x) {
        data = x;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        render(c, new RectF(0, 0, getWidth(), getHeight()), data, d);
    }

    static void render(Canvas c, RectF box, Data x, float d) {
        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setColor(0xFF24392C);
        c.drawRoundRect(box, 18 * d, 18 * d, bg);

        // ince ızgara
        Paint grid = new Paint();
        grid.setColor(0x14FFFFFF);
        grid.setStrokeWidth(1);
        for (float gx = box.left + 24 * d; gx < box.right; gx += 24 * d) c.drawLine(gx, box.top + 6 * d, gx, box.bottom - 6 * d, grid);
        for (float gy = box.top + 24 * d; gy < box.bottom; gy += 24 * d) c.drawLine(box.left + 6 * d, gy, box.right - 6 * d, gy, grid);

        double minX = 1e18, minY = 1e18, maxX = -1e18, maxY = -1e18;
        List<List<double[]>> all = new ArrayList<>();
        all.add(x.path); all.add(x.iz); all.add(x.field); all.add(x.corners);
        for (List<double[]> l : all) for (double[] q : l) {
            minX = Math.min(minX, q[0]); maxX = Math.max(maxX, q[0]);
            minY = Math.min(minY, q[1]); maxY = Math.max(maxY, q[1]);
        }
        if (x.current != null) {
            minX = Math.min(minX, x.current[0]); maxX = Math.max(maxX, x.current[0]);
            minY = Math.min(minY, x.current[1]); maxY = Math.max(maxY, x.current[1]);
        }
        Paint txt = new Paint(Paint.ANTI_ALIAS_FLAG);
        txt.setColor(0xCCFFFFFF);
        txt.setTextSize(13 * d);
        txt.setTextAlign(Paint.Align.CENTER);
        if (minX > maxX) {
            c.drawText(x.label != null ? x.label : "Kayıt başlayınca kroki burada çizilir",
                    box.centerX(), box.centerY(), txt);
            return;
        }
        double span = Math.max(Math.max(maxX - minX, maxY - minY), 30);
        float pad = 26 * d;
        float w = box.width() - 2 * pad, h = box.height() - 2 * pad;
        double sc = Math.min(w / span, h / span);
        if (maxX - minX > 0 && maxY - minY > 0) sc = Math.min(w / Math.max(maxX - minX, 30), h / Math.max(maxY - minY, 30));
        final double s = sc;
        final double cx = (minX + maxX) / 2, cy = (minY + maxY) / 2;
        final float ox = box.centerX(), oy = box.centerY();

        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setStrokeCap(Paint.Cap.ROUND);

        // tarla sınırı (dolgu + kalın sarı çizgi)
        if (x.field.size() >= 3) {
            Path fp = path(x.field, true, s, cx, cy, ox, oy);
            p.setStyle(Paint.Style.FILL);
            p.setColor(0x40E0A526);
            c.drawPath(fp, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(3 * d);
            p.setColor(0xFFF4C542);
            c.drawPath(fp, p);
        }
        // GPS yolu
        if (x.path.size() >= 2) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1.6f * d);
            p.setColor(0x99FFFFFF);
            c.drawPath(path(x.path, false, s, cx, cy, ox, oy), p);
        }
        // makine izi çokgeni (kesikli yeşil)
        if (x.iz.size() >= 3) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2 * d);
            p.setColor(0xFF7FD69A);
            p.setPathEffect(new DashPathEffect(new float[]{8 * d, 5 * d}, 0));
            c.drawPath(path(x.iz, x.closeIz, s, cx, cy, ox, oy), p);
            p.setPathEffect(null);
        }
        // köşeler
        Paint kp = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint kt = new Paint(Paint.ANTI_ALIAS_FLAG);
        kt.setColor(0xFF1F3B2C);
        kt.setTextSize(10 * d);
        kt.setTypeface(Typeface.DEFAULT_BOLD);
        kt.setTextAlign(Paint.Align.CENTER);
        if (x.corners.size() >= 2) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2.4f * d);
            p.setColor(0xFFF4C542);
            c.drawPath(path(x.corners, x.corners.size() >= 3, s, cx, cy, ox, oy), p);
        }
        for (int i = 0; i < x.corners.size(); i++) {
            double[] q = x.corners.get(i);
            float px = (float) (ox + (q[0] - cx) * s), py = (float) (oy - (q[1] - cy) * s);
            kp.setColor(0xFFF4C542);
            c.drawCircle(px, py, 9 * d, kp);
            c.drawText(String.valueOf(i + 1), px, py + 3.5f * d, kt);
        }
        // başlangıç noktası
        if (!x.path.isEmpty()) {
            double[] q = x.path.get(0);
            float px = (float) (ox + (q[0] - cx) * s), py = (float) (oy - (q[1] - cy) * s);
            kp.setColor(0xFFFFFFFF);
            c.drawCircle(px, py, 6 * d, kp);
            kp.setColor(0xFF22A565);
            c.drawCircle(px, py, 4 * d, kp);
        }
        // anlık konum
        if (x.current != null) {
            float px = (float) (ox + (x.current[0] - cx) * s), py = (float) (oy - (x.current[1] - cy) * s);
            kp.setColor(0x403D9BF0);
            c.drawCircle(px, py, (float) Math.max(8 * d, x.currentAcc * s), kp);
            kp.setColor(0xFFFFFFFF);
            c.drawCircle(px, py, 7 * d, kp);
            kp.setColor(0xFF3D7BF0);
            c.drawCircle(px, py, 5 * d, kp);
        }
        // kuzey oku
        float nx = box.right - 18 * d, ny = box.top + 14 * d;
        Path n = new Path();
        n.moveTo(nx, ny);
        n.lineTo(nx - 5 * d, ny + 13 * d);
        n.lineTo(nx, ny + 10 * d);
        n.lineTo(nx + 5 * d, ny + 13 * d);
        n.close();
        kp.setColor(0xFFFFFFFF);
        c.drawPath(n, kp);
        txt.setTextSize(10 * d);
        c.drawText("K", nx, ny + 25 * d, txt);
        // ölçek çubuğu
        double target = (box.width() * 0.28) / s;
        double[] steps = {5, 10, 20, 25, 50, 100, 200, 250, 500, 1000, 2000};
        double len = steps[0];
        for (double st : steps) if (st <= target) len = st;
        float bx = box.left + 14 * d, by = box.bottom - 14 * d, bl = (float) (len * s);
        Paint sp = new Paint(Paint.ANTI_ALIAS_FLAG);
        sp.setColor(0xFFFFFFFF);
        sp.setStrokeWidth(2 * d);
        c.drawLine(bx, by, bx + bl, by, sp);
        c.drawLine(bx, by - 4 * d, bx, by + 1 * d, sp);
        c.drawLine(bx + bl, by - 4 * d, bx + bl, by + 1 * d, sp);
        txt.setTextAlign(Paint.Align.LEFT);
        c.drawText((int) len + " m", bx + bl + 6 * d, by + 4 * d, txt);
    }

    private static Path path(List<double[]> l, boolean close, double s, double cx, double cy, float ox, float oy) {
        Path p = new Path();
        for (int i = 0; i < l.size(); i++) {
            double[] q = l.get(i);
            float px = (float) (ox + (q[0] - cx) * s), py = (float) (oy - (q[1] - cy) * s);
            if (i == 0) p.moveTo(px, py); else p.lineTo(px, py);
        }
        if (close) p.close();
        return p;
    }
}
