package cn.xqly.app;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import cn.xqly.app.core.AppState;
import cn.xqly.app.core.DesignTokens;

/**
 * 全状态覆盖层：为任何页面提供 首次加载/成功/空/无网有缓存/无网无缓存/
 * 失败重试/部分可用/校验失败/权限拒绝/存储不足/待切换 的可操作界面。
 * 绝不出现「只有成功界面 + 无限转圈」；异常态一律给出明确说明与动作。
 */
public final class StateOverlay extends FrameLayout {

    public interface OnAction { void onAction(AppState.Phase phase); }

    private final LinearLayout card;
    private final TextView title, msg;
    private final ProgressBar spinner;
    private final TextView action;
    private OnAction listener;
    private AppState.Phase phase = AppState.Phase.SUCCESS;

    public StateOverlay(Context ctx) {
        super(ctx);
        setBackgroundColor(DesignTokens.palette(DesignTokens.Theme.DARK, DesignTokens.Mode.GLASS)
                .get(DesignTokens.ColorKey.OVERLAY));
        setClickable(true);
        setFocusable(true);

        card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = dp(24);
        card.setPadding(pad, pad, pad, pad);
        card.setBackground(glassBg());
        LayoutParams clp = new LayoutParams(dp(300), LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        addView(card, clp);

        spinner = new ProgressBar(ctx);
        spinner.setIndeterminate(true);
        card.addView(spinner, new LinearLayout.LayoutParams(dp(36), dp(36)));

        title = new TextView(ctx);
        title.setTextColor(DesignTokens.palette(DesignTokens.Theme.DARK, DesignTokens.Mode.GLASS)
                .get(DesignTokens.ColorKey.TEXT));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, DesignTokens.T_TITLE.sp);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-1, -2);
        tlp.topMargin = dp(14);
        card.addView(title, tlp);

        msg = new TextView(ctx);
        msg.setTextColor(Color.argb(190, 214, 226, 255));
        msg.setTextSize(TypedValue.COMPLEX_UNIT_SP, DesignTokens.T_BODY.sp);
        msg.setGravity(Gravity.CENTER);
        msg.setLineSpacing(0f, 1.6f);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(-1, -2);
        mlp.topMargin = dp(8);
        card.addView(msg, mlp);

        action = new TextView(ctx);
        action.setTextColor(0xFFFFE1A8);
        action.setTextSize(TypedValue.COMPLEX_UNIT_SP, DesignTokens.T_LABEL.sp);
        action.setGravity(Gravity.CENTER);
        action.setBackground(actionBg());
        int ap = dp(12);
        action.setPadding(ap * 2, ap, ap * 2, ap);
        action.setMinHeight(DesignTokens.MIN_TOUCH > 0 ? dp(DesignTokens.MIN_TOUCH) : dp(48));
        action.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(-2, -2);
        alp.topMargin = dp(18);
        action.setOnClickListener(v -> { if (listener != null) listener.onAction(phase); });
        card.addView(action, alp);
    }

    public void setOnAction(OnAction l) { listener = l; }

    /** 展示某页面的某状态；SUCCESS 直接隐藏。 */
    public void show(AppState.Page page, AppState.Phase ph) {
        this.phase = ph;
        AppState.ViewState v = AppState.of(page, ph);
        if (ph == AppState.Phase.SUCCESS) { setVisibility(GONE); return; }
        setVisibility(VISIBLE);
        spinner.setVisibility(v.spinner ? View.VISIBLE : View.GONE);
        title.setText(v.title);
        // 支持系统字体缩放：TextView 用 sp，不裁切
        msg.setText(v.message);
        msg.setContentDescription(v.title + "。" + v.message);
        if (v.retryable && !v.actionLabel.isEmpty()) {
            action.setVisibility(View.VISIBLE);
            action.setText(v.actionLabel);
            action.setContentDescription(v.actionLabel + "按钮");
        } else {
            action.setVisibility(View.GONE);
        }
        card.setContentDescription(v.title);
    }

    public AppState.Phase phase() { return phase; }

    private GlassDrawable glassBg() {
        return new GlassDrawable(DesignTokens.R_DIALOG, 0xFFA87CFF, false);
    }
    private GlassDrawable actionBg() {
        return new GlassDrawable(DesignTokens.R_CONTROL, 0xFFFFCE86, false);
    }
    private int dp(float v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }
}
