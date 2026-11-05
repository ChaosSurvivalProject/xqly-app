package cn.xqly.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;

/**
 * 液态玻璃底部 Dock 导航栏（仿 Apple macOS / iOS 26 设计语言）
 *  - 统一的玻璃药丸容器，与侧边药丸共用同一套材质
 *  - macOS 风格「放大镜」效果：靠近指针的图标平滑放大并溢出相邻项
 *  - 选中项内嵌磨砂玻璃药丸（iOS/macOS 标签栏「悬浮玻璃」观感）
 *  - 顶部受光高光、同心圆角，跟随内容折射
 *  - 滚动时收拢、上滚时展开（iOS 26 标签栏行为）
 */
public class GlassDock extends LinearLayout {

    public interface OnDockClick { void onClick(int index, String path); }

    private static final String[][] ITEMS = {
            {"/", "首页"}, {"/faction-beta", "阵营"}, {"/forum", "社区"},
            {"/team", "团队"}, {"__ARCHIVE__", "档案"}
    };
    private static final int DAWN = 0xFFFFCE86;
    private static final int DUSK = 0xFFA87CFF;
    private static final int SKY  = 0xFF6FA8FF;
    private static final float LOUP = 0.85f;    // 最大放大增量
    private static final float SIGMA = 0.55f;   // 受影响半径系数（以单格宽度计）

    private final DockItem[] items = new DockItem[ITEMS.length];
    private final GlassDrawable glass;
    private OnDockClick listener;
    private int active = 0;
    private float scale = 1f;

    // 放大镜（loupe）插值状态
    private final float[] mag = new float[ITEMS.length];
    private final float[] magTarget = new float[ITEMS.length];
    private ValueAnimator loupe;
    private float touchX = -1f, touchY = -1f;
    private boolean touching = false;

    // 全局液态涟漪（叠在最上层）
    private float ripple = 0f, rippleX = 0f, rippleY = 0f;
    private ValueAnimator rippleAnim;
    private final Paint ripplePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public GlassDock(Context ctx) {
        super(ctx);
        setOrientation(HORIZONTAL);
        float density = ctx.getResources().getDisplayMetrics().density;
        int pad = (int) (8 * density);
        setPadding(pad, pad, pad, pad);
        setClipChildren(false);
        setClipToPadding(false);
        setWillNotDraw(false);

        // 容器：完整药丸（半径 > 高度一半即被 GlassDrawable 钳为半圆端）
        glass = new GlassDrawable(46 * density, DUSK, true);
        glass.attach(this);

        for (int i = 0; i < ITEMS.length; i++) {
            DockItem it = new DockItem(ctx, i, ITEMS[i][1]);
            LayoutParams lp = new LayoutParams(0, LayoutParams.MATCH_PARENT, 1f);
            it.setLayoutParams(lp);
            items[i] = it;
            addView(it);
        }
        for (int i = 0; i < mag.length; i++) mag[i] = 1f;
        setActive(0);
        post(() -> { if (glass != null) glass.startFlow(); });
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        glass.setBounds(0, 0, w, h);
        if (w > 0 && !touching) { updateTargets(); startLoupe(); }
    }

    @Override protected void onDraw(Canvas canvas) {
        glass.setBounds(0, 0, getWidth(), getHeight());
        glass.draw(canvas);
        super.onDraw(canvas);              // 容器背景
    }

    @Override protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);        // 先画子项（含放大后的图标）
        if (ripple > 0.001f && rippleX >= 0) {
            float maxR = getWidth() * 0.55f * (0.35f + ripple);
            ripplePaint.setShader(new android.graphics.RadialGradient(rippleX, rippleY, maxR,
                    Color.argb((int) (64 * (1f - ripple)), 255, 240, 210),
                    Color.TRANSPARENT, Shader.TileMode.CLAMP));
            canvas.drawCircle(rippleX, rippleY, maxR, ripplePaint);
            ripplePaint.setShader(null);
        }
    }

    public void setOnDockClick(OnDockClick l) { listener = l; }

    public void setActive(int index) {
        if (index < 0 || index >= items.length) return;
        active = index;
        for (int i = 0; i < items.length; i++) items[i].setActive(i == index);
        glass.setAccent(index == 1 ? DAWN : (index == 2 ? SKY : DUSK));
        updateTargets();
        startLoupe();
        invalidate();
    }

    public int getActive() { return active; }

    /** 大屏/横屏：切换为竖向导航栏（Navigation Rail），避免挤压正文。 */
    public void setVerticalRail(boolean vertical) {
        setRail(vertical);
        setOrientation(vertical ? VERTICAL : HORIZONTAL);
        for (int i = 0; i < items.length; i++) {
            LayoutParams lp = (LayoutParams) items[i].getLayoutParams();
            if (vertical) { lp.width = LayoutParams.MATCH_PARENT; lp.height = 0; lp.weight = 1f; }
            else { lp.width = 0; lp.height = LayoutParams.MATCH_PARENT; lp.weight = 1f; }
            items[i].setLayoutParams(lp);
        }
        requestLayout();
        invalidate();
    }

    /** 设置 UI 缩放系数（平板等大屏下放大图标与文字） */
    public void setScale(float s) { scale = s; invalidate(); }

    public void startFlow() { glass.startFlow(); }
    public void stopFlow() { glass.stopFlow(); }

    /** iOS 26 标签栏式：滚动时收拢，停止/上滚时展开 */
    public void setCompact(boolean compact) {
        float s = compact ? 0.90f : 1f;
        float ty = compact ? (10 * getResources().getDisplayMetrics().density) : 0f;
        animate().scaleX(s).scaleY(s).translationY(ty).alpha(compact ? 0.92f : 1f)
                .setDuration(280)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.4f))
                .start();
    }

    // ------------------------------------------------------------------
    // 放大（放大镜）逻辑：触摸设备仅作为增强，选中态才是主要入口指示
    private boolean rail = false;

    public void setRail(boolean r) { rail = r; }

    private void updateTargets() {
        int n = items.length;
        float span = rail ? getHeight() : getWidth();
        if (span <= 0) return;
        float cell = span / n;
        float pointer = rail ? touchY : touchX;
        for (int i = 0; i < n; i++) {
            float center = (i + 0.5f) * cell;
            float base = (i == active) ? 1.12f : 1.0f;
            if (touching && pointer >= 0) {
                float dist = Math.abs(pointer - center);
                float x = dist / (cell * SIGMA);
                float bump = (float) Math.exp(-x * x);          // 高斯衰减，贴近 macOS 程序坞
                magTarget[i] = Math.max(base, 1f + LOUP * bump);
            } else {
                magTarget[i] = base;
            }
        }
    }

    private void startLoupe() {
        if (loupe != null && loupe.isRunning()) return;
        if (loupe == null) {
            loupe = ValueAnimator.ofFloat(0f, 1f);
            loupe.setDuration(160);
            loupe.setRepeatCount(ValueAnimator.INFINITE);
            loupe.setRepeatMode(ValueAnimator.RESTART);
        }
        loupe.removeAllUpdateListeners();
        loupe.addUpdateListener(a -> {
            float maxd = 0f;
            for (int i = 0; i < mag.length; i++) {
                mag[i] += (magTarget[i] - mag[i]) * 0.30f;
                float d = Math.abs(magTarget[i] - mag[i]);
                if (d > maxd) maxd = d;
            }
            if (maxd < 0.002f) {
                for (int i = 0; i < mag.length; i++) mag[i] = magTarget[i];
                if (loupe != null) loupe.cancel();
            }
            invalidate();
        });
        loupe.start();
    }

    private int indexFromX(float pos) {
        int n = items.length;
        float span = rail ? getHeight() : getWidth();
        int idx = span > 0 ? (int) (pos / (span / n)) : 0;
        if (idx < 0) idx = 0;
        if (idx >= n) idx = n - 1;
        return idx;
    }

    private void spawnRipple(float x, float y) {
        rippleX = x; rippleY = y;
        ripple = 1f;
        if (rippleAnim != null) rippleAnim.cancel();
        rippleAnim = ValueAnimator.ofFloat(1f, 0f);
        rippleAnim.setDuration(440);
        rippleAnim.addUpdateListener(a -> { ripple = (float) a.getAnimatedValue(); invalidate(); });
        rippleAnim.start();
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                touching = true; touchX = e.getX(); touchY = e.getY();
                updateTargets(); startLoupe();
                spawnRipple(e.getX(), e.getY());
                return true;
            case MotionEvent.ACTION_MOVE:
                touchX = e.getX(); touchY = e.getY();
                updateTargets();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                int idx = indexFromX(rail ? (touchY < 0 ? e.getY() : touchY) : (touchX < 0 ? e.getX() : touchX));
                touching = false; touchX = -1f; touchY = -1f;
                updateTargets(); startLoupe();
                if (e.getActionMasked() == MotionEvent.ACTION_UP && listener != null) {
                    listener.onClick(idx, ITEMS[idx][0]);
                }
                return true;
            }
        }
        return super.onTouchEvent(e);
    }

    // ------------------------------------------------------------------
    private class DockItem extends View {
        final int index;
        final String label;
        boolean isActive;
        final GlassDrawable pill;       // 选中态内嵌磨砂玻璃药丸
        final Paint icon = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
        final float density;

        DockItem(Context ctx, int index, String label) {
            super(ctx);
            this.index = index;
            this.label = label;
            density = ctx.getResources().getDisplayMetrics().density;
            pill = new GlassDrawable(18 * density, DAWN, false);
            pill.setSmoked(false);
            pill.attach(this);
            icon.setStyle(Paint.Style.STROKE);
            icon.setStrokeCap(Paint.Cap.ROUND);
            icon.setStrokeJoin(Paint.Join.ROUND);
            text.setTextAlign(Paint.Align.CENTER);
            text.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
            dot.setStyle(Paint.Style.FILL);
            setWillNotDraw(false);
        }

        void setActive(boolean b) { isActive = b; }

        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;
            float m = mag[index];
            float cx = w / 2f;

            c.save();
            // 以底部中心为轴放大：图标向上溢出，贴近 macOS 程序坞观感
            c.scale(m, m, cx, h * 0.92f);

            // 选中态：内嵌磨砂玻璃药丸（iOS/macOS 标签栏「悬浮玻璃」）
            if (isActive) {
                float pw = w * 0.84f, ph = h * 0.74f;
                RectF pr = new RectF(cx - pw / 2, (h - ph) / 2, cx + pw / 2, (h + ph) / 2);
                pill.setBounds((int) pr.left, (int) pr.top, (int) pr.right, (int) pr.bottom);
                pill.setIntensity(0.92f);
                pill.setAccent(index == 1 ? DAWN : (index == 2 ? SKY : DUSK));
                pill.draw(c);
            }

            float iconSize = 23 * density * scale;
            float iconCy = h * 0.42f;
            int col = isActive ? 0xFFFFE1A8 : Color.argb(176, 226, 234, 255);
            icon.setColor(col);
            icon.setStrokeWidth((isActive ? 2.0f : 1.7f) * density * scale);
            if (isActive) {
                icon.setShader(new LinearGradient(cx - iconSize / 2, iconCy - iconSize / 2,
                        cx + iconSize / 2, iconCy + iconSize / 2,
                        new int[]{0xFFFFD88F, 0xFFFFF4DC}, null, Shader.TileMode.CLAMP));
            } else {
                icon.setShader(null);
            }
            drawIcon(c, index, cx, iconCy, iconSize, icon, col);
            icon.setShader(null);

            text.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 10.5f * scale, getResources().getDisplayMetrics()));
            text.setColor(isActive ? 0xFFFFE7BC : Color.argb(158, 220, 230, 255));
            Paint.FontMetrics fm = text.getFontMetrics();
            c.drawText(label, cx, h * 0.80f - (fm.ascent + fm.descent) / 2f, text);

            if (isActive) {
                float d = 9 * density * scale;
                dot.setShader(new LinearGradient(cx - d, 0, cx + d, 0,
                        new int[]{0xFFA87CFF, 0xFFFFCE86, 0xFFFFCE86, 0xFFA87CFF}, null, Shader.TileMode.CLAMP));
                c.drawRoundRect(new RectF(cx - d, h - 5.5f * density * scale, cx + d, h - 3.5f * density * scale),
                        2 * density * scale, 2 * density * scale, dot);
                dot.setShader(null);
            }
            c.restore();
        }

        private void drawIcon(Canvas c, int type, float cx, float cy, float size, Paint p, int col) {
            Path path = new Path();
            float s = size / 24f;
            switch (type) {
                case 0: // 首页
                    path.moveTo(3, 10.6f); path.lineTo(12, 3.2f); path.lineTo(21, 10.6f);
                    path.moveTo(5.6f, 9.2f); path.lineTo(5.6f, 20.5f);
                    path.lineTo(18.4f, 20.5f); path.lineTo(18.4f, 9.2f);
                    path.moveTo(9.7f, 20.5f); path.lineTo(9.7f, 14.2f);
                    path.lineTo(14.3f, 14.2f); path.lineTo(14.3f, 20.5f);
                    break;
                case 1: // 阵营：盾 + 星
                    path.moveTo(12, 2.4f); path.lineTo(20, 5.9f); path.lineTo(20, 12f);
                    path.quadTo(20, 17.4f, 12, 21.6f);
                    path.quadTo(4, 17.4f, 4, 12f);
                    path.lineTo(4, 5.9f); path.close();
                    path.moveTo(12, 8f); path.lineTo(12, 16f);
                    path.moveTo(8.6f, 12f); path.lineTo(15.4f, 12f);
                    break;
                case 2: // 社区：双气泡
                    path.moveTo(3.6f, 5.4f); path.lineTo(16.4f, 5.4f);
                    path.quadTo(18, 5.4f, 18, 7f);
                    path.lineTo(18, 13.4f); path.quadTo(18, 15, 16.4f, 15);
                    path.lineTo(9.6f, 15); path.lineTo(5.6f, 18.4f);
                    path.lineTo(5.6f, 15); path.lineTo(3.6f, 15);
                    path.quadTo(2, 15, 2, 13.4f);
                    path.lineTo(2, 7f); path.quadTo(2, 5.4f, 3.6f, 5.4f); path.close();
                    path.moveTo(20.2f, 8.6f); path.lineTo(21.2f, 8.6f);
                    path.quadTo(22, 8.6f, 22, 9.4f); path.lineTo(22, 16.6f);
                    path.quadTo(22, 17.4f, 21.2f, 17.4f); path.lineTo(20.2f, 17.4f);
                    path.lineTo(20.2f, 20f); path.lineTo(17.6f, 17.4f);
                    break;
                case 3: // 团队
                    path.addCircle(12, 7.4f, 3.1f, Path.Direction.CW);
                    path.moveTo(5.6f, 19.4f); path.quadTo(5.6f, 13.8f, 12, 13.8f);
                    path.quadTo(18.4f, 13.8f, 18.4f, 19.4f);
                    path.moveTo(4.2f, 9.2f); path.quadTo(4.2f, 6.8f, 6.4f, 6.8f);
                    path.moveTo(19.8f, 9.2f); path.quadTo(19.8f, 6.8f, 17.6f, 6.8f);
                    break;
                default: // 档案：书页
                    path.moveTo(4.5f, 3.6f); path.lineTo(10.5f, 3.6f);
                    path.quadTo(12, 3.6f, 12, 5.2f); path.lineTo(12, 20.2f);
                    path.quadTo(12, 18.6f, 10.5f, 18.6f); path.lineTo(4.5f, 18.6f); path.close();
                    path.moveTo(19.5f, 3.6f); path.lineTo(13.5f, 3.6f);
                    path.quadTo(12, 3.6f, 12, 5.2f); path.lineTo(12, 20.2f);
                    path.quadTo(12, 18.6f, 13.5f, 18.6f); path.lineTo(19.5f, 18.6f); path.close();
                    path.moveTo(6.4f, 8f); path.lineTo(9.6f, 8f);
                    path.moveTo(14.4f, 8f); path.lineTo(17.6f, 8f);
                    break;
            }
            Matrix mm = new Matrix();
            mm.setScale(s, s);
            mm.postTranslate(cx - size / 2f, cy - size / 2f);
            path.transform(mm);
            p.setStyle(Paint.Style.STROKE);
            c.drawPath(path, p);
            if (type == 3) {
                Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
                fill.setColor(col);
                fill.setStyle(Paint.Style.FILL);
                c.drawCircle(cx, cy - 4.6f * s, 1.0f * s, fill);
            }
        }

        @Override public boolean onTouchEvent(MotionEvent e) { return false; } // 点击由 Dock 统一处理
    }
}
