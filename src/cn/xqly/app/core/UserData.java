package cn.xqly.app.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 用户数据（收藏 / 收藏夹 / 标签 / 历史 / 书签 / 阅读进度 / 阅读设置 / 最近搜索）。
 *
 * 关键约束：全部存放在「用户命名空间」里，与内容缓存完全分离。
 * 清理缓存 / 内容包回滚 / 更新失败只操作缓存命名空间，绝不触碰这里。
 * 纯 Java + UserPrefs 接口，可在 JVM 完整测试（含迁移与状态转换）。
 */
public final class UserData {
    private UserData() {}

    public static final String K_FAVORITES = "u.favorites";       // docId 列表（有序）
    public static final String K_COLLECTIONS = "u.collections";   // 收藏夹 → docId 列表
    public static final String K_TAGS = "u.tags";                 // docId → 标签列表
    public static final String K_HISTORY = "u.history";           // 最近阅读（最多 100）
    public static final String K_BOOKMARKS = "u.bookmarks";       // docId → 锚点列表
    public static final String K_PROGRESS = "u.progress";         // docId → 0..1
    public static final String K_RECENT_SEARCH = "u.recent_search";
    public static final String K_READING = "u.reading";           // 字体/行距/宽度
    public static final String K_GAME_SAVE = "u.game_save";       // 游戏存档
    public static final String K_GAME_STATS = "u.game_stats";
    public static final String K_SCHEMA = "u.schema";

    public static final int SCHEMA = 2;
    public static final int HISTORY_MAX = 100;

    /** 逐级迁移（真实版本测试用）：schema 0/缺失 → 2。迁移失败保留原值，不清空。 */
    public static int migrate(UserPrefs p) {
        int from = (int) parseLong(p.get(K_SCHEMA, "0"), 0);
        if (from < 1) {
            // v0→v1：早期 favorites 可能是逗号分隔字符串，安全转换为 JSON 数组
            for (String k : new String[]{K_FAVORITES, K_HISTORY, K_RECENT_SEARCH}) {
                String v = p.get(k, "");
                if (!v.isEmpty() && !v.trim().startsWith("[")) {
                    List<Object> arr = new ArrayList<Object>();
                    for (String s : v.split(",")) if (!s.trim().isEmpty()) arr.add(s.trim());
                    p.put(k, MiniJson.stringify(arr));
                }
            }
            from = 1;
        }
        if (from < 2) {
            // v1→v2：补齐阅读设置默认值
            if (!p.has(K_READING)) p.put(K_READING, defaultReading());
            from = 2;
        }
        p.put(K_SCHEMA, String.valueOf(from));
        return from;
    }

    // ---- 收藏 ----
    public static List<String> favorites(UserPrefs p) { return strList(p, K_FAVORITES); }
    public static boolean isFavorite(UserPrefs p, String id) { return favorites(p).contains(id); }
    public static boolean toggleFavorite(UserPrefs p, String id) {
        if (id == null || id.isEmpty() || id.length() > 128) return false;
        Set<String> s = new LinkedHashSet<String>(favorites(p));
        boolean added = s.add(id);
        if (!added) s.remove(id);
        p.put(K_FAVORITES, MiniJson.stringify(new ArrayList<Object>(s)));
        return added;
    }
    public static void removeFavorites(UserPrefs p, List<String> ids) {
        Set<String> s = new LinkedHashSet<String>(favorites(p));
        s.removeAll(ids);
        p.put(K_FAVORITES, MiniJson.stringify(new ArrayList<Object>(s)));
    }

    // ---- 自定义标签 ----
    public static List<String> tagsOf(UserPrefs p, String docId) {
        Map<String, Object> m = objMap(p, K_TAGS);
        Object v = m.get(docId);
        List<String> out = new ArrayList<String>();
        if (v instanceof List) for (Object item : (List<?>) v) if (item instanceof String) out.add((String) item);
        return out;
    }
    public static void setTags(UserPrefs p, String docId, List<String> tags) {
        Map<String, Object> m = objMap(p, K_TAGS);
        List<Object> clean = new ArrayList<Object>();
        if (tags != null) for (String t : tags) if (t != null && t.length() <= 24 && clean.size() < 8) clean.add(t);
        m.put(docId, clean);
        p.put(K_TAGS, MiniJson.stringify(m));
    }

    // ---- 收藏夹 ----
    public static Map<String, List<String>> collections(UserPrefs p) {
        Map<String, List<String>> out = new LinkedHashMap<String, List<String>>();
        Map<String, Object> m = objMap(p, K_COLLECTIONS);
        for (Map.Entry<String, Object> e : m.entrySet()) {
            List<String> l = new ArrayList<String>();
            if (e.getValue() instanceof List)
                for (Object o : (List<?>) e.getValue()) if (o instanceof String) l.add((String) o);
            out.put(e.getKey(), l);
        }
        return out;
    }
    public static void addToCollection(UserPrefs p, String name, String docId) {
        if (name == null || name.length() > 32) return;
        Map<String, List<String>> c = collections(p);
        List<String> l = c.containsKey(name) ? c.get(name) : new ArrayList<String>();
        if (!l.contains(docId)) l.add(docId);
        c.put(name, l);
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        for (Map.Entry<String, List<String>> e : c.entrySet()) m.put(e.getKey(), new ArrayList<Object>(e.getValue()));
        p.put(K_COLLECTIONS, MiniJson.stringify(m));
    }

    // ---- 历史 / 书签 / 进度 ----
    public static List<String> history(UserPrefs p) { return strList(p, K_HISTORY); }
    public static void recordHistory(UserPrefs p, String docId) {
        if (docId == null || docId.isEmpty()) return;
        Set<String> s = new LinkedHashSet<String>();
        s.add(docId);
        for (String h : history(p)) { if (s.size() >= HISTORY_MAX) break; s.add(h); }
        p.put(K_HISTORY, MiniJson.stringify(new ArrayList<Object>(s)));
    }
    public static void setProgress(UserPrefs p, String docId, float ratio) {
        Map<String, Object> m = objMap(p, K_PROGRESS);
        m.put(docId, Math.max(0f, Math.min(1f, ratio)));
        p.put(K_PROGRESS, MiniJson.stringify(m));
    }
    public static float progressOf(UserPrefs p, String docId) {
        Object v = objMap(p, K_PROGRESS).get(docId);
        return v instanceof Number ? ((Number) v).floatValue() : 0f;
    }
    public static void addBookmark(UserPrefs p, String docId, String anchor) {
        Map<String, Object> m = objMap(p, K_BOOKMARKS);
        List<Object> l = m.get(docId) instanceof List ? (List<Object>) m.get(docId) : new ArrayList<Object>();
        if (!l.contains(anchor) && l.size() < 50) l.add(anchor);
        m.put(docId, l);
        p.put(K_BOOKMARKS, MiniJson.stringify(m));
    }

    // ---- 最近搜索 ----
    public static List<String> recentSearch(UserPrefs p) { return strList(p, K_RECENT_SEARCH); }
    public static void recordSearch(UserPrefs p, String q) {
        if (q == null || q.trim().isEmpty() || q.length() > 80) return;
        q = q.trim();
        Set<String> s = new LinkedHashSet<String>();
        s.add(q);
        for (String r : recentSearch(p)) { if (s.size() >= 8) break; s.add(r); }
        p.put(K_RECENT_SEARCH, MiniJson.stringify(new ArrayList<Object>(s)));
    }

    // ---- 阅读设置 ----
    public static String defaultReading() {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("fontScale", 1.0); m.put("lineSpacing", 1.6); m.put("maxWidthDp", 720);
        m.put("theme", "dark"); m.put("mode", "glass");
        return MiniJson.stringify(m);
    }
    public static Map<String, Object> reading(UserPrefs p) {
        String raw = p.get(K_READING, "");
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        if (!raw.isEmpty()) {
            try {
                Object o = MiniJson.parse(raw);
                if (o instanceof Map) m.putAll((Map<String, Object>) o);
            } catch (Exception ignored) { }
        }
        if (m.isEmpty()) {
            Object o = MiniJson.parse(defaultReading());
            m.putAll((Map<String, Object>) o);
        }
        // 逐项兜底 + 夹取，防止畸形配置破坏阅读界面
        m.put("fontScale", clampD(num(m, "fontScale", 1.0), 0.8, 2.0));
        m.put("lineSpacing", clampD(num(m, "lineSpacing", 1.6), 1.2, 2.4));
        m.put("maxWidthDp", clampD(num(m, "maxWidthDp", 720), 320, 1400));
        if (!(m.get("theme") instanceof String)) m.put("theme", "dark");
        if (!(m.get("mode") instanceof String)) m.put("mode", "glass");
        return m;
    }
    private static double num(Map<String, Object> m, String k, double d) {
        Object v = m.get(k); return v instanceof Number ? ((Number) v).doubleValue() : d;
    }
    private static double clampD(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }

    /** 只清理缓存命名空间，用户数据（u.*）完全不动。 */
    public static List<String> cacheKeysToClear() {
        List<String> l = new ArrayList<String>();
        l.add("cache."); l.add("pack.tmp."); l.add("update.tmp.");
        return l;
    }
    public static boolean isUserKey(String key) { return key != null && key.startsWith("u."); }

    // ---- helpers ----
    private static List<String> strList(UserPrefs p, String k) {
        List<String> out = new ArrayList<String>();
        String raw = p.get(k, "[]");
        try {
            Object o = MiniJson.parse(raw);
            if (o instanceof List) for (Object e : (List<?>) o) if (e instanceof String) out.add((String) e);
        } catch (Exception ignored) { }
        return out;
    }
    @SuppressWarnings("unchecked")
    private static Map<String, Object> objMap(UserPrefs p, String k) {
        String raw = p.get(k, "{}");
        try {
            Object o = MiniJson.parse(raw);
            if (o instanceof Map) return new LinkedHashMap<String, Object>((Map<String, Object>) o);
        } catch (Exception ignored) { }
        return new LinkedHashMap<String, Object>();
    }
    private static long parseLong(String s, long def) {
        try { return Long.parseLong(s); } catch (Exception e) { return def; }
    }

    /** 测试用内存实现。 */
    public static final class MapPrefs implements UserPrefs {
        private final Map<String, String> m;
        public MapPrefs() { this(new LinkedHashMap<String, String>()); }
        public MapPrefs(Map<String, String> m) { this.m = m; }
        public MapPrefs(String json) {
            m = new LinkedHashMap<String, String>();
            try {
                Object o = MiniJson.parse(json);
                if (o instanceof Map) for (Map.Entry<String, Object> e : ((Map<String, Object>) o).entrySet())
                    m.put(e.getKey(), String.valueOf(e.getValue()));
            } catch (Exception ignored) { }
        }
        public String get(String k, String d) { return m.containsKey(k) ? m.get(k) : d; }
        public void put(String k, String v) { m.put(k, v); }
        public boolean has(String k) { return m.containsKey(k); }
        public void remove(String k) { m.remove(k); }
        public void clearAll() { m.clear(); }
        public String dump() { return MiniJson.stringify(new LinkedHashMap<String, Object>((Map) m)); }
    }
}
