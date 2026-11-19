package cn.xqly.app.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * xqly-bridge/1 十六条方法的**唯一**校验入口（Java 侧镜像 shared/lib/src/bridge_v1.dart）。
 *
 * 每条方法都有 schema、超时、幂等性与权限标签；未知方法、越界 appId、
 * 超长参数、过快调用一律拒绝并记录原因。禁止同步返回密钥、令牌、
 * 完整设备标识或任意文件路径。
 */
public final class BridgeV1 {
    private BridgeV1() {}

    public static final String VERSION = "xqly-bridge/1";
    public static final int MAX_MESSAGE = 8192;
    public static final int MAX_PARAM_LEN = 2048;
    public static final int MAX_PARAMS = 12;
    public static final int MAX_ID_LEN = 64;
    public static final int MIN_CALL_INTERVAL_MS = 120;

    public static final Set<String> DEFAULT_PERMISSIONS = Collections.unmodifiableSet(
            new HashSet<String>(Arrays.asList(
                    "public", "ui", "nav", "link", "update", "clipboard")));

    public static final Set<String> FORBIDDEN_FRAGMENTS = Collections.unmodifiableSet(
            new HashSet<String>(Arrays.asList(
                    "token", "secret", "password", "apikey", "api_key", "private",
                    "imei", "imsi", "deviceid", "cookie", "authorization",
                    "cmd", "exec", "script", "install", "adb", "keystore",
                    "dump", "shell")));

    public static final Set<String> RESTRICTED_KEYS = Collections.unmodifiableSet(
            new HashSet<String>(Arrays.asList("url", "path", "appId")));

    public static final class MethodSpec {
        public final String name, permission;
        public final int timeoutMs;
        public final boolean idempotent, requiresApproval, network;
        public final Set<String> allowedKeys, requiredKeys;
        MethodSpec(String name, String permission, int timeoutMs, boolean idempotent,
                   String[] allowed, String[] required, boolean approval, boolean network) {
            this.name = name; this.permission = permission;
            this.timeoutMs = timeoutMs; this.idempotent = idempotent;
            this.allowedKeys = set(allowed); this.requiredKeys = set(required);
            this.requiresApproval = approval; this.network = network;
        }
        private static Set<String> set(String[] a) {
            return Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(a)));
        }
    }

    public enum Reject {
        NONE, TOO_LARGE, BAD_JSON, BAD_VERSION, FOREIGN_APP, UNKNOWN_METHOD,
        BAD_ID, DUPLICATE_ID, BAD_PARAMS, FORBIDDEN_KEY, RESTRICTED_KEY,
        PERMISSION_DENIED, RATE_LIMITED, OFFLINE_UNAVAILABLE, TELEMETRY_OFF,
        APPROVAL_REQUIRED, BAD_ENVELOPE
    }

    public static final class CallResult {
        public final boolean ok;
        public final Reject reject;
        public final String method, id;
        public final Map<String, Object> params;
        public final MethodSpec spec;
        CallResult(boolean ok, Reject r, String method, String id,
                   Map<String, Object> params, MethodSpec spec) {
            this.ok = ok; this.reject = r; this.method = method;
            this.id = id; this.params = params; this.spec = spec;
        }
        public String reason() { return reject.name(); }
        static CallResult bad(Reject r) {
            return new CallResult(false, r, "", "", Collections.<String, Object>emptyMap(), null);
        }
    }

    public static final Map<String, MethodSpec> METHODS;
    static {
        Map<String, MethodSpec> m = new LinkedHashMap<String, MethodSpec>();
        put(m, "xqly.env.getInfo", "public", 1000, true, new String[]{}, new String[]{}, false, false);
        put(m, "xqly.ui.setTitle", "ui", 1000, false,
                new String[]{"title", "subtitle"}, new String[]{"title"}, false, false);
        put(m, "xqly.ui.showFeedback", "ui", 1000, false,
                new String[]{"level", "durationMs", "action"}, new String[]{"level"}, false, false);
        put(m, "xqly.theme.subscribe", "public", 1000, true,
                new String[]{"tokens"}, new String[]{}, false, false);
        put(m, "xqly.nav.open", "nav", 2000, false,
                new String[]{"appId", "path", "replace"}, new String[]{"appId"}, false, false);
        put(m, "xqly.link.open", "link", 2000, false,
                new String[]{"url", "external"}, new String[]{"url"}, false, false);
        put(m, "xqly.credential.getEndpoint", "credential", 3000, true,
                new String[]{"endpointId"}, new String[]{"endpointId"}, false, false);
        put(m, "xqly.credential.unlock", "credential", 30000, false,
                new String[]{"reason"}, new String[]{}, false, false);
        put(m, "xqly.devportal.connect", "network:devportal", 30000, false,
                new String[]{"endpointId", "transport"}, new String[]{"endpointId"}, false, true);
        put(m, "xqly.devportal.send", "network:devportal", 30000, false,
                new String[]{"requestId", "payload"}, new String[]{"requestId"}, false, true);
        put(m, "xqly.devportal.approveTool", "user_approval", 30000, false,
                new String[]{"toolId", "scope", "grant"}, new String[]{"toolId", "scope"}, true, false);
        put(m, "xqly.file.pickSave", "file:export", 30000, false,
                new String[]{"mime", "suggestedName"}, new String[]{}, false, false);
        put(m, "xqly.clipboard.copy", "clipboard", 1000, false,
                new String[]{"text", "sensitive"}, new String[]{"text"}, false, false);
        put(m, "xqly.ota.check", "update", 10000, true,
                new String[]{"channel"}, new String[]{}, false, false);
        put(m, "xqly.telemetry.report", "telemetry:optional", 2000, true,
                new String[]{"event", "props"}, new String[]{}, false, false);
        put(m, "xqly.error.report", "diagnostics", 3000, true,
                new String[]{"code", "messageHash", "breadcrumbs"}, new String[]{}, false, false);
        // ---- AI 工作台：对标 harness 交互的纯本地方法，零网络、零执行、零密钥 ----
        put(m, "xqly.workbench.env", "public", 1000, true,
                new String[]{}, new String[]{}, false, false);
        put(m, "xqly.workbench.snapshot", "workbench", 1000, true,
                new String[]{"freeMb"}, new String[]{}, false, false);
        put(m, "xqly.workbench.session.list", "workbench", 1000, true,
                new String[]{}, new String[]{}, false, false);
        put(m, "xqly.workbench.session.create", "workbench", 1000, false,
                new String[]{"id", "title"}, new String[]{"id", "title"}, false, false);
        put(m, "xqly.workbench.session.append", "workbench", 1000, false,
                new String[]{"id", "role", "text"}, new String[]{"id", "role", "text"}, false, false);
        put(m, "xqly.workbench.session.remove", "workbench", 1000, false,
                new String[]{"id"}, new String[]{"id"}, false, false);
        put(m, "xqly.workbench.provider.list", "workbench", 1000, true,
                new String[]{}, new String[]{}, false, false);
        put(m, "xqly.workbench.provider.mark", "workbench", 1000, false,
                new String[]{"id", "configured"}, new String[]{"id"}, false, false);
        put(m, "xqly.workbench.plugin.list", "workbench", 1000, true,
                new String[]{}, new String[]{}, false, false);
        put(m, "xqly.workbench.plugin.register", "workbench", 1000, false,
                new String[]{"id", "displayName", "source", "version"},
                new String[]{"id", "displayName", "source", "version"}, false, false);
        put(m, "xqly.workbench.plugin.toggle", "workbench", 1000, false,
                new String[]{"id", "enabled"}, new String[]{"id", "enabled"}, false, false);
        put(m, "xqly.workbench.dropzone.check", "workbench", 1000, true,
                new String[]{"raw"}, new String[]{"raw"}, false, false);
        put(m, "xqly.workbench.selfcheck", "workbench", 1000, true,
                new String[]{}, new String[]{}, false, false);
        put(m, "xqly.workbench.log.append", "workbench", 1000, false,
                new String[]{"level", "code", "text"},
                new String[]{"level", "code", "text"}, false, false);
        put(m, "xqly.workbench.log.query", "workbench", 1000, true,
                new String[]{"level", "keyword", "tail"}, new String[]{}, false, false);
        METHODS = Collections.unmodifiableMap(m);
    }

    /** 每个合法桥应用对应的作用域；未知 appId 返回 null。 */
    public static String scopeFor(String appId) {
        if (AppRegistry.DEV_PORTAL_ID.equals(appId)) return "devportal:*";
        if (AppRegistry.AI_WORKBENCH_ID.equals(appId)) return "workbench:*";
        return null;
    }

    private static void put(Map<String, MethodSpec> m, String name, String perm,
                           int timeout, boolean idem, String[] allowed, String[] required,
                           boolean approval, boolean network) {
        m.put(name, new MethodSpec(name, perm, timeout, idem, allowed, required, approval, network));
    }

    private static final Pattern ID_PATTERN = Pattern.compile("^[A-Za-z0-9_-]+$");
    private static final Pattern ENDPOINT_ID = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");
    private static final Pattern TOOL_ID = Pattern.compile("^[A-Za-z0-9_.-]{1,64}$");
    private static final Pattern HASH64 = Pattern.compile("^[0-9a-f]{64}$");
    private static final Pattern ID48 = Pattern.compile("^[A-Za-z0-9_-]{1,48}$");
    private static final Pattern PLUGIN_ID = Pattern.compile("^[A-Za-z0-9]([A-Za-z0-9._-]{0,62}[A-Za-z0-9])?$");
    private static final Pattern VERSION_PATTERN = Pattern.compile("^\\d{1,4}\\.\\d{1,4}\\.\\d{1,4}$");
    private static final Set<String> WB_ROLES = new HashSet<String>(
            Arrays.asList("user", "draft", "note"));
    private static final Set<String> LEVELS = new HashSet<String>(
            Arrays.asList("info", "success", "warning", "error"));
    private static final Set<String> GRANTS = new HashSet<String>(
            Arrays.asList("once", "session", "deny"));

    public static CallResult validate(String raw, Set<String> permissions, Set<String> seenIds,
                                      long nowMs, Map<String, Long> lastCallAt,
                                      boolean online, boolean telemetryOptIn,
                                      boolean approvalGranted, Set<String> endpointAllowlist) {
        if (raw == null || raw.isEmpty() || raw.length() > MAX_MESSAGE)
            return CallResult.bad(Reject.TOO_LARGE);
        final Map<String, Object> m;
        try { m = MiniJson.object(raw); }
        catch (Exception e) { return CallResult.bad(Reject.BAD_JSON); }

        if (!VERSION.equals(MiniJson.str(m, "bridge", "")))
            return CallResult.bad(Reject.BAD_VERSION);
        String appId = MiniJson.str(m, "appId", "");
        String scope = scopeFor(appId);
        if (scope == null || !AppRegistry.scopeAllows(appId, scope))
            return CallResult.bad(Reject.FOREIGN_APP);

        String method = MiniJson.str(m, "method", "");
        MethodSpec spec = METHODS.get(method);
        if (spec == null) return CallResult.bad(Reject.UNKNOWN_METHOD);

        String id = MiniJson.str(m, "id", "");
        if (id.isEmpty() || id.length() > MAX_ID_LEN || !ID_PATTERN.matcher(id).matches())
            return CallResult.bad(Reject.BAD_ID);
        if (seenIds != null && !seenIds.add(id)) return CallResult.bad(Reject.DUPLICATE_ID);

        Map<String, Object> params = MiniJson.obj(m, "params");
        if (params == null) params = Collections.emptyMap();
        if (params.size() > MAX_PARAMS || !paramsValid(spec, params, endpointAllowlist))
            return CallResult.bad(Reject.BAD_PARAMS);
        for (String k : params.keySet()) {
            String lk = k.toLowerCase(java.util.Locale.US);
            if (lk.length() > 32) return CallResult.bad(Reject.BAD_PARAMS);
            for (String f : FORBIDDEN_FRAGMENTS)
                if (lk.contains(f)) return CallResult.bad(Reject.FORBIDDEN_KEY);
        }
        if (!restrictedValid(spec, params)) return CallResult.bad(Reject.RESTRICTED_KEY);

        if (permissions == null || !permissions.contains(spec.permission))
            return CallResult.bad(Reject.PERMISSION_DENIED);
        long last = lastCallAt.containsKey(method)
                ? lastCallAt.get(method) : -MIN_CALL_INTERVAL_MS - 1;
        if (nowMs - last < MIN_CALL_INTERVAL_MS) return CallResult.bad(Reject.RATE_LIMITED);
        lastCallAt.put(method, nowMs);

        if (spec.network && !online) return CallResult.bad(Reject.OFFLINE_UNAVAILABLE);
        if (method.equals("xqly.telemetry.report") && !telemetryOptIn)
            return CallResult.bad(Reject.TELEMETRY_OFF);
        if (spec.requiresApproval && !approvalGranted)
            return CallResult.bad(Reject.APPROVAL_REQUIRED);
        return new CallResult(true, Reject.NONE, method, id, params, spec);
    }

    private static boolean paramsValid(MethodSpec spec, Map<String, Object> p,
                                       Set<String> allowlist) {
        for (Map.Entry<String, Object> e : p.entrySet()) {
            if (!spec.allowedKeys.contains(e.getKey())) return false;
            Object v = e.getValue();
            if (v instanceof String && ((String) v).length() > MAX_PARAM_LEN) return false;
            if (v instanceof Double && !Double.isFinite((Double) v)) return false;
            if (v instanceof Map && ((Map<?, ?>) v).size() > MAX_PARAMS) return false;
            if (v instanceof List && ((List<?>) v).size() > 64) return false;
        }
        for (String k : spec.requiredKeys) if (!p.containsKey(k)) return false;

        if (spec.name.equals("xqly.ui.setTitle")) {
            Object t = p.get("title");
            return t instanceof String && !((String) t).isEmpty() && ((String) t).length() <= 40;
        }
        if (spec.name.equals("xqly.ui.showFeedback")) {
            Object lv = p.get("level");
            if (!(lv instanceof String) || !LEVELS.contains(lv)) return false;
            if (p.containsKey("durationMs")) {
                Object d = p.get("durationMs");
                if (!(d instanceof Number)) return false;
                double v = ((Number) d).doubleValue();
                if (v < 0 || v > 30000) return false;
            }
            if (p.containsKey("action")) {
                Object a = p.get("action");
                if (!(a instanceof String) || ((String) a).length() > 2) return false;
            }
        }
        if (spec.name.equals("xqly.link.open")) {
            Object u = p.get("url");
            return u instanceof String && EndpointPolicy.linkAllowed((String) u);
        }
        if (spec.name.equals("xqly.nav.open")) {
            Object a = p.get("appId");
            if (!(a instanceof String) || !AppRegistry.registered((String) a)) return false;
            if (p.containsKey("path")) {
                Object path = p.get("path");
                if (!(path instanceof String) || ((String) path).length() > 128
                        || ((String) path).contains("..")) return false;
            }
        }
        if (spec.name.equals("xqly.credential.getEndpoint")
                || spec.name.equals("xqly.devportal.connect")) {
            Object e = p.get("endpointId");
            return e instanceof String && ENDPOINT_ID.matcher((String) e).matches()
                    && allowlist != null && allowlist.contains(e);
        }
        if (spec.name.equals("xqly.devportal.approveTool")) {
            // scope 是工具作用域，grant 才是 once/session/deny 的审批决定；两者不可混用。
            Object s = p.get("scope");
            if (!(s instanceof String) || !TOOL_ID.matcher((String) s).matches()) return false;
            Object g = p.get("grant");
            if (g != null && (!(g instanceof String) || !GRANTS.contains((String) g))) return false;
            Object t = p.get("toolId");
            return t instanceof String && TOOL_ID.matcher((String) t).matches();
        }
        if (spec.name.equals("xqly.error.report")) {
            Object h = p.get("messageHash");
            return h == null || (h instanceof String && HASH64.matcher((String) h).matches());
        }
        if (spec.name.equals("xqly.file.pickSave") && p.containsKey("suggestedName")) {
            Object n = p.get("suggestedName");
            if (!(n instanceof String) || ((String) n).length() > 96
                    || ((String) n).contains("/") || ((String) n).contains("\\")) return false;
        }
        // ---- AI 工作台方法级 schema ----
        if (spec.name.equals("xqly.workbench.snapshot") && p.containsKey("freeMb")) {
            Object f = p.get("freeMb");
            if (!(f instanceof Number)) return false;
            double v = ((Number) f).doubleValue();
            if (v < 0 || v > 1024L * 1024L) return false;
        }
        if (spec.name.equals("xqly.workbench.session.create")) {
            Object i = p.get("id"), t = p.get("title");
            return i instanceof String && ID48.matcher((String) i).matches()
                    && t instanceof String && !((String) t).isEmpty()
                    && ((String) t).length() <= 80;
        }
        if (spec.name.equals("xqly.workbench.session.append")) {
            Object i = p.get("id"), role = p.get("role"), text = p.get("text");
            return i instanceof String && ID48.matcher((String) i).matches()
                    && role instanceof String && WB_ROLES.contains(role)
                    && text instanceof String && !((String) text).isEmpty()
                    && ((String) text).length() <= 2048;
        }
        if (spec.name.equals("xqly.workbench.session.remove")) {
            Object i = p.get("id");
            return i instanceof String && ID48.matcher((String) i).matches();
        }
        if (spec.name.equals("xqly.workbench.provider.mark")) {
            Object i = p.get("id");
            if (!(i instanceof String) || !WorkbenchProvider.validId((String) i)) return false;
            if (p.containsKey("configured") && !(p.get("configured") instanceof Boolean)) return false;
            return true;
        }
        if (spec.name.equals("xqly.workbench.plugin.register")) {
            Object i = p.get("id"), s = p.get("source"), v = p.get("version");
            return i instanceof String && PLUGIN_ID.matcher((String) i).matches()
                    && s instanceof String && WorkbenchPlugin.SOURCES.contains(s)
                    && v instanceof String && VERSION_PATTERN.matcher((String) v).matches();
        }
        if (spec.name.equals("xqly.workbench.plugin.toggle")) {
            Object i = p.get("id"), e = p.get("enabled");
            return i instanceof String && PLUGIN_ID.matcher((String) i).matches()
                    && e instanceof Boolean;
        }
        if (spec.name.equals("xqly.workbench.dropzone.check")) {
            Object raw = p.get("raw");
            return raw instanceof String && !((String) raw).isEmpty()
                    && ((String) raw).length() <= 192;
        }
        if (spec.name.equals("xqly.workbench.log.append")) {
            Object lv = p.get("level"), code = p.get("code"), text = p.get("text");
            return lv instanceof String && LEVELS.contains(lv)
                    && code instanceof String && ((String) code).length() <= 48
                    && text instanceof String && !((String) text).isEmpty()
                    && ((String) text).length() <= 2048;
        }
        if (spec.name.equals("xqly.workbench.log.query")) {
            if (p.containsKey("level")) {
                Object lv = p.get("level");
                if (!(lv instanceof String) || !LEVELS.contains(lv)) return false;
            }
            if (p.containsKey("keyword") && !(p.get("keyword") instanceof String)) return false;
            if (p.containsKey("tail")) {
                Object t = p.get("tail");
                if (!(t instanceof Number)) return false;
                double v = ((Number) t).doubleValue();
                if (v < 1 || v > 256) return false;
            }
            return true;
        }
        return true;
    }

    private static boolean restrictedValid(MethodSpec spec, Map<String, Object> p) {
        for (String k : p.keySet()) {
            if (!RESTRICTED_KEYS.contains(k)) continue;
            if (k.equals("url") && !spec.name.equals("xqly.link.open")) return false;
            if (k.equals("path") && !spec.name.equals("xqly.nav.open")) return false;
            if (k.equals("appId") && !spec.name.equals("xqly.nav.open")) return false;
        }
        return true;
    }

    public static boolean responseSafe(String payload) {
        if (payload == null || payload.isEmpty() || payload.length() > MAX_MESSAGE) return false;
        String low = payload.toLowerCase(java.util.Locale.US);
        String[] banned = {"imei", "imsi", "deviceid", "android_id", "authorization",
                "bearer ", "private_key", "api_key", "apikey", "file:///data",
                "/data/user/", "/storage/emulated", "content://"};
        for (String f : banned) if (low.contains(f)) return false;
        if (Pattern.compile("[0-9a-f]{64}").matcher(low).find()
                && !low.contains("messagehash") && !low.contains("digest")) return false;
        return true;
    }

    /** 日志 / 崩溃 / 导出统一脱敏。 */
    public static String redact(String text) {
        if (text == null) return "";
        String s = text.replaceAll(
                "(?i)\"(token|key|secret|password|apiKey|api_key|authorization|cookie|private_key)\"\\s*:\\s*\"[^\"]*\"",
                "\"$1\":\"***\"");
        s = s.replaceAll("\\b[0-9a-fA-F]{40,}\\b", "***");
        s = s.replaceAll("(?i)(file|content)://[^\\s\"'<]+", "***");
        return s;
    }

    public static Map<String, Object> permissionMatrix() {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        for (Map.Entry<String, MethodSpec> e : METHODS.entrySet()) {
            Map<String, Object> v = new LinkedHashMap<String, Object>();
            v.put("permission", e.getValue().permission);
            v.put("timeoutMs", e.getValue().timeoutMs);
            v.put("idempotent", e.getValue().idempotent);
            v.put("requiresApproval", e.getValue().requiresApproval);
            v.put("network", e.getValue().network);
            out.put(e.getKey(), v);
        }
        return out;
    }
}
