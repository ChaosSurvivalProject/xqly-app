package cn.xqly.app;

import android.app.Activity;
import android.app.AlertDialog;
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
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.SslErrorHandler;
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

import cn.xqly.app.core.AppIdentity;
import cn.xqly.app.core.AppState;
import cn.xqly.app.core.ContentManifest;
import cn.xqly.app.core.DesignTokens;
import cn.xqly.app.core.FeatureFlags;
import cn.xqly.app.core.MiniJson;
import cn.xqly.app.core.SearchIndex;
import cn.xqly.app.core.UrlPolicy;
import cn.xqly.app.core.UserData;
import cn.xqly.app.core.UserPrefs;
import cn.xqly.app.ui.LibraryView;

/** 星穹旅驿 · 星穹玻璃设计系统客户端（v2.0.0 安全加固版） */
public class MainActivity extends Activity implements WebBridge.Host {

    private static final String HOST = AppIdentity.SITE_HOST;
    private static final String BASE = AppIdentity.SITE_BASE;
    private static final int DAWN = 0xFFFFCE86;
    private static final int DUSK = 0xFFA87CFF;

    public static final String VERSION_NAME = "2.1.0";
    public static final int VERSION_CODE = 7;

    private FrameLayout root;
    private WebView web, archiveWeb, gameWeb, libraryWeb;
    private GlassDock dock;
    private GlassDrawable pillGlass;
    private View sidePill;
    private TextView modeLabel, pillPage;
    private View statusDot, progressFill, progressTrack;
    private FrameLayout archiveOverlay, gameOverlay, libraryOverlay, splash, fullscreenContainer;
    private StateOverlay stateOverlay;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private GlowView glow;

    private float density;
    private float uiScale = 1f;
    private boolean isTablet = false, isLandscape = false, foldUnfolded = false;
    private boolean offline = false;
    private boolean archiveOpen = false, gameOpen = false, libraryOpen = false;
    private Updater updater;
    private String currentPath = "/";
    private String pageName = "首页";
    private int dockSide = 0;
    private UserPrefs userPrefs, cachePrefs;
    private WebView activeWeb;
    private long lastBack = 0L;
    private List<String> apiSnapshots = new ArrayList<>();
    private DesignTokens.Theme theme = DesignTokens.Theme.DARK;
    private DesignTokens.Mode displayMode = DesignTokens.Mode.GLASS;
    private boolean pageFinished = false;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private Runnable loadTimeout;
    private SearchIndex.Index searchIndex;
    private int bridgeRejected = 0;
    private static final int LOAD_TIMEOUT_MS = 20000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        density = getResources().getDisplayMetrics().density;
        userPrefs = new PrefsStore(this, true);
        cachePrefs = new PrefsStore(this, false);
        UserData.migrate(userPrefs);           // 真实逐级迁移，异常不清空
        computeScale();
        dockSide = "1".equals(userPrefs.get("u.dock_side", "0")) ? 1 : 0;
        theme = "light".equals(userPrefs.get("u.theme", "dark")) ? DesignTokens.Theme.LIGHT : DesignTokens.Theme.DARK;
        displayMode = modeOf(userPrefs.get("u.display_mode", "glass"));

        // 调试 WebView 一律关闭（不依赖 BuildConfig.DEBUG）
        WebView.setWebContentsDebuggingEnabled(false);

        root = new FrameLayout(this);
        root.setBackgroundColor(palette().get(DesignTokens.ColorKey.BACKGROUND));
        root.setClipChildren(false);
        root.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if ((r - l) != (or - ol) || (b - t) != (ob - ot)) applyAdaptiveLayout();
        });
        glow = new GlowView(this);
        root.addView(glow, new FrameLayout.LayoutParams(-1, -1));

        web = new WebView(this);
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));
        web.setBackgroundColor(palette().get(DesignTokens.ColorKey.BACKGROUND));
        configureWeb(web, false);

        sidePill = buildSidePill();
        root.addView(sidePill, sidePillParams());

        progressTrack = new View(this);
        progressTrack.setBackgroundColor(Color.argb(30, 255, 255, 255));
        FrameLayout.LayoutParams ptLp = new FrameLayout.LayoutParams(-1, dp(2));
        ptLp.gravity = Gravity.TOP;
        root.addView(progressTrack, ptLp);
        progressFill = new View(this);
        progressFill.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xFFA87CFF, 0xFFFFCE86, 0xFF7FD4FF}));
        FrameLayout.LayoutParams pf = new FrameLayout.LayoutParams(0, dp(2));
        pf.gravity = Gravity.TOP;
        root.addView(progressFill, pf);

        dock = new GlassDock(this);
        dock.setScale(uiScale);
        dock.setOnDockClick((index, path) -> onDockClick(index, path));
        root.addView(dock, dockParams());

        stateOverlay = new StateOverlay(this);
        stateOverlay.setOnAction(this::onStateAction);
        stateOverlay.setVisibility(View.GONE);
        root.addView(stateOverlay, new FrameLayout.LayoutParams(-1, -1));

        archiveOverlay = buildLocalOverlay("archive.html");
        root.addView(archiveOverlay, new FrameLayout.LayoutParams(-1, -1));
        // library 用原生「星穹玻璃」界面（LibraryView），不再走 WebView 壳。
        // 仍创建 libraryWeb 并保非 null，以维持生命周期遍历（onResume/onPause/destroy）稳定。
        libraryWeb = new WebView(this);
        configureWeb(libraryWeb, false);
        libraryOverlay = buildLibraryOverlay();
        root.addView(libraryOverlay, new FrameLayout.LayoutParams(-1, -1));
        gameOverlay = buildLocalOverlay("game.html");
        root.addView(gameOverlay, new FrameLayout.LayoutParams(-1, -1));
        splash = buildSplash();
        root.addView(splash, new FrameLayout.LayoutParams(-1, -1));

        setContentView(root);
        applyThemeAndMode();

        try {
            for (String f : getAssets().list("data"))
                if (f.endsWith(".json")) apiSnapshots.add(f);
        } catch (IOException ignored) { }
        loadSearchIndex();
        verifyBundledContent();
        updater = new Updater(this);

        if (!isNetworkAvailable()) {
            offline = true;
            toast("未检测到网络，已启用离线镜像（内容已内置）");
        }
        updateModeUi();
        syncTitle();
        root.post(this::applyAdaptiveLayout);
        navigate(BASE + "/");

        // 自动更新：首屏稳定后延迟检查，且受功能开关控制
        if (FeatureGate.enabled(this, "auto_update") && isNetworkAvailable()) {
            ui.postDelayed(() -> { if (!isFinishing() && updater != null) updater.checkForUpdates(false); }, 6000);
        }
    }

    // ---------------------------------------------------------------- 设计令牌 / 模式
    private DesignTokens.Palette palette() { return DesignTokens.palette(theme, displayMode); }

    private static DesignTokens.Mode modeOf(String s) {
        if ("eco".equals(s)) return DesignTokens.Mode.ECO;
        if ("contrast".equals(s)) return DesignTokens.Mode.CONTRAST;
        return DesignTokens.Mode.GLASS;
    }

    private void applyThemeAndMode() {
        int bg = palette().get(DesignTokens.ColorKey.BACKGROUND);
        root.setBackgroundColor(bg);
        if (web != null) web.setBackgroundColor(bg);
        boolean anim = displayMode == DesignTokens.Mode.GLASS && FeatureGate.enabled(this, "glass_anim");
        if (dock != null) { if (anim) dock.startFlow(); else dock.stopFlow(); }
        if (pillGlass != null) { if (anim) pillGlass.startFlow(); else pillGlass.stopFlow(); }
        glow.setReducedMotion(!anim);
        applyWebTheme();
    }

    /** 把当前深/浅主题与三种显示模式写到每个内置页面的 <html> 上，共享同一套 DOM。 */
    private void applyWebTheme() {
        final String t = theme == DesignTokens.Theme.LIGHT ? "light" : "dark";
        final String m = displayMode == DesignTokens.Mode.ECO ? "eco"
                : displayMode == DesignTokens.Mode.CONTRAST ? "contrast" : "glass";
        final String js = "(function(){var d=document.documentElement;if(!d)return;"
                + "d.setAttribute('data-theme','" + t + "');"
                + "d.setAttribute('data-mode','" + m + "');})();";
        for (WebView w : new WebView[]{web, archiveWeb, libraryWeb, gameWeb}) {
            if (w != null) try { w.evaluateJavascript(js, null); } catch (Exception ignored) { }
        }
    }

    /** 三模式共享同一套导航/业务状态，只切换材质与对比度，不做三套孤立页面。 */
    private void setDisplayMode(DesignTokens.Mode m) {
        displayMode = m;
        userPrefs.put("u.display_mode", m == DesignTokens.Mode.ECO ? "eco"
                : m == DesignTokens.Mode.CONTRAST ? "contrast" : "glass");
        applyThemeAndMode();
        toast("显示模式：" + (m == DesignTokens.Mode.GLASS ? "星穹玻璃"
                : m == DesignTokens.Mode.ECO ? "原生节能" : "增强可读"));
    }

    private void toggleTheme() {
        theme = theme == DesignTokens.Theme.DARK ? DesignTokens.Theme.LIGHT : DesignTokens.Theme.DARK;
        userPrefs.put("u.theme", theme == DesignTokens.Theme.LIGHT ? "light" : "dark");
        applyThemeAndMode();
        toast(theme == DesignTokens.Theme.LIGHT ? "已切换浅色主题" : "已切换深色主题");
    }

    // ---------------------------------------------------------------- 自适应布局
    private void computeScale() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        float wDp = dm.widthPixels / density, hDp = dm.heightPixels / density;
        float minSide = Math.min(wDp, hDp);
        isTablet = minSide >= 600f;
        isLandscape = wDp > hDp * 1.15f;
        foldUnfolded = minSide >= 720f;   // 展开折叠屏近似判定（无 Jetpack WindowManager 时的保守实现）
        uiScale = Math.max(1f, Math.min(1.7f, minSide / 430f));
    }

    private boolean useRail() { return isTablet || foldUnfolded || (isLandscape && !isTablet); }

    private FrameLayout.LayoutParams dockParams() {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, sdp(72));
        lp.gravity = Gravity.BOTTOM;
        lp.leftMargin = sdp(10); lp.rightMargin = sdp(10); lp.bottomMargin = sdp(10);
        return lp;
    }

    private FrameLayout.LayoutParams sidePillParams() {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(sdp(46), -2);
        lp.gravity = Gravity.LEFT | Gravity.CENTER_VERTICAL;
        lp.leftMargin = sdp(8);
        return lp;
    }

    /** 手机竖屏=底栏；横屏/平板/展开=竖向导航栏 + 正文让位，不拉伸正文。 */
    private void applyAdaptiveLayout() {
        if (root == null || root.getWidth() <= 0 || dock == null) return;
        boolean rail = useRail();
        dock.setVerticalRail(rail);
        dock.setRail(rail);
        FrameLayout.LayoutParams dpLp = (FrameLayout.LayoutParams) dock.getLayoutParams();
        if (rail) {
            dpLp.width = sdp(76);
            dpLp.height = Math.min(root.getHeight() - sdp(24), sdp(420));
            dpLp.gravity = Gravity.LEFT | Gravity.CENTER_VERTICAL;
            dpLp.leftMargin = sdp(10); dpLp.rightMargin = 0; dpLp.bottomMargin = 0;
        } else {
            dpLp.width = isTablet ? sdp(440) : -1;
            dpLp.height = sdp(72);
            dpLp.gravity = (isTablet ? Gravity.CENTER_HORIZONTAL : 0) | Gravity.BOTTOM;
            dpLp.leftMargin = sdp(10); dpLp.rightMargin = sdp(10); dpLp.bottomMargin = sdp(10);
        }
        dock.setLayoutParams(dpLp);

        if (web != null) {
            int contentPad = rail ? sdp(96) : 0;
            web.setPadding(contentPad, 0, 0, rail ? 0 : sdp(82));
        }
        if (sidePill != null) {
            FrameLayout.LayoutParams sp = (FrameLayout.LayoutParams) sidePill.getLayoutParams();
            sp.gravity = (rail ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL;
            sp.leftMargin = sdp(8); sp.rightMargin = sdp(8);
            sidePill.setLayoutParams(sp);
        }
        applySide(false);
    }

    private void applySide(boolean animate) {
        if (sidePill == null || root.getWidth() <= 0 || useRail()) return;
        float tx = dockSide == 1 ? (root.getWidth() - sidePill.getWidth() - sdp(16)) : 0f;
        if (animate) sidePill.animate().translationX(tx).setDuration(420)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.5f)).start();
        else sidePill.setTranslationX(tx);
    }

    private void switchSide() {
        dockSide = dockSide == 0 ? 1 : 0;
        userPrefs.put("u.dock_side", String.valueOf(dockSide));
        applySide(true);
        toast(dockSide == 0 ? "药丸已停靠左侧" : "药丸已停靠右侧");
    }

    // ---------------------------------------------------------------- 侧边药丸
    private View buildSidePill() {
        LinearLayout pill = new LinearLayout(this);
        pill.setOrientation(LinearLayout.VERTICAL);
        pill.setGravity(Gravity.CENTER_HORIZONTAL);
        pill.setPadding(sdp(3), sdp(9), sdp(3), sdp(9));
        pillGlass = new GlassDrawable(dp(23), DUSK, true);
        pillGlass.setSmoked(true);
        pillGlass.attach(pill);
        pill.setWillNotDraw(false);
        pill.setBackground(wrap(pillGlass));
        pill.post(() -> pillGlass.startFlow());

        statusDot = new View(this);
        GradientDrawable sd = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xFF59E6A8, 0xFF4BD6FF});
        sd.setShape(GradientDrawable.OVAL);
        statusDot.setBackground(sd);
        LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(sdp(6), sdp(6));
        sLp.bottomMargin = sdp(4);
        pill.addView(statusDot, sLp);

        View b0 = pillButton("✦", "首页", DAWN, v ->
                toast("星穹旅驿 · " + pageName + " · " + (offline ? "离线镜像" : "在线直连")));
        b0.setOnLongClickListener(v -> { openSettings(); return true; });
        pillPage = (TextView) ((LinearLayout) b0).getChildAt(1);
        pill.addView(b0);

        View b1 = pillButton("◉", "在线", DUSK, v -> toggleMode());
        modeLabel = (TextView) ((LinearLayout) b1).getChildAt(1);
        pill.addView(b1);

        pill.addView(pillButton("▤", "资料", DUSK, v -> openLibrary()));
        pill.addView(pillButton("◈", "游戏", DUSK, v -> openGame()));
        pill.addView(pillButton("↻", "刷新", DUSK, v -> reloadCurrent()));
        pill.addView(pillButton("⇄", "换边", DUSK, v -> switchSide()));
        pill.addView(pillButton("⇧", "更新", DUSK, v -> {
            toast("正在检查更新…");
            updater.checkForUpdates(true);
        }));
        return pill;
    }

    private View pillButton(String icon, String label, int accent, View.OnClickListener click) {
        LinearLayout b = new LinearLayout(this);
        b.setOrientation(LinearLayout.VERTICAL);
        b.setGravity(Gravity.CENTER);
        b.setClickable(true);
        GlassDrawable g = new GlassDrawable(sdp(14), accent, false);
        g.setSmoked(false);
        g.setIntensity(0.55f);
        g.attach(b);
        b.setBackground(wrap(g));
        b.setMinimumHeight(dp(DesignTokens.MIN_TOUCH));

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
        b.setContentDescription(label + "按钮");
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
            return false;
        });
        return b;
    }

    private android.graphics.drawable.Drawable wrap(final GlassDrawable g) {
        return new android.graphics.drawable.Drawable() {
            @Override public void draw(Canvas c) { g.setBounds(0, 0, getBounds().width(), getBounds().height()); g.draw(c); }
            @Override public void setAlpha(int a) { g.setAlpha(a); }
            @Override public void setColorFilter(android.graphics.ColorFilter cf) { }
            @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
        };
    }

    // ---------------------------------------------------------------- WebView 安全
    private void configureWeb(WebView w, final boolean localPage) {
        WebSettings s = w.getSettings();
        s.setJavaScriptEnabled(true);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(false);
        // 默认禁用文件访问；仅内置离线页面所在 WebView 打开，且禁止 file→file / file→universal
        s.setAllowFileAccess(localPage);
        s.setAllowContentAccess(false);
        s.setAllowFileAccessFromFileURLs(false);
        s.setAllowUniversalAccessFromFileURLs(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setMediaPlaybackRequiresUserGesture(true);
        s.setGeolocationEnabled(false);
        s.setSaveFormData(false);
        s.setSavePassword(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setDefaultTextEncodingName("UTF-8");
        if (Build.VERSION.SDK_INT >= 26) s.setSafeBrowsingEnabled(true);
        if (Build.VERSION.SDK_INT >= 23) s.setOffscreenPreRaster(false);

        // 唯一 JS 入口：版本化 postMessage。每个 WebView 一个独立桥实例，
        // 该实例只认可自己所属页面的 URL，杜绝跨 WebView 的来源冒用。
        final WebBridge bridge = new WebBridge(this);
        w.addJavascriptInterface(bridge, AppIdentity.BRIDGE_NAME);
        activeWeb = w;

        w.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (UrlPolicy.localResourceAllowed(url)) return null;   // 内置 js/css 资源
                if (UrlPolicy.classify(url) == UrlPolicy.Kind.BLOCKED) return emptyResponse();
                if (!offline || Build.VERSION.SDK_INT < 21) return null;
                if (localPage) return null;
                return localResponse(request.getUrl());
            }
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
                if (UrlPolicy.localResourceAllowed(url)) return null;
                if (UrlPolicy.classify(url) == UrlPolicy.Kind.BLOCKED) return emptyResponse();
                if (!offline || localPage) return null;
                return localResponse(Uri.parse(url));
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleUrl(view, request.getUrl());
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleUrl(view, Uri.parse(url));
            }
            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                bridge.setCurrentUrl(url);   // 记录本页真实地址，供消息来源绑定校验
                pageFinished = false;
                scheduleLoadTimeout();
            }
            @Override public void onPageFinished(WebView view, String url) {
                pageFinished = true;
                cancelLoadTimeout();
                hideSplash();
                updateProgress(100);
                syncTitle();
                if (stateOverlay.phase() == AppState.Phase.LOADING) {
                    stateOverlay.show(AppState.Page.HOME, offline ? AppState.Phase.OFFLINE_CACHED : AppState.Phase.SUCCESS);
                }
                injectBridgeShim(view);
                injectScrollReporter(view);
                applyWebTheme();
            }
            @Override public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
                try {
                    Uri u = Uri.parse(url);
                    if (HOST.equals(u.getHost())) { currentPath = u.getPath(); syncTitle(); }
                } catch (Exception ignored) { }
            }
            @Override public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.cancel();   // 绝不忽略证书错误
                cancelLoadTimeout();
                showState(AppState.Phase.ERROR_RETRY);
                toast("证书校验失败，已阻止加载");
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (Build.VERSION.SDK_INT >= 23 && request != null && request.isForMainFrame() && !localPage) {
                    onMainFrameError();
                }
            }
            @Override public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                if (!localPage && failingUrl != null && failingUrl.contains(HOST)) onMainFrameError();
            }
        });

        w.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView view, int p) { updateProgress(p); }
            @Override public void onReceivedTitle(WebView view, String t) { syncTitle(); }
            @Override public void onShowCustomView(View v, CustomViewCallback cb) {
                if (customView != null) { cb.onCustomViewHidden(); return; }
                customView = v; customViewCallback = cb;
                fullscreenContainer = new FrameLayout(MainActivity.this);
                fullscreenContainer.setBackgroundColor(Color.BLACK);
                fullscreenContainer.addView(v, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
                root.addView(fullscreenContainer, new FrameLayout.LayoutParams(-1, -1));
                fullscreenContainer.bringToFront();
            }
            @Override public void onHideCustomView() { hideCustomView(); }
        });

        w.setDownloadListener((url, ua, cd, mime, len) -> {
            if (UrlPolicy.classify(url) == UrlPolicy.Kind.EXTERNAL) {
                try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
                catch (Exception e) { toast("无法打开外部链接"); }
            } else toast("已阻止未授权的下载");
        });
    }

    private void onMainFrameError() {
        cancelLoadTimeout();
        if (!offline) {
            offline = true;                     // 在线失败 → 自动降级到内置镜像（保留原能力）
            updateModeUi();
            showState(AppState.Phase.OFFLINE_CACHED);
            toast("连接失败，已自动切换到离线镜像（全站内容已内置）");
            web.loadUrl(BASE + currentPath);
        } else {
            showState(AppState.Phase.OFFLINE_NO_CACHE);
        }
    }

    private void scheduleLoadTimeout() {
        cancelLoadTimeout();
        loadTimeout = () -> { if (!pageFinished) { showState(AppState.Phase.ERROR_RETRY); toast("加载超时，可重试"); } };
        ui.postDelayed(loadTimeout, LOAD_TIMEOUT_MS);
    }
    private void cancelLoadTimeout() { if (loadTimeout != null) { ui.removeCallbacks(loadTimeout); loadTimeout = null; } }

    /** 注入统一 JS shim：页面只通过 XQ.post 发送版本化消息。 */
    private void injectBridgeShim(WebView v) {
        if (!UrlPolicy.bridgeAllowed(v.getUrl())) return;
        v.evaluateJavascript(
                "(function(){if(window.XQ)return;var n=0;window.XQ={"
                        + "post:function(t,a){try{" + AppIdentity.BRIDGE_NAME + ".postMessage(JSON.stringify({v:"
                        + AppIdentity.BRIDGE_VERSION + ",id:'m'+(++n)+'_'+Date.now(),type:t,args:a||{},"
                        + "origin:location.href}))}catch(e){}},"
                        + "onMessage:function(o){try{window.dispatchEvent(new CustomEvent('xqly',{detail:o}))}catch(e){}},"
                        + "pause:function(){try{document.dispatchEvent(new Event('xqlypause'))}catch(e){}},"
                        + "resume:function(){try{document.dispatchEvent(new Event('xqlyresume'))}catch(e){}}};})();", null);
    }

    /** 滚动收拢默认关闭；仅在开关显式开启后注入。 */
    private void injectScrollReporter(WebView v) {
        if (!FeatureGate.enabled(this, "scroll_compact")) return;
        v.evaluateJavascript(
                "(function(){if(window.__xqlyScroll)return;window.__xqlyScroll=1;"
                        + "function h(){var y=window.scrollY||document.documentElement.scrollTop||0;"
                        + "if(window.XQ)XQ.post('scroll',{y:y|0});}"
                        + "window.addEventListener('scroll',h,true);})();", null);
    }

    private boolean handleUrl(WebView view, Uri u) {
        if (u == null) return true;
        UrlPolicy.Kind k = UrlPolicy.classify(u.toString());
        switch (k) {
            case SITE:
                currentPath = u.getPath() == null ? "/" : u.getPath();
                syncTitle();
                return false;
            case LOCAL_ASSET:
                return false;
            case EXTERNAL:
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); }
                catch (Exception e) { toast("无法打开外部链接"); }
                return true;
            case BLOCKED:
            default:
                toast("已阻止不安全的链接");
                return true;
        }
    }

    private WebResourceResponse emptyResponse() {
        Map<String, String> h = new HashMap<>();
        h.put("Content-Type", "text/plain");
        return new WebResourceResponse("text/plain", "UTF-8", 403, "Blocked", h,
                new ByteArrayInputStream(new byte[0]));
    }

    // ---------------------------------------------------------------- 离线镜像（保留并改进）
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
        return new WebResourceResponse(mimeOf(rel, isApi), "UTF-8", 200, "OK", headers, is);
    }

    private InputStream apiSnapshot(String path, String query) {
        String key = path.replaceFirst("^/api/", "").split("\\?")[0];
        String[] parts = key.split("/");
        if (parts.length >= 3 && "forum".equals(parts[0]) && "articles".equals(parts[1]) && isNumeric(parts[2])) {
            InputStream a = openAsset("data/article_" + parts[2] + ".json");
            if (a != null) return a;
        }
        String norm = ("api_" + key).replaceAll("[^A-Za-z0-9]+", "_").toLowerCase(Locale.US);
        String q = query == null ? "" : ("_" + query.replaceAll("[^A-Za-z0-9]+", "_").toLowerCase(Locale.US));
        String want = norm + q;
        for (String f : apiSnapshots) {
            String n = f.replace(".json", "").toLowerCase(Locale.US);
            if (n.equals(norm) || n.equals(want)) return openAsset("data/" + f);
        }
        for (String f : apiSnapshots) {
            String n = f.replace(".json", "").toLowerCase(Locale.US);
            if (n.startsWith(norm) || want.startsWith(n)) return openAsset("data/" + f);
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
        String l = rel.toLowerCase(Locale.US);
        if (l.endsWith(".js")) return "application/javascript";
        if (l.endsWith(".css")) return "text/css";
        if (l.endsWith(".html") || l.endsWith(".htm")) return "text/html";
        if (l.endsWith(".json")) return "application/json";
        if (l.endsWith(".woff2")) return "font/woff2";
        if (l.endsWith(".woff")) return "font/woff";
        if (l.endsWith(".ttf")) return "font/ttf";
        if (l.endsWith(".svg")) return "image/svg+xml";
        if (l.endsWith(".png")) return "image/png";
        if (l.endsWith(".jpg") || l.endsWith(".jpeg")) return "image/jpeg";
        if (l.endsWith(".webp")) return "image/webp";
        if (l.endsWith(".gif")) return "image/gif";
        if (l.endsWith(".ico")) return "image/x-icon";
        if (l.endsWith(".mp4")) return "video/mp4";
        if (isApi) return "application/json";
        if (!l.contains(".")) return "text/html";
        return "application/octet-stream";
    }

    // ---------------------------------------------------------------- WebBridge.Host
    /** 只判定「实际发起消息的那一页」，不再遍历全部 WebView 取并集。 */
    @Override public boolean pageTrusted(String currentUrl) {
        return currentUrl != null && UrlPolicy.bridgeAllowed(currentUrl);
    }

    @Override public boolean allowWrite() { return true; }

    @Override public void onOpenPage(String path) {
        if (!path.startsWith("/") || !UrlPolicy.sitePathAllowed(path)) { toast("已阻止越权跳转"); return; }
        if (archiveOpen) closeArchive();
        if (libraryOpen) closeLibrary();
        currentPath = path; syncTitle();
        web.loadUrl(BASE + path);
    }

    @Override public void onCloseOverlay() {
        if (gameOpen) closeGame();
        else if (archiveOpen) closeArchive();
        else if (libraryOpen) closeLibrary();
    }

    @Override public void onScroll(int y) {
        if (!FeatureGate.enabled(this, "scroll_compact")) return;   // 默认关闭
        if (dock != null && !useRail()) dock.setCompact(y > 160);
    }

    @Override public void onSearch(String q, String tag, boolean fav, String id) {
        if (searchIndex == null) loadSearchIndex();
        List<SearchIndex.Hit> hits = SearchIndex.search(searchIndex, q, tag, fav, 30);
        List<Object> arr = new ArrayList<>();
        for (SearchIndex.Hit h : hits) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", h.id); m.put("title", h.title); m.put("snippet", h.snippet);
            m.put("favorite", UserData.isFavorite(userPrefs, h.id));
            arr.add(m);
        }
        Map<String, Object> resp = new HashMap<>();
        resp.put("v", AppIdentity.BRIDGE_VERSION);
        resp.put("id", id);
        resp.put("type", "searchResult");
        resp.put("hits", arr);
        UserData.recordSearch(userPrefs, q);
        reply(MiniJson.stringify(resp));
    }

    @Override public void onFavorite(String docId, String id) {
        boolean added = UserData.toggleFavorite(userPrefs, docId);
        Map<String, Object> resp = new HashMap<>();
        resp.put("v", AppIdentity.BRIDGE_VERSION);
        resp.put("id", id);
        resp.put("type", "favoriteResult");
        resp.put("docId", docId);
        resp.put("favorite", added);
        reply(MiniJson.stringify(resp));
        toast(added ? "已收藏" : "已取消收藏");
    }

    @Override public void onProgress(String docId, float ratio, String id) {
        UserData.setProgress(userPrefs, docId, ratio);
        UserData.recordHistory(userPrefs, docId);
    }

    @Override public void onGetState(String id) {
        Map<String, Object> st = new HashMap<>();
        st.put("v", AppIdentity.BRIDGE_VERSION);
        st.put("id", id);
        st.put("type", "state");
        st.put("version", VERSION_NAME);
        st.put("offline", offline);
        st.put("theme", theme == DesignTokens.Theme.LIGHT ? "light" : "dark");
        st.put("mode", displayMode == DesignTokens.Mode.ECO ? "eco"
                : displayMode == DesignTokens.Mode.CONTRAST ? "contrast" : "glass");
        st.put("favorites", UserData.favorites(userPrefs));
        st.put("history", UserData.history(userPrefs));
        st.put("recentSearch", UserData.recentSearch(userPrefs));
        st.put("fontScale", UserData.reading(userPrefs).get("fontScale"));
        st.put("lineSpacing", UserData.reading(userPrefs).get("lineSpacing"));
        // 以下均为真实读取值，不是占位：开关来自 flags.json，内容包信息来自清单抽样校验结果
        st.put("sound", FeatureGate.enabled(this, "sound"));
        st.put("haptics", FeatureGate.enabled(this, "haptics"));
        st.put("packVersion", cachePrefs.get("pack.version", "0"));
        st.put("packFiles", cachePrefs.get("pack.files", "0"));
        st.put("packBytes", cachePrefs.get("pack.bytes", "0"));
        reply(MiniJson.stringify(st));
    }

    @Override public void onGameSave(String data, String id) {
        if (data != null && data.length() <= 8192) userPrefs.put(UserData.K_GAME_SAVE, data);
    }

    @Override public void onGameLoad(String id) {
        Map<String, Object> resp = new HashMap<>();
        resp.put("v", AppIdentity.BRIDGE_VERSION);
        resp.put("id", id);
        resp.put("type", "gameSave");
        resp.put("data", userPrefs.get(UserData.K_GAME_SAVE, ""));
        reply(MiniJson.stringify(resp));
    }

    @Override public void reply(String json) {
        String call = "window.XQ&&XQ.onMessage(" + json + ");";
        for (WebView w : new WebView[]{gameWeb, libraryWeb, archiveWeb, web}) {
            if (w != null) try { w.evaluateJavascript(call, null); } catch (Exception ignored) { }
        }
    }

    @Override public void onRejected(String reason) { bridgeRejected++; }

    // ---------------------------------------------------------------- 内容校验 / 搜索索引
    private void loadSearchIndex() {
        try {
            searchIndex = SearchIndex.build(SearchIndex.deserialize(readAsset("data/search-index.json")));
        } catch (Exception e) { searchIndex = SearchIndex.build(new ArrayList<SearchIndex.Doc>()); }
    }

    /** 校验内置内容清单：失败只保留当前可用版本，绝不覆盖。 */
    private void verifyBundledContent() {
        new Thread(() -> {
            try {
                String j = readAsset("MANIFEST.json");
                long current = Long.parseLong(cachePrefs.get("pack.version", "0"));
                ContentManifest.Report r = ContentManifest.verify(j, current);
                if (r.ok) {
                    boolean sampleOk = true;
                    List<ContentManifest.Entry> f = r.manifest.files;
                    int[] pick = {0, f.size() / 2, f.size() - 1};
                    for (int i : pick) {
                        if (i < 0 || i >= f.size()) continue;
                        ContentManifest.Entry e = f.get(i);
                        byte[] b = readAssetBytes(e.path);
                        if (b == null || !ContentManifest.verifyFile(e, b)) { sampleOk = false; break; }
                    }
                    if (sampleOk) {
                        cachePrefs.put("pack.version", String.valueOf(r.manifest.version));
                        cachePrefs.put("pack.files", String.valueOf(f.size()));
                        cachePrefs.put("pack.bytes", String.valueOf(r.totalBytes));
                    }
                }
            } catch (Exception ignored) { }
        }, "xqly-verify").start();
    }

    private String readAsset(String p) throws IOException {
        InputStream in = getAssets().open(p);
        java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192]; int n;
        while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
        in.close();
        return new String(b.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
    }
    private byte[] readAssetBytes(String p) {
        try {
            InputStream in = getAssets().open(p);
            java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192]; int n;
            while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
            in.close(); return b.toByteArray();
        } catch (Exception e) { return null; }
    }

    // ---------------------------------------------------------------- 导航 / 浮层
    private void navigate(String url) {
        UrlPolicy.Kind k = UrlPolicy.classify(url);
        if (k != UrlPolicy.Kind.SITE && k != UrlPolicy.Kind.LOCAL_ASSET) { toast("已阻止不安全的地址"); return; }
        pageFinished = false;
        showState(AppState.Phase.LOADING);
        web.loadUrl(url);
        scheduleLoadTimeout();
    }

    private void onDockClick(int index, String path) {
        if (gameOpen) closeGame();
        dock.setActive(index);
        if ("__ARCHIVE__".equals(path)) { openArchive(); return; }
        if (archiveOpen) closeArchive();
        if (libraryOpen) closeLibrary();
        currentPath = path; syncTitle();
        navigate(BASE + path);
    }

    /** 本地 WebView 浮层：archive / library / game 共用同一套加固配置。 */
    private FrameLayout buildLocalOverlay(String page) {
        FrameLayout overlay = new FrameLayout(this);
        overlay.setVisibility(View.GONE);
        overlay.setBackgroundColor(palette().get(DesignTokens.ColorKey.OVERLAY));
        overlay.setClickable(true);
        WebView w = new WebView(this);
        w.setBackgroundColor(Color.TRANSPARENT);
        configureWeb(w, true);
        w.loadUrl("file:///android_asset/" + page);
        overlay.addView(w, new FrameLayout.LayoutParams(-1, -1));
        if ("archive.html".equals(page)) archiveWeb = w;
        else if ("library.html".equals(page)) libraryWeb = w;
        else if ("game.html".equals(page)) gameWeb = w;
        return overlay;
    }

    private void showOverlay(FrameLayout o) {
        o.setAlpha(0f); o.setVisibility(View.VISIBLE);
        o.animate().alpha(1f).setDuration(240).start();
        o.setScaleX(0.97f); o.setScaleY(0.97f);
        o.animate().scaleX(1f).scaleY(1f).setDuration(300).start();
    }
    private void hideOverlay(FrameLayout o) {
        o.animate().alpha(0f).setDuration(200).withEndAction(() -> o.setVisibility(View.GONE)).start();
    }

    private void openArchive() { archiveOpen = true; activeWeb = archiveWeb; showOverlay(archiveOverlay); }
    private void closeArchive() { if (!archiveOpen) return; archiveOpen = false; activeWeb = web; hideOverlay(archiveOverlay); }
    private void openLibrary() { libraryOpen = true; activeWeb = web; showOverlay(libraryOverlay); }
    private void closeLibrary() { if (!libraryOpen) return; libraryOpen = false; activeWeb = web; hideOverlay(libraryOverlay); }

    /** 原生资料库浮层：直接承载 LibraryView，复用 core 的搜索/收藏，不加载 WebView。 */
    private FrameLayout buildLibraryOverlay() {
        FrameLayout f = new FrameLayout(this);
        f.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
        if (searchIndex == null) loadSearchIndex();
        if (searchIndex != null) {
            LibraryView lib = new LibraryView(this, searchIndex, userPrefs,
                    (id, title) -> openLibraryDoc(id, title));
            f.addView(lib, new FrameLayout.LayoutParams(-1, -1));
        }
        return f;
    }

    /** 资料库点击「打开」：关闭所有浮层并导航到在线内容（离线则回落无网态）。 */
    private void openLibraryDoc(String id, String title) {
        closeLibrary();
        closeArchive();
        closeGame();
        String path = (id == null || id.isEmpty()) ? "/" : id;
        web.loadUrl(BASE + path);
        activeWeb = web;
    }
    private void openGame() {
        if (archiveOpen) closeArchive();
        if (libraryOpen) closeLibrary();
        gameOpen = true; activeWeb = gameWeb; showOverlay(gameOverlay);
        toast(offline ? "离线小游戏 · 方块合成（无需联网）" : "方块合成 · 本地运行，不消耗流量");
    }
    private void closeGame() { if (!gameOpen) return; gameOpen = false; activeWeb = web; hideOverlay(gameOverlay); }

    // ---------------------------------------------------------------- 状态机
    private void showState(AppState.Phase p) {
        stateOverlay.show(AppState.Page.HOME, p);
        // 异常态同步给内置浮层，让每个页面都有对应的完整状态 UI，而不是只有主界面有
        if (p != AppState.Phase.SUCCESS && p != AppState.Phase.LOADING) {
            reply("{\"v\":" + AppIdentity.BRIDGE_VERSION
                    + ",\"type\":\"appState\",\"phase\":\"" + phaseKey(p) + "\"}");
        }
    }

    private static String phaseKey(AppState.Phase p) {
        switch (p) {
            case EMPTY: return "empty";
            case OFFLINE_CACHED: return "offline_cached";
            case OFFLINE_NO_CACHE: return "offline_empty";
            case ERROR_RETRY: return "error_retry";
            case PARTIAL: return "partial";
            case VALIDATION_FAILED: return "validation";
            case PERMISSION_DENIED: return "permission";
            case STORAGE_LOW: return "storage_low";
            case PENDING_SWITCH: return "pending_switch";
            default: return "success";
        }
    }

    private void onStateAction(AppState.Phase p) {
        switch (p) {
            case ERROR_RETRY:
            case OFFLINE_NO_CACHE:
                offline = !isNetworkAvailable();
                updateModeUi();
                navigate(BASE + currentPath);
                break;
            case STORAGE_LOW:
                clearCacheSafely();
                break;
            case VALIDATION_FAILED:
                verifyBundledContent();
                showState(AppState.Phase.SUCCESS);
                toast("已使用上一个可用版本");
                break;
            case PERMISSION_DENIED:
                openAppSettings();
                break;
            default:
                showState(AppState.Phase.SUCCESS);
        }
    }

    /** 只清理缓存命名空间；收藏/历史/进度/存档在独立用户文件，绝不动。 */
    private void clearCacheSafely() {
        if (web != null) web.clearCache(true);
        cachePrefs.clearAll();
        toast("已清理网页缓存，收藏与存档已保留");
    }

    private void openAppSettings() {
        try {
            startActivity(new Intent("android.settings.APPLICATION_DETAILS_SETTINGS",
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception e) { toast("请到系统设置中授权"); }
    }

    // ---------------------------------------------------------------- 模式 / 设置
    private void reloadCurrent() {
        navigate(BASE + currentPath);
        toast(offline ? "已刷新离线镜像" : "正在重新连接站点");
    }

    private void toggleMode() {
        setOffline(!offline, offline ? "已切换：在线直连原站" : "已切换：离线镜像（内置全站内容）");
    }
    private void setOffline(boolean off, String msg) {
        if (gameOpen) closeGame();
        offline = off;
        updateModeUi();
        toast(msg);
        navigate(BASE + currentPath);
    }
    private void updateModeUi() {
        if (modeLabel == null) return;
        modeLabel.setText(offline ? "离线" : "在线");
        GradientDrawable sd = offline
                ? new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0xFFFFCE86, 0xFFFFE7BC})
                : new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0xFF59E6A8, 0xFF4BD6FF});
        sd.setShape(GradientDrawable.OVAL);
        statusDot.setBackground(sd);
    }

    private void syncTitle() {
        pageName = routeName(currentPath);
        if (pillPage == null) return;
        String s = pageName.startsWith("Wiki") ? "Wiki"
                : (pageName.length() > 2 ? pageName.substring(0, 2) : pageName);
        pillPage.setText(s);
    }
    private static String routeName(String p) {
        if (p == null || p.equals("/") || p.isEmpty()) return "首页";
        if (p.startsWith("/faction-beta")) return "阵营内测";
        if (p.startsWith("/forum/post")) return "帖子详情";
        if (p.startsWith("/forum/new")) return "发布帖子";
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

    /** 设置：主题 / 三种显示模式 / 阅读设置 / 功能开关 / 更新中心 / 清理缓存 / 隐私。 */
    private void openSettings() {
        LinearLayout lay = new LinearLayout(this);
        lay.setOrientation(LinearLayout.VERTICAL);
        lay.setPadding(dp(20), dp(12), dp(20), dp(4));

        TextView ver = new TextView(this);
        ver.setText("当前版本：v" + VERSION_NAME + " (code " + VERSION_CODE + ")"
                + (offline ? " · 离线镜像" : " · 在线直连"));
        ver.setTextColor(palette().get(DesignTokens.ColorKey.TEXT));
        ver.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        lay.addView(ver);

        String[] modeNames = {"星穹玻璃", "原生节能", "增强可读"};
        DesignTokens.Mode[] modes = {DesignTokens.Mode.GLASS, DesignTokens.Mode.ECO, DesignTokens.Mode.CONTRAST};
        int cur = displayMode == DesignTokens.Mode.ECO ? 1 : displayMode == DesignTokens.Mode.CONTRAST ? 2 : 0;
        section(lay, "显示模式（星穹玻璃 / 原生节能 / 增强可读）")
                .setOnClickListener(v -> new AlertDialog.Builder(MainActivity.this)
                        .setTitle("显示模式（共享同一套导航与业务状态）")
                        .setSingleChoiceItems(modeNames, cur, (d, which) -> { setDisplayMode(modes[which]); d.dismiss(); })
                        .setNegativeButton("取消", null).show());
        section(lay, "主题：深色 / 浅色").setOnClickListener(v -> toggleTheme());
        section(lay, "阅读设置（字号 / 行距 / 正文宽度）").setOnClickListener(v -> openReadingSettings());
        section(lay, "功能开关与 Kill Switch").setOnClickListener(v -> openFlags());
        section(lay, "更新中心（校验结果 / 记录 / 回退）")
                .setOnClickListener(v -> UpdateCenter.show(this, userPrefs, null));
        section(lay, "立即检查更新").setOnClickListener(v -> updater.checkForUpdates(true));
        section(lay, "清理网页缓存（保留收藏与存档）").setOnClickListener(v -> clearCacheSafely());
        section(lay, "隐私说明与数据清单").setOnClickListener(v -> showPrivacy());

        new AlertDialog.Builder(this).setTitle("设置").setView(wrapScroll(lay))
                .setPositiveButton("关闭", null).show();
    }

    private TextView section(LinearLayout parent, String text) {
        TextView t = new TextView(this);
        t.setText("  " + text);
        t.setTextColor(palette().get(DesignTokens.ColorKey.TEXT));
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        t.setMinHeight(dp(DesignTokens.MIN_TOUCH));
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setPadding(0, dp(6), 0, dp(6));
        t.setClickable(true);
        parent.addView(t, new LinearLayout.LayoutParams(-1, -2));
        return t;
    }
    private View wrapScroll(View v) {
        android.widget.ScrollView s = new android.widget.ScrollView(this);
        s.addView(v); return s;
    }

    private void openReadingSettings() {
        Map<String, Object> r = UserData.reading(userPrefs);
        final float[] scales = {0.85f, 1.0f, 1.15f, 1.3f, 1.5f, 2.0f};
        String[] labels = {"小(85%)", "标准(100%)", "较大(115%)", "大(130%)", "特大(150%)", "最大(200%)"};
        new AlertDialog.Builder(this).setTitle("正文字号（支持系统字体缩放至约 200%）")
                .setItems(labels, (d, w) -> {
                    r.put("fontScale", scales[w]);
                    userPrefs.put(UserData.K_READING, MiniJson.stringify(r));
                    toast("字号已保存：" + labels[w]);
                }).setNegativeButton("取消", null).show();
    }

    private void openFlags() {
        FeatureFlags.Flag[] defs = FeatureFlags.DEFINITIONS;
        String[] names = new String[defs.length];
        boolean[] checked = new boolean[defs.length];
        for (int i = 0; i < defs.length; i++) {
            names[i] = defs[i].label;
            checked[i] = FeatureGate.enabled(this, defs[i].key);
        }
        new AlertDialog.Builder(this).setTitle("功能开关 / Kill Switch（关闭即停用对应能力）")
                .setMultiChoiceItems(names, checked, (d, which, isChecked) ->
                        FeatureGate.setOverride(this, defs[which].key, isChecked))
                .setPositiveButton("完成", null).show();
    }

    private void showPrivacy() {
        new AlertDialog.Builder(this).setTitle("隐私说明与数据清单")
                .setMessage("本机数据（存于应用私有目录，不上传）：\n"
                        + "· 收藏 / 收藏夹 / 自定义标签\n· 阅读历史 / 进度 / 书签\n"
                        + "· 最近搜索 / 阅读设置\n· 游戏存档 / 成就 / 统计\n"
                        + "· 更新记录（仅时间、阶段、错误分类与结论摘要）\n\n"
                        + "网络请求：仅访问官方站点 " + AppIdentity.SITE_HOST
                        + " 与 " + AppIdentity.API_HOST + "（检查更新时）。\n"
                        + "WebView 采用严格白名单，默认禁用文件访问、调试与混合内容；\n"
                        + "桥接为版本化 postMessage，仅开放阅读所需能力。\n"
                        + "无广告、无第三方统计、无隐性追踪、无付费服务。\n"
                        + "清理缓存不会删除上述任何用户数据。")
                .setPositiveButton("知道了", null).show();
    }

    // ---------------------------------------------------------------- 进度 / 启动页 / 全屏
    private void updateProgress(int p) {
        if (progressFill == null) return;
        int w = root.getWidth();
        if (w <= 0) w = getResources().getDisplayMetrics().widthPixels;
        ViewGroup.LayoutParams lp = progressFill.getLayoutParams();
        lp.width = (int) (w * Math.min(100, p) / 100f);
        progressFill.setLayoutParams(lp);
        if (p >= 100) progressFill.animate().alpha(0f).setDuration(320).start();
        else progressFill.setAlpha(1f);
    }

    private FrameLayout buildSplash() {
        FrameLayout sp = new FrameLayout(this);
        sp.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF0A1030, 0xFF050713, 0xFF120B2C}));
        GlowView g = new GlowView(this); g.setReducedMotion(true);
        sp.addView(g, new FrameLayout.LayoutParams(-1, -1));
        FrameLayout card = new FrameLayout(this);
        GlassDrawable sg = new GlassDrawable(dp(30), DAWN, true);
        sg.attach(card); card.setBackground(wrap(sg));
        card.post(sg::startFlow);
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(sdp(250), sdp(230));
        clp.gravity = Gravity.CENTER; sp.addView(card, clp);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(sdp(20), sdp(28), sdp(20), sdp(24));
        card.addView(col, new FrameLayout.LayoutParams(-1, -1));
        col.addView(new StarMark(this), new LinearLayout.LayoutParams(sdp(64), sdp(64)));
        TextView name = new TextView(this);
        name.setText("星穹旅驿");
        name.setTextColor(0xFFF6F8FF);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22 * uiScale);
        name.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(-2, -2);
        nlp.topMargin = sdp(14); col.addView(name, nlp);
        TextView sub = new TextView(this);
        sub.setText("阵营对抗 · 乱世生存");
        sub.setTextColor(Color.argb(165, 220, 228, 255));
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f * uiScale);
        sub.setLetterSpacing(0.18f);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-2, -2);
        slp.topMargin = sdp(5); col.addView(sub, slp);
        TextView load = new TextView(this);
        load.setText("正在连接星穹界…");
        load.setTextColor(0xFFFFD99A);
        load.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f * uiScale);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(-2, -2);
        llp.topMargin = sdp(22); col.addView(load, llp);
        return sp;
    }
    private void hideSplash() {
        if (splash == null || splash.getVisibility() == View.GONE) return;
        splash.animate().alpha(0f).setDuration(520).setStartDelay(180)
                .withEndAction(() -> splash.setVisibility(View.GONE)).start();
    }
    private void hideCustomView() {
        if (customView == null) return;
        if (fullscreenContainer != null) root.removeView(fullscreenContainer);
        fullscreenContainer = null; customView = null;
        if (customViewCallback != null) try { customViewCallback.onCustomViewHidden(); } catch (Exception ignored) { }
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
    private int sdp(float v) { return dp(v * uiScale); }

    @Override public void onBackPressed() {
        if (customView != null) { hideCustomView(); return; }
        if (gameOpen) { closeGame(); return; }
        if (libraryOpen) { closeLibrary(); return; }
        if (archiveOpen) { closeArchive(); return; }
        if (web != null && web.canGoBack()) { web.goBack(); return; }
        long now = System.currentTimeMillis();
        if (now - lastBack < 2000) super.onBackPressed();
        else { lastBack = now; toast("再按一次退出星穹旅驿"); }
    }

    @Override protected void onResume() {
        super.onResume();
        applyThemeAndMode();
        if (web != null) { web.onResume(); web.resumeTimers(); }
        for (WebView w : new WebView[]{gameWeb, archiveWeb, libraryWeb}) if (w != null) { w.onResume(); w.resumeTimers(); }
    }

    @Override protected void onPause() {
        if (updater != null) updater.cancel();      // 后台不继续下载
        if (web != null) { web.onPause(); web.pauseTimers(); }
        for (WebView w : new WebView[]{gameWeb, archiveWeb, libraryWeb}) if (w != null) { w.onPause(); w.pauseTimers(); }
        cancelLoadTimeout();
        super.onPause();
    }

    @Override protected void onDestroy() {
        cancelLoadTimeout();
        for (WebView w : new WebView[]{web, archiveWeb, gameWeb, libraryWeb}) if (w != null) w.destroy();
        web = archiveWeb = gameWeb = libraryWeb = null;
        super.onDestroy();
    }

    // ---------------------------------------------------------------- 装饰 View
    private static class GlowView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float t = 0f;
        private boolean reduced = false;
        private android.animation.ValueAnimator anim;
        GlowView(Context c) {
            super(c);
            anim = android.animation.ValueAnimator.ofFloat(0f, 1f);
            anim.setDuration(14000);
            anim.setRepeatCount(android.animation.ValueAnimator.INFINITE);
            anim.addUpdateListener(a -> { t = (float) a.getAnimatedValue(); if (!reduced) invalidate(); });
        }
        void setReducedMotion(boolean r) {
            reduced = r;
            if (r) { if (anim != null) anim.cancel(); }
            else if (anim != null && !anim.isRunning()) anim.start();
            invalidate();
        }
        @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); if (!reduced && anim != null) anim.start(); }
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
        }
    }

    private static class StarMark extends View {
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        StarMark(Context c) {
            super(c); stroke.setStyle(Paint.Style.STROKE); stroke.setStrokeCap(Paint.Cap.ROUND);
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
