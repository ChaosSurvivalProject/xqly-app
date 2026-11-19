import 'apps.dart';
import 'endpoint_policy.dart';
import 'foundation.dart';
import 'workbench_plugin.dart';
import 'workbench_provider.dart';

/// xqly-bridge/1 十六条方法的规格。每条方法必须有 schema、超时、幂等性与权限标签。
class MethodSpec {
  final String name;
  final String permission;
  final int timeoutMs;
  final bool idempotent;
  final Set<String> allowedKeys;
  final Set<String> requiredKeys;
  final bool requiresApproval;
  final bool network;

  const MethodSpec(
    this.name,
    this.permission,
    this.timeoutMs,
    this.idempotent, {
    this.allowedKeys = const <String>{},
    this.requiredKeys = const <String>{},
    this.requiresApproval = false,
    this.network = false,
  });
}

enum RejectV1 {
  none,
  tooLarge,
  badJson,
  badVersion,
  foreignApp,
  unknownMethod,
  badId,
  duplicateId,
  badParams,
  forbiddenKey,
  restrictedKey,
  permissionDenied,
  rateLimited,
  offlineUnavailable,
  telemetryOff,
  approvalRequired,
  badEnvelope,
}

class CallResult {
  final bool ok;
  final RejectV1 reject;
  final String method;
  final String id;
  final Map<String, Object?> params;
  final MethodSpec? spec;

  const CallResult.ok(this.method, this.id, this.params, this.spec)
      : ok = true,
        reject = RejectV1.none;
  const CallResult.bad(this.reject)
      : ok = false,
        method = '',
        id = '',
        params = const <String, Object?>{},
        spec = null;

  String get reason => reject.name;
}

/// 桥 v1 的**唯一**校验入口（纯 Dart，供三端共用规则）。
///
/// 校验链：长度 → 严格 JSON → bridge 版本 → appId 越界 → 方法白名单 →
/// id 格式/幂等去重 → params schema（键白名单/类型/长度）→ 危险键 →
/// 受控键专项校验 → 权限标签 → 速率 → 离线降级 → 遥测开关 → 工具审批。
/// 未知方法、越界 appId、超长参数、过快调用一律拒绝并记录原因（不回显细节）。
class BridgeV1 {
  BridgeV1._();

  static const String version = 'xqly-bridge/1';
  static const int maxMessage = 8192;
  static const int maxParamLen = 2048;
  static const int maxParams = 12;
  static const int maxIdLen = 64;
  static const int minCallIntervalMs = 120;

  /// 默认授予的能力；credential / network / approval / export / telemetry / diagnostics
  /// 必须由用户显式授权，未知能力默认关闭。
  static const Set<String> defaultPermissions = <String>{
    'public', 'ui', 'nav', 'link', 'update', 'clipboard',
  };

  static const Map<String, MethodSpec> methods = <String, MethodSpec>{
    'xqly.env.getInfo': MethodSpec('xqly.env.getInfo', 'public', 1000, true),
    'xqly.ui.setTitle': MethodSpec('xqly.ui.setTitle', 'ui', 1000, false,
        allowedKeys: <String>{'title', 'subtitle'}, requiredKeys: <String>{'title'}),
    'xqly.ui.showFeedback': MethodSpec('xqly.ui.showFeedback', 'ui', 1000, false,
        allowedKeys: <String>{'level', 'durationMs', 'action'},
        requiredKeys: <String>{'level'}),
    'xqly.theme.subscribe': MethodSpec('xqly.theme.subscribe', 'public', 1000, true,
        allowedKeys: <String>{'tokens'}),
    'xqly.nav.open': MethodSpec('xqly.nav.open', 'nav', 2000, false,
        allowedKeys: <String>{'appId', 'path', 'replace'}, requiredKeys: <String>{'appId'}),
    'xqly.link.open': MethodSpec('xqly.link.open', 'link', 2000, false,
        allowedKeys: <String>{'url', 'external'}, requiredKeys: <String>{'url'}),
    'xqly.credential.getEndpoint': MethodSpec(
        'xqly.credential.getEndpoint', 'credential', 3000, true,
        allowedKeys: <String>{'endpointId'}, requiredKeys: <String>{'endpointId'}),
    'xqly.credential.unlock': MethodSpec(
        'xqly.credential.unlock', 'credential', 30000, false,
        allowedKeys: <String>{'reason'}),
    'xqly.devportal.connect': MethodSpec(
        'xqly.devportal.connect', 'network:devportal', 30000, false,
        allowedKeys: <String>{'endpointId', 'transport'},
        requiredKeys: <String>{'endpointId'}, network: true),
    'xqly.devportal.send': MethodSpec(
        'xqly.devportal.send', 'network:devportal', 30000, false,
        allowedKeys: <String>{'requestId', 'payload'},
        requiredKeys: <String>{'requestId'}, network: true),
    'xqly.devportal.approveTool': MethodSpec(
        'xqly.devportal.approveTool', 'user_approval', 30000, false,
        allowedKeys: <String>{'toolId', 'scope', 'grant'},
        requiredKeys: <String>{'toolId', 'scope'}, requiresApproval: true),
    'xqly.file.pickSave': MethodSpec('xqly.file.pickSave', 'file:export', 30000, false,
        allowedKeys: <String>{'mime', 'suggestedName'}),
    'xqly.clipboard.copy': MethodSpec('xqly.clipboard.copy', 'clipboard', 1000, false,
        allowedKeys: <String>{'text', 'sensitive'}, requiredKeys: <String>{'text'}),
    'xqly.ota.check': MethodSpec('xqly.ota.check', 'update', 10000, true,
        allowedKeys: <String>{'channel'}),
    'xqly.telemetry.report': MethodSpec(
        'xqly.telemetry.report', 'telemetry:optional', 2000, true,
        allowedKeys: <String>{'event', 'props'}),
    'xqly.error.report': MethodSpec('xqly.error.report', 'diagnostics', 3000, true,
        allowedKeys: <String>{'code', 'messageHash', 'breadcrumbs'}),
    // ---- AI 工作台：对标 harness 交互的纯本地方法，零网络、零执行、零密钥 ----
    'xqly.workbench.env': MethodSpec(
        'xqly.workbench.env', 'public', 1000, true),
    'xqly.workbench.snapshot': MethodSpec(
        'xqly.workbench.snapshot', 'workbench', 1000, true,
        allowedKeys: <String>{'freeMb'}),
    'xqly.workbench.session.list': MethodSpec(
        'xqly.workbench.session.list', 'workbench', 1000, true),
    'xqly.workbench.session.create': MethodSpec(
        'xqly.workbench.session.create', 'workbench', 1000, false,
        allowedKeys: <String>{'id', 'title'}, requiredKeys: <String>{'id', 'title'}),
    'xqly.workbench.session.append': MethodSpec(
        'xqly.workbench.session.append', 'workbench', 1000, false,
        allowedKeys: <String>{'id', 'role', 'text'},
        requiredKeys: <String>{'id', 'role', 'text'}),
    'xqly.workbench.session.remove': MethodSpec(
        'xqly.workbench.session.remove', 'workbench', 1000, false,
        allowedKeys: <String>{'id'}, requiredKeys: <String>{'id'}),
    'xqly.workbench.provider.list': MethodSpec(
        'xqly.workbench.provider.list', 'workbench', 1000, true),
    // 只登记「已配置」布尔状态；allowedKeys 中绝无密钥/令牌字段。
    'xqly.workbench.provider.mark': MethodSpec(
        'xqly.workbench.provider.mark', 'workbench', 1000, false,
        allowedKeys: <String>{'id', 'configured'}, requiredKeys: <String>{'id'}),
    'xqly.workbench.plugin.list': MethodSpec(
        'xqly.workbench.plugin.list', 'workbench', 1000, true),
    'xqly.workbench.plugin.register': MethodSpec(
        'xqly.workbench.plugin.register', 'workbench', 1000, false,
        allowedKeys: <String>{'id', 'displayName', 'source', 'version'},
        requiredKeys: <String>{'id', 'displayName', 'source', 'version'}),
    'xqly.workbench.plugin.toggle': MethodSpec(
        'xqly.workbench.plugin.toggle', 'workbench', 1000, false,
        allowedKeys: <String>{'id', 'enabled'}, requiredKeys: <String>{'id', 'enabled'}),
    'xqly.workbench.dropzone.check': MethodSpec(
        'xqly.workbench.dropzone.check', 'workbench', 1000, true,
        allowedKeys: <String>{'raw'}, requiredKeys: <String>{'raw'}),
    'xqly.workbench.selfcheck': MethodSpec(
        'xqly.workbench.selfcheck', 'workbench', 1000, true),
    'xqly.workbench.log.append': MethodSpec(
        'xqly.workbench.log.append', 'workbench', 1000, false,
        allowedKeys: <String>{'level', 'code', 'text'},
        requiredKeys: <String>{'level', 'code', 'text'}),
    'xqly.workbench.log.query': MethodSpec(
        'xqly.workbench.log.query', 'workbench', 1000, true,
        allowedKeys: <String>{'level', 'keyword', 'tail'}),
  };

  /// 每个合法桥应用对应的作用域；未知 appId 返回 null。
  static String? scopeFor(String appId) {
    if (appId == AppRegistry.devPortalId) return 'devportal:*';
    if (appId == AppRegistry.aiWorkbenchId) return 'workbench:*';
    return null;
  }

  /// 任何方法都不允许携带的键片段（小写包含匹配）。
  static const Set<String> forbiddenFragments = <String>{
    'token', 'secret', 'password', 'apikey', 'api_key', 'private',
    'imei', 'imsi', 'deviceid', 'cookie', 'authorization',
    'cmd', 'exec', 'script', 'install', 'adb', 'keystore', 'dump', 'shell',
  };

  /// 受控键：只允许特定方法，且必须经过专项校验。
  static const Set<String> restrictedKeys = <String>{'url', 'path', 'appId'};

  static CallResult validate(
    String? raw, {
    required Set<String> permissions,
    Set<String>? seenIds,
    required int nowMs,
    required Map<String, int> lastCallAt,
    required bool online,
    required bool telemetryOptIn,
    required bool approvalGranted,
    Set<String> endpointAllowlist = const <String>{},
  }) {
    if (raw == null || raw.isEmpty || raw.length > maxMessage) {
      return const CallResult.bad(RejectV1.tooLarge);
    }
    final Map<String, Object?> m;
    try {
      m = Json.object(raw);
    } catch (_) {
      return const CallResult.bad(RejectV1.badJson);
    }

    if (Json.str(m, 'bridge', '') != version) {
      return const CallResult.bad(RejectV1.badVersion);
    }
    // 越界 appId：只接受已登记且作用域匹配的应用（dev-portal / ai-workbench）。
    final String appId = Json.str(m, 'appId', '');
    final String? scope = scopeFor(appId);
    if (scope == null || !AppRegistry.scopeAllows(appId, scope)) {
      return const CallResult.bad(RejectV1.foreignApp);
    }

    final String method = Json.str(m, 'method', '');
    final MethodSpec? spec = methods[method];
    if (spec == null) return const CallResult.bad(RejectV1.unknownMethod);

    final String id = Json.str(m, 'id', '');
    if (id.isEmpty || id.length > maxIdLen ||
        !RegExp(r'^[A-Za-z0-9_-]+$').hasMatch(id)) {
      return const CallResult.bad(RejectV1.badId);
    }
    if (seenIds != null && !seenIds.add(id)) {
      return const CallResult.bad(RejectV1.duplicateId);
    }

    final Map<String, Object?> params = Json.obj(m, 'params');
    if (params.length > maxParams || !_paramsValid(spec, params, endpointAllowlist)) {
      return const CallResult.bad(RejectV1.badParams);
    }
    for (final String k in params.keys) {
      final String lk = k.toLowerCase();
      if (lk.length > 32) return const CallResult.bad(RejectV1.badParams);
      for (final String f in forbiddenFragments) {
        if (lk.contains(f)) return const CallResult.bad(RejectV1.forbiddenKey);
      }
    }
    if (!_restrictedValid(spec, params)) {
      return const CallResult.bad(RejectV1.restrictedKey);
    }

    if (!permissions.contains(spec.permission)) {
      return const CallResult.bad(RejectV1.permissionDenied);
    }
    final int last = lastCallAt[method] ?? -minCallIntervalMs - 1;
    if (nowMs - last < minCallIntervalMs) {
      return const CallResult.bad(RejectV1.rateLimited);
    }
    lastCallAt[method] = nowMs;

    if (spec.network && !online) {
      return const CallResult.bad(RejectV1.offlineUnavailable);
    }
    if (method == 'xqly.telemetry.report' && !telemetryOptIn) {
      return const CallResult.bad(RejectV1.telemetryOff);
    }
    if (spec.requiresApproval && !approvalGranted) {
      return const CallResult.bad(RejectV1.approvalRequired);
    }
    return CallResult.ok(method, id, params, spec);
  }

  static bool _paramsValid(
      MethodSpec spec, Map<String, Object?> p, Set<String> allowlist) {
    for (final MapEntry<String, Object?> e in p.entries) {
      if (!spec.allowedKeys.contains(e.key)) return false;
      final Object? v = e.value;
      if (v is String && v.length > maxParamLen) return false;
      if (v is num && !v.isFinite) return false;
      if (v is Map && v.length > maxParams) return false;
      if (v is List && v.length > 64) return false;
    }
    for (final String k in spec.requiredKeys) {
      if (!p.containsKey(k)) return false;
    }
    // 方法级 schema
    if (spec.name == 'xqly.ui.setTitle') {
      final Object? t = p['title'];
      return t is String && t.isNotEmpty && t.length <= 40;
    }
    if (spec.name == 'xqly.ui.showFeedback') {
      final Object? lv = p['level'];
      if (lv is! String ||
          !const <String>['info', 'success', 'warning', 'error'].contains(lv)) {
        return false;
      }
      if (p.containsKey('durationMs')) {
        final Object? d = p['durationMs'];
        if (d is! num || d < 0 || d > 30000) return false;
      }
      if (p.containsKey('action')) {
        final Object? a = p['action'];
        if (a is! String || a.length > 2) return false;
      }
    }
    if (spec.name == 'xqly.link.open') {
      final Object? u = p['url'];
      return u is String && EndpointPolicy.linkAllowed(u);
    }
    if (spec.name == 'xqly.nav.open') {
      final Object? a = p['appId'];
      if (a is! String || !AppRegistry.registered(a)) return false;
      if (p.containsKey('path')) {
        final Object? path = p['path'];
        if (path is! String || path.length > 128 || path.contains('..')) return false;
      }
    }
    if (spec.name == 'xqly.credential.getEndpoint' ||
        spec.name == 'xqly.devportal.connect') {
      final Object? e = p['endpointId'];
      return e is String &&
          RegExp(r'^[A-Za-z0-9_-]{1,64}$').hasMatch(e) &&
          allowlist.contains(e);
    }
    if (spec.name == 'xqly.devportal.approveTool') {
      // scope 是工具作用域，grant 才是 once/session/deny 的审批决定；两者不可混用。
      final Object? s = p['scope'];
      if (s is! String || !RegExp(r'^[A-Za-z0-9_.-]{1,64}$').hasMatch(s)) {
        return false;
      }
      final Object? g = p['grant'];
      if (g != null &&
          (g is! String || !const <String>['once', 'session', 'deny'].contains(g))) {
        return false;
      }
      final Object? t = p['toolId'];
      return t is String && RegExp(r'^[A-Za-z0-9_.-]{1,64}$').hasMatch(t);
    }
    if (spec.name == 'xqly.error.report') {
      // 只接受哈希与脱敏轨迹，不接受原文。
      final Object? h = p['messageHash'];
      return h == null ||
          (h is String && RegExp(r'^[0-9a-f]{64}$').hasMatch(h));
    }
    if (spec.name == 'xqly.file.pickSave') {
      if (p.containsKey('suggestedName')) {
        final Object? n = p['suggestedName'];
        if (n is! String || n.length > 96 || n.contains('/') || n.contains('\\')) {
          return false;
        }
      }
    }
    // ---- AI 工作台方法级 schema ----
    if (spec.name == 'xqly.workbench.snapshot' && p.containsKey('freeMb')) {
      final Object? f = p['freeMb'];
      if (f is! num || f < 0 || f > 1024 * 1024) return false;
    }
    if (spec.name == 'xqly.workbench.session.create') {
      final Object? i = p['id'];
      final Object? t = p['title'];
      return i is String && RegExp(r'^[A-Za-z0-9_-]{1,48}$').hasMatch(i) &&
          t is String && t.isNotEmpty && t.length <= 80;
    }
    if (spec.name == 'xqly.workbench.session.append') {
      final Object? i = p['id'];
      final Object? role = p['role'];
      final Object? text = p['text'];
      return i is String && RegExp(r'^[A-Za-z0-9_-]{1,48}$').hasMatch(i) &&
          role is String && const <String>['user', 'draft', 'note'].contains(role) &&
          text is String && text.isNotEmpty && text.length <= 2048;
    }
    if (spec.name == 'xqly.workbench.session.remove') {
      final Object? i = p['id'];
      return i is String && RegExp(r'^[A-Za-z0-9_-]{1,48}$').hasMatch(i);
    }
    if (spec.name == 'xqly.workbench.provider.mark') {
      final Object? i = p['id'];
      if (i is! String || !WorkbenchProvider.validId(i)) return false;
      if (p.containsKey('configured') && p['configured'] is! bool) return false;
      return true;
    }
    if (spec.name == 'xqly.workbench.plugin.register') {
      final Object? i = p['id'];
      final Object? s = p['source'];
      final Object? v = p['version'];
      return i is String && RegExp(r'^[A-Za-z0-9]([A-Za-z0-9._-]{0,62}[A-Za-z0-9])?$').hasMatch(i) &&
          s is String && WorkbenchPlugin.sources.contains(s) &&
          v is String && RegExp(r'^\d{1,4}\.\d{1,4}\.\d{1,4}$').hasMatch(v);
    }
    if (spec.name == 'xqly.workbench.plugin.toggle') {
      final Object? i = p['id'];
      return i is String && RegExp(r'^[A-Za-z0-9]([A-Za-z0-9._-]{0,62}[A-Za-z0-9])?$').hasMatch(i) &&
          p['enabled'] is bool;
    }
    if (spec.name == 'xqly.workbench.dropzone.check') {
      final Object? raw = p['raw'];
      return raw is String && raw.isNotEmpty && raw.length <= 192;
    }
    if (spec.name == 'xqly.workbench.log.append') {
      final Object? lv = p['level'];
      final Object? code = p['code'];
      final Object? text = p['text'];
      return lv is String && const <String>['info', 'success', 'warning', 'error'].contains(lv) &&
          code is String && code.length <= 48 &&
          text is String && text.isNotEmpty && text.length <= 2048;
    }
    if (spec.name == 'xqly.workbench.log.query') {
      if (p.containsKey('level')) {
        final Object? lv = p['level'];
        if (lv is! String ||
            !const <String>['info', 'success', 'warning', 'error'].contains(lv)) return false;
      }
      if (p.containsKey('keyword') && p['keyword'] is! String) return false;
      if (p.containsKey('tail')) {
        final Object? t = p['tail'];
        if (t is! num || t < 1 || t > 256) return false;
      }
      return true;
    }
    return true;
  }

  static bool _restrictedValid(MethodSpec spec, Map<String, Object?> p) {
    for (final String k in p.keys) {
      if (!restrictedKeys.contains(k)) continue;
      if (k == 'url' && spec.name != 'xqly.link.open') return false;
      if (k == 'path' && spec.name != 'xqly.nav.open') return false;
      if (k == 'appId' && spec.name != 'xqly.nav.open') return false;
    }
    return true;
  }

  /// 禁止同步返回密钥、令牌、完整设备标识或任意文件路径。
  /// 供原生构造 success 响应时做最后一道闸门。
  static bool responseSafe(String payload) {
    if (payload.isEmpty || payload.length > maxMessage) return false;
    final String low = payload.toLowerCase();
    for (final String f in <String>[
      'imei', 'imsi', 'deviceid', 'android_id', 'authorization', 'bearer ',
      'private_key', 'api_key', 'apikey', 'file:///data', '/data/user/',
      '/storage/emulated', 'content://',
    ]) {
      if (low.contains(f)) return false;
    }
    // 64 位连续 hex 很可能是密钥 / 令牌原文（哈希上报只允许 messageHash 字段）。
    if (RegExp(r'[0-9a-f]{64}').hasMatch(low) &&
        !low.contains('messagehash') && !low.contains('digest')) {
      return false;
    }
    return true;
  }

  /// 日志 / 崩溃 / 导出统一脱敏：只保留结构，敏感值替换为 ***。
  static String redact(String text) {
    String s = text;
    // replaceAll 的替换串不做捕获组引用；用 replaceAllMapped 保留键名、抹掉值。
    s = s.replaceAllMapped(
        RegExp(r'"(token|key|secret|password|apiKey|api_key|authorization|cookie|private_key)"\s*:\s*"[^"]*"',
            caseSensitive: false),
        (Match m) => '"${m.group(1)}":"***"');
    s = s.replaceAll(RegExp(r'\b[0-9a-fA-F]{40,}\b'), '***');
    // 用 \x27 表示单引号，避免 raw 单引号字符串被 \' 提前终止。
    s = s.replaceAll(RegExp(r'(file|content)://[^\s"\x27<]+'), '***');
    return s;
  }

  static Map<String, Object?> permissionMatrix() => <String, Object?>{
        for (final MapEntry<String, MethodSpec> e in methods.entries)
          e.key: <String, Object?>{
            'permission': e.value.permission,
            'timeoutMs': e.value.timeoutMs,
            'idempotent': e.value.idempotent,
            'requiresApproval': e.value.requiresApproval,
            'network': e.value.network,
          },
      };
}
