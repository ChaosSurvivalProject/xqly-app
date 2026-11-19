package cn.xqly.app.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 诊断日志（Java 侧镜像 shared/lib/src/workbench_log.dart）。零 Android 依赖。 */
public final class WorkbenchLog {

    public enum Level { INFO, SUCCESS, WARNING, ERROR }

    public static final class Entry {
        public final Level level;
        public final String code, text;
        public final long atMs;
        Entry(Level level, String code, String text, long atMs) {
            this.level = level; this.code = code; this.text = text;
            this.atMs = atMs;
        }
        public Map<String, Object> toJson() {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("level", level.name().toLowerCase(Locale.US));
            m.put("code", code); m.put("text", text); m.put("atMs", atMs);
            return m;
        }
    }

    private static final Pattern KV = Pattern.compile(
            "(?i)\"(token|key|secret|password|apiKey|api_key|authorization|cookie|private_key)\"\\s*:\\s*\"[^\"]*\"");
    private static final Pattern KV_BARE = Pattern.compile("(?i)\\b(apiKey|api_key|token|secret|password|authorization)\\s*=\\s*[^\\s;,\"]+");
    private static final Pattern SK = Pattern.compile("(?i)\\bsk-[A-Za-z0-9._-]{8,}");
    private static final Pattern BEARER = Pattern.compile("(?i)bearer\\s+[A-Za-z0-9._~-]{8,}");
    private static final Pattern HEX = Pattern.compile("\\b[0-9a-fA-F]{40,}\\b");
    private static final Pattern URI = Pattern.compile("(?i)(file|content)://[^\\s\"'<>]+");

    /** 与 Dart workbenchRedact / BridgeV1.redact 对齐的统一脱敏。 */
    public static String redact(String text) {
        if (text == null) return "";
        Matcher m = KV.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement("\"" + m.group(1) + "\":\"***\""));
        }
        m.appendTail(sb);
        Matcher km = KV_BARE.matcher(sb.toString());
        StringBuffer kb = new StringBuffer();
        while (km.find()) {
            km.appendReplacement(kb, Matcher.quoteReplacement(km.group(1) + "=***"));
        }
        km.appendTail(kb);
        String s = SK.matcher(kb.toString()).replaceAll("***");
        s = BEARER.matcher(s).replaceAll("Bearer ***");
        s = HEX.matcher(s).replaceAll("***");
        s = URI.matcher(s).replaceAll("***");
        return s;
    }

    private final int capacity;
    private final List<Entry> entries = new ArrayList<Entry>();

    public WorkbenchLog() { this(256); }
    public WorkbenchLog(int capacity) {
        if (capacity < 8 || capacity > 1024) throw new IllegalArgumentException("capacity");
        this.capacity = capacity;
    }

    public int size() { return entries.size(); }

    public Entry append(Level level, String code, String text, long atMs) {
        Entry e = new Entry(level, code, redact(text), atMs);
        entries.add(e);
        while (entries.size() > capacity) entries.remove(0);
        return e;
    }

    public List<Entry> query(Level level, String keyword, int tail) {
        int n = tail < 1 ? 1 : (tail > 256 ? 256 : tail);
        String kw = keyword == null ? "" : keyword.toLowerCase(Locale.US);
        List<Entry> hit = new ArrayList<Entry>();
        for (Entry e : entries) {
            if (level != null && e.level != level) continue;
            if (!kw.isEmpty() && !e.text.toLowerCase(Locale.US).contains(kw)) continue;
            hit.add(e);
        }
        if (hit.size() <= n) return Collections.unmodifiableList(hit);
        return Collections.unmodifiableList(hit.subList(hit.size() - n, hit.size()));
    }

    public void clear() { entries.clear(); }

    public static String verdict(String text) {
        if (text == null) return "未命中已知特征：可导出脱敏日志交由维护者判读。";
        String low = text.toLowerCase(Locale.US);
        if (low.contains("credential") || low.contains("unauthorized"))
            return "凭据缺失或失效：请在外部环境更新凭据后重试；本客户端不保存密钥。";
        if (low.contains("module") && low.contains("load"))
            return "插件加载失败：本客户端不加载第三方代码，属预期边界。";
        if (low.contains("port") && low.contains("use"))
            return "端口被占用：本客户端不启动任何本机服务，属预期边界。";
        return "未命中已知特征：可导出脱敏日志交由维护者判读。";
    }
}
