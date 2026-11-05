package cn.xqly.app;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.View;

/**
 * 液态玻璃绘制层。
 * 由「半透明玻璃体 + 双色折射光斑 + 流动高光 + 折射描边 + 悬浮阴影」叠加而成，
 * 不依赖 RenderEffect，兼容 Android 7.0+。
 */
public class GlassDrawable extends Drawable {

    private float radius;
    private int accent;
    private float sweep = 0.28f;
    private float intensity = 1f;
    private boolean flowing;
    private boolean smoked;   // 烟熏深色底，用于叠在浅色内容上的悬浮药丸
    private ValueAnimator animator;

    private final RectF rect = new RectF();
    private final Paint body = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint smoke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint refract = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sheen = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint specular = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint catchLight = new Paint(Paint.ANTI_ALIAS_FLAG);

    public GlassDrawable(float radiusDp, int accent, boolean flowing) {
        this.radius = radiusDp;
        this.accent = accent;
        this.flowing = flowing;
        rim.setStyle(Paint.Style.STROKE);
        specular.setStyle(Paint.Style.STROKE);
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);
        rect.set(bounds.left, bounds.top, bounds.right, bounds.bottom);
    }

    /** 绑定到宿主 View：手动绘制的玻璃层需要它，才能把 invalidateSelf 转成重绘 */
    public void attach(View host) {
        setCallback(new Callback() {
            @Override public void invalidateDrawable(Drawable who) { host.invalidate(); }
            @Override public void scheduleDrawable(Drawable who, Runnable what, long when) { host.postDelayed(what, when - android.os.SystemClock.uptimeMillis()); }
            @Override public void unscheduleDrawable(Drawable who, Runnable what) { host.removeCallbacks(what); }
        });
    }

    public void setIntensity(float v) {
        intensity = v;
        invalidateSelf();
    }

    public void setAccent(int color) {
        accent = color;
        invalidateSelf();
    }

    /** 烟熏玻璃：加深底色，让白色图标文字在浅色网页上依然清晰 */
    public void setSmoked(boolean b) {
        smoked = b;
        invalidateSelf();
    }

    public void setSweep(float v) {
        sweep = v;
        invalidateSelf();
    }

    /** 由宿主 Activity 在 onResume / onPause 中调用，避免后台空转 */
    public void startFlow() {
        if (!flowing || animator != null) return;
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(5600);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setRepeatMode(ValueAnimator.RESTART);
        animator.addUpdateListener(a -> {
            sweep = (float) a.getAnimatedValue();
            invalidateSelf();
        });
        animator.start();
    }

    public void stopFlow() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
    }

    @Override
    public void draw(Canvas c) {
        float w = rect.width(), h = rect.height();
        if (w <= 0 || h <= 0) return;
        float r = Math.min(radius, h / 2f);
        float a = intensity;

        // ---- 1. 悬浮阴影：两层偏移暗角 ----
        shadow.setShader(null);
        shadow.setColor(Color.argb((int) (70 * a), 0, 0, 0));
        c.drawRoundRect(rect.left + 2, rect.top + r * 0.30f, rect.right + 2, rect.bottom + r * 0.30f, r, r, shadow);
        shadow.setColor(Color.argb((int) (36 * a), 0, 0, 0));
        c.drawRoundRect(rect.left, rect.top + r * 0.16f, rect.right, rect.bottom + r * 0.16f, r, r, shadow);

        // ---- 2. 玻璃主体：自上而下的半透明渐变 ----
        int top = Color.argb((int) (72 * a), 235, 242, 255);
        int mid = Color.argb((int) (26 * a), 190, 205, 255);
        int low = Color.argb((int) (16 * a), 120, 140, 220);
        int bot = Color.argb((int) (44 * a), 205, 218, 255);
        body.setShader(new LinearGradient(rect.left, rect.top, rect.right * 0.25f, rect.bottom,
                new int[]{top, mid, low, bot}, new float[]{0f, 0.34f, 0.72f, 1f}, Shader.TileMode.CLAMP));
        c.drawRoundRect(rect, r, r, body);

        // ---- 2.5 烟熏深底（仅悬浮药丸启用） ----
        if (smoked) {
            smoke.setShader(new LinearGradient(rect.left, rect.top, rect.right, rect.bottom,
                    new int[]{Color.argb((int) (158 * a), 8, 12, 30),
                            Color.argb((int) (128 * a), 16, 20, 48),
                            Color.argb((int) (172 * a), 5, 7, 22)},
                    new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
            c.drawRoundRect(rect, r, r, smoke);
        }

        // ---- 3. 双色折射光斑：黎明金 / 暮夜紫，模拟背景透过玻璃的色散 ----
        int ar = Color.red(accent), ag = Color.green(accent), ab = Color.blue(accent);
        float gx = rect.left + w * 0.22f, gy = rect.top + h * 0.10f;
        refract.setShader(new RadialGradient(gx, gy, w * 0.62f,
                Color.argb((int) (54 * a), ar, ag, ab), Color.TRANSPARENT, Shader.TileMode.CLAMP));
        c.drawRoundRect(rect, r, r, refract);
        refract.setShader(new RadialGradient(rect.right - w * 0.12f, rect.bottom, w * 0.52f,
                Color.argb((int) (40 * a), 138, 100, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP));
        c.drawRoundRect(rect, r, r, refract);

        // ---- 4. 流动高光带（液态感核心） ----
        float p = sweep * 1.6f - 0.3f;
        float[] pos = {0f,
                Math.max(0f, p - 0.16f),
                Math.max(0.001f, p - 0.02f),
                Math.min(1f, p),
                Math.min(1f, p + 0.05f),
                Math.min(1f, p + 0.16f),
                1f};
        int[] col = {
                Color.TRANSPARENT, Color.TRANSPARENT,
                Color.argb((int) (16 * a), 255, 255, 255),
                Color.argb((int) (66 * a), 255, 255, 255),
                Color.argb((int) (14 * a), 255, 255, 255),
                Color.TRANSPARENT, Color.TRANSPARENT
        };
        sheen.setShader(new LinearGradient(rect.left, rect.top, rect.right, rect.bottom,
                col, pos, Shader.TileMode.CLAMP));
        c.drawRoundRect(rect, r, r, sheen);

        // ---- 4.5 顶部受光：玻璃上沿捕捉环境光，制造柔和高光（Apple 玻璃质感关键） ----
        catchLight.setShader(new RadialGradient(rect.left + w * 0.5f, rect.top - h * 0.12f, w * 0.78f,
                Color.argb((int) (46 * a), 255, 255, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP));
        c.drawRoundRect(rect, r, r, catchLight);

        // ---- 5. 折射描边：上亮下暗，右下补一道冷光 ----
        rim.setStrokeWidth(Math.max(1.2f, h * 0.012f));
        rim.setShader(new LinearGradient(rect.left, rect.top, rect.left, rect.bottom,
                new int[]{Color.argb((int) (190 * a), 255, 255, 255),
                        Color.argb((int) (54 * a), 255, 255, 255),
                        Color.argb((int) (18 * a), 255, 255, 255),
                        Color.argb((int) (96 * a), 200, 220, 255)},
                new float[]{0f, 0.30f, 0.75f, 1f}, Shader.TileMode.CLAMP));
        c.drawRoundRect(rect, r, r, rim);

        // ---- 6. 内圈高光细线，制造玻璃厚度 ----
        specular.setStrokeWidth(Math.max(0.8f, h * 0.006f));
        specular.setShader(new LinearGradient(rect.left, rect.top, rect.right, rect.bottom,
                new int[]{Color.argb((int) (110 * a), 255, 255, 255), Color.TRANSPARENT, Color.TRANSPARENT},
                new float[]{0f, 0.42f, 1f}, Shader.TileMode.CLAMP));
        RectF inner = new RectF(rect.left + 1.5f, rect.top + 1.5f, rect.right - 1.5f, rect.bottom - 1.5f);
        c.drawRoundRect(inner, Math.max(1f, r - 1.5f), Math.max(1f, r - 1.5f), specular);
    }

    @Override public void setAlpha(int alpha) { intensity = alpha / 255f; invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter cf) { }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
