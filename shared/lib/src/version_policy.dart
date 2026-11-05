import 'identity.dart';
import 'sha256.dart';

/// 版本判定结果。
enum VDecision { upgrade, same, downgrade, malformed }

/// 版本字符串解析与升降级策略。
///
/// 规则与 Android `VersionPolicy` 一致：允许 `v` 前缀，按数字段比较，
/// 缺段按 0 补齐；预发布后缀（`-beta` 等）一律视为非法，避免绕过非降级检查。
class VersionPolicy {
  VersionPolicy._();

  static List<int>? parse(String? raw) {
    if (raw == null) return null;
    String s = raw.trim().toLowerCase();
    if (s.startsWith('v')) s = s.substring(1);
    if (s.isEmpty || s.length > 32) return null;
    if (!RegExp(r'^[0-9]+(\.[0-9]+)*$').hasMatch(s)) return null;
    final List<int> parts = <int>[];
    for (final String p in s.split('.')) {
      if (p.length > 9) return null;
      final int? n = int.tryParse(p);
      if (n == null) return null;
      parts.add(n);
    }
    if (parts.length > 6) return null;
    return parts;
  }

  /// 返回 -1 / 0 / 1；任一非法则按字符串比较给出稳定结果（但 decide 会拒绝）。
  static int compare(String? a, String? b) {
    final List<int>? x = parse(a);
    final List<int>? y = parse(b);
    if (x == null || y == null) {
      return (a ?? '').compareTo(b ?? '');
    }
    final int n = x.length > y.length ? x.length : y.length;
    for (int i = 0; i < n; i++) {
      final int xv = i < x.length ? x[i] : 0;
      final int yv = i < y.length ? y[i] : 0;
      if (xv != yv) return xv < yv ? -1 : 1;
    }
    return 0;
  }

  static bool valid(String? s) => parse(s) != null;

  /// 综合 tag 与 versionCode 判定。versionCode 优先，防止 tag 相同但 code 回退。
  static VDecision decide(String candidateTag, int candidateCode,
      String currentName, int currentCode) {
    if (!valid(candidateTag) || !valid(currentName)) {
      return VDecision.malformed;
    }
    if (candidateCode <= 0) return VDecision.malformed;
    if (candidateCode < currentCode) return VDecision.downgrade;
    if (candidateCode > currentCode) return VDecision.upgrade;
    final int c = compare(candidateTag, currentName);
    if (c < 0) return VDecision.downgrade;
    if (c == 0) return VDecision.same;
    return VDecision.upgrade;
  }

  /// 资产名必须与 tag 表示同一版本（OTA 发现逻辑）。
  static bool validAssetName(String? name, String? tag) {
    if (name == null || tag == null) return false;
    if (!name.startsWith(AppIdentity.apkAssetPrefix) ||
        !name.endsWith(AppIdentity.apkAssetSuffix)) {
      return false;
    }
    final String version = name.substring(
      AppIdentity.apkAssetPrefix.length,
      name.length - AppIdentity.apkAssetSuffix.length,
    );
    return compare(version, tag) == 0;
  }

  static String? normalizeDigest(String? raw) => normalizeSha256(raw);
}
