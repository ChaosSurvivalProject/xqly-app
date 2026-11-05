package cn.xqly.app.core;

/** 极简键值接口：Android 端用 SharedPreferences 实现，测试端用内存实现。 */
public interface UserPrefs {
    String get(String key, String def);
    void put(String key, String value);
    boolean has(String key);
    void remove(String key);
    /** 清空当前命名空间（仅允许对缓存实例调用，用户数据实例不得调用）。 */
    void clearAll();
}
