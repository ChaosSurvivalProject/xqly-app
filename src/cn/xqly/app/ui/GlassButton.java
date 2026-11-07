package cn.xqly.app.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

import cn.xqly.app.GlassDrawable;
import cn.xqly.app.core.DesignTokens;

/**
 * 「星穹玻璃」药丸按钮：玻璃体 + 黎明金 / 暮夜紫折射，深色字保证可读（WCAG AA）。
 * 直接继承 FrameLayout，使用方调用 {@code setOnClickListener} 即可。
 */
public class GlassButton extends FrameLayout {

    private final GlassDrawable glass;
    private final TextView label;

    public GlassButton(Context ctx, int accent, CharSequence text) {
        super(ctx);
        setClickable(true);
        setFocusable(true);

        glass = new GlassDrawable(999f, accent, false);
        setBackground(glass);

        label = new TextView(ctx);
        label.setText(text);
        label.setGravity(Gravity.CENTER);
        // 玻璃主体偏亮蓝白，深色字在任意主题下均达 AA
        label.setTextColor(0xFF0A1030);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, DesignTokens.T_LABEL.sp);
        label.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        int p = dp(12);
        label.setPadding(p * 2, p, p * 2, p);
        label.setMinHeight(dp(DesignTokens.MIN_TOUCH));
        addView(label, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));
    }

    public void setText(CharSequence t) { label.setText(t); }

    public void setAccent(int accent) { glass.setAccent(accent); }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
