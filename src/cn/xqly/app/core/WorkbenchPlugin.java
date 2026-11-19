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

/** 插件清单管理器（Java 侧镜像）。不加载、不执行任何第三方代码。 */
public final class WorkbenchPlugin {
    public WorkbenchPlugin() {}

    public static final int MAX_PLUGINS = 48;
    private static final Pattern ID = Pattern.compile("^[A-Za-z0-9]([A-Za-z0-9._-]{0,62}[A-Za-z0-9])?$");
    private static final Pattern VERSION = Pattern.compile("^\\d{1,4}\\.\\d{1,4}\\.\\d{1,4}$");
    public static final Set<String> SOURCES = Collections.unmodifiableSet(
            new HashSet<String>(Arrays.asList("npm", "https", "git-https")));

    public static final class Entry {
        public final String id, displayName, source;
        public String version;
        public boolean enabled;
        Entry(String id, String name, String source, String version) {
            this.id = id; this.displayName = name; this.source = source;
            this.version = version; this.enabled = false;
        }
        public Map<String, Object> toJson() {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("id", id); m.put("displayName", displayName); m.put("source", source);
            m.put("version", version); m.put("enabled", enabled);
            return m;
        }
    }

    private final Map<String, Entry> plugins = new LinkedHashMap<String, Entry>();
    private final Map<String, String> previous = new LinkedHashMap<String, String>();

    public int count() { return plugins.size(); }

    public boolean register(String id, String name, String source, String version) {
        if (id == null || !ID.matcher(id).matches()) return false;
        if (name == null || name.isEmpty() || name.length() > 60) return false;
        if (source == null || !SOURCES.contains(source)) return false;
        if (version == null || !VERSION.matcher(version).matches()) return false;
        if (plugins.containsKey(id) || plugins.size() >= MAX_PLUGINS) return false;
        plugins.put(id, new Entry(id, name, source, version));
        return true;
    }

    public boolean toggle(String id, boolean enabled) {
        Entry e = plugins.get(id);
        if (e == null) return false;
        e.enabled = enabled;
        return true;
    }

    public boolean update(String id, String version) {
        Entry e = plugins.get(id);
        if (e == null || version == null || !VERSION.matcher(version).matches()) return false;
        if (compare(version, e.version) <= 0) return false;
        previous.put(id, e.version);
        e.version = version;
        return true;
    }

    public boolean rollback(String id) {
        Entry e = plugins.get(id);
        String prev = previous.get(id);
        if (e == null || prev == null) return false;
        e.version = prev;
        e.enabled = false;           // 回滚后禁用，防止静默生效
        previous.remove(id);
        return true;
    }

    public boolean remove(String id) {
        if (plugins.remove(id) == null) return false;
        previous.remove(id);
        return true;
    }

    public List<Map<String, Object>> list() {
        List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
        for (Entry e : plugins.values()) out.add(e.toJson());
        return Collections.unmodifiableList(out);
    }

    static int compare(String a, String b) {
        String[] x = a.split("\\."), y = b.split("\\.");
        for (int i = 0; i < 3; i++) {
            int d = Integer.parseInt(x[i]) - Integer.parseInt(y[i]);
            if (d != 0) return d > 0 ? 1 : -1;
        }
        return 0;
    }
}
