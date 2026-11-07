package cn.xqly.app.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 本地优先的跨设备同步引擎（纯 Java、零 Android 依赖、可在 JVM 完整测试）。
 *
 * 设计原则（与用户要求「云端同步你自己看着弄」一致）：
 * 1. 默认只做**本地**导出 / 导入（`.xqlypack`），不连接任何服务器。
 * 2. 云端传输是一个可插拔接口 {@link SyncTransport}，**默认不启用**；即便启用，
 *    也只在 `cloud_sync` 功能开关为 true 时才会被调用，且由调用方显式发起。
 *    本类绝不自行发起网络请求、绝不硬编码任何端点、绝不伪造凭据。
 * 3. 同步包带 schema、设备 id、生成时间与 SHA-256 校验和；截断 / 篡改 / 降 schema
 *    一律整体拒绝，绝不静默覆盖本地数据（与内容清单的防篡改思路一致）。
 * 4. 合并为本地优先：集合类（收藏/历史/最近搜索）做并集、绝不删除本地项；
 *    映射类（标签/收藏夹/书签/进度/阅读设置/游戏存档）按「远端比本地基线更新才覆盖」
 *    的按类最后写入时间判定，冲突默认保留本地。
 */
public final class SyncEngine {
    private SyncEngine() {}

    /** 同步包 schema：与 shared/lib/src/identity.dart 的 syncSchema 必须一致。 */
    public static final int SCHEMA = 1;

    // 同步包内部类别键（与 UserData 持久化键一一对应，便于直接搬迁）。
    public static final String C_FAVORITES = UserData.K_FAVORITES;
    public static final String C_HISTORY = UserData.K_HISTORY;
    public static final String C_RECENT_SEARCH = UserData.K_RECENT_SEARCH;
    public static final String C_TAGS = UserData.K_TAGS;
    public static final String C_COLLECTIONS = UserData.K_COLLECTIONS;
    public static final String C_BOOKMARKS = UserData.K_BOOKMARKS;
    public static final String C_PROGRESS = UserData.K_PROGRESS;
    public static final String C_READING = UserData.K_READING;
    public static final String C_GAME_SAVE = UserData.K_GAME_SAVE;
    public static final String C_GAME_STATS = UserData.K_GAME_STATS;

    /** 同步包本地基线时间戳键（用于「远端更新才覆盖」判定）。 */
    public static final String K_BASELINE_AT = "u.sync.baseline_at";
    public static final String K_GAME_AT = "u.sync.game_at";

    // 集合类（做并集、不删本地）
    private static final Set<String> SET_CATEGORIES = new LinkedHashSet<String>();
    static {
        SET_CATEGORIES.add(C_FAVORITES);
        SET_CATEGORIES.add(C_HISTORY);
        SET_CATEGORIES.add(C_RECENT_SEARCH);
    }

    /** 解析校验结果。 */
    public enum Reject { OK, BAD_JSON, BAD_SCHEMA, NO_DEVICE, BAD_CHECKSUM, TRUNCATED }

    public static final class Parsed {
        public final Reject reject;
        public final long generatedAt;
        public final String deviceId;
        public final Map<String, Object> categories;
        Parsed(Reject r, long at, String dev, Map<String, Object> cats) {
            reject = r; generatedAt = at; deviceId = dev; categories = cats;
        }
    }

    public static final class MergeReport {
        public int setsAdded = 0;       // 集合类新增条目
        public int mapsUpdated = 0;     // 映射类被远端更新的键数
        public int skipped = 0;         // 因本地更新而保留本地的键数
        public boolean gameMerged = false;
        public boolean ok = false;
    }

    /**
     * 从本地 UserPrefs 导出同步包（确定性 JSON：键序固定，便于校验和与跨端一致）。
     * 不写入任何远端；返回的就是文件内容，调用方决定存盘还是上传。
     */
    public static String exportPackage(UserPrefs p, String deviceId, long nowMs) {
        Map<String, Object> cats = new LinkedHashMap<String, Object>();
        for (String k : new String[]{C_FAVORITES, C_HISTORY, C_RECENT_SEARCH,
                C_TAGS, C_COLLECTIONS, C_BOOKMARKS, C_PROGRESS, C_READING,
                C_GAME_SAVE, C_GAME_STATS}) {
            String raw = p.get(k, "");
            if (raw.isEmpty()) continue;
            Object v = MiniJson.parse(raw);
            if (v != null) cats.put(k, v);
        }
        return buildEnvelope(cats, deviceId, nowMs);
    }

    private static String buildEnvelope(Map<String, Object> cats, String deviceId, long nowMs) {
        Map<String, Object> env = new LinkedHashMap<String, Object>();
        env.put("schema", SCHEMA);
        env.put("deviceId", deviceId == null ? "" : deviceId);
        env.put("generatedAt", nowMs);
        env.put("categories", cats);
        // 校验和先于 envelope 计算：对不含 checksum 的 payload 取规范序列化后哈希。
        String payload = MiniJson.stringify(cats);
        env.put("checksum", ContentManifest.sha256(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        return MiniJson.stringify(env);
    }

    /** 严格解析并校验同步包；任何异常一律拒绝，绝不返回部分结果。 */
    public static Parsed parsePackage(String json) {
        if (json == null || json.trim().isEmpty())
            return new Parsed(Reject.BAD_JSON, 0, "", null);
        Map<String, Object> env;
        try { env = MiniJson.object(json); }
        catch (Exception e) { return new Parsed(Reject.BAD_JSON, 0, "", null); }
        if (env == null) return new Parsed(Reject.BAD_JSON, 0, "", null);

        Object s = env.get("schema");
        if (!(s instanceof Number) || ((Number) s).intValue() != SCHEMA)
            return new Parsed(Reject.BAD_SCHEMA, 0, "", null);

        String dev = MiniJson.str(env, "deviceId", "");
        if (dev.isEmpty()) return new Parsed(Reject.NO_DEVICE, 0, "", null);

        Object g = env.get("generatedAt");
        long at = (g instanceof Number) ? ((Number) g).longValue() : 0L;

        Object c = env.get("categories");
        if (!(c instanceof Map)) return new Parsed(Reject.BAD_JSON, at, dev, null);
        @SuppressWarnings("unchecked")
        Map<String, Object> cats = (Map<String, Object>) c;

        // 校验和：重算 payload 的哈希，与包内 checksum 比对（防篡改 / 截断）。
        String payload = MiniJson.stringify(cats);
        String want = ContentManifest.sha256(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        if (!want.equals(MiniJson.str(env, "checksum", "")))
            return new Parsed(Reject.BAD_CHECKSUM, at, dev, null);

        return new Parsed(Reject.OK, at, dev, cats);
    }

    /**
     * 把已解析的远端包合并进本地（本地优先、按类最后写入时间判定）。
     * 不抛异常；失败（如 reject != OK）返回未修改的 report。
     */
    public static MergeReport merge(Parsed pkg, UserPrefs p) {
        MergeReport r = new MergeReport();
        if (pkg == null || pkg.reject != Reject.OK || pkg.categories == null) return r;

        long baseline = parseLong(p.get(K_BASELINE_AT, "0"), 0);
        boolean remoteNewer = pkg.generatedAt >= baseline;

        for (Map.Entry<String, Object> e : pkg.categories.entrySet()) {
            String cat = e.getKey();
            if (!SET_CATEGORIES.contains(cat)) continue;
            List<String> remote = stringList(e.getValue());
            Set<String> local = new LinkedHashSet<String>(listOf(p.get(cat, "[]")));
            int before = local.size();
            local.addAll(remote);                 // 并集：绝不删除本地项
            if (local.size() > before) {
                r.setsAdded += (local.size() - before);
                p.put(cat, MiniJson.stringify(new ArrayList<Object>(local)));
            }
        }

        // 映射类：远端比本地基线更新才按 key 覆盖，否则保留本地。
        for (String cat : new String[]{C_TAGS, C_COLLECTIONS, C_BOOKMARKS, C_PROGRESS, C_READING}) {
            Object rv = pkg.categories.get(cat);
            if (!(rv instanceof Map)) continue;
            @SuppressWarnings("unchecked")
            Map<String, Object> remoteMap = (Map<String, Object>) rv;
            Map<String, Object> localMap = objectMap(p.get(cat, "{}"));
            int updated = 0, kept = 0;
            for (Map.Entry<String, Object> ke : remoteMap.entrySet()) {
                if (remoteNewer) { localMap.put(ke.getKey(), ke.getValue()); updated++; }
                else kept++;
            }
            if (updated > 0) {
                p.put(cat, MiniJson.stringify(localMap));
                r.mapsUpdated += updated;
            }
            r.skipped += kept;
        }

        // 游戏存档：独立的最后写入时间，避免其它类别的合并节奏污染。
        long gameAt = parseLong(p.get(K_GAME_AT, "0"), 0);
        if (pkg.generatedAt > gameAt) {
            for (String cat : new String[]{C_GAME_SAVE, C_GAME_STATS}) {
                if (pkg.categories.containsKey(cat))
                    p.put(cat, MiniJson.stringify(pkg.categories.get(cat)));
            }
            p.put(K_GAME_AT, String.valueOf(pkg.generatedAt));
            r.gameMerged = true;
        }

        if (pkg.generatedAt > baseline) p.put(K_BASELINE_AT, String.valueOf(pkg.generatedAt));
        r.ok = true;
        return r;
    }

    /** 可插拔云端传输：本类不实现任何网络，且默认不启用（null）。 */
    public interface SyncTransport {
        /** 上传本地包（返回远端生成的 at，失败抛异常）。可能返回 null 表示未上传。 */
        byte[] upload(String deviceId, String pkg) throws Exception;
        /** 拉取远端包（无则返回 null）。 */
        String download(String deviceId) throws Exception;
    }

    // ---- 小工具 ----
    private static List<String> stringList(Object v) {
        List<String> out = new ArrayList<String>();
        if (v instanceof List) for (Object o : (List<?>) v) if (o instanceof String) out.add((String) o);
        return out;
    }
    @SuppressWarnings("unchecked")
    private static Map<String, Object> objectMap(String raw) {
        try {
            Object o = MiniJson.parse(raw);
            if (o instanceof Map) return new LinkedHashMap<String, Object>((Map<String, Object>) o);
        } catch (Exception ignored) { }
        return new LinkedHashMap<String, Object>();
    }
    private static long parseLong(String s, long def) {
        try { return Long.parseLong(s); } catch (Exception e) { return def; }
    }

    @SuppressWarnings("unchecked")
    private static List<String> listOf(String raw) {
        List<String> out = new ArrayList<String>();
        try {
            Object o = MiniJson.parse(raw);
            if (o instanceof List) for (Object e : (List<?>) o) if (e instanceof String) out.add((String) e);
        } catch (Exception ignored) { }
        return out;
    }
}
