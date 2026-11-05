package cn.xqly.app.core;

/**
 * 页面状态的统一枚举与「状态 → 用户可见文案/可操作」映射。
 * 每个页面必须能从任意状态恢复到 SUCCESS；禁止只有成功界面 + 无限转圈。
 */
public final class AppState {
    private AppState() {}

    public enum Phase {
        LOADING,           // 首次加载
        SUCCESS,           // 成功
        EMPTY,             // 空状态
        OFFLINE_CACHED,    // 无网有缓存
        OFFLINE_NO_CACHE,  // 无网无缓存
        ERROR_RETRY,       // 请求失败与重试
        PARTIAL,           // 部分内容可用
        VALIDATION_FAILED, // 校验失败
        PERMISSION_DENIED, // 权限拒绝
        STORAGE_LOW,       // 存储空间不足
        PENDING_SWITCH     // 内容版本待重启/待切换
    }

    public enum Page { HOME, LIBRARY, ARTICLE, SEARCH, FAVORITE, DOWNLOAD, UPDATE, GAME }

    public static final class ViewState {
        public final Phase phase;
        public final String title, message;
        public final boolean retryable, showContent, spinner;
        public final String actionLabel;
        ViewState(Phase p, String t, String m, boolean r, boolean c, boolean s, String a) {
            phase = p; title = t; message = m; retryable = r; showContent = c; spinner = s; actionLabel = a;
        }
    }

    public static ViewState of(Page page, Phase phase) {
        switch (phase) {
            case LOADING:
                return new ViewState(phase, "正在准备内容", "首次加载中，请稍候", false, false, true, "");
            case SUCCESS:
                return new ViewState(phase, "", "", false, true, false, "");
            case EMPTY:
                return new ViewState(phase, emptyTitle(page), "这里还没有内容", false, false, false, "");
            case OFFLINE_CACHED:
                return new ViewState(phase, "当前为离线模式", "正在使用本机已保存的内容，联网后自动更新", false, true, false, "");
            case OFFLINE_NO_CACHE:
                return new ViewState(phase, "暂无离线内容", "本机还没有保存该内容，请联网后重试或先下载离线包", true, false, false, "去下载");
            case ERROR_RETRY:
                return new ViewState(phase, "加载失败", "连接出现问题，可以重试", true, false, false, "重试");
            case PARTIAL:
                return new ViewState(phase, "部分内容可用", "部分资源未能加载，已展示可用内容", true, true, false, "重新加载");
            case VALIDATION_FAILED:
                return new ViewState(phase, "内容校验未通过", "已保留上一个可用版本，未应用新内容", true, true, false, "重新校验");
            case PERMISSION_DENIED:
                return new ViewState(phase, "需要授权", "请在系统设置中允许所需权限后继续", true, false, false, "去设置");
            case STORAGE_LOW:
                return new ViewState(phase, "存储空间不足", "请清理空间后重试，收藏与存档不会受影响", true, false, false, "清理缓存");
            case PENDING_SWITCH:
                return new ViewState(phase, "新版本已就绪", "新内容将在下次启动时启用，也可立即切换", false, true, false, "立即切换");
            default:
                return new ViewState(phase, "", "", false, true, false, "");
        }
    }

    private static String emptyTitle(Page p) {
        switch (p) {
            case SEARCH: return "没有找到结果";
            case FAVORITE: return "还没有收藏";
            case DOWNLOAD: return "暂无下载任务";
            case UPDATE: return "暂无更新记录";
            case GAME: return "还没有对局记录";
            case LIBRARY: return "还没有阅读记录";
            default: return "暂无内容";
        }
    }

    /** 状态机转换合法性（防止从终态跳到不合法态）。 */
    public static boolean canTransition(Phase from, Phase to) {
        if (from == to) return true;
        if (to == Phase.LOADING) return true;             // 任何状态都可重新加载
        if (to == Phase.SUCCESS) return true;             // 任何状态都可恢复
        if (from == Phase.SUCCESS) return true;           // 成功态可进入任意异常态
        return true;                                       // 允许异常态之间互相转换（重试/降级）
    }
}
