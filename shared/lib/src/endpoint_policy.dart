import 'apps.dart';

/// 远程连接允许的传输方式。明文 HTTP / WS 一律禁止。
enum Transport { wss, sse, https }

/// 用户显式保存的一条远程 endpoint。
/// 注意：这里**只保存主机配置，绝不保存任何密钥 / Token / 凭据**。
class Endpoint {
  final String id;
  final String host;
  final int port;
  final Transport transport;
  final String label;

  /// 可选证书 / 公钥 pin（小写 hex，无冒号）；为空表示不启用 pin。
  final String pinSha256;

  const Endpoint({
    required this.id,
    required this.host,
    this.port = 443,
    this.transport = Transport.wss,
    this.label = '',
    this.pinSha256 = '',
  });
}

enum EndpointReject {
  none,
  emptyHost,
  badHost,
  badPort,
  privateNetwork,
  loopback,
  linkLocal,
  wildcard,
  badTransport,
  badPin,
  badId,
  tooMany,
  notAllowlisted,
}

class EndpointVerdict {
  final bool ok;
  final EndpointReject reject;
  const EndpointVerdict.ok() : ok = true, reject = EndpointReject.none;
  const EndpointVerdict.bad(this.reject) : ok = false;
  String get reason => reject.name;
}

/// 网络分段代理与 Deep Link 白名单。
///
/// 硬约束：
///  - WebView 默认禁止 `0.0.0.0/0` 与链路本地端口扫描；
///  - 只能连用户保存的 endpoint 或预设代理；
///  - 明文 HTTP 默认禁止；密钥由系统凭证库保存，不经过本页面配置。
class EndpointPolicy {
  EndpointPolicy._();

  static const int maxHostLen = 253;
  static const int maxEndpoints = 16;
  static const int maxEndpointIdLen = 64;
  static const Set<int> allowedPorts = <int>{443, 8443};

  /// 仅允许两类 Deep Link，路径归一化、禁 `..`、查询参数白名单。
  static const List<String> allowedDeepLinkPaths = <String>[
    'xqly://app/dev-portal',
    'xqly://app/dev-portal/session/{id}',
  ];
  static const Set<String> allowedQueryKeys = <String>{'view', 'focus'};

  static const Set<String> blockedSchemes = <String>{
    'javascript', 'file', 'intent', 'data', 'blob', 'about', 'content', 'chrome',
  };

  static bool privateNetwork(String host) {
    final String h = host.toLowerCase();
    if (h.startsWith('10.') || h.startsWith('192.168.')) return true;
    if (h.startsWith('172.')) {
      final List<String> p = h.split('.');
      if (p.length >= 2) {
        final int? second = int.tryParse(p[1]);
        if (second != null && second >= 16 && second <= 31) return true;
      }
    }
    if (h.startsWith('127.') || h == '0.0.0.0' || h == '::1' || h == '[::1]') {
      return true;
    }
    if (h.startsWith('169.254.') || h.startsWith('fe80:') || h.startsWith('fc') ||
        h.startsWith('fd') || h == 'localhost' || h.endsWith('.local') ||
        h.endsWith('.internal')) {
      return true;
    }
    return false;
  }

  static bool hostAllowed(String host) {
    if (host.isEmpty || host.length > maxHostLen) return false;
    if (!RegExp(r'^[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?$').hasMatch(host)) return false;
    if (host.contains('..') || host.startsWith('.') || host.contains('\u0000')) {
      return false;
    }
    // 纯 IPv4 / 通配 / 私网一律不允许直接在 WebView 内连接。
    if (RegExp(r'^[0-9.]+$').hasMatch(host)) return false;
    if (host.contains('*') || host.contains('[') || host.contains(']')) return false;
    if (privateNetwork(host)) return false;
    return true;
  }

  /// 校验一条用户配置；allowlist 为空 = 未配置 = 一律拒绝（不默认连任何服务）。
  static EndpointVerdict validate(Endpoint e, Set<String> allowlist) {
    if (e.id.isEmpty || e.id.length > maxEndpointIdLen ||
        !RegExp(r'^[A-Za-z0-9_-]+$').hasMatch(e.id)) {
      return const EndpointVerdict.bad(EndpointReject.badId);
    }
    if (allowlist.length > maxEndpoints) {
      return const EndpointVerdict.bad(EndpointReject.tooMany);
    }
    if (!allowlist.contains(e.id)) {
      return const EndpointVerdict.bad(EndpointReject.notAllowlisted);
    }
    if (e.host.isEmpty) return const EndpointVerdict.bad(EndpointReject.emptyHost);
    // 先判私网再判主机格式，保证拒绝原因准确（否则纯 IP 会被 BAD_HOST 抢先吞掉）。
    if (privateNetwork(e.host)) {
      return const EndpointVerdict.bad(EndpointReject.privateNetwork);
    }
    if (!hostAllowed(e.host)) return const EndpointVerdict.bad(EndpointReject.badHost);
    if (!allowedPorts.contains(e.port)) {
      return const EndpointVerdict.bad(EndpointReject.badPort);
    }
    if (e.transport == Transport.https && e.port != 443 && e.port != 8443) {
      return const EndpointVerdict.bad(EndpointReject.badTransport);
    }
    if (e.pinSha256.isNotEmpty && !RegExp(r'^[0-9a-f]{64}$').hasMatch(e.pinSha256)) {
      return const EndpointVerdict.bad(EndpointReject.badPin);
    }
    return const EndpointVerdict.ok();
  }

  /// 链接打开：仅 HTTPS 或已登记的 Deep Link；禁 javascript:/file:/intent: 等越权协议。
  static bool linkAllowed(String url) {
    if (url.isEmpty || url.length > 2048) return false;
    final String low = url.toLowerCase();
    for (final String s in blockedSchemes) {
      if (low.startsWith('$s:')) return false;
    }
    if (low.startsWith('https://')) {
      final Uri? u = Uri.tryParse(url);
      if (u == null || u.userInfo.isNotEmpty || u.host.isEmpty) return false;
      return hostAllowed(u.host);
    }
    if (low.startsWith('$AppRegistry.deepLinkScheme://')) {
      return deepLinkAllowed(url);
    }
    return false;
  }

  /// 路径归一化：先去 fragment，再拒绝 `..`、反斜杠、控制字符与百分号编码绕过。
  static String normalizePath(String raw) {
    String p = raw;
    final int hash = p.indexOf('#');
    if (hash >= 0) p = p.substring(0, hash);
    p = p.replaceAll('\\', '/');
    // 只归一化 scheme 之后的路径：若把 "xqly://" 的两个斜杠也折叠成 "xqly:/"，
    // 所有 Deep Link 都会因路径不匹配而被误拒。
    final int s = p.indexOf('://');
    final String head = s >= 0 ? p.substring(0, s + 3) : '';
    String tail = s >= 0 ? p.substring(s + 3) : p;
    while (tail.contains('//')) tail = tail.replaceAll('//', '/');
    return head + tail;
  }

  static bool deepLinkAllowed(String raw) {
    if (raw.isEmpty || raw.length > 512) return false;
    final String low = raw.toLowerCase();
    if (low.contains('..') || low.contains('%2e') || low.contains('%2f') ||
        low.contains('%5c') || low.contains('\u0000') || raw.contains('\\')) {
      return false;
    }
    final int q = raw.indexOf('?');
    final String path = normalizePath(q < 0 ? raw : raw.substring(0, q));
    if (path == 'xqly://app/dev-portal') {
      return q < 0 || queryAllowed(raw.substring(q + 1));
    }
    if (path.startsWith('xqly://app/dev-portal/session/')) {
      final String id = path.substring('xqly://app/dev-portal/session/'.length);
      if (!RegExp(r'^[A-Za-z0-9_-]{1,64}$').hasMatch(id)) return false;
      return q < 0 || queryAllowed(raw.substring(q + 1));
    }
    return false;
  }

  static String? sessionIdFrom(String raw) {
    if (!deepLinkAllowed(raw)) return null;
    final int q = raw.indexOf('?');
    final String path = normalizePath(q < 0 ? raw : raw.substring(0, q));
    const String pre = 'xqly://app/dev-portal/session/';
    return path.startsWith(pre) ? path.substring(pre.length) : null;
  }

  static bool queryAllowed(String query) {
    if (query.isEmpty || query.length > 256) return false;
    for (final String pair in query.split('&')) {
      if (pair.isEmpty) return false;
      final int eq = pair.indexOf('=');
      final String k = eq < 0 ? pair : pair.substring(0, eq);
      final String v = eq < 0 ? '' : pair.substring(eq + 1);
      if (!allowedQueryKeys.contains(k)) return false;
      if (v.length > 64 || !RegExp(r'^[A-Za-z0-9_-]*$').hasMatch(v)) return false;
    }
    return true;
  }

  /// 默认零遥测：关闭时不发起任何第三方请求。
  static const bool telemetryDefault = false;
}
