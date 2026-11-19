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

/** 模型供应商目录（Java 侧镜像 shared/lib/src/workbench_provider.dart）。 */
public final class WorkbenchProvider {
    public WorkbenchProvider() {}

    public static final class Spec {
        public final String id, displayName, host;
        public final boolean needsCredential;
        Spec(String id, String name, String host, boolean need) {
            this.id = id; this.displayName = name; this.host = host; this.needsCredential = need;
        }
        public Map<String, Object> toJson(boolean configured) {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("id", id); m.put("displayName", displayName); m.put("host", host);
            m.put("needsCredential", needsCredential); m.put("configured", configured);
            return m;
        }
    }

    public static final List<Spec> CATALOG = Collections.unmodifiableList(Arrays.asList(
            new Spec("deepseek", "DeepSeek", "api.deepseek.com", true),
            new Spec("openai", "OpenAI", "api.openai.com", true),
            new Spec("anthropic", "Anthropic", "api.anthropic.com", true),
            new Spec("gemini", "Google Gemini", "generativelanguage.googleapis.com", true),
            new Spec("openrouter", "OpenRouter", "openrouter.ai", true),
            new Spec("groq", "Groq", "api.groq.com", true),
            new Spec("xai", "xAI", "api.x.ai", true),
            new Spec("mistral", "Mistral", "api.mistral.ai", true),
            new Spec("custom", "自建兼容端点", "", true)));

    private static final Set<String> IDS;
    static {
        Set<String> s = new HashSet<String>();
        for (Spec p : CATALOG) s.add(p.id);
        IDS = Collections.unmodifiableSet(s);
    }
    private static final Pattern HOST = Pattern.compile("^[A-Za-z0-9]([A-Za-z0-9.-]*[A-Za-z0-9])?$");
    private static final Pattern IP = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");

    public static boolean validId(String id) { return id != null && IDS.contains(id); }

    public static boolean customHostAllowed(String host) {
        if (host == null || host.isEmpty() || host.length() > 253) return false;
        if (host.contains("://") || host.contains("/") || host.contains("@")) return false;
        if ("localhost".equals(host) || host.endsWith(".local")) return false;
        if (IP.matcher(host).matches()) return false;
        return HOST.matcher(host).matches();
    }

    private final Set<String> configured = new HashSet<String>();

    public boolean isConfigured(String id) { return configured.contains(id); }

    /** 只登记「已配置」布尔状态，绝不接收或保存密钥值。 */
    public boolean markConfigured(String id, boolean value) {
        if (!validId(id)) return false;
        if (value) configured.add(id); else configured.remove(id);
        return true;
    }

    public List<Map<String, Object>> list() {
        List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
        for (Spec p : CATALOG) out.add(p.toJson(isConfigured(p.id)));
        return Collections.unmodifiableList(out);
    }
}
