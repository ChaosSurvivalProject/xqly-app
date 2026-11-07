package cn.xqly.app.ui;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.widget.FrameLayout;

import cn.xqly.app.GlassDrawable;

/**
 * 「星穹玻璃」卡片容器：半透明玻璃体 + 双色折射光斑 + 顶部受光 + 折射描边，
 * 作为列表、对话框、设置面板、浮层的内容载体。
 * 与 HTML/CSS 版设计令牌共享同一套语义，但不是 WebView——这是原生绘制层。
 */
public class GlassCard extends FrameLayout {

    private final GlassDrawable glass;

    public GlassCard(Context ctx) {
        this(ctx, 0xFFA87CFF, 20f, false);
    }

    public GlassCard(Context ctx, int accent, float radiusDp, boolean smoked) {
        super(ctx);
        glass = new GlassDrawable(radiusDp, accent, false);
        glass.setSmoked(smoked);
        setBackground(glass);
        setClipToOutline(true);
    }

    /** 暴露底层玻璃层，便于主题切换时改 accent / 开关烟熏。 */
    public GlassDrawable glass() { return glass; }

    /** 给卡片内容一个统一的留白。 */
    public void setContentPadding(int dp) {
        setPadding(dp, dp, dp, dp);
    }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
