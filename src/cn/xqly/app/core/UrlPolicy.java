package cn.xqly.app.core;

import java.net.URI;
import java.util.Locale;

/** 严格 URL 白名单：协议 / 主机 / 路径三段判定，默认拒绝。 */
public final class UrlPolicy {
    private UrlPolicy() {}

    public enum Kind {
        SITE,          // 官方站点：WebView 内部加载
        LOCAL_ASSET,   // 内置离线页面（file:///android_asset/...）
        EXTERNAL,      // 白名单外链：交系统浏览器
        BLOCKED        // 危险协议 / 未知主机 / 非法路径
    }

    /** 允许在 WebView 内打开的站点路径前缀（站外一律不内开）。 */
    private static final String[] SITE_PATH_PREFIXES = {
            "/", "/faction-beta", "/forum", "/team", "/login", "/register",
            "/announcements", "/kb", "/monitor", "/wiki", "/admin", "/api", "/assets"
    };

    /** 内置 HTML 页面白名单（仅这几个可 file:// 打开）。 */
    // 设置由原生 Dialog 实现，不设 file:// 页面；只保留真实存在的内置页
    private static final String[] LOCAL_PAGES = {
            "archive.html", "game.html", "library.html"
    };

    public static Kind classify(String raw) {
        if (raw == null || raw.isEmpty() || raw.length() > 4096) return Kind.BLOCKED;
        final URI u;
        try { u = new URI(raw); }
        catch (Exception e) { return Kind.BLOCKED; }

        String scheme = u.getScheme() == null ? "" : u.getScheme().toLowerCase(Locale.US);
        String host = u.getHost() == null ? "" : u.getHost().toLowerCase(Locale.US);

        // file:// 仅允许内置页面，且路径不得含 .. 或绝对路径逃逸
        if ("file".equals(scheme)) {
            String path = u.getPath() == null ? "" : u.getPath();
            if (!path.startsWith("/android_asset/")) return Kind.BLOCKED;
            if (path.contains("..") || path.contains("\\") || path.contains("%2e%2e")) return Kind.BLOCKED;
            String name = path.substring("/android_asset/".length());
            if (name.contains("/")) return Kind.BLOCKED;
            for (String ok : LOCAL_PAGES) if (ok.equals(name)) return Kind.LOCAL_ASSET;
            return Kind.BLOCKED;
        }

        // 危险协议一律拒绝
        if (!"https".equals(scheme) && !"http".equals(scheme)) return Kind.BLOCKED;
        if (u.getUserInfo() != null) return Kind.BLOCKED;   // user:pass@host 钓鱼
        if (u.getPort() != -1) return Kind.BLOCKED;          // 拒绝非常规端口
        if (host.isEmpty()) return Kind.BLOCKED;

        if (AppIdentity.SITE_HOST.equals(host)) {
            String p = u.getPath() == null ? "/" : u.getPath();
            if (p.contains("..") || p.contains("\\")) return Kind.BLOCKED;
            if ("https".equals(scheme)) return Kind.SITE;
            // 官方主机禁止明文 http
            return Kind.BLOCKED;
        }

        // 更新 API / 下载主机不允许在 WebView 内打开
        if (AppIdentity.API_HOST.equals(host)) return Kind.EXTERNAL;
        for (String h : AppIdentity.DOWNLOAD_HOSTS) if (h.equals(host)) return Kind.EXTERNAL;

        // 其余 https 外链：系统浏览器；http 明文外链拒绝
        if ("https".equals(scheme)) return Kind.EXTERNAL;
        return Kind.BLOCKED;
    }

    /** 是否允许注入 postMessage 桥（仅官方站点与内置页面）。 */
    public static boolean bridgeAllowed(String url) {
        Kind k = classify(url);
        return k == Kind.SITE || k == Kind.LOCAL_ASSET;
    }

    /** 内置页面可加载的同目录 js/css 资源（严格白名单，不含子目录、不含穿越）。 */
    private static final String[] LOCAL_RESOURCES = {
            "game-engine.js", "bridge-shim.js", "library.css", "game.css"
    };

    public static boolean localResourceAllowed(String raw) {
        if (raw == null || raw.length() > 512) return false;
        try {
            URI u = new URI(raw);
            String scheme = u.getScheme() == null ? "" : u.getScheme().toLowerCase(Locale.US);
            if (!"file".equals(scheme)) return false;
            String path = u.getPath() == null ? "" : u.getPath();
            if (!path.startsWith("/android_asset/")) return false;
            String name = path.substring("/android_asset/".length());
            if (name.contains("/") || name.contains("..") || name.contains("\\")) return false;
            for (String ok : LOCAL_RESOURCES) if (ok.equals(name)) return true;
            return false;
        } catch (Exception e) { return false; }
    }

    /** 资产相对路径安全校验：防 ZIP 路径穿越。 */
    public static boolean safeAssetPath(String p) {
        if (p == null || p.isEmpty() || p.length() > 512) return false;
        if (p.startsWith("/") || p.startsWith("\\")) return false;
        if (p.contains("..") || p.contains("\\") || p.contains("\0")) return false;
        if (p.matches("^[A-Za-z]:.*")) return false;
        for (int i = 0; i < p.length(); i++) {
            char c = p.charAt(i);
            if (c < 0x20) return false;
        }
        return true;
    }

    /** 站点路径是否在允许前缀内。 */
    public static boolean sitePathAllowed(String path) {
        if (path == null) return false;
        String p = path.isEmpty() ? "/" : path;
        if (p.contains("..")) return false;
        for (String pre : SITE_PATH_PREFIXES) if (p.equals(pre) || p.startsWith(pre.endsWith("/") ? pre : pre + "/")) return true;
        return false;
    }
}
