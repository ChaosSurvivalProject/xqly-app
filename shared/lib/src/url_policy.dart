import 'identity.dart';

enum UrlKind { site, localAsset, external, blocked }

/// 严格协议 / 主机 / 路径白名单。
///
/// WebView（Android）与 WebView2（Windows）共用同一套判定，
/// 桥注入只发生在 [bridgeAllowed] 为真的页面上。
class UrlPolicy {
  UrlPolicy._();

  static const String localAssetPrefix = 'file:///android_asset/';

  static const List<String> sitePathPrefixes = <String>[
    '/', '/home', '/wiki', '/forum', '/team', '/monitor', '/kb',
    '/article', '/post', '/category', '/tag', '/search', '/about',
    '/assets', '/static', '/api',
  ];

  /// 允许由内置页面加载的本地资源（其余内置文件不给网页读取）。
  static const List<String> localResources = <String>[
    'game-engine.js',
    'bridge-shim.js',
    'library.css',
    'game.css',
    'design-tokens.css',
  ];

  static const List<String> localPages = <String>[
    'archive.html',
    'game.html',
    'library.html',
  ];

  static UrlKind classify(String? raw) {
    if (raw == null || raw.isEmpty || raw.length > 4096) {
      return UrlKind.blocked;
    }
    final String s = raw.trim();
    final String low = s.toLowerCase();

    // 危险协议一律拒绝。
    if (low.startsWith('javascript:') ||
        low.startsWith('data:') ||
        low.startsWith('content:') ||
        low.startsWith('blob:') ||
        low.startsWith('about:') ||
        low.startsWith('chrome') ||
        low.startsWith('file:') && !low.startsWith(localAssetPrefix)) {
      return UrlKind.blocked;
    }

    if (low.startsWith(localAssetPrefix)) {
      final String path = s.substring(localAssetPrefix.length);
      return safeAssetPath(path) ? UrlKind.localAsset : UrlKind.blocked;
    }

    // 官方站点只允许 https，且拒绝 userinfo / 非标准端口 / 路径穿越。
    if (low.startsWith('https://${AppIdentity.siteHost}/') ||
        low == 'https://${AppIdentity.siteHost}') {
      final Uri? u = Uri.tryParse(s);
      if (u == null || u.userInfo.isNotEmpty) return UrlKind.blocked;
      if (u.hasPort && u.port != 443) return UrlKind.blocked;
      if (s.contains('..') || s.contains('\\') || s.contains('\u0000')) {
        return UrlKind.blocked;
      }
      return UrlKind.site;
    }
    // 明文官方站、子域名伪装、相似域名全部拒绝。
    if (low.startsWith('http://${AppIdentity.siteHost}') ||
        low.contains(AppIdentity.siteHost)) {
      return UrlKind.blocked;
    }

    if (low.startsWith('https://')) {
      final Uri? u = Uri.tryParse(s);
      if (u == null || u.userInfo.isNotEmpty) return UrlKind.blocked;
      return UrlKind.external;
    }
    return UrlKind.blocked;
  }

  /// 只有官方站点与内置页面允许注入桥。
  static bool bridgeAllowed(String? url) {
    final UrlKind k = classify(url);
    if (k != UrlKind.site && k != UrlKind.localAsset) return false;
    if (k == UrlKind.localAsset) {
      final String p = url!.substring(localAssetPrefix.length);
      final String base = p.contains('/') ? p.substring(p.lastIndexOf('/') + 1) : p;
      return localPages.contains(base);
    }
    return true;
  }

  static bool sitePathAllowed(String path) {
    if (path.isEmpty || path.length > 512) return false;
    if (!path.startsWith('/') || path.contains('..') || path.contains('\u0000')) {
      return false;
    }
    for (final String p in sitePathPrefixes) {
      if (path == p || path.startsWith('$p/') || path.startsWith('$p?')) {
        return true;
      }
    }
    return false;
  }

  static bool localResourceAllowed(String? raw) {
    if (raw == null) return false;
    String p = raw;
    if (p.startsWith(localAssetPrefix)) {
      p = p.substring(localAssetPrefix.length);
    }
    final String base = p.contains('/') ? p.substring(p.lastIndexOf('/') + 1) : p;
    return localResources.contains(base) && safeAssetPath(p);
  }

  /// 防 ZIP 路径穿越：拒绝绝对、回退、反斜杠、盘符、控制字符与超长路径。
  static bool safeAssetPath(String? p) {
    if (p == null || p.isEmpty || p.length > 512) return false;
    if (p.startsWith('/') || p.startsWith('\\')) return false;
    if (p.contains('..') || p.contains('\\') || p.contains('\u0000')) {
      return false;
    }
    if (RegExp(r'^[A-Za-z]:').hasMatch(p)) return false;
    if (p.contains('%2e') || p.contains('%2f') || p.contains('%5c')) {
      return false;
    }
    for (int i = 0; i < p.length; i++) {
      final int c = p.codeUnitAt(i);
      if (c < 0x20) return false;
    }
    return true;
  }

  static bool downloadHostAllowed(String? url) {
    if (url == null) return false;
    for (final String h in AppIdentity.downloadHosts) {
      if (url.startsWith('https://$h/')) return true;
    }
    return false;
  }
}
