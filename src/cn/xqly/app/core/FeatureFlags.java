package cn.xqly.app.core;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 功能开关 / Kill Switch（安全默认 + 可覆盖，纯 Java 可测）。
 *  shipped 内置一份 assets/config/flags.json（不含任何密钥），
 *  变更开关只需改该文件或在设置里覆盖，无需改代码、无需重新编译。
 *  出现线上事故时把对应开关置 false 即可远程/本地止损。
 */
public final class FeatureFlags {
    private FeatureFlags() {}

    public static final class Flag {
        public final String key;
        public final boolean defaultValue;
        public final String label;
        Flag(String k, boolean d, String l) { key = k; defaultValue = d; label = l; }
    }

    public static final Flag[] DEFINITIONS = {
            new Flag("online_enabled", true,  "在线直连"),
            new Flag("auto_update", true,  "自动检查更新"),
            new Flag("update_download", true, "允许下载更新包"),
            new Flag("content_pack", true,  "离线内容包可更新"),
            new Flag("bridge", true,        "版本化桥接"),
            new Flag("game", true,          "方块合成"),
            new Flag("glass_anim", true,    "玻璃流动动效"),
            new Flag("scroll_compact", false, "滚动收拢 Dock（默认关闭）"),
            new Flag("sound", false,        "游戏音效（默认关闭）"),
            new Flag("haptics", true,       "游戏震动"),
            new Flag("daily_challenge", true, "每日挑战"),
    };

    public static Map<String, Boolean> defaults() {
        Map<String, Boolean> m = new LinkedHashMap<String, Boolean>();
        for (Flag f : DEFINITIONS) m.put(f.key, f.defaultValue);
        return m;
    }

    /** 解析配置 JSON：未知键忽略、非布尔忽略、异常时回退到全默认。 */
    public static Map<String, Boolean> parse(String json) {
        Map<String, Boolean> out = defaults();
        if (json == null) return out;
        try {
            Map<String, Object> o = MiniJson.object(json);
            for (Map.Entry<String, Object> e : o.entrySet()) {
                if (out.containsKey(e.getKey()) && e.getValue() instanceof Boolean) {
                    out.put(e.getKey(), (Boolean) e.getValue());
                }
            }
        } catch (Exception ignored) { return defaults(); }
        return out;
    }

    /** 全局 Kill Switch：任一为 false 时对应能力彻底停用（含不发起网络）。 */
    public static boolean enabled(Map<String, Boolean> flags, String key) {
        Boolean v = flags == null ? null : flags.get(key);
        if (v == null) {
            for (Flag f : DEFINITIONS) if (f.key.equals(key)) return f.defaultValue;
            return false;  // 未知能力默认关闭
        }
        return v;
    }

    public static String toJson(Map<String, Boolean> flags) {
        Map<String, Object> o = new LinkedHashMap<String, Object>();
        for (Flag f : DEFINITIONS) o.put(f.key, enabled(flags, f.key));
        return MiniJson.stringify(o);
    }
}
