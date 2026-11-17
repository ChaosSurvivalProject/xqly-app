package cn.xqly.app.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 开发者平台状态机（Java 侧镜像 shared/lib/src/dev_portal_state.dart）。
 *
 * 硬约束：远程会话数据默认不落盘；关闭应用后无后台服务；
 * 凭据只从系统凭证库读入原生内存，这里**不保存任何密钥**。
 */
public final class DevPortalState {

    public enum Link { DISCONNECTED, CHECKING, CONNECTED, ERROR }
    public enum Mode { ONLINE, OFFLINE_READ_ONLY }

    public static final String APP_ID = AppRegistry.DEV_PORTAL_ID;
    public static final String OFFLINE_CAPABILITY = "read-only";

    public static final class Approval {
        public final String toolId, scope, grant;
        public Approval(String toolId, String scope, String grant) {
            this.toolId = toolId; this.scope = scope; this.grant = grant;
        }
    }

    private static final Pattern ID = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");

    private Link link = Link.DISCONNECTED;
    private Mode mode = Mode.OFFLINE_READ_ONLY;
    private final List<String> sessions = new ArrayList<String>();
    private final List<Approval> approvals = new ArrayList<Approval>();
    private int unlockFailures = 0;
    private long lockedUntilMs = 0;
    private String endpointId = "";
    private int chunks = 0;

    public Link link() { return link; }
    public Mode mode() { return mode; }
    public List<String> sessions() { return Collections.unmodifiableList(sessions); }
    public List<Approval> approvals() { return Collections.unmodifiableList(approvals); }
    public int unlockFailures() { return unlockFailures; }
    public String endpointId() { return endpointId; }
    public int streamedChunks() { return chunks; }
    public boolean canConnect() { return mode == Mode.ONLINE; }
    public boolean canExecute() { return false; } // v1 不执行第三方插件 / 不内置模型

    public void setOnline(boolean online) {
        mode = online ? Mode.ONLINE : Mode.OFFLINE_READ_ONLY;
        if (!online && link != Link.DISCONNECTED) disconnect();
    }

    public boolean isLocked(long nowMs) { return nowMs < lockedUntilMs; }

    /** 系统级身份验证：失败三次锁 60 秒。 */
    public boolean recordUnlock(boolean success, long nowMs) {
        if (isLocked(nowMs)) return false;
        if (success) { unlockFailures = 0; return true; }
        unlockFailures++;
        if (unlockFailures >= 3) {
            lockedUntilMs = nowMs + 60000;
            unlockFailures = 0;
            return false;
        }
        return true;
    }

    public boolean connect(String endpointId, Set<String> allowlist, long nowMs) {
        if (!canConnect() || isLocked(nowMs) || allowlist == null) return false;
        if (!allowlist.contains(endpointId)) return false;
        if (endpointId == null || !ID.matcher(endpointId).matches()) return false;
        link = Link.CHECKING;
        this.endpointId = endpointId;
        return true;
    }

    public void confirmConnected() { if (link == Link.CHECKING) link = Link.CONNECTED; }
    public void failConnect() { link = Link.ERROR; }

    public void disconnect() {
        link = Link.DISCONNECTED;
        endpointId = "";
        sessions.clear();
        approvals.clear();
        chunks = 0;
    }

    public boolean addSession(String id) {
        if (link != Link.CONNECTED || id == null || !ID.matcher(id).matches()) return false;
        if (!sessions.contains(id)) sessions.add(id);
        return true;
    }

    public void onChunk() { if (link == Link.CONNECTED) chunks++; }

    /** 每次危险操作都要重新弹原生审批；grant=session 时本次会话内有效。 */
    public boolean approve(Approval a) {
        if (link != Link.CONNECTED) return false;
        for (int i = approvals.size() - 1; i >= 0; i--)
            if (approvals.get(i).toolId.equals(a.toolId)) approvals.remove(i);
        if (!"deny".equals(a.grant)) approvals.add(a);
        return true;
    }

    public boolean hasApproval(String toolId) {
        for (Approval a : approvals) if (a.toolId.equals(toolId) && !"deny".equals(a.grant))
            return true;
        return false;
    }

    /** 只暴露非敏感 UI 状态；不含密钥、令牌、设备标识或任意文件路径。 */
    public Map<String, Object> snapshot() {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("appId", APP_ID);
        m.put("link", link.name());
        m.put("mode", mode.name());
        m.put("offlineCapability", OFFLINE_CAPABILITY);
        m.put("endpointConfigured", !endpointId.isEmpty());
        m.put("sessionCount", sessions.size());
        m.put("streamedChunks", chunks);
        m.put("locked", false);
        m.put("canConnect", canConnect());
        m.put("canExecute", canExecute());
        m.put("telemetry", false);
        return m;
    }

    /** 离线空状态：写清「现在没有内容 / 为什么 / 下一步做什么」。 */
    public static Map<String, Object> offlineEmptyState() {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("title", "当前处于离线只读模式");
        m.put("why", "本模块在离线档案中不提供远程会话与工具执行，仅保留应用外壳、帮助与接入条件。");
        m.put("next", "连接已配置的远程服务后可使用会话功能；或返回应用抽屉继续浏览离线内容。");
        m.put("notFound", "本模块在离线档案中不可用");
        m.put("action", "重新检查更新");
        m.put("thirdPartyLabel", "社区非官方 · 自研远程门户");
        return m;
    }

    public static Map<String, Object> envInfo() {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("appId", APP_ID);
        m.put("bridge", AppRegistry.BRIDGE_VERSION);
        m.put("theme", "dark");
        m.put("language", "zh-CN");
        m.put("breakpoint", "phone");
        m.put("networkMode", "online-or-offline");
        // 明确不返回 IMEI / IMSI / 设备标识 / 文件路径。
        return m;
    }
}
