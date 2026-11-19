import 'endpoint_policy.dart';

/// 投递区路径策略（对标 harness 的「工作区 NoFollow 校验」）。
/// 本客户端不读写任意文件：只对相对路径做安全判定，返回一个可分享的安全标识。
class DropzoneVerdict {
  final bool allowed;
  final String reason;
  final String safeId;

  const DropzoneVerdict.allow(this.safeId)
      : allowed = true, reason = 'NONE';
  const DropzoneVerdict.deny(String reason)
      : allowed = false, safeId = '', reason = reason;
}

/// 只接受 `root/1` 工作区下的相对普通文件路径。
class WorkbenchDropzone {
  static const String workspace = 'root/1';
  static const int maxLen = 192;

  static DropzoneVerdict check(String raw) {
    if (raw.isEmpty || raw.length > maxLen) {
      return const DropzoneVerdict.deny('TOO_LONG');
    }
    if (raw.contains('\0') || raw.contains('\\')) {
      return const DropzoneVerdict.deny('BAD_CHAR');
    }
    // 编码斜杠 / 目录跳转 / 绝对路径 / URI scheme 一律拒绝
    final String low = raw.toLowerCase();
    if (low.contains('%2f') || low.contains('%5c') || low.contains('..')) {
      return const DropzoneVerdict.deny('TRAVERSAL');
    }
    if (raw.startsWith('/') || low.contains('://')) {
      return const DropzoneVerdict.deny('ABSOLUTE');
    }
    const String prefix = workspace + '/';
    if (!raw.startsWith(prefix)) {
      return const DropzoneVerdict.deny('OUTSIDE_WORKSPACE');
    }
    final String rel = raw.substring(prefix.length);
    if (rel.isEmpty || rel.startsWith('/') || rel.endsWith('/')) {
      return const DropzoneVerdict.deny('BAD_PATH');
    }
    // 每段必须是安全文件名：仅中英文、数字、点、下划线、连字符
    for (final String seg in rel.split('/')) {
      if (seg.isEmpty || seg == '.' || seg == '..') {
        return const DropzoneVerdict.deny('BAD_SEGMENT');
      }
      if (!RegExp(r'^[\w\u4e00-\u9fa5][\w.\-\u4e00-\u9fa5]{0,126}$').hasMatch(seg)) {
        return const DropzoneVerdict.deny('BAD_SEGMENT');
      }
    }
    // 返回稳定标识而非路径本身（路径不进桥响应）
    final String safeId = 'dz_' + dropzoneStableId(rel).substring(0, 16);
    return DropzoneVerdict.allow(safeId);
  }

  /// 端点主机校验复用统一策略（这里仅为对标「远程来源」登记）。
  static bool remoteAllowed(String url) => EndpointPolicy.linkAllowed(url);
}

/// 轻量确定性哈希：仅用于生成安全标识，非安全用途。
String dropzoneStableId(String input) {
  // 用 dart:convert 无法做 SHA-256；这里采用 FNV-1a 双路由 64bit 摘要，
  // 与 Java WorkbenchDropzone.stableId 保持逐字节一致。
  int h1 = 0xcbf29ce484222325;
  int h2 = 0x9e3779b97f4a7c15;
  for (final int c in input.codeUnits) {
    h1 = ((h1 ^ c) * 0x100000001b3) & 0xffffffffffffffff;
    h2 = ((h2 + c + (h1 >> 8)) * 0x100000001b3) & 0xffffffffffffffff;
  }
  final String a = h1.toRadixString(16).padLeft(16, '0');
  final String b = h2.toRadixString(16).padLeft(16, '0');
  return a + b;
}
