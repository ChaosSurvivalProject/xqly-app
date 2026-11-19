package cn.xqly.app.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 内置应用注册表 —— 「单一 WebView 池 + 多应用路由 + 独立沙箱」的唯一真源。
 * 与 shared/lib/src/apps.dart 逐项对应；两端规则必须一致（由门禁脚本校验）。
 *
 * 不因新增开发者平台而新开 WebView Activity：dev-portal 只是抽屉里与
 * 「方块合成」并列的一个路由，共享返回栈、设计 token、临时反馈与更新机制，
 * 但拥有独立的存储分区、权限策略与桥作用域。
 */
public final class AppRegistry {
    private AppRegistry() {}

    public enum Offline { FULL, READ_ONLY, NONE }

    public static final class AppSpec {
        public final String id, displayName, subtitle, route, bridgeScope;
        public final String storagePartition, communityLabel, iconLabel;
        public final Offline offline;
        public final int maxNavDepth;

        AppSpec(String id, String name, String sub, String route, String scope,
                String partition, Offline offline, int depth, String label, String icon) {
            this.id = id; this.displayName = name; this.subtitle = sub;
            this.route = route; this.bridgeScope = scope;
            this.storagePartition = partition; this.offline = offline;
            this.maxNavDepth = depth; this.communityLabel = label;
            this.iconLabel = icon;
        }
    }

    /** 导航四层：抽屉→应用首页→详情/会话→模态/审批。 */
    public static final int NAV_DRAWER = 1, NAV_APP_HOME = 2, NAV_DETAIL = 3, NAV_MODAL = 4;

    public static final String BRIDGE_VERSION = "xqly-bridge/1";
    public static final String ROUTE_SCHEME = "xqly-app";
    public static final String DEEP_LINK_SCHEME = "xqly";
    public static final String DEV_PORTAL_ID = "dev-portal";
    public static final String AI_WORKBENCH_ID = "ai-workbench";

    public static final List<AppSpec> APPS = Collections.unmodifiableList(
            Arrays.asList(
                    new AppSpec("web", "星穹旅驿", "官网 · 社区 · 档案",
                            "xqly-app://apps/web", "site:*", "web",
                            Offline.FULL, NAV_DETAIL, "", "首页"),
                    new AppSpec("cube-craft", "方块合成", "离线小游戏",
                            "xqly-app://apps/cube-craft", "game:*", "cube-craft",
                            Offline.FULL, NAV_DETAIL, "", "游戏"),
                    new AppSpec(DEV_PORTAL_ID, "星穹旅驿开发者平台", "开发者工具·远程门户",
                            "xqly-app://apps/dev-portal", "devportal:*", "dev-portal",
                            Offline.READ_ONLY, NAV_MODAL, "社区非官方 · 自研远程门户", "开发"),
                    new AppSpec(AI_WORKBENCH_ID, "星穹旅驿 AI 工作台", "本地交互对标 · 不内置模型与运行时",
                            "xqly-app://apps/ai-workbench", "workbench:*", "ai-workbench",
                            Offline.FULL, NAV_DETAIL, "社区非官方 · 零依赖功能对标", "工作台")
            ));

    public static AppSpec byId(String id) {
        if (id == null) return null;
        for (AppSpec a : APPS) if (a.id.equals(id)) return a;
        return null;
    }

    public static boolean registered(String id) { return byId(id) != null; }

    public static String routeFor(String id) {
        AppSpec a = byId(id);
        return a == null ? null : a.route;
    }

    public static boolean routeAllowed(String route) {
        if (route == null || route.isEmpty() || route.length() > 128) return false;
        String pre = ROUTE_SCHEME + "://apps/";
        if (!route.startsWith(pre)) return false;
        if (route.contains("..") || route.contains("\\") || route.contains("?")
                || route.contains("#") || route.contains("\0")) return false;
        return registered(route.substring(pre.length()));
    }

    public static boolean scopeAllows(String appId, String scope) {
        AppSpec a = byId(appId);
        return a != null && scope != null && !scope.isEmpty()
                && scope.length() <= 64 && scope.equals(a.bridgeScope);
    }

    /** 每应用独立 Web 存储分区，绝不共用。 */
    public static boolean storageIsolated(String a, String b) {
        AppSpec x = byId(a), y = byId(b);
        return x != null && y != null && !x.storagePartition.equals(y.storagePartition);
    }

    public static Offline offlineOf(String id) {
        AppSpec a = byId(id);
        return a == null ? Offline.NONE : a.offline;
    }

    public static boolean offlineAvailable(String id) {
        Offline o = offlineOf(id);
        return o == Offline.FULL || o == Offline.READ_ONLY;
    }

    public static boolean navDepthAllowed(String id, int depth) {
        AppSpec a = byId(id);
        return a != null && depth >= NAV_DRAWER && depth <= a.maxNavDepth;
    }

    public static Map<String, Object> toJson() {
        Map<String, Object> root = new LinkedHashMap<String, Object>();
        root.put("bridgeVersion", BRIDGE_VERSION);
        List<Object> apps = new ArrayList<Object>();
        for (AppSpec a : APPS) {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("id", a.id);
            m.put("displayName", a.displayName);
            m.put("subtitle", a.subtitle);
            m.put("route", a.route);
            m.put("bridgeScope", a.bridgeScope);
            m.put("storagePartition", a.storagePartition);
            m.put("offlineCapability", a.offline.name());
            m.put("maxNavDepth", a.maxNavDepth);
            m.put("communityLabel", a.communityLabel);
            m.put("iconLabel", a.iconLabel);
            apps.add(m);
        }
        root.put("apps", apps);
        return root;
    }
}
