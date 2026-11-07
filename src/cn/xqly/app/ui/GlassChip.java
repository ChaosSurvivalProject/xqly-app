package cn.xqly.app.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.TextView;

import cn.xqly.app.GlassDrawable;

/**
 * 「星穹玻璃」标签 / 筛选药丸：未选中为中性玻璃，选中切换到黎明金折射高亮。
 * 用于资料库的标签筛选、同步状态等。
 */
public class GlassChip extends TextView {

    private final GlassDrawable glass;

    public GlassChip(Context ctx, CharSequence t) {
        super(ctx);
        setText(t);
        setGravity(Gravity.CENTER);
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        glass = new GlassDrawable(999f, 0xFFA87CFF, false);
        setBackground(glass);
        int p = dp(8);
        setPadding(p * 2, p, p * 2, p);
        setMinHeight(dp(36));
        setClickable(true);
        setFocusable(true);
        apply(false);
    }

    @Override public void setSelected(boolean s) {
        apply(s);
        super.setSelected(s);
    }

    private void apply(boolean s) {
        glass.setAccent(s ? 0xFFFFCE86 : 0x402A4A8C);
        setTextColor(s ? 0xFF241500 : 0xB4DCE6FF);
    }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
