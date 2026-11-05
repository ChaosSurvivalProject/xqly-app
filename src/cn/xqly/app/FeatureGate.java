package cn.xqly.app;

import android.content.Context;

import java.util.Map;

import cn.xqly.app.core.FeatureFlags;

/** Android 侧功能开关：读 assets/config/flags.json，用户覆盖存用户命名空间。 */
public final class FeatureGate {
    private FeatureGate() {}

    private static volatile Map<String, Boolean> cached;

    public static Map<String, Boolean> flags(Context ctx) {
        Map<String, Boolean> c = cached;
        if (c != null) return c;
        synchronized (FeatureGate.class) {
            if (cached != null) return cached;
            String json = "{}";
            try {
                java.io.InputStream in = ctx.getAssets().open("config/flags.json");
                java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                byte[] b = new byte[4096];
                int n;
                while ((n = in.read(b)) > 0) bos.write(b, 0, n);
                in.close();
                json = new String(bos.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
            } catch (Exception ignored) { }
            cached = FeatureFlags.parse(json);
            return cached;
        }
    }

    public static boolean enabled(Context ctx, String key) {
        if (!FeatureFlags.enabled(flags(ctx), key)) return false;
        // 用户覆盖：仅允许把开→关（Kill Switch 方向），不允许用设置页绕过默认关闭项
        PrefsStore p = new PrefsStore(ctx, true);
        String override = p.get("u.flag." + key, "");
        if ("0".equals(override)) return false;
        return true;
    }

    public static void setOverride(Context ctx, String key, boolean on) {
        new PrefsStore(ctx, true).put("u.flag." + key, on ? "1" : "0");
    }

    /** 测试/设置页使用：重置缓存以便重新读取。 */
    public static void reset() { cached = null; }
}
