import 'foundation.dart';
import 'identity.dart';
import 'sha256.dart';
import 'url_policy.dart';

enum MReject {
  badJson,
  badSchema,
  badVersion,
  rollback,
  unsafePath,
  badHash,
  badSize,
  duplicatePath,
  tooManyFiles,
  tooLarge,
}

class MEntry {
  final String path;
  final String sha256;
  final int size;

  const MEntry(this.path, this.sha256, this.size);
}

class MReport {
  final bool ok;
  final MReject? reject;
  final String detail;
  final int version;
  final List<MEntry> files;

  MReport.ok(this.version, this.files)
      : ok = true, reject = null, detail = '';
  MReport.bad(this.reject, this.detail)
      : ok = false, version = 0, files = <MEntry>[];
}

/// 内容包/内置镜像的签名清单校验。
///
/// 攻击面：路径穿越、ZIP 炸弹、截断包、超大总量、重复路径、哈希缺失、版本回退。
/// 校验失败时调用方只删除临时内容，保留当前可用版本（双槽切换）。
class ContentManifest {
  ContentManifest._();

  static MReport verify(String json, int currentVersion) {
    final Map<String, Object?> root;
    try {
      root = Json.object(json);
    } catch (_) {
      return MReport.bad(MReject.badJson, 'JSON 解析失败');
    }

    final int schema = Json.integer(root, 'schema', -1);
    if (schema != AppIdentity.contentManifestSchema) {
      return MReport.bad(MReject.badSchema, 'schema 不匹配');
    }

    final int version = Json.integer(root, 'version', -1);
    if (version <= 0) {
      return MReport.bad(MReject.badVersion, '版本号非法');
    }
    if (currentVersion > 0 && version < currentVersion) {
      return MReport.bad(MReject.rollback,
          'candidate=$version current=$currentVersion');
    }

    final List<Object?> rawFiles = Json.list(root, 'files');
    if (rawFiles.isEmpty || rawFiles.length > AppIdentity.packMaxFiles) {
      return MReport.bad(MReject.tooManyFiles, '文件数超限或为空');
    }

    final List<MEntry> out = <MEntry>[];
    final Set<String> seen = <String>{};
    int total = 0;

    for (final Object? item in rawFiles) {
      if (item is! Map) {
        return MReport.bad(MReject.badJson, 'files 元素非对象');
      }
      final Map<String, Object?> e =
          item.map((k, v) => MapEntry<String, Object?>(k.toString(), v));
      final String p = Json.str(e, 'p', '');
      if (!UrlPolicy.safeAssetPath(p)) {
        return MReport.bad(MReject.unsafePath, p);
      }
      if (!seen.add(p)) {
        return MReport.bad(MReject.duplicatePath, p);
      }

      final String hash = normalizeSha256(Json.str(e, 'sha256', '')) ?? '';
      if (hash.isEmpty) {
        return MReport.bad(MReject.badHash, p);
      }

      // 允许 size == 0：镜像中存在空 JS 占位文件。
      final int sz = Json.integer(e, 'size', -1);
      if (sz < 0 || sz > AppIdentity.packMaxSingleBytes) {
        return MReport.bad(MReject.badSize, p);
      }
      total += sz;
      if (total > AppIdentity.packMaxTotalBytes) {
        return MReport.bad(MReject.tooLarge, '总量超限');
      }
      out.add(MEntry(p, hash, sz));
    }

    return MReport.ok(version, out);
  }

  static bool verifyFile(MEntry e, List<int> data) {
    if (data.length != e.size) return false;
    return sha256Hex(data) == e.sha256;
  }

  /// 压缩比异常（ZIP 炸弹）判定。
  static bool badExpandRatio(int compressed, int uncompressed) {
    if (compressed <= 0 || uncompressed < 0) return true;
    return uncompressed > compressed * AppIdentity.packMaxExpandRatio;
  }

  /// 生成清单 JSON（确定性键序），供 tools/gen_manifest 与双平台共用。
  static String build(int version, List<MEntry> files) => Json.encode(<String, Object?>{
        'schema': AppIdentity.contentManifestSchema,
        'version': version,
        'files': <Object?>[
          for (final MEntry e in files)
            <String, Object?>{'p': e.path, 'sha256': e.sha256, 'size': e.size},
        ],
      });
}
