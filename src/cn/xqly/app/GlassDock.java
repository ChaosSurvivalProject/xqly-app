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
import android.view.animation.OvershootInterpolator;
import android.widget.LinearLayout;

/** 液态玻璃底部 Dock 导航栏 */
public class GlassDock extends LinearLayout {

    public interface OnDockClick { void onClick(int index, String path); }

    private static final String[][] ITEMS = {
            {"/", "首页"}, {"/faction-beta", "阵营"}, {"/forum", "社区"},
            {"/team", "团队"}, {"__ARCHIVE__", "档案"}
    };
    private static final int DAWN = 0xFFFFCE86;
    private static final int DUSK = 0xFFA87CFF;

    private final DockItem[] items = new DockItem[ITEMS.length];
    private final GlassDrawable glass;
    private OnDockClick listener;
    private int active = 0;

    public GlassDock(Context ctx) {
        super(ctx);
        setOrientation(HORIZONTAL);
        float density = ctx.getResources().getDisplayMetrics().density;
        int pad = (int) (7 * density);
        setPadding(pad, pad, pad, pad);
        setClipChildren(false);
        setClipToPadding(false);
        glass = new GlassDrawable(30 * density, DUSK, true);
        glass.attach(this);
        setWillNotDraw(false);

        for (int i = 0; i < ITEMS.length; i++) {
            DockItem it = new DockItem(ctx, i, ITEMS[i][1]);
            LayoutParams lp = new LayoutParams(0, LayoutParams.MATCH_PARENT, 1f);
            it.setLayoutParams(lp);
            items[i] = it;
            addView(it);
        }
        setActive(0);
        post(() -> { if (glass != null) glass.startFlow(); });
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        glass.setBounds(0, 0, w, h);
    }

    @Override protected void onDraw(Canvas canvas) {
        glass.setBounds(0, 0, getWidth(), getHeight());
        glass.draw(canvas);
        super.onDraw(canvas);
    }

    public void setOnDockClick(OnDockClick l) { listener = l; }

    public void setActive(int index) {
        active = index;
        for (int i = 0; i < items.length; i++) {
            items[i].setActive(i == index);
        }
        glass.setAccent(index == 1 ? DAWN : (index == 2 ? 0xFF6FA8FF : DUSK));
        invalidate();
    }

    public int getActive() { return active; }

    public void startFlow() { glass.startFlow(); }
    public void stopFlow() { glass.stopFlow(); }

    // ------------------------------------------------------------------
    private class DockItem extends View {
        private final int index;
        private final String label;
        private boolean isActive;
        private float ripple = 0f;
        private float rx = 0f, ry = 0f;
        private ValueAnimator rippleAnim;
        private final GlassDrawable pill;
        private final Paint icon = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ripplePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float density;

        DockItem(Context ctx, int index, String label) {
            super(ctx);
            this.index = index;
            this.label = label;
            density = ctx.getResources().getDisplayMetrics().density;
            pill = new GlassDrawable(20 * density, DAWN, false);
            pill.attach(this);
            icon.setStyle(Paint.Style.STROKE);
            icon.setStrokeCap(Paint.Cap.ROUND);
            icon.setStrokeJoin(Paint.Join.ROUND);
            text.setTextAlign(Paint.Align.CENTER);
            text.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 10.5f, ctx.getResources().getDisplayMetrics()));
            text.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
            dot.setStyle(Paint.Style.FILL);
        }

        void setActive(boolean b) {
            if (isActive == b) return;
            isActive = b;
            float s = b ? 1.06f : 1f;
            animate().scaleX(s).scaleY(s).setDuration(320).setInterpolator(new OvershootInterpolator(1.6f)).start();
            invalidate();
        }

        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;
            float cx = w / 2f;

            // 激活态：内嵌玻璃药丸
            if (isActive) {
                float pw = w * 0.86f, ph = h * 0.80f;
                RectF pr = new RectF(cx - pw / 2, (h - ph) / 2 - 2 * density, cx + pw / 2, (h + ph) / 2 - 2 * density);
                pill.setBounds((int) pr.left, (int) pr.top, (int) pr.right, (int) pr.bottom);
                pill.setIntensity(0.82f);
                pill.draw(c);
            }

            // 按压液态扩散
            if (ripple > 0f) {
                float maxR = (float) Math.hypot(w, h) * 0.75f;
                ripplePaint.setShader(new android.graphics.RadialGradient(rx, ry, maxR * ripple + 1,
                        Color.argb((int) (70 * (1f - ripple)), 255, 236, 200),
                        Color.TRANSPARENT, Shader.TileMode.CLAMP));
                c.drawCircle(rx, ry, maxR * ripple, ripplePaint);
            }

            float iconSize = 24 * density;
            float iconCy = h * 0.40f;

            int col = isActive ? 0xFFFFE1A8 : Color.argb(168, 226, 234, 255);
            icon.setColor(col);
            icon.setStrokeWidth((isActive ? 2.0f : 1.7f) * density);
            icon.setShader(null);
            if (isActive) {
                icon.setShader(new LinearGradient(cx - iconSize / 2, iconCy - iconSize / 2,
                        cx + iconSize / 2, iconCy + iconSize / 2,
                        new int[]{0xFFFFD88F, 0xFFFFF4DC}, null, Shader.TileMode.CLAMP));
            }
            drawIcon(c, index, cx, iconCy, iconSize, icon, col);
            icon.setShader(null);

            text.setColor(isActive ? 0xFFFFE7BC : Color.argb(150, 220, 230, 255));
            Paint.FontMetrics fm = text.getFontMetrics();
            c.drawText(label, cx, h * 0.80f - (fm.ascent + fm.descent) / 2f, text);

            if (isActive) {
                dot.setShader(new LinearGradient(cx - 9 * density, 0, cx + 9 * density, 0,
                        new int[]{0xFFA87CFF, 0xFFFFCE86, 0xFFFFCE86, 0xFFA87CFF}, null, Shader.TileMode.CLAMP));
                c.drawRoundRect(new RectF(cx - 9 * density, h - 5.5f * density, cx + 9 * density, h - 3.5f * density),
                        2 * density, 2 * density, dot);
                dot.setShader(null);
            }
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
            Matrix m = new Matrix();
            m.setScale(s, s);
            m.postTranslate(cx - size / 2f, cy - size / 2f);
            path.transform(m);
            p.setStyle(Paint.Style.STROKE);
            c.drawPath(path, p);
            if (type == 3) { // 团队图标的头部实心圆点
                Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
                fill.setColor(col);
                fill.setStyle(Paint.Style.FILL);
                c.drawCircle(cx, cy - 4.6f * s, 1.0f * s, fill);
            }
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    rx = e.getX(); ry = e.getY();
                    animate().scaleX(0.90f).scaleY(0.90f).setDuration(110).start();
                    if (rippleAnim != null) rippleAnim.cancel();
                    rippleAnim = ValueAnimator.ofFloat(0f, 1f);
                    rippleAnim.setDuration(520);
                    final float start = ripple;
                    rippleAnim.addUpdateListener(a -> {
                        ripple = start + (1f - start) * (float) a.getAnimatedValue();
                        invalidate();
                    });
                    rippleAnim.start();
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    animate().scaleX(isActive ? 1.06f : 1f).scaleY(isActive ? 1.06f : 1f)
                            .setDuration(300).setInterpolator(new OvershootInterpolator(1.8f)).start();
                    if (rippleAnim != null) {
                        rippleAnim.cancel();
                        ValueAnimator fade = ValueAnimator.ofFloat(ripple, 0f);
                        fade.setDuration(260);
                        fade.addUpdateListener(a -> { ripple = (float) a.getAnimatedValue(); invalidate(); });
                        fade.start();
                    }
                    if (e.getActionMasked() == MotionEvent.ACTION_UP && listener != null) {
                        performClick();
                        listener.onClick(index, ITEMS[index][0]);
                    }
                    return true;
            }
            return super.onTouchEvent(e);
        }

        @Override public boolean performClick() { return super.performClick(); }
    }
}
