package cn.xqly.app.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 环境自检（Java 侧镜像）。运行时项一律 UNAVAILABLE，不伪造。 */
public final class WorkbenchSelfCheck {
    private WorkbenchSelfCheck() {}

    public enum State { PASS, WARN, FAIL, UNAVAILABLE }

    public static final class Item {
        public final String id, label, note;
        public final State state;
        Item(String id, String label, State state, String note) {
            this.id = id; this.label = label; this.state = state; this.note = note;
        }
        public Map<String, Object> toJson() {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("id", id); m.put("label", label);
            m.put("state", state.name().toLowerCase(java.util.Locale.US));
            m.put("note", note);
            return m;
        }
    }

    private static final String[][] RUNTIME = {
            {"shell", "Shell (bash)"}, {"node", "Node.js"}, {"proot", "PRoot 沙箱"},
            {"landlock", "内核 Landlock"}, {"pty", "PTY 终端"}, {"ripgrep", "ripgrep"},
            {"compiler", "C 编译器 / make / Python"}, {"plugin_exec", "第三方插件执行"},
            {"shell_tools", "设备 Shell 工具"}, {"shizuku", "Shizuku 授权"},
            {"a11y", "无障碍自动化"}, {"virtual_display", "目标应用副屏"},
            {"webview_runtime", "运行时控制台"},
    };

    public static List<Item> run(boolean manifestOk, boolean tokensOk, boolean bridgeOk,
                                 boolean signedOk, boolean offlineAssetsOk, int freeMb) {
        List<Item> r = new ArrayList<Item>();
        r.add(new Item("manifest", "内置资源清单",
                manifestOk ? State.PASS : State.FAIL,
                manifestOk ? "清单哈希校验通过" : "清单校验失败，禁止继续"));
        r.add(new Item("tokens", "共享设计令牌",
                tokensOk ? State.PASS : State.FAIL,
                tokensOk ? "三端令牌逐字节一致" : "令牌不一致"));
        r.add(new Item("bridge", "xqly-bridge/1 协议",
                bridgeOk ? State.PASS : State.FAIL,
                bridgeOk ? "方法 schema 已加载" : "协议未就绪"));
        r.add(new Item("signature", "APK 签名证书",
                signedOk ? State.PASS : State.WARN,
                signedOk ? "正式签名，可覆盖升级" : "测试签名，请勿作为正式版"));
        r.add(new Item("offline", "离线随包资源",
                offlineAssetsOk ? State.PASS : State.WARN,
                offlineAssetsOk ? "档案/游戏可离线使用" : "部分离线资源缺失"));
        r.add(new Item("storage", "可用空间",
                freeMb >= 512 ? State.PASS : State.WARN,
                freeMb >= 512 ? freeMb + " MB，充足" : freeMb + " MB，建议清理至 512 MB 以上"));
        for (String[] x : RUNTIME) {
            r.add(new Item(x[0], x[1], State.UNAVAILABLE,
                    "本客户端零依赖内置，不提供该运行时；见模组说明"));
        }
        return Collections.unmodifiableList(r);
    }

    public static Map<String, Object> summary(List<Item> items) {
        int pass = 0, warn = 0, fail = 0, unavailable = 0;
        for (Item i : items) {
            switch (i.state) {
                case PASS: pass++; break;
                case WARN: warn++; break;
                case FAIL: fail++; break;
                case UNAVAILABLE: unavailable++; break;
            }
        }
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("total", items.size()); m.put("pass", pass); m.put("warn", warn);
        m.put("fail", fail); m.put("unavailable", unavailable);
        m.put("healthy", fail == 0);
        return m;
    }
}
