import 'foundation.dart';
import 'identity.dart';
import 'sha256.dart';
import 'url_policy.dart';
import 'version_policy.dart';

enum ParseError {
  ok,
  badJson,
  notOfficial,
  draftOrPrerelease,
  noTag,
  badAssetHost,
  noValidAsset,
}

class ReleaseAsset {
  final String name;
  final String url;
  final String digest;
  final int size;

  const ReleaseAsset(this.name, this.url, this.digest, this.size);
}

class ReleaseInfo {
  final String tag;
  final String name;
  final String body;
  final String htmlUrl;
  final ReleaseAsset asset;

  const ReleaseInfo(this.tag, this.name, this.body, this.htmlUrl, this.asset);
}

class ParseResult {
  final ParseError error;
  final ReleaseInfo? release;

  const ParseResult(this.error, this.release);
  bool get ok => error == ParseError.ok && release != null;
}

/// 安装前实测值（由各平台外壳填充，规则判定保持平台无关）。
class Measured {
  final int bytes;
  final String fileSha256;
  final String signingCertSha256;
  final String apkVersionName;
  final int apkVersionCode;

  const Measured(this.bytes, this.fileSha256, this.signingCertSha256,
      this.apkVersionName, this.apkVersionCode);
}

class Check {
  final String key;
  final String label;
  final bool ok;
  final String detail;

  const Check(this.key, this.label, this.ok, this.detail);
}

class Verdict {
  final bool trusted;
  final List<Check> checks;
  final VDecision decision;

  const Verdict(this.trusted, this.checks, this.decision);

  List<Check> get failed =>
      <Check>[for (final Check c in checks) if (!c.ok) c];
}

enum NetError {
  timeout,
  noNetwork,
  http,
  tls,
  parse,
  cancelled,
  io,
  diskFull,
  unknown,
}

/// 可信更新链的**平台无关**判定核心。
///
/// 绝不因为 URL 属于 GitHub 就信任 APK：发布者、资产名、下载主机、
/// 体积、整包 SHA-256、签名证书指纹、版本/versionCode 必须全部通过。
class UpdateVerdict {
  UpdateVerdict._();

  /// 严格解析 GitHub `releases/latest` 响应。
  static ParseResult parseRelease(String json) {
    final Map<String, Object?> root;
    try {
      root = Json.object(json);
    } catch (_) {
      return const ParseResult(ParseError.badJson, null);
    }

    final String htmlUrl = Json.str(root, 'html_url', '');
    if (!htmlUrl.startsWith(
        'https://github.com/${AppIdentity.repo}/releases/tag/')) {
      return const ParseResult(ParseError.notOfficial, null);
    }

    final bool draft = Json.boolean(root, 'draft', true);
    final bool pre = Json.boolean(root, 'prerelease', true);
    if (draft || pre) {
      return const ParseResult(ParseError.draftOrPrerelease, null);
    }

    final String tag = Json.str(root, 'tag_name', '');
    if (!VersionPolicy.valid(tag)) {
      return const ParseResult(ParseError.noTag, null);
    }

    String name = Json.str(root, 'name', tag);
    if (name.length > 256) name = name.substring(0, 256);
    String body = Json.str(root, 'body', '');
    if (body.length > 8000) body = body.substring(0, 8000);

    ReleaseAsset? chosen;
    for (final Object? a in Json.list(root, 'assets')) {
      if (a is! Map) continue;
      final Map<String, Object?> am =
          a.map((k, v) => MapEntry<String, Object?>(k.toString(), v));
      final String an = Json.str(am, 'name', '');
      final String url = Json.str(am, 'browser_download_url', '');
      if (!VersionPolicy.validAssetName(an, tag)) continue;
      if (!UrlPolicy.downloadHostAllowed(url)) {
        return const ParseResult(ParseError.badAssetHost, null);
      }
      final String dig = Json.str(am, 'digest', '');
      final int sz = Json.integer(am, 'size', 0);
      chosen = ReleaseAsset(an, url, dig, sz);
      break;
    }
    if (chosen == null) {
      return const ParseResult(ParseError.noValidAsset, null);
    }
    return ParseResult(ParseError.ok, ReleaseInfo(tag, name, body, htmlUrl, chosen));
  }

  static Verdict evaluate(ReleaseInfo? rel, Measured? m, String currentName,
      int currentCode, String pinnedCert) {
    final List<Check> checks = <Check>[];

    final bool publisherOk = rel != null &&
        rel.htmlUrl.startsWith('https://github.com/${AppIdentity.repo}/');
    checks.add(Check('publisher', '官方发布者', publisherOk,
        rel == null ? '无 Release' : rel.htmlUrl));

    if (rel == null || m == null) {
      checks.add(const Check('download', '下载完整', false, '无实测值'));
      return Verdict(false, checks, VDecision.malformed);
    }

    checks.add(Check('asset_name', '资产命名与版本匹配',
        VersionPolicy.validAssetName(rel.asset.name, rel.tag), rel.asset.name));

    checks.add(Check('asset_host', '下载来源主机',
        UrlPolicy.downloadHostAllowed(rel.asset.url), rel.asset.url));

    final bool sizeOk = m.bytes >= AppIdentity.apkMinBytes &&
        m.bytes <= AppIdentity.apkMaxBytes &&
        (rel.asset.size <= 0 || rel.asset.size == m.bytes);
    checks.add(Check('size', '文件大小合理', sizeOk,
        '实测=${m.bytes} 声明=${rel.asset.size}'));

    final String? declared = normalizeSha256(rel.asset.digest);
    final String? actual = normalizeSha256(m.fileSha256);
    final bool digestOk =
        declared != null && actual != null && declared == actual;
    checks.add(Check('sha256', '整包 SHA-256 摘要', digestOk,
        '声明=${declared ?? '缺失'} 实测=${actual ?? '缺失'}'));

    final String? pin = normalizeSha256(pinnedCert);
    final String? cert = normalizeSha256(m.signingCertSha256);
    final bool certOk = pin != null && cert != null && pin == cert;
    checks.add(Check('signature', '签名证书指纹钉扎', certOk,
        certOk ? '匹配' : '不匹配 / 签名变化'));

    final VDecision decision = VersionPolicy.decide(
        rel.tag, m.apkVersionCode, currentName, currentCode);
    checks.add(Check('version', '版本与 versionCode 非降级',
        decision == VDecision.upgrade,
        'decision=${decision.name} tag=${rel.tag} code=${m.apkVersionCode}'));

    final bool apkVersionOk = VersionPolicy.compare(
            m.apkVersionName, rel.tag) ==
        0;
    checks.add(Check('apk_version', '包内版本与 Release 一致', apkVersionOk,
        'apk=${m.apkVersionName} tag=${rel.tag}'));

    final bool trusted = checks.every((Check c) => c.ok);
    return Verdict(trusted, checks, decision);
  }

  static NetError classify(int? status, String? exception) {
    final String e = (exception ?? '').toLowerCase();
    if (e.contains('cancel')) return NetError.cancelled;
    if (e.contains('timeout') || e.contains('timedout')) return NetError.timeout;
    if (e.contains('socket') || e.contains('network') || e.contains('host')) {
      return NetError.noNetwork;
    }
    if (e.contains('handshake') || e.contains('certificate') || e.contains('tls')) {
      return NetError.tls;
    }
    if (e.contains('no space') || e.contains('enospc')) return NetError.diskFull;
    if (e.contains('format') || e.contains('json')) return NetError.parse;
    if (e.contains('io') || e.contains('file')) return NetError.io;
    if (status != null && status >= 400) return NetError.http;
    return NetError.unknown;
  }
}
