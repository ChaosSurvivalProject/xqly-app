package cn.xqly.app.core;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 网络分段代理、endpoint allowlist 与 Deep Link 白名单。
 * 与 shared/lib/src/endpoint_policy.dart 对应。
 *
 * 硬约束：WebView 默认禁止 0.0.0.0/0 与链路本地端口扫描；
 * 只能连用户保存的 endpoint；明文 HTTP/WS 默认禁止；
 * 本类**不保存任何密钥 / Token / 凭据**。
 */
public final class EndpointPolicy {
    private EndpointPolicy() {}

    public enum Transport { WSS, SSE, HTTPS }

    public static final class Endpoint {
        public final String id, host, label, pinSha256;
        public final int port;
        public final Transport transport;
        public Endpoint(String id, String host, int port, Transport t, String label, String pin) {
            this.id = id; this.host = host; this.port = port;
            this.transport = t; this.label = label; this.pinSha256 = pin;
        }
    }

    public enum Reject {
        NONE, EMPTY_HOST, BAD_HOST, BAD_PORT, PRIVATE_NETWORK, LOOPBACK,
        LINK_LOCAL, WILDCARD, BAD_TRANSPORT, BAD_PIN, BAD_ID, TOO_MANY, NOT_ALLOWLISTED
    }

    public static final class Verdict {
        public final boolean ok;
        public final Reject reject;
        Verdict(boolean ok, Reject r) { this.ok = ok; this.reject = r; }
        public String reason() { return reject.name(); }
    }

    public static final int MAX_HOST_LEN = 253;
    public static final int MAX_ENDPOINTS = 16;
    public static final Set<Integer> ALLOWED_PORTS =
            Collections.unmodifiableSet(new HashSet<Integer>(Arrays.asList(443, 8443)));
    public static final Set<String> ALLOWED_QUERY_KEYS =
            Collections.unmodifiableSet(new HashSet<String>(Arrays.asList("view", "focus")));
    public static final Set<String> BLOCKED_SCHEMES = Collections.unmodifiableSet(
            new HashSet<String>(Arrays.asList("javascript", "file", "intent", "data",
                    "blob", "about", "content", "chrome")));
    public static final boolean TELEMETRY_DEFAULT = false;

    private static final Pattern HOST = Pattern.compile("^[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?$");
    private static final Pattern IPV4 = Pattern.compile("^[0-9.]+$");
    private static final Pattern ID = Pattern.compile("^[A-Za-z0-9_-]+$");
    private static final Pattern SESSION = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");
    private static final Pattern QUERY_VALUE = Pattern.compile("^[A-Za-z0-9_-]*$");
    private static final Pattern PIN = Pattern.compile("^[0-9a-f]{64}$");

    public static boolean privateNetwork(String host) {
        if (host == null) return true;
        String h = host.toLowerCase(Locale.US);
        if (h.startsWith("10.") || h.startsWith("192.168.")) return true;
        if (h.startsWith("172.")) {
            String[] p = h.split("\\.");
            if (p.length >= 2) {
                try {
                    int s = Integer.parseInt(p[1]);
                    if (s >= 16 && s <= 31) return true;
                } catch (NumberFormatException ignored) { }
            }
        }
        if (h.startsWith("127.") || h.equals("0.0.0.0") || h.equals("::1")
                || h.equals("[::1]") || h.equals("localhost")) return true;
        if (h.startsWith("169.254.") || h.startsWith("fe80:")
                || h.startsWith("fc") || h.startsWith("fd")
                || h.endsWith(".local") || h.endsWith(".internal")) return true;
        return false;
    }

    public static boolean hostAllowed(String host) {
        if (host == null || host.isEmpty() || host.length() > MAX_HOST_LEN) return false;
        String h = host.toLowerCase(Locale.US);
        if (!HOST.matcher(h).matches()) return false;
        if (h.contains("..") || h.startsWith(".") || h.contains("\0")) return false;
        if (IPV4.matcher(h).matches()) return false;   // 不接受裸 IP
        if (h.contains("*") || h.contains("[") || h.contains("]")) return false;
        return !privateNetwork(h);
    }

    public static Verdict validate(Endpoint e, Set<String> allowlist) {
        if (e == null || e.id == null || e.id.isEmpty() || e.id.length() > 64
                || !ID.matcher(e.id).matches()) return new Verdict(false, Reject.BAD_ID);
        if (allowlist == null || allowlist.size() > MAX_ENDPOINTS)
            return new Verdict(false, Reject.TOO_MANY);
        if (!allowlist.contains(e.id)) return new Verdict(false, Reject.NOT_ALLOWLISTED);
        if (e.host == null || e.host.isEmpty()) return new Verdict(false, Reject.EMPTY_HOST);
        // 先判私网再判主机格式，保证拒绝原因准确（否则纯 IP 会被 BAD_HOST 抢先吞掉）。
        if (privateNetwork(e.host)) return new Verdict(false, Reject.PRIVATE_NETWORK);
        if (!hostAllowed(e.host)) return new Verdict(false, Reject.BAD_HOST);
        if (!ALLOWED_PORTS.contains(e.port)) return new Verdict(false, Reject.BAD_PORT);
        if (e.pinSha256 != null && !e.pinSha256.isEmpty()
                && !PIN.matcher(e.pinSha256).matches())
            return new Verdict(false, Reject.BAD_PIN);
        return new Verdict(true, Reject.NONE);
    }

    /** 仅 HTTPS 或已登记的 Deep Link；禁 javascript:/file:/intent: 等越权协议。 */
    public static boolean linkAllowed(String url) {
        if (url == null || url.isEmpty() || url.length() > 2048) return false;
        String low = url.toLowerCase(Locale.US);
        for (String s : BLOCKED_SCHEMES) if (low.startsWith(s + ":")) return false;
        if (low.startsWith("https://")) {
            try {
                URI u = new URI(url);
                if (u.getUserInfo() != null || u.getHost() == null) return false;
                return hostAllowed(u.getHost());
            } catch (Exception ex) { return false; }
        }
        if (low.startsWith(DEEP_LINK_SCHEME + "://")) return deepLinkAllowed(url);
        return false;
    }

    private static final String DEEP_LINK_SCHEME = AppRegistry.DEEP_LINK_SCHEME;
    private static final String SESSION_PREFIX = "xqly://app/dev-portal/session/";

    public static String normalizePath(String raw) {
        String p = raw;
        int hash = p.indexOf('#');
        if (hash >= 0) p = p.substring(0, hash);
        p = p.replace('\\', '/');
        // 只归一化 scheme 之后的路径：若把 "xqly://" 折叠成 "xqly:/"，
        // 所有 Deep Link 都会因路径不匹配而被误拒。
        int s = p.indexOf("://");
        String head = s >= 0 ? p.substring(0, s + 3) : "";
        String tail = s >= 0 ? p.substring(s + 3) : p;
        while (tail.contains("//")) tail = tail.replace("//", "/");
        return head + tail;
    }

    public static boolean deepLinkAllowed(String raw) {
        if (raw == null || raw.isEmpty() || raw.length() > 512) return false;
        String low = raw.toLowerCase(Locale.US);
        if (low.contains("..") || low.contains("%2e") || low.contains("%2f")
                || low.contains("%5c") || low.contains("\0") || raw.contains("\\"))
            return false;
        int q = raw.indexOf('?');
        String path = normalizePath(q < 0 ? raw : raw.substring(0, q));
        if (path.equals("xqly://app/dev-portal"))
            return q < 0 || queryAllowed(raw.substring(q + 1));
        if (path.startsWith(SESSION_PREFIX)) {
            String id = path.substring(SESSION_PREFIX.length());
            if (!SESSION.matcher(id).matches()) return false;
            return q < 0 || queryAllowed(raw.substring(q + 1));
        }
        return false;
    }

    public static String sessionIdFrom(String raw) {
        if (!deepLinkAllowed(raw)) return null;
        int q = raw.indexOf('?');
        String path = normalizePath(q < 0 ? raw : raw.substring(0, q));
        return path.startsWith(SESSION_PREFIX) ? path.substring(SESSION_PREFIX.length()) : null;
    }

    public static boolean queryAllowed(String query) {
        if (query == null || query.isEmpty() || query.length() > 256) return false;
        for (String pair : query.split("&")) {
            if (pair.isEmpty()) return false;
            int eq = pair.indexOf('=');
            String k = eq < 0 ? pair : pair.substring(0, eq);
            String v = eq < 0 ? "" : pair.substring(eq + 1);
            if (!ALLOWED_QUERY_KEYS.contains(k)) return false;
            if (v.length() > 64 || !QUERY_VALUE.matcher(v).matches()) return false;
        }
        return true;
    }
}
