import 'dart:convert';

/// 统一结果类型：业务判定不用异常控制流程，便于双平台共用与测试。
class XResult<T> {
  final bool ok;
  final T? value;
  final String? error;

  const XResult.success(this.value) : ok = true, error = null;
  const XResult.failure(this.error) : ok = false, value = null;

  @override
  String toString() => ok ? 'OK($value)' : 'ERR($error)';
}

/// 严格 JSON 封装。
///
/// - 限制文档长度，避免超大输入耗尽内存；
/// - `jsonDecode` 本身拒绝尾随内容、未转义控制字符与非法字面量；
/// - 显式拒绝 NaN / Infinity；
/// - 序列化时按字典序输出键，保证同一数据在任何平台得到**逐字节相同**的字符串，
///   这是同步包与内容清单校验和可比的前提。
class Json {
  Json._();

  static const int maxChars = 2 * 1024 * 1024;

  static Object? decode(String s) {
    if (s.length > maxChars) {
      throw const FormatException('document too large');
    }
    final Object? v = jsonDecode(s);
    _rejectNonFinite(v, 0);
    return v;
  }

  static void _rejectNonFinite(Object? v, int depth) {
    if (depth > 64) throw const FormatException('nesting too deep');
    if (v is double && !v.isFinite) {
      throw const FormatException('non-finite number');
    }
    if (v is Map) {
      for (final Object? e in v.values) {
        _rejectNonFinite(e, depth + 1);
      }
    } else if (v is List) {
      for (final Object? e in v) {
        _rejectNonFinite(e, depth + 1);
      }
    }
  }

  /// 确定性序列化：Map 键按字典序排列。
  static String encode(Object? v) => jsonEncode(_canonical(v, 0));

  static Object? _canonical(Object? v, int depth) {
    if (depth > 64) throw const FormatException('nesting too deep');
    if (v is Map) {
      final entries = v.entries.toList()
        ..sort((a, b) => a.key.toString().compareTo(b.key.toString()));
      return <String, Object?>{
        for (final e in entries) e.key.toString(): _canonical(e.value, depth + 1),
      };
    }
    if (v is List) {
      return <Object?>[for (final e in v) _canonical(e, depth + 1)];
    }
    if (v is double && !v.isFinite) {
      throw const FormatException('non-finite number');
    }
    return v;
  }

  static Map<String, Object?> object(String s) {
    final Object? v = decode(s);
    if (v is! Map) throw const FormatException('expected object');
    return v.map((k, val) => MapEntry<String, Object?>(k.toString(), val));
  }

  static String str(Map<String, Object?> m, String key, String def) {
    final Object? v = m[key];
    return v is String ? v : def;
  }

  static int integer(Map<String, Object?> m, String key, int def) {
    final Object? v = m[key];
    if (v is int) return v;
    if (v is num) return v.toInt();
    return def;
  }

  static bool boolean(Map<String, Object?> m, String key, bool def) {
    final Object? v = m[key];
    return v is bool ? v : def;
  }

  static Map<String, Object?> obj(Map<String, Object?> m, String key) {
    final Object? v = m[key];
    if (v is Map) {
      return v.map((k, val) => MapEntry<String, Object?>(k.toString(), val));
    }
    return <String, Object?>{};
  }

  static List<Object?> list(Map<String, Object?> m, String key) {
    final Object? v = m[key];
    return v is List ? v : <Object?>[];
  }
}

/// 轻量元组，用于返回「值 + 说明」的判定结果。
class Explained<T> {
  final T value;
  final String detail;
  const Explained(this.value, this.detail);
}
