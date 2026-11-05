package cn.xqly.app;

import android.content.Context;
import android.content.SharedPreferences;

import cn.xqly.app.core.UserPrefs;

/** UserPrefs 的 Android 实现：用户数据与缓存分文件存放，清缓存绝不误删。 */
public final class PrefsStore implements UserPrefs {
    private final SharedPreferences sp;

    /** @param user true=用户数据文件（永不随缓存清理）, false=缓存/临时文件 */
    public PrefsStore(Context ctx, boolean user) {
        sp = ctx.getSharedPreferences(user ? "xqly_user" : "xqly_cache", Context.MODE_PRIVATE);
    }

    public String get(String k, String d) { try { return sp.getString(k, d); } catch (Exception e) { return d; } }
    public void put(String k, String v) { sp.edit().putString(k, v).apply(); }
    public boolean has(String k) { return sp.contains(k); }
    public void remove(String k) { sp.edit().remove(k).apply(); }
    public void clearAll() { sp.edit().clear().apply(); }  // 仅允许对 cache 实例调用
}
