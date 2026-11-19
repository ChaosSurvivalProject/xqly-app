package cn.xqly.app.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** AI 工作台聚合状态机（Java 侧镜像 shared/lib/src/workbench_state.dart）。 */
public final class WorkbenchState {
    public static final String APP_ID = AppRegistry.AI_WORKBENCH_ID;
    public static final String OFFLINE_CAPABILITY = "local-only";
    public static final String INTRO = "本地 AI 工作台 · 会话与清单留在设备 · 不内置模型与运行时";

    private final WorkbenchChat chats = new WorkbenchChat();
    private final WorkbenchProvider providers = new WorkbenchProvider();
    private final WorkbenchPlugin plugins = new WorkbenchPlugin();
    private final WorkbenchLog log = new WorkbenchLog();

    public WorkbenchChat chats() { return chats; }
    public WorkbenchProvider providers() { return providers; }
    public WorkbenchPlugin plugins() { return plugins; }
    public WorkbenchLog log() { return log; }

    public Map<String, Object> snapshot(boolean manifestOk, boolean tokensOk,
                                        boolean bridgeOk, boolean signedOk,
                                        boolean offlineAssetsOk, int freeMb) {
        List<WorkbenchSelfCheck.Item> checks = WorkbenchSelfCheck.run(
                manifestOk, tokensOk, bridgeOk, signedOk, offlineAssetsOk, freeMb);
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("appId", APP_ID);
        m.put("bridge", AppRegistry.BRIDGE_VERSION);
        m.put("offlineCapability", OFFLINE_CAPABILITY);
        m.put("intro", INTRO);
        m.put("canExecute", false);
        m.put("canCallModel", false);
        m.put("telemetry", false);
        m.put("credentialStored", false);
        m.put("sessionCount", chats.count());
        m.put("pluginCount", plugins.count());
        m.put("providerCount", WorkbenchProvider.CATALOG.size());
        m.put("logSize", log.size());
        m.put("selfcheck", WorkbenchSelfCheck.summary(checks));
        return m;
    }

    public static Map<String, Object> envInfo() {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("appId", APP_ID);
        m.put("bridge", AppRegistry.BRIDGE_VERSION);
        m.put("theme", "dark");
        m.put("language", "zh-CN");
        m.put("breakpoint", "phone");
        m.put("capability", OFFLINE_CAPABILITY);
        m.put("canExecute", false);
        m.put("canCallModel", false);
        return m;
    }

    public static Map<String, Object> emptyState() {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("title", "本地工作台已就绪，但不内置模型与运行时");
        m.put("why", "本模块对标 AI 工作台的交互与本地数据管理，不含 PRoot / Ubuntu / Node，也不发起模型请求。");
        m.put("next", "可记录本地会话草稿、登记供应商与插件清单、运行环境自检；模型推理请在你信任的外部服务完成。");
        m.put("notFound", "未执行任何工具或模型调用");
        m.put("action", "运行环境自检");
        return m;
    }
}
