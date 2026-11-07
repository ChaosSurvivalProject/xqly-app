package cn.xqly.app.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;

import cn.xqly.app.GlassDrawable;
import cn.xqly.app.core.DesignTokens;

/**
 * 「星穹玻璃」搜索栏：玻璃体背景 + 原生 EditText + 一键清除。
 * 每次输入通过 {@link OnQueryListener} 回调，供上层做实时本地搜索。
 */
public class GlassSearchBar extends FrameLayout {

    private final EditText input;
    private final TextView clear;
    private OnQueryListener listener;

    public interface OnQueryListener { void onQuery(String q); }

    public GlassSearchBar(Context ctx, int accent) {
        super(ctx);
        setBackground(new GlassDrawable(16f, accent, false));

        input = new EditText(ctx);
        input.setBackground(null);
        input.setSingleLine(true);
        input.setHint("搜索资料、收藏、历史…");
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, DesignTokens.T_BODY.sp);
        // 玻璃主体偏亮，深色字 + 半透明提示
        input.setTextColor(0xFF0A1030);
        input.setHintTextColor(0x800A1030);
        input.setPadding(dp(14), 0, dp(38), 0);

        clear = new TextView(ctx);
        clear.setText("✕");
        clear.setTextColor(0xFF0A1030);
        clear.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        clear.setGravity(Gravity.CENTER);
        clear.setClickable(true);
        clear.setVisibility(View.GONE);
        clear.setOnClickListener(v -> {
            input.setText("");
            if (listener != null) listener.onQuery("");
        });

        addView(input, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_VERTICAL));
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(
                dp(28), dp(28), Gravity.CENTER_VERTICAL | Gravity.END);
        cp.rightMargin = dp(8);
        addView(clear, cp);

        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                clear.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                if (listener != null) listener.onQuery(s.toString());
            }
            @Override public void afterTextChanged(Editable e) {}
        });
    }

    public String getQuery() { return input.getText().toString(); }

    public void setOnQueryListener(OnQueryListener l) { listener = l; }

    public void clearQuery() { input.setText(""); }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
