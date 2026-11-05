import 'foundation.dart';
import 'identity.dart';
import 'url_policy.dart';

enum Reject {
  tooLarge,
  badJson,
  badVersion,
  badOrigin,
  untrustedOrigin,
  sameOriginMismatch,
  unknownType,
  badArgs,
  forbiddenKey,
  duplicateId,
  permissionDenied,
}

/// 校验通过的消息。
class BridgeMessage {
  final String type;
  final Map<String, Object?> args;
  final String id;
  final String origin;

  const BridgeMessage(this.type, this.args, this.id, this.origin);
}

class BridgeResult {
  final bool ok;
  final Reject? reject;
  final BridgeMessage? message;

  const BridgeResult.ok(this.message) : ok = true, reject = null;
  const BridgeResult.bad(this.reject) : ok = false, message = null;
}

/// 版本化 postMessage 桥的**唯一**校验入口。
///
/// 校验链：长度 → 严格 JSON → 版本 → origin 格式 → 页面可信 →
/// 页面与 origin 同源 → 类型白名单 → 参数白名单/类型/长度 →
/// 危险键 → 权限 → 幂等 id。
///
/// [pageOrigin] 是宿主在 onPageStarted 记录的**实际发起页面 URL**，
/// 用于修复「任一 WebView 可信即放行」的来源混淆缺陷。
class BridgeProtocol {
  BridgeProtocol._();

  static const int maxMessage = 8192;
  static const int maxArgLen = 2048;
  static const int maxArgs = 12;

  static const Set<String> types = <String>{
    'openPage',
    'closeOverlay',
    'scroll',
    'search',
    'favorite',
    'progress',
    'getState',
    'gameSave',
    'gameLoad',
    'syncPreview',
    'syncApply',
    'syncCancel',
    'windowCommand',
  };

  static const Set<String> writeTypes = <String>{
    'favorite',
    'progress',
    'gameSave',
    'syncApply',
    'syncCancel',
    'windowCommand',
  };

  /// 永远不允许从页面传入的键。
  /// 注意：`path` 不在此列——`openPage` 合法使用它，且已由
  /// [UrlPolicy.sitePathAllowed] 做严格白名单校验，不会变成任意文件路径。
  static const Set<String> forbiddenKeys = <String>{
    'token', 'password', 'cookie', 'file', 'url', 'cmd', 'exec',
    'install', 'script', 'registry', 'process', 'keystore',
  };

  static BridgeResult validate(
    String? raw, {
    required bool trustedPage,
    required String pageOrigin,
    Set<String>? seenIds,
    required bool allowWrite,
  }) {
    if (raw == null || raw.isEmpty || raw.length > maxMessage) {
      return const BridgeResult.bad(Reject.tooLarge);
    }

    final Map<String, Object?> m;
    try {
      m = Json.object(raw);
    } catch (_) {
      return const BridgeResult.bad(Reject.badJson);
    }

    final int v = Json.integer(m, 'v', -1);
    if (v != AppIdentity.bridgeVersion) {
      return const BridgeResult.bad(Reject.badVersion);
    }

    final String origin = Json.str(m, 'origin', '');
    if (origin.isEmpty || origin.length > 256 ||
        (UrlPolicy.classify(origin) != UrlKind.site &&
            UrlPolicy.classify(origin) != UrlKind.localAsset)) {
      return const BridgeResult.bad(Reject.badOrigin);
    }
    if (!trustedPage) {
      return const BridgeResult.bad(Reject.untrustedOrigin);
    }
    // 页面自称的 origin 必须与宿主记录的真实页面一致（或同为该内置页面）。
    if (!_sameOrigin(origin, pageOrigin)) {
      return const BridgeResult.bad(Reject.sameOriginMismatch);
    }

    final String type = Json.str(m, 'type', '');
    if (!types.contains(type)) {
      return const BridgeResult.bad(Reject.unknownType);
    }

    final Map<String, Object?> args = Json.obj(m, 'args');
    if (args.length > maxArgs || !_argsValid(type, args)) {
      return const BridgeResult.bad(Reject.badArgs);
    }

    final String id = Json.str(m, 'id', '');
    if (id.isEmpty || id.length > 64 || !RegExp(r'^[A-Za-z0-9_-]+$').hasMatch(id)) {
      return const BridgeResult.bad(Reject.badArgs);
    }
    if (seenIds != null && !seenIds.add(id)) {
      return const BridgeResult.bad(Reject.duplicateId);
    }

    if (writeTypes.contains(type) && !allowWrite) {
      return const BridgeResult.bad(Reject.permissionDenied);
    }

    return BridgeResult.ok(BridgeMessage(type, args, id, origin));
  }

  static bool _argsValid(String type, Map<String, Object?> a) {
    for (final MapEntry<String, Object?> e in a.entries) {
      final String k = e.key;
      if (forbiddenKeys.contains(k.toLowerCase()) || k.length > 64) return false;
      final Object? v = e.value;
      if (v is String && v.length > maxArgLen) return false;
      if (v is num && !v.isFinite) return false;
      if (v is Map && v.length > maxArgs) return false;
      if (v is List && v.length > 256) return false;
    }
    // openPage 只能打开白名单路径；windowCommand 只接受固定命令集。
    if (type == 'openPage') {
      final Object? p = a['path'];
      return p is String && UrlPolicy.sitePathAllowed(p);
    }
    if (type == 'windowCommand') {
      final Object? c = a['command'];
      return c is String &&
          const <String>['newTab', 'closeTab', 'nextTab', 'prevTab', 'restoreTab',
            'focusSearch', 'openSettings', 'togglePause'].contains(c);
    }
    return true;
  }

  static bool _sameOrigin(String origin, String pageOrigin) {
    if (origin == pageOrigin) return true;
    final UrlKind a = UrlPolicy.classify(origin);
    final UrlKind b = UrlPolicy.classify(pageOrigin);
    if (a == UrlKind.site && b == UrlKind.site) {
      final Uri? ua = Uri.tryParse(origin);
      final Uri? ub = Uri.tryParse(pageOrigin);
      return ua != null && ub != null && ua.host == ub.host && ua.scheme == ub.scheme;
    }
    // 内置页面：必须精确匹配文件名，防止 archive.html 冒用 game.html。
    if (a == UrlKind.localAsset && b == UrlKind.localAsset) {
      return _base(origin) == _base(pageOrigin) &&
          UrlPolicy.localPages.contains(_base(origin));
    }
    return false;
  }

  static String _base(String url) {
    final String p = url.startsWith(UrlPolicy.localAssetPrefix)
        ? url.substring(UrlPolicy.localAssetPrefix.length)
        : url;
    return p.contains('/') ? p.substring(p.lastIndexOf('/') + 1) : p;
  }
}
