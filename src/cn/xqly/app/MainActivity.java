package cn.xqly.app;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.DownloadListener;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 星穹旅驿 · 液态玻璃客户端 */
public class MainActivity extends Activity {

    private static final String HOST = "xqly.xt91tv.shop";
    private static final String BASE = "https://" + HOST;
    private static final int DAWN = 0xFFFFCE86;
    private static final int DUSK = 0xFFA87CFF;

    public static final String VERSION_NAME = "1.3.0";
    public static final int VERSION_CODE = 4;

    /** 版本比对结果载体（由 Updater 填充后回传 UI 线程） */
    public static class VersionResult {
        final String tag, name, body, apkUrl;
        final int cmp;
        VersionResult(String tag, String name, String body, String apkUrl, int cmp) {
            this.tag = tag; this.name = name; this.body = body; this.apkUrl = apkUrl; this.cmp = cmp;
        }
    }

    private FrameLayout root;
    private WebView web, archiveWeb, gameWeb;
    private GlassDock dock;
    private GlassDrawable pillGlass;
    private View sidePill;
    private TextView modeLabel, pillPage;
    private View statusDot, progressFill, progressTrack;
    private FrameLayout archiveOverlay, gameOverlay, splash, fullscreenContainer;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private GlowView glow;

    private float density;
    private float uiScale = 1f;            // 平板/大屏缩放系数（基于屏幕最短边）
    private boolean isTablet = false;
    private boolean offline = false;
    private boolean archiveOpen = false;
    private boolean gameOpen = false;
    private Updater updater;
    private String currentPath = "/";
    private String pageName = "首页";
    private int dockSide = 0;                 // 0 = 左侧（默认）, 1 = 右侧
    private android.content.SharedPreferences prefs;
    private long lastBack = 0L;
    private int lastScrollY = 0;
    private long lastCompactAt = 0L;
    private List<String> apiSnapshots = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        density = getResources().getDisplayMetrics().density;
        computeScale();
        prefs = getSharedPreferences("xqly_ui", MODE_PRIVATE);
        dockSide = prefs.getInt("dock_side", 0);   // 默认左侧

        root = new FrameLayout(this);
        root.setBackgroundColor(0xFF050713);
        root.setClipChildren(false);
        // 横竖屏切换后重新计算药丸停靠位置
        root.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if ((r - l) != (or - ol)) applySide(false);
        });

        // 背景氛围光
        glow = new GlowView(this);
        root.addView(glow, new FrameLayout.LayoutParams(-1, -1));

        // ---- WebView ----
        web = new WebView(this);
        FrameLayout.LayoutParams webLp = new FrameLayout.LayoutParams(-1, -1);
        root.addView(web, webLp);
        web.setPadding(0, 0, 0, sdp(82));
        web.setBackgroundColor(0xFF050713);
        configureWeb(web);

        // ---- 侧边液态玻璃药丸（默认左） ----
        sidePill = buildSidePill();
        root.addView(sidePill, sidePillParams());

        // ---- 顶部细进度条 ----
        progressTrack = new View(this);
        progressTrack.setBackgroundColor(Color.argb(30, 255, 255, 255));
        FrameLayout.LayoutParams ptLp = new FrameLayout.LayoutParams(-1, dp(2));
        ptLp.gravity = Gravity.TOP;
        root.addView(progressTrack, ptLp);
        progressFill = new View(this);
        GradientDrawable pg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xFFA87CFF, 0xFFFFCE86, 0xFF7FD4FF});
        progressFill.setBackground(pg);
        FrameLayout.LayoutParams pf = new FrameLayout.LayoutParams(0, dp(2));
        pf.gravity = Gravity.TOP;
        root.addView(progressFill, pf);

        // ---- 底部液态玻璃 Dock ----
        dock = new GlassDock(this);
        dock.setScale(uiScale);
        FrameLayout.LayoutParams dockLp = new FrameLayout.LayoutParams(-1, sdp(72));
        dockLp.gravity = Gravity.BOTTOM;
        if (isTablet) {
            // 平板：Dock 居中限宽，避免在大屏上拉成一条细长的横条
            dockLp.width = sdp(440);
            dockLp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        } else {
            dockLp.leftMargin = sdp(10);
            dockLp.rightMargin = sdp(10);
        }
        dockLp.bottomMargin = sdp(10);
        root.addView(dock, dockLp);
        dock.setOnDockClick((index, path) -> onDockClick(index, path));

        // ---- 全站档案浮层 ----
        archiveOverlay = buildArchiveOverlay();
        root.addView(archiveOverlay, new FrameLayout.LayoutParams(-1, -1));

        // ---- 启动页 ----
        splash = buildSplash();
        root.addView(splash, new FrameLayout.LayoutParams(-1, -1));

        // ---- 离线小游戏浮层（最上层，在线/离线均可打开）----
        gameOverlay = buildGameOverlay();
        root.addView(gameOverlay, new FrameLayout.LayoutParams(-1, -1));

        setContentView(root);

        // 缓存 API 快照文件名
        try {
            for (String f : getAssets().list("data")) {
                if (f.endsWith(".json")) apiSnapshots.add(f);
            }
        } catch (IOException ignored) { }

        // 无网络时直接走离线镜像
        if (!isNetworkAvailable()) {
            offline = true;
            toast("未检测到网络，已启用离线镜像");
        }
        updateModeUi();
        syncTitle();
        root.post(() -> applySide(false));
        web.loadUrl(BASE + "/");

        // ---- 自动更新：启动后静默检查（可在设置中关闭）----
        updater = new Updater(this);
        if (prefs.getBoolean("auto_update", true) && isNetworkAvailable()) {
            updater.checkForUpdates(false);
        }
    }

    /** 根据屏幕最短边计算缩放系数：手机为 1.0，平板逐步放大并标记 isTablet */
    private void computeScale() {
        float wDp = getResources().getDisplayMetrics().widthPixels / density;
        float hDp = getResources().getDisplayMetrics().heightPixels / density;
        float minSide = Math.min(wDp, hDp);
        isTablet = minSide >= 600f;
        uiScale = Math.max(1f, Math.min(1.7f, minSide / 430f));
    }

    /** 受 uiScale 影响的 dp 换算（用于 UI 装饰层，站点内容本身响应式） */
    private int sdp(float v) { return dp(v * uiScale); }

    // ---------------------------------------------------------------- UI
    /** 侧边药丸：宽约 46dp 的竖向胶囊，默认贴左，可一键换到右侧（随屏幕缩放） */
    private FrameLayout.LayoutParams sidePillParams() {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(sdp(46), -2);
        lp.gravity = Gravity.LEFT | Gravity.CENTER_VERTICAL;
        lp.leftMargin = sdp(8);
        return lp;
    }

    private void applySide(boolean animate) {
        if (sidePill == null || root.getWidth() <= 0) return;
        float tx = dockSide == 1 ? (root.getWidth() - sidePill.getWidth() - sdp(16)) : 0f;
        if (animate) {
            sidePill.animate().translationX(tx).setDuration(420)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator(1.5f)).start();
        } else {
            sidePill.setTranslationX(tx);
        }
    }

    private void switchSide() {
        dockSide = dockSide == 0 ? 1 : 0;
        prefs.edit().putInt("dock_side", dockSide).apply();
        applySide(true);
        toast(dockSide == 0 ? "药丸已停靠左侧" : "药丸已停靠右侧");
    }

    private View buildSidePill() {
        LinearLayout pill = new LinearLayout(this);
        pill.setOrientation(LinearLayout.VERTICAL);
        pill.setGravity(Gravity.CENTER_HORIZONTAL);
        pill.setPadding(sdp(3), sdp(9), sdp(3), sdp(9));

        pillGlass = new GlassDrawable(dp(23), DUSK, true);
        pillGlass.setSmoked(true);   // 官网首页为浅色，需深底保证可读性
        pillGlass.attach(pill);
        pill.setWillNotDraw(false);
        pill.setBackground(new android.graphics.drawable.Drawable() {
            @Override public void draw(Canvas c) { pillGlass.setBounds(0, 0, getBounds().width(), getBounds().height()); pillGlass.draw(c); }
            @Override public void setAlpha(int a) { pillGlass.setAlpha(a); }
            @Override public void setColorFilter(android.graphics.ColorFilter cf) { }
            @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
        });
        pill.post(() -> pillGlass.startFlow());

        // 顶部状态指示灯
        statusDot = new View(this);
        GradientDrawable sd = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xFF59E6A8, 0xFF4BD6FF});
        sd.setShape(GradientDrawable.OVAL);
        statusDot.setBackground(sd);
        LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(sdp(6), sdp(6));
        sLp.bottomMargin = sdp(4);
        pill.addView(statusDot, sLp);

        // ① 品牌 / 当前页面（点击显示完整信息；长按打开设置）
        View b0 = pillButton("✦", "首页", DAWN, v ->
                toast("星穹旅驿 · " + pageName + " · " + (offline ? "离线镜像" : "在线直连")));
        b0.setOnLongClickListener(v -> { openSettings(); return true; });
        pillPage = (TextView) ((LinearLayout) b0).getChildAt(1);
        pill.addView(b0);

        // ② 在线 / 离线 切换
        View b1 = pillButton("◉", "在线", DUSK, v -> toggleMode());
        modeLabel = (TextView) ((LinearLayout) b1).getChildAt(1);
        ((LinearLayout) b1).setOnLongClickListener(v -> {
            toast("点击切换「在线直连 / 离线镜像」"); return true;
        });
        pill.addView(b1);

        // ③ 离线小游戏（纯本地资源，在线/离线都能玩）
        pill.addView(pillButton("◈", "游戏", DUSK, v -> openGame()));

        // ④ 刷新
        pill.addView(pillButton("↻", "刷新", DUSK, v -> {
            web.reload();
            toast(offline ? "已刷新离线镜像" : "正在重新连接站点");
        }));

        // ⑤ 换边
        pill.addView(pillButton("⇄", "换边", DUSK, v -> switchSide()));

        // ⑥ 检查更新（拉取 GitHub 最新 Release）
        pill.addView(pillButton("⇧", "更新", DUSK, v -> {
            toast("正在检查更新…");
            updater.checkForUpdates(true);
        }));
        return pill;
    }

    /** 设置面板：自动更新开关 + 检查更新（长按品牌 StarMark 或点「更新」可进入） */
    private void openSettings() {
        boolean cur = prefs.getBoolean("auto_update", true);
        android.widget.LinearLayout lay = new android.widget.LinearLayout(this);
        lay.setOrientation(android.widget.LinearLayout.VERTICAL);
        lay.setPadding(dp(20), dp(16), dp(20), dp(8));

        TextView ver = new TextView(this);
        ver.setText("当前版本：v" + VERSION_NAME);
        ver.setTextColor(0xFFDCE6FF);
        ver.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        lay.addView(ver);

        android.widget.CheckBox cb = new android.widget.CheckBox(this);
        cb.setText("自动检查更新（发现新版本自动提示）");
        cb.setChecked(cur);
        cb.setTextColor(0xFFDCE6FF);
        cb.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        cb.setPadding(0, dp(10), 0, dp(4));
        lay.addView(cb);

        TextView hint = new TextView(this);
        hint.setText("更新包来自 GitHub 官方仓库 ChaosSurvivalProject/xqly-app，发布即取最新 Release");
        hint.setTextColor(Color.argb(150, 200, 210, 240));
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        hint.setPadding(0, dp(4), 0, dp(6));
        lay.addView(hint);

        android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(this);
        b.setTitle("设置");
        b.setView(lay);
        b.setPositiveButton("保存", (d, w) -> {
            prefs.edit().putBoolean("auto_update", cb.isChecked()).apply();
            toast(cb.isChecked() ? "已开启自动更新" : "已关闭自动更新");
        });
        b.setNegativeButton("检查更新", (d, w) -> updater.checkForUpdates(true));
        b.setNeutralButton("取消", null);
        b.show();
    }

    private View pillButton(String icon, String label, int accent, View.OnClickListener click) {
        LinearLayout b = new LinearLayout(this);
        b.setOrientation(LinearLayout.VERTICAL);
        b.setGravity(Gravity.CENTER);
        b.setClickable(true);

        // 与底部 Dock 共用同一套玻璃材质：亮磨砂药丸（容器已是烟熏深底，形成层次）
        GlassDrawable g = new GlassDrawable(sdp(14), accent, false);
        g.setSmoked(false);
        g.setIntensity(0.55f);
        g.attach(b);
        b.setBackground(new android.graphics.drawable.Drawable() {
            @Override public void draw(Canvas c) { g.setBounds(0, 0, getBounds().width(), getBounds().height()); g.draw(c); }
            @Override public void setAlpha(int a) { g.setAlpha(a); }
            @Override public void setColorFilter(android.graphics.ColorFilter cf) { }
            @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
        });

        TextView ic = new TextView(this);
        ic.setText(icon);
        ic.setTextColor(0xFFE9F0FF);
        ic.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15 * uiScale);
        ic.setGravity(Gravity.CENTER);
        ic.setIncludeFontPadding(false);
        b.addView(ic, new LinearLayout.LayoutParams(-1, -2));

        TextView tx = new TextView(this);
        tx.setText(label);
        tx.setTextColor(Color.argb(190, 222, 234, 255));
        tx.setTextSize(TypedValue.COMPLEX_UNIT_SP, 8 * uiScale);
        tx.setGravity(Gravity.CENTER);
        tx.setSingleLine(true);
        tx.setIncludeFontPadding(false);
        b.addView(tx, new LinearLayout.LayoutParams(-1, -2));

        b.setLayoutParams(new LinearLayout.LayoutParams(-1, sdp(42)));
        b.setOnClickListener(click);
        b.setOnTouchListener((v, e) -> {
            int a = e.getAction();
            if (a == android.view.MotionEvent.ACTION_DOWN) {
                v.animate().scaleX(0.90f).scaleY(0.90f).setDuration(110).start();
                g.setIntensity(0.95f);
            } else if (a == android.view.MotionEvent.ACTION_UP || a == android.view.MotionEvent.ACTION_CANCEL) {
                v.animate().scaleX(1f).scaleY(1f).setDuration(260).start();
                g.setIntensity(0.55f);
            }
            return false;   // 不消费，保证 OnClickListener / OnLongClickListener 正常触发
        });
        return b;
    }

    private FrameLayout buildArchiveOverlay() {
        FrameLayout overlay = new FrameLayout(this);
        overlay.setVisibility(View.GONE);
        overlay.setBackgroundColor(Color.argb(214, 4, 6, 18));
        overlay.setClickable(true);

        FrameLayout card = new FrameLayout(this);
        card.setWillNotDraw(false);
        GlassDrawable cg = new GlassDrawable(dp(26), DUSK, true);
        cg.attach(card);
        card.setBackground(new android.graphics.drawable.Drawable() {
            @Override public void draw(Canvas c) { cg.setBounds(0, 0, getBounds().width(), getBounds().height()); cg.draw(c); }
            @Override public void setAlpha(int a) { cg.setAlpha(a); }
            @Override public void setColorFilter(android.graphics.ColorFilter cf) { }
            @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
        });
        card.post(cg::startFlow);
        FrameLayout.LayoutParams cLp = new FrameLayout.LayoutParams(-1, -1);
        cLp.leftMargin = sdp(10); cLp.rightMargin = sdp(10);
        cLp.topMargin = sdp(10); cLp.bottomMargin = sdp(92);
        overlay.addView(card, cLp);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        card.addView(col, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(16), dp(12), dp(12), dp(8));
        TextView t = new TextView(this);
        t.setText("全站档案");
        t.setTextColor(0xFFF3F6FF);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        head.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView close = new TextView(this);
        close.setText("✕");
        close.setTextColor(0xFFC9D6FF);
        close.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        close.setPadding(dp(12), dp(4), dp(8), dp(4));
        close.setOnClickListener(v -> closeArchive());
        head.addView(close);
        col.addView(head);

        archiveWeb = new WebView(this);
        archiveWeb.setBackgroundColor(Color.TRANSPARENT);
        WebSettings s = archiveWeb.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        archiveWeb.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public void openPage(String path) {
                runOnUiThread(() -> {
                    closeArchive();
                    currentPath = path;
                    syncTitle();
                    web.loadUrl(BASE + path);
                });
            }
            @JavascriptInterface
            public void close() { runOnUiThread(MainActivity.this::closeArchive); }
        }, "Android");
        archiveWeb.loadUrl("file:///android_asset/archive.html");
        col.addView(archiveWeb, new LinearLayout.LayoutParams(-1, 0, 1f));
        return overlay;
    }

    /** 离线小游戏：整页本地 HTML，无任何网络请求，飞行模式也能玩 */
    private FrameLayout buildGameOverlay() {
        FrameLayout overlay = new FrameLayout(this);
        overlay.setVisibility(View.GONE);
        overlay.setBackgroundColor(0xFF050713);
        overlay.setClickable(true);

        gameWeb = new WebView(this);
        gameWeb.setBackgroundColor(Color.TRANSPARENT);
        WebSettings s = gameWeb.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        gameWeb.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public void close() { runOnUiThread(MainActivity.this::closeGame); }
        }, "Android");
        gameWeb.loadUrl("file:///android_asset/game.html");
        overlay.addView(gameWeb, new FrameLayout.LayoutParams(-1, -1));
        return overlay;
    }

    private FrameLayout buildSplash() {
        FrameLayout sp = new FrameLayout(this);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF0A1030, 0xFF050713, 0xFF120B2C});
        sp.setBackground(bg);
        GlowView spGlow = new GlowView(this);
        sp.addView(spGlow, new FrameLayout.LayoutParams(-1, -1));

        // 中央玻璃卡片
        FrameLayout card = new FrameLayout(this);
        GlassDrawable sg = new GlassDrawable(dp(30), DAWN, true);
        sg.attach(card);
        card.setBackground(new android.graphics.drawable.Drawable() {
            @Override public void draw(Canvas c) { sg.setBounds(0, 0, getBounds().width(), getBounds().height()); sg.draw(c); }
            @Override public void setAlpha(int a) { sg.setAlpha(a); }
            @Override public void setColorFilter(android.graphics.ColorFilter cf) { }
            @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
        });
        card.post(sg::startFlow);
        FrameLayout.LayoutParams cLp = new FrameLayout.LayoutParams(sdp(250), sdp(230));
        cLp.gravity = Gravity.CENTER;
        sp.addView(card, cLp);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(sdp(20), sdp(28), sdp(20), sdp(24));
        card.addView(col, new FrameLayout.LayoutParams(-1, -1));

        StarMark mark = new StarMark(this);
        col.addView(mark, new LinearLayout.LayoutParams(sdp(64), sdp(64)));
        TextView name = new TextView(this);
        name.setText("星穹旅驿");
        name.setTextColor(0xFFF6F8FF);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22 * uiScale);
        name.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        LinearLayout.LayoutParams nLp = new LinearLayout.LayoutParams(-2, -2);
        nLp.topMargin = sdp(14);
        col.addView(name, nLp);
        TextView sub = new TextView(this);
        sub.setText("阵营对抗 · 乱世生存");
        sub.setTextColor(Color.argb(165, 220, 228, 255));
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f * uiScale);
        sub.setLetterSpacing(0.18f);
        LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(-2, -2);
        sLp.topMargin = sdp(5);
        col.addView(sub, sLp);
        TextView loading = new TextView(this);
        loading.setText("正在连接星穹界…");
        loading.setTextColor(0xFFFFD99A);
        loading.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f * uiScale);
        LinearLayout.LayoutParams lLp = new LinearLayout.LayoutParams(-2, -2);
        lLp.topMargin = sdp(22);
        col.addView(loading, lLp);
        return sp;
    }

    // ---------------------------------------------------------------- WebView
    private void configureWeb(WebView w) {
        WebSettings s = w.getSettings();
        s.setJavaScriptEnabled(true);
        w.addJavascriptInterface(new DockScrollBridge(this), "AndroidDock");
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        s.setDefaultTextEncodingName("UTF-8");
        if (Build.VERSION.SDK_INT >= 26) s.setSafeBrowsingEnabled(false);

        w.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if (!offline || Build.VERSION.SDK_INT < 21) return null;
                return localResponse(request.getUrl());
            }
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
                if (!offline) return null;
                return localResponse(Uri.parse(url));
            }
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleUrl(request.getUrl());
            }
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleUrl(Uri.parse(url));
            }
            @Override
            public void onPageFinished(WebView view, String url) {
                hideSplash();
                updateProgress(100);
                syncTitle();
                // 注入滚动监听：iOS 26 标签栏式「下滚收拢 / 上滚展开」
                view.evaluateJavascript(
                        "javascript:(function(){if(window.__xqlyDock)return;window.__xqlyDock=1;"
                                + "function h(){var y=window.scrollY||document.documentElement.scrollTop"
                                + "||document.body.scrollTop||0;if(window.AndroidDock)window.AndroidDock.onScroll(y|0);}"
                                + "window.addEventListener('scroll',h,true);"
                                + "document.addEventListener('scroll',h,true);})();", null);
            }
            @Override
            public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
                // Vue Router 的 pushState 会走到这里，用于同步 SPA 路由
                try {
                    Uri u = Uri.parse(url);
                    if (HOST.equals(u.getHost())) { currentPath = u.getPath(); syncTitle(); }
                } catch (Exception ignored) { }
            }
            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (!offline && Build.VERSION.SDK_INT >= 23 && request.isForMainFrame()) {
                    setOffline(true, "连接失败，已自动切换到离线镜像（全站内容已内置）");
                }
            }
            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                if (!offline && failingUrl != null && failingUrl.contains(HOST)) {
                    setOffline(true, "连接失败，已自动切换到离线镜像");
                }
            }
        });

        w.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView view, int newProgress) { updateProgress(newProgress); }
            @Override public void onReceivedTitle(WebView view, String title) { syncTitle(); }
            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customView != null) { callback.onCustomViewHidden(); return; }
                customView = view;
                customViewCallback = callback;
                fullscreenContainer = new FrameLayout(MainActivity.this);
                fullscreenContainer.setBackgroundColor(Color.BLACK);
                fullscreenContainer.addView(view, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
                root.addView(fullscreenContainer, new FrameLayout.LayoutParams(-1, -1));
                fullscreenContainer.bringToFront();
            }
            @Override public void onHideCustomView() { hideCustomView(); }
        });

        w.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
            catch (Exception e) { toast("无法打开下载链接"); }
        });
    }

    private boolean handleUrl(Uri u) {
        if (u == null) return false;
        String scheme = u.getScheme();
        if ("tel".equals(scheme) || "mailto".equals(scheme)) {
            try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) { }
            return true;
        }
        if (HOST.equals(u.getHost())) {
            currentPath = u.getPath();
            syncTitle();
            return false;
        }
        // 站外链接交给系统浏览器，避免应用内跳走
        try { startActivity(new Intent(Intent.ACTION_VIEW, u)); }
        catch (Exception e) { toast("无法打开外部链接"); }
        return true;
    }

    /** 离线模式：把站点请求映射到 assets 镜像 */
    private WebResourceResponse localResponse(Uri u) {
        if (u == null || !HOST.equals(u.getHost())) return null;
        String path = u.getPath();
        if (path == null || path.isEmpty()) path = "/";
        boolean isApi = path.startsWith("/api/");
        String rel = path.substring(1);
        if (rel.isEmpty()) rel = "index.html";

        InputStream is = openAsset("site/" + rel);
        if (is == null && isApi) is = apiSnapshot(path, u.getQuery());
        if (is == null && !isApi && !rel.contains(".")) {
            // SPA history fallback：按子站选择入口，并把 MIME 纠正为 text/html
            if (rel.startsWith("wiki")) is = openAsset("site/wiki/index.html");
            else if (rel.startsWith("admin")) is = openAsset("site/admin/index.html");
            else is = openAsset("site/index.html");
            rel = "index.html";
        }
        if (is == null) {
            if (isApi) is = apiSnapshot(path, u.getQuery());
            if (is == null) {
                byte[] empty = (isApi ? "{\"code\":0,\"message\":\"offline\",\"data\":null}" : "").getBytes();
                is = new ByteArrayInputStream(empty);
            }
        }
        Map<String, String> headers = new HashMap<>();
        headers.put("Access-Control-Allow-Origin", "*");
        headers.put("Cache-Control", "public, max-age=31536000, immutable");
        String mime = mimeOf(rel, isApi);
        return new WebResourceResponse(mime, "UTF-8", 200, "OK", headers, is);
    }

    private InputStream apiSnapshot(String path, String query) {
        String key = path.replaceFirst("^/api/", "").split("\\?")[0];
        String[] parts = key.split("/");
        // 文章详情 -> article_N.json
        if (parts.length >= 3 && "forum".equals(parts[0]) && "articles".equals(parts[1]) && isNumeric(parts[2])) {
            InputStream a = openAsset("data/article_" + parts[2] + ".json");
            if (a != null) return a;
        }
        String norm = ("api_" + key).replaceAll("[^A-Za-z0-9]+", "_").toLowerCase(Locale.US);
        String q = query == null ? "" : ("_" + query.replaceAll("[^A-Za-z0-9]+", "_").toLowerCase(Locale.US));
        String want = norm + q;
        // 精确 / 前缀 / 包含 三级匹配
        for (String f : apiSnapshots) {
            String n = f.replace(".json", "").toLowerCase(Locale.US);
            if (n.equals(norm) || n.equals(want)) return openAsset("data/" + f);
        }
        for (String f : apiSnapshots) {
            String n = f.replace(".json", "").toLowerCase(Locale.US);
            if (n.startsWith(norm) || want.startsWith(n)) return openAsset("data/" + f);
        }
        for (String f : apiSnapshots) {
            if (f.toLowerCase(Locale.US).contains(parts.length > 0 ? parts[parts.length - 1].toLowerCase(Locale.US) : "x")
                    && parts.length > 1 && f.contains("articles")) return openAsset("data/" + f);
        }
        return null;
    }

    private InputStream openAsset(String p) {
        try { return getAssets().open(p); } catch (Exception e) { return null; }
    }

    private static boolean isNumeric(String s) {
        if (s == null || s.isEmpty()) return false;
        for (char c : s.toCharArray()) if (!Character.isDigit(c)) return false;
        return true;
    }

    private static String mimeOf(String rel, boolean isApi) {
        String low = rel.toLowerCase(Locale.US);
        if (low.endsWith(".js")) return "application/javascript";
        if (low.endsWith(".css")) return "text/css";
        if (low.endsWith(".html") || low.endsWith(".htm")) return "text/html";
        if (low.endsWith(".json")) return "application/json";
        if (low.endsWith(".woff2")) return "font/woff2";
        if (low.endsWith(".woff")) return "font/woff";
        if (low.endsWith(".ttf")) return "font/ttf";
        if (low.endsWith(".svg")) return "image/svg+xml";
        if (low.endsWith(".png")) return "image/png";
        if (low.endsWith(".jpg") || low.endsWith(".jpeg")) return "image/jpeg";
        if (low.endsWith(".webp")) return "image/webp";
        if (low.endsWith(".gif")) return "image/gif";
        if (low.endsWith(".ico")) return "image/x-icon";
        if (low.endsWith(".mp4")) return "video/mp4";
        if (isApi) return "application/json";
        // 无扩展名的 SPA 路由一律按 HTML 返回，避免被当作下载
        if (!low.contains(".")) return "text/html";
        return "application/octet-stream";
    }

    // ---------------------------------------------------------------- 交互
    private void onDockClick(int index, String path) {
        if (gameOpen) closeGame();
        dock.setActive(index);
        if ("__ARCHIVE__".equals(path)) { openArchive(); return; }
        if (archiveOpen) closeArchive();
        currentPath = path;
        syncTitle();
        web.loadUrl(BASE + path);
    }

    private void openArchive() {
        archiveOpen = true;
        archiveOverlay.setVisibility(View.VISIBLE);
        archiveOverlay.setAlpha(0f);
        archiveOverlay.animate().alpha(1f).setDuration(260).start();
        archiveOverlay.setTranslationY(dp(24));
        archiveOverlay.animate().translationY(0f).setDuration(320).start();
    }

    private void closeArchive() {
        if (!archiveOpen) return;
        archiveOpen = false;
        archiveOverlay.animate().alpha(0f).setDuration(200).withEndAction(() ->
                archiveOverlay.setVisibility(View.GONE)).start();
    }

    private void openGame() {
        if (archiveOpen) closeArchive();
        gameOpen = true;
        gameOverlay.setVisibility(View.VISIBLE);
        gameOverlay.setAlpha(0f);
        gameOverlay.animate().alpha(1f).setDuration(240).start();
        gameOverlay.setScaleX(0.95f);
        gameOverlay.setScaleY(0.95f);
        gameOverlay.animate().scaleX(1f).scaleY(1f).setDuration(320).start();
        toast(offline ? "离线小游戏 · 方块合成（无需联网）" : "方块合成 · 本地运行，不消耗流量");
    }

    private void closeGame() {
        if (!gameOpen) return;
        gameOpen = false;
        gameOverlay.animate().alpha(0f).setDuration(200).withEndAction(() ->
                gameOverlay.setVisibility(View.GONE)).start();
    }

    private void toggleMode() {
        setOffline(!offline, offline ? "已切换：在线直连原站" : "已切换：离线镜像（内置全站内容）");
    }

    private void setOffline(boolean off, String msg) {
        if (gameOpen) closeGame();
        offline = off;
        updateModeUi();
        toast(msg);
        web.loadUrl(BASE + currentPath);
    }

    private void updateModeUi() {
        if (modeLabel == null) return;
        modeLabel.setText(offline ? "离线" : "在线");
        GradientDrawable sd = offline
                ? new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0xFFFFCE86, 0xFFFFE7BC})
                : new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0xFF59E6A8, 0xFF4BD6FF});
        sd.setShape(GradientDrawable.OVAL);
        if (offline) sd.setStroke(1, Color.argb(130, 255, 232, 176));
        statusDot.setBackground(sd);
    }

    /** 由网页滚动事件驱动：向下滚收拢 Dock，回到顶部或上滚时展开 */
    private void onWebScroll(int y) {
        int dy = y - lastScrollY;
        lastScrollY = y;
        long now = System.currentTimeMillis();
        if (now - lastCompactAt < 220) return;
        if (dock == null || gameOpen || archiveOpen) return;
        if (y < 60) {
            dock.setCompact(false);
            lastCompactAt = now;
        } else if (dy > 12) {
            dock.setCompact(true);
            lastCompactAt = now;
        } else if (dy < -8) {
            dock.setCompact(false);
            lastCompactAt = now;
        }
    }

    private void syncTitle() {
        pageName = routeName(currentPath);
        if (pillPage == null) return;
        // 药丸只有 46dp 宽，页面名压缩成两字显示，完整名称点第一项查看
        String s = pageName.startsWith("Wiki") ? "Wiki"
                : (pageName.length() > 2 ? pageName.substring(0, 2) : pageName);
        pillPage.setText(s);
    }

    private static String routeName(String p) {
        if (p == null || p.equals("/") || p.isEmpty()) return "首页";
        if (p.startsWith("/faction-beta")) return "阵营内测";
        if (p.startsWith("/forum/post")) return "帖子详情";
        if (p.startsWith("/forum/new")) return "发布帖子";
        if (p.startsWith("/forum/my")) return "我的帖子";
        if (p.startsWith("/forum/recycle")) return "回收站";
        if (p.startsWith("/forum")) return "玩家社区";
        if (p.startsWith("/team")) return "管理团队";
        if (p.startsWith("/login")) return "登录";
        if (p.startsWith("/register")) return "注册";
        if (p.startsWith("/announcements")) return "公告";
        if (p.startsWith("/kb")) return "知识库";
        if (p.startsWith("/monitor")) return "服务监控";
        if (p.startsWith("/wiki")) return "Wiki 知识库";
        if (p.startsWith("/admin")) return "管理后台";
        return "星穹旅驿";
    }

    private void updateProgress(int p) {
        if (progressFill == null) return;
        int w = root.getWidth();
        if (w <= 0) w = getResources().getDisplayMetrics().widthPixels;
        ViewGroup.LayoutParams lp = progressFill.getLayoutParams();
        lp.width = (int) (w * Math.min(100, p) / 100f);
        progressFill.setLayoutParams(lp);
        if (p >= 100) {
            progressFill.animate().alpha(0f).setDuration(320).start();
        } else {
            progressFill.setAlpha(1f);
        }
    }

    private void hideSplash() {
        if (splash == null || splash.getVisibility() == View.GONE) return;
        splash.animate().alpha(0f).setDuration(520).setStartDelay(180)
                .withEndAction(() -> { splash.setVisibility(View.GONE); }).start();
    }

    private void hideCustomView() {
        if (customView == null) return;
        if (fullscreenContainer != null) root.removeView(fullscreenContainer);
        fullscreenContainer = null;
        customView = null;
        if (customViewCallback != null) { try { customViewCallback.onCustomViewHidden(); } catch (Exception ignored) { } }
        customViewCallback = null;
    }

    private boolean isNetworkAvailable() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo ni = cm.getActiveNetworkInfo();
            return ni != null && ni.isConnected();
        } catch (Exception e) { return false; }
    }

    public void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
    private int dp(float v) { return (int) (v * density + 0.5f); }

    @Override public void onBackPressed() {
        if (customView != null) { hideCustomView(); return; }
        if (gameOpen) { closeGame(); return; }
        if (archiveOpen) { closeArchive(); return; }
        if (web.canGoBack()) { web.goBack(); return; }
        long now = System.currentTimeMillis();
        if (now - lastBack < 2000) { super.onBackPressed(); }
        else { lastBack = now; toast("再按一次退出星穹旅驿"); }
    }

    @Override protected void onResume() {
        super.onResume();
        if (dock != null) dock.startFlow();
        if (pillGlass != null) pillGlass.startFlow();
        if (web != null) web.onResume();
        if (gameWeb != null) gameWeb.onResume();
    }

    @Override protected void onPause() {
        if (dock != null) dock.stopFlow();
        if (pillGlass != null) pillGlass.stopFlow();
        if (web != null) web.onPause();
        if (gameWeb != null) gameWeb.onPause();
        super.onPause();
    }

    @Override protected void onDestroy() {
        if (web != null) { web.destroy(); web = null; }
        super.onDestroy();
    }

    // ---------------------------------------------------------------- 装饰 View
    /** 网页滚动桥：把页面滚动事件回传给 Java 层，用于 Dock 收拢/展开 */
    private static class DockScrollBridge {
        private final java.lang.ref.WeakReference<MainActivity> ref;
        DockScrollBridge(MainActivity a) { ref = new java.lang.ref.WeakReference<>(a); }
        @android.webkit.JavascriptInterface
        public void onScroll(int y) {
            MainActivity a = ref.get();
            if (a != null) a.runOnUiThread(() -> a.onWebScroll(y));
        }
    }

    /** 缓慢漂移的双色氛围光 */
    private static class GlowView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float t = 0f;
        private ValueAnimator anim;
        GlowView(Context c) {
            super(c);
            anim = ValueAnimator.ofFloat(0f, 1f);
            anim.setDuration(14000);
            anim.setRepeatCount(ValueAnimator.INFINITE);
            anim.addUpdateListener(a -> { t = (float) a.getAnimatedValue(); invalidate(); });
        }
        @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); if (anim != null) anim.start(); }
        @Override protected void onDetachedFromWindow() { if (anim != null) anim.cancel(); super.onDetachedFromWindow(); }
        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            float a = (float) (Math.sin(t * Math.PI * 2) * 0.5 + 0.5);
            p.setShader(new RadialGradient(w * (0.12f + 0.12f * a), h * 0.14f, w * 0.85f,
                    Color.argb(70, 255, 186, 92), Color.TRANSPARENT, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, p);
            p.setShader(new RadialGradient(w * (0.92f - 0.10f * a), h * 0.88f, w * 0.90f,
                    Color.argb(78, 128, 92, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, p);
            p.setShader(null);
            p.setShader(new LinearGradient(0, 0, 0, h,
                    new int[]{Color.argb(60, 10, 16, 48), Color.TRANSPARENT, Color.argb(90, 4, 5, 18)},
                    null, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, p);
            p.setShader(null);
        }
    }

    /** 星穹之门标记 */
    private static class StarMark extends View {
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        StarMark(Context c) {
            super(c);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeCap(Paint.Cap.ROUND);
        }
        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), cx = w / 2f, cy = h / 2f;
            float r = Math.min(w, h) * 0.40f;
            stroke.setStrokeWidth(Math.max(2f, r * 0.055f));
            stroke.setShader(new LinearGradient(0, 0, w, h,
                    new int[]{0xFFFFCE86, 0xFFFFF0D6, 0xFFA87CFF}, null, Shader.TileMode.CLAMP));
            c.drawCircle(cx, cy, r, stroke);
            c.drawCircle(cx, cy, r * 0.80f, stroke);
            float L = r * 0.58f;
            c.drawLine(cx, cy - L, cx, cy + L, stroke);
            c.drawLine(cx - L, cy, cx + L, cy, stroke);
            float d = r * 0.34f;
            c.drawLine(cx - d, cy - d, cx + d, cy + d, stroke);
            c.drawLine(cx - d, cy + d, cx + d, cy - d, stroke);
            stroke.setStyle(Paint.Style.FILL);
            c.drawCircle(cx, cy, r * 0.13f, stroke);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setShader(null);
        }
    }
}
