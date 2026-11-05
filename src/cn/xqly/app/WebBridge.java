package cn.xqly.app;

import android.webkit.JavascriptInterface;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import cn.xqly.app.core.AppIdentity;
import cn.xqly.app.core.BridgeProtocol;
import cn.xqly.app.core.MiniJson;

/**
 * 唯一 JS 入口（版本化 postMessage）。替代原先 3 个宽泛 JavascriptInterface。
 *
 * 页面侧统一调用：
 *   XQLYBridge.postMessage(JSON.stringify({v:2,id,type,args,origin:location.href}))
 *
 * - 只暴露一个 @JavascriptInterface 方法：postMessage(String)
 * - 每条消息经 BridgeProtocol 严格校验；非法消息静默丢弃并计数（不回显细节给页面）
 * - 不暴露文件、安装、命令、令牌、任意 URL、Cookie
 * - 每个 WebView 持有独立实例与独立 currentUrl：消息来源绑定到「实际发起页」，
 *   既不信任页面自报 origin，也不会因为「另一个 WebView 可信」而放行（P0 来源绑定）
 * - 桥本身不持有 Context / WebView 引用，避免循环持有与泄漏；仅保存一个 URL 字符串
 */
public final class WebBridge {

    /** 宿主能力：由 MainActivity 实现。 */
    public interface Host {
        /** 该消息发起页的当前 URL 是否通过白名单。参数由桥在 onPageStarted 时记录。 */
        boolean pageTrusted(String currentUrl);
        boolean allowWrite();
        void onOpenPage(String path);
        void onCloseOverlay();
        void onScroll(int y);
        void onSearch(String query, String tag, boolean onlyFav, String id);
        void onFavorite(String docId, String id);
        void onProgress(String docId, float ratio, String id);
        void onGetState(String id);
        void onGameSave(String data, String id);
        void onGameLoad(String id);
        void reply(String json);
        void onRejected(String reason);
    }

    private final Host host;
    private final Set<String> seenIds = new HashSet<String>();
    /** 由 WebViewClient.onPageStarted 写入，JavaBridge 线程只读，volatile 保证可见性。 */
    private volatile String currentUrl = "";
    private int rejected = 0;

    public WebBridge(Host h) { this.host = h; }

    /** 记录本实例所属 WebView 的真实地址；不调用 WebView.getUrl()，避免跨线程访问控件。 */
    public void setCurrentUrl(String url) { currentUrl = url == null ? "" : url; }

    public int rejectedCount() { return rejected; }

    /** 保持进程内 id 去重有界，避免长期运行内存增长。 */
    private void trim() {
        if (seenIds.size() > 512) seenIds.clear();
    }

    @JavascriptInterface
    public void postMessage(String raw) {
        try {
            trim();
            final String url = currentUrl;
            boolean trusted = host.pageTrusted(url);
            BridgeProtocol.Result r = BridgeProtocol.validate(raw, trusted, seenIds, host.allowWrite(), url);
            if (!r.ok) { rejected++; host.onRejected(r.reason()); return; }
            Map<String, Object> a = r.args;
            switch (r.type) {
                case "ping":
                    host.reply("{\"v\":" + AppIdentity.BRIDGE_VERSION + ",\"id\":" + MiniJson.stringify(r.id) + ",\"ok\":true}");
                    break;
                case "openPage":
                    host.onOpenPage(str(a, "path"));
                    break;
                case "closeOverlay":
                    host.onCloseOverlay();
                    break;
                case "scroll":
                    host.onScroll((int) num(a, "y"));
                    break;
                case "search":
                    host.onSearch(str(a, "q"), str(a, "tag"), bool(a, "fav"), r.id);
                    break;
                case "favorite":
                    host.onFavorite(str(a, "docId"), r.id);
                    break;
                case "progress":
                    host.onProgress(str(a, "docId"), (float) num(a, "ratio"), r.id);
                    break;
                case "getState":
                    host.onGetState(r.id);
                    break;
                case "gameSave":
                    host.onGameSave(str(a, "data"), r.id);
                    break;
                case "gameLoad":
                    host.onGameLoad(r.id);
                    break;
                default:
                    rejected++;
                    host.onRejected("UNKNOWN_TYPE");
            }
        } catch (Throwable t) {
            // 桥层不得把异常抛回页面（避免崩溃 / 信息泄露）
            rejected++;
            host.onRejected("INTERNAL");
        }
    }

    private static String str(Map<String, Object> a, String k) {
        Object v = a.get(k);
        return v instanceof String ? (String) v : "";
    }
    private static double num(Map<String, Object> a, String k) {
        Object v = a.get(k);
        return v instanceof Number ? ((Number) v).doubleValue() : 0;
    }
    private static boolean bool(Map<String, Object> a, String k) {
        Object v = a.get(k);
        return v instanceof Boolean && (Boolean) v;
    }
}
