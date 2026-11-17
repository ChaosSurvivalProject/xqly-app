import 'apps.dart';
import 'endpoint_policy.dart';

/// 连接状态点只表示：已连接 / 断开 / 检查中，不暴露内部 host 实现。
enum LinkState { disconnected, checking, connected, error }

/// 在线 / 离线双模式：执行能力按模式降级。
enum PortalMode { online, offlineReadOnly }

/// 工具审批：每次危险操作都要弹原生确认，最小权限、单次/会话可选。
class ToolApproval {
  final String toolId;
  final String scope;
  final String grant; // once | session | deny
  const ToolApproval(this.toolId, this.scope, this.grant);
}

/// 开发者平台状态机（纯内存）。
///
/// 硬约束：远程会话数据默认不落盘；关闭应用后无后台服务；
/// 凭据只从系统凭证库读入原生内存，这里**不保存任何密钥**。
class DevPortalState {
  final String appId = AppRegistry.devPortalId;
  LinkState _link = LinkState.disconnected;
  PortalMode _mode = PortalMode.offlineReadOnly;
  final List<String> _sessions = <String>[];
  final List<ToolApproval> _approvals = <ToolApproval>[];
  int _unlockFailures = 0;
  int _lockedUntilMs = 0;
  String _endpointId = '';
  int _chunks = 0;

  LinkState get link => _link;
  PortalMode get mode => _mode;
  List<String> get sessions => List<String>.unmodifiable(_sessions);
  List<ToolApproval> get approvals => List<ToolApproval>.unmodifiable(_approvals);
  int get unlockFailures => _unlockFailures;
  String get endpointId => _endpointId;
  int get streamedChunks => _chunks;
  bool get canConnect => _mode == PortalMode.online;
  bool get canExecute => false; // v1 远程门户不执行第三方插件 / 不内置模型

  /// 离线能力登记：只读，与「可玩的小游戏」明确区分。
  static const String offlineCapability = 'read-only';

  void setOnline(bool online) {
    _mode = online ? PortalMode.online : PortalMode.offlineReadOnly;
    if (!online && _link != LinkState.disconnected) disconnect();
  }

  bool isLocked(int nowMs) => nowMs < _lockedUntilMs;

  /// 系统级身份验证：失败三次锁 60 秒。
  bool recordUnlock(bool success, int nowMs) {
    if (isLocked(nowMs)) return false;
    if (success) {
      _unlockFailures = 0;
      return true;
    }
    _unlockFailures++;
    if (_unlockFailures >= 3) {
      _lockedUntilMs = nowMs + 60000;
      _unlockFailures = 0;
      return false;
    }
    return true;
  }

  bool connect(String endpointId, Set<String> allowlist, int nowMs) {
    if (!canConnect || isLocked(nowMs)) return false;
    if (!allowlist.contains(endpointId)) return false;
    if (!RegExp(r'^[A-Za-z0-9_-]{1,64}$').hasMatch(endpointId)) return false;
    _link = LinkState.checking;
    _endpointId = endpointId;
    return true;
  }

  void confirmConnected() {
    if (_link == LinkState.checking) _link = LinkState.connected;
  }

  void failConnect() {
    _link = LinkState.error;
  }

  void disconnect() {
    _link = LinkState.disconnected;
    _endpointId = '';
    // 远程会话数据默认不落盘：断开即清空内存中的会话与流式块。
    _sessions.clear();
    _approvals.clear();
    _chunks = 0;
  }

  bool addSession(String id) {
    if (_link != LinkState.connected) return false;
    if (!RegExp(r'^[A-Za-z0-9_-]{1,64}$').hasMatch(id)) return false;
    if (!_sessions.contains(id)) _sessions.add(id);
    return true;
  }

  void onChunk() {
    if (_link == LinkState.connected) _chunks++;
  }

  /// 每次危险操作都要重新弹原生审批；grant=session 时本次会话内有效。
  bool approve(ToolApproval a) {
    if (_link != LinkState.connected) return false;
    if (a.grant == 'deny') {
      _approvals.removeWhere((ToolApproval x) => x.toolId == a.toolId);
      return true;
    }
    _approvals.removeWhere((ToolApproval x) => x.toolId == a.toolId);
    _approvals.add(a);
    return true;
  }

  bool hasApproval(String toolId) =>
      _approvals.any((ToolApproval a) => a.toolId == toolId && a.grant != 'deny');

  /// 只暴露非敏感 UI 状态；不含密钥、令牌、设备标识或任意文件路径。
  Map<String, Object?> snapshot() => <String, Object?>{
        'appId': appId,
        'link': _link.name,
        'mode': _mode.name,
        'offlineCapability': offlineCapability,
        'endpointConfigured': _endpointId.isNotEmpty,
        'sessionCount': _sessions.length,
        'streamedChunks': _chunks,
        'locked': false,
        'canConnect': canConnect,
        'canExecute': canExecute,
        'telemetry': false,
      };

  /// 离线空状态必须写清「现在没有内容 / 为什么 / 下一步做什么」。
  static Map<String, Object?> offlineEmptyState() => <String, Object?>{
        'title': '当前处于离线只读模式',
        'why': '本模块在离线档案中不提供远程会话与工具执行，仅保留应用外壳、帮助与接入条件。',
        'next': '连接已配置的远程服务后可使用会话功能；或返回应用抽屉继续浏览离线内容。',
        'notFound': '本模块在离线档案中不可用',
        'action': '重新检查更新',
        'thirdPartyLabel': '社区非官方 · 自研远程门户',
      };

  static Map<String, Object?> envInfo() => <String, Object?>{
        'appId': AppRegistry.devPortalId,
        'bridge': AppRegistry.bridgeVersion,
        'theme': 'dark',
        'language': 'zh-CN',
        'breakpoint': 'phone',
        'networkMode': 'online-or-offline',
        // 明确不返回 IMEI / IMSI / 设备标识 / 文件路径。
      };

  static bool telemetryDefault() => EndpointPolicy.telemetryDefault;
}
