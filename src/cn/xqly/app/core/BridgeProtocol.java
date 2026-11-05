package cn.xqly.app.core;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 版本化 postMessage 桥协议（单一入口，替代宽泛 JavascriptInterface）。
 *
 * 每条消息形如：
 * {"v":2,"id":"...","type":"openPage","args":{"path":"/forum"},"origin":"..."}
 *
 * 校验链：长度 → JSON 严格解析 → 版本 → 来源可信 → 类型白名单 →
 *         参数白名单/类型/长度 → 权限 → 幂等 id 去重。
 * 设计原则：只开放「阅读所需」能力，绝不暴露文件、安装、命令、令牌、任意 URL。
 */
public final class BridgeProtocol {
    private BridgeProtocol() {}

    public static final int MAX_MESSAGE = 8192;
    public static final int MAX_ARG_LEN = 2048;   // 游戏存档 JSON 需要略大空间
    public static final int MAX_ARGS = 8;
    public static final int MAX_ID_LEN = 64;

    /** 允许的消息类型（最小能力集）。 */
    public static final Set<String> TYPES;
    static {
        Set<String> s = new HashSet<String>();
        s.add("ping");           // 存活探测
        s.add("openPage");       // 站内路径跳转（需 SITE 白名单路径）
        s.add("closeOverlay");   // 关闭浮层
        s.add("scroll");         // 报告滚动位置（Dock 收拢）
        s.add("search");         // 本地全文检索请求
        s.add("favorite");       // 收藏切换
        s.add("progress");       // 阅读进度上报
        s.add("getState");       // 读取非敏感 UI 状态
        s.add("gameSave");       // 游戏存档写入（本机用户命名空间）
        s.add("gameLoad");       // 游戏存档读取
        TYPES = Collections.unmodifiableSet(s);
    }

    public enum Reject {
        NONE, TOO_LARGE, BAD_JSON, BAD_ENVELOPE, BAD_VERSION, UNTRUSTED_ORIGIN,
        UNKNOWN_TYPE, BAD_ARGS, FORBIDDEN_CAPABILITY, DUPLICATE_ID, BAD_ORIGIN
    }

    public static final class Result {
        public final boolean ok;
        public final Reject reject;
        public final String type;
        public final Map<String, Object> args;
        public final String id;
        Result(boolean ok, Reject r, String t, Map<String, Object> a, String id) {
            this.ok = ok; this.reject = r; this.type = t; this.args = a; this.id = id;
        }
        public String reason() { return reject == null ? "OK" : reject.name(); }
    }

    private static Result bad(Reject r) { return new Result(false, r, null, null, null); }

    /**
     * @param raw          原始消息字符串
     * @param trustedPage  当前页面是否已通过 UrlPolicy.bridgeAllowed()
     * @param seenIds      已处理 id 集合（调用方持久化，用于幂等去重）
     * @param allowWrite   是否允许写类能力（收藏/进度），浮层只读页传 false
     */
    public static Result validate(String raw, boolean trustedPage, Set<String> seenIds, boolean allowWrite) {
        return validate(raw, trustedPage, seenIds, allowWrite, null);
    }

    /**
     * @param expectedOrigin 发起消息的 WebView 当前 URL（由原生在 onPageStarted 记录）。
     *                       非 null 时额外校验页面自报 origin 与之同源，防止伪造来源。
     */
    public static Result validate(String raw, boolean trustedPage, Set<String> seenIds,
                                  boolean allowWrite, String expectedOrigin) {
        if (raw == null || raw.length() > MAX_MESSAGE) return bad(Reject.TOO_LARGE);
        final Map<String, Object> m;
        try { m = MiniJson.object(raw); }
        catch (Exception e) { return bad(Reject.BAD_JSON); }

        long v = MiniJson.num(m, "v", -1);
        if (v != AppIdentity.BRIDGE_VERSION) return bad(Reject.BAD_VERSION);

        String origin = MiniJson.str(m, "origin", "");
        if (origin.length() > 256 || !originAllowed(origin)) return bad(Reject.BAD_ORIGIN);
        if (expectedOrigin != null && !sameOrigin(origin, expectedOrigin))
            return bad(Reject.BAD_ORIGIN);
        if (!trustedPage) return bad(Reject.UNTRUSTED_ORIGIN);

        String type = MiniJson.str(m, "type", "");
        if (!TYPES.contains(type)) return bad(Reject.UNKNOWN_TYPE);

        String id = MiniJson.str(m, "id", "");
        if (id.isEmpty() || id.length() > MAX_ID_LEN || !id.matches("[A-Za-z0-9_.-]+"))
            return bad(Reject.BAD_ENVELOPE);
        if (seenIds != null && !seenIds.add(id)) return bad(Reject.DUPLICATE_ID);

        Map<String, Object> args = MiniJson.obj(m, "args");
        if (args == null) args = Collections.emptyMap();
        if (args.size() > MAX_ARGS) return bad(Reject.BAD_ARGS);
        for (Map.Entry<String, Object> e : args.entrySet()) {
            if (!e.getKey().matches("[A-Za-z0-9_]{1,32}")) return bad(Reject.BAD_ARGS);
            Object val = e.getValue();
            if (val instanceof String) {
                if (((String) val).length() > MAX_ARG_LEN) return bad(Reject.BAD_ARGS);
            } else if (val instanceof Number || val instanceof Boolean || val == null) {
                // 允许
            } else {
                return bad(Reject.BAD_ARGS);   // 拒绝嵌套对象/数组
            }
        }

        // 能力级校验
        if ("favorite".equals(type) || "progress".equals(type)) {
            if (!allowWrite) return bad(Reject.FORBIDDEN_CAPABILITY);
        }
        if ("openPage".equals(type)) {
            String path = args.get("path") instanceof String ? (String) args.get("path") : "";
            if (!path.startsWith("/") || !UrlPolicy.sitePathAllowed(path) || path.length() > 200)
                return bad(Reject.FORBIDDEN_CAPABILITY);
        }
        // 任何类型都不得携带危险键
        for (String k : args.keySet()) {
            String lk = k.toLowerCase();
            if (lk.contains("token") || lk.contains("file") || lk.contains("url")
                    || lk.contains("cmd") || lk.contains("install") || lk.contains("exec")
                    || lk.contains("cookie") || lk.contains("script")) {
                return bad(Reject.FORBIDDEN_CAPABILITY);
            }
        }
        return new Result(true, Reject.NONE, type, args, id);
    }

    private static boolean originAllowed(String origin) {
        if (origin.isEmpty()) return false;
        UrlPolicy.Kind k = UrlPolicy.classify(origin);
        return k == UrlPolicy.Kind.SITE || k == UrlPolicy.Kind.LOCAL_ASSET;
    }

    /**
     * 页面自报 origin 必须与原生记录的当前页面同源：
     * - file:// 内置页：去掉 fragment 后路径完全一致；
     * - https 站点：scheme + host 一致（SPA pushState 允许路径不同）。
     */
    private static boolean sameOrigin(String claimed, String actual) {
        if (claimed == null || actual == null || claimed.isEmpty() || actual.isEmpty()) return false;
        int ch = claimed.indexOf('#'); if (ch >= 0) claimed = claimed.substring(0, ch);
        int ah = actual.indexOf('#'); if (ah >= 0) actual = actual.substring(0, ah);
        if (claimed.startsWith("file:///android_asset/")) return claimed.equals(actual);
        int ci = claimed.indexOf("://"), ai = actual.indexOf("://");
        if (ci < 0 || ai < 0) return false;
        if (!claimed.substring(0, ci).equalsIgnoreCase(actual.substring(0, ai))) return false;
        String restC = claimed.substring(ci + 3), restA = actual.substring(ai + 3);
        int ce = restC.indexOf('/'), ae = restA.indexOf('/');
        String hostC = ce < 0 ? restC : restC.substring(0, ce);
        String hostA = ae < 0 ? restA : restA.substring(0, ae);
        return hostC.equalsIgnoreCase(hostA);
    }
}
