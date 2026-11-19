/// 诊断日志级别。着色仅由 UI 处理，核心只保留语义。
enum LogLevel { info, success, warning, error }

/// 单条结构化日志。不保存原始消息，只保存可审阅的 [text]（已脱敏）。
class LogEntry {
  final LogLevel level;
  final String code;
  final String text;
  final int atMs;

  const LogEntry(this.level, this.code, this.text, this.atMs);

  Map<String, Object?> toJson() => <String, Object?>{
        'level': level.name,
        'code': code,
        'text': text,
        'atMs': atMs,
      };
}

/// 统一脱敏：密钥 / 令牌 / 文件路径 / 长十六进制串一律替换。
///
/// 与 Java `BridgeV1.redact` 与 `WorkbenchLog.redact` 逐项对齐，
/// 三端任何日志、导出、预览都不得绕过本函数。
String workbenchRedact(String text) {
  String s = text;
  // "apiKey":"..." 等键值对的引号值
  s = s.replaceAllMapped(
    RegExp(r'"(token|key|secret|password|apiKey|api_key|authorization|cookie|private_key)"\s*:\s*"[^"]*"',
        caseSensitive: false),
    (Match m) => '"${m.group(1)}":"***"');
  // 裸 key=value 形式的密钥（apiKey=xxx / token=xxx），非 JSON 场景
  s = s.replaceAllMapped(
    RegExp(r'\b(apiKey|api_key|token|secret|password|authorization)\s*=\s*[^\s;,"]+',
        caseSensitive: false),
    (Match m) => '${m.group(1)}=***');
  // 已知厂商 token 前缀（sk- / sk-ant- 等）
  s = s.replaceAll(RegExp(r'\bsk-[A-Za-z0-9._-]{8,}', caseSensitive: false), '***');
  // 裸 Bearer 令牌
  s = s.replaceAll(RegExp(r'bearer\s+[A-Za-z0-9._~-]{8,}', caseSensitive: false), 'Bearer ***');
  // 长十六进制串（哈希 / 指纹）
  s = s.replaceAll(RegExp(r'\b[0-9a-fA-F]{40,}\b'), '***');
  // 本地 / 内容 URI
  s = s.replaceAll(RegExp(r'(file|content)://[^\s\x22\x27<>]+', caseSensitive: false), '***');
  return s;
}

/// 有界诊断日志缓冲：尾部窗口 + 级别/关键字过滤 + 统一脱敏。
class WorkbenchLog {
  final int capacity;
  final List<LogEntry> _entries = <LogEntry>[];

  WorkbenchLog({this.capacity = 256}) {
    if (capacity < 8 || capacity > 1024) {
      throw ArgumentError('capacity out of range');
    }
  }

  int get size => _entries.length;

  /// 追加一条日志。[text] 在入库前即脱敏，内存中不留明文。
  LogEntry append(LogLevel level, String code, String text, int atMs) {
    final LogEntry e = LogEntry(level, code, workbenchRedact(text), atMs);
    _entries.add(e);
    while (_entries.length > capacity) {
      _entries.removeAt(0);
    }
    return e;
  }

  /// 过滤并裁剪到尾部 [tail] 条；关键字对脱敏后的文本匹配。
  List<LogEntry> query({LogLevel? level, String keyword = '', int tail = 64}) {
    final int n = tail < 1 ? 1 : (tail > 256 ? 256 : tail);
    final List<LogEntry> hit = _entries.where((LogEntry e) {
      if (level != null && e.level != level) return false;
      if (keyword.isNotEmpty && !e.text.toLowerCase().contains(keyword.toLowerCase())) {
        return false;
      }
      return true;
    }).toList();
    if (hit.length <= n) return List<LogEntry>.unmodifiable(hit);
    return List<LogEntry>.unmodifiable(hit.sublist(hit.length - n));
  }

  void clear() => _entries.clear();

  /// 已知错误特征的判读：只给结论与下一步，不回显原文。
  static String verdict(String text) {
    final String low = text.toLowerCase();
    if (low.contains('credential') || low.contains('unauthorized')) {
      return '凭据缺失或失效：请在外部环境更新凭据后重试；本客户端不保存密钥。';
    }
    if (low.contains('module') && low.contains('load')) {
      return '插件加载失败：本客户端不加载第三方代码，属预期边界。';
    }
    if (low.contains('port') && low.contains('use')) {
      return '端口被占用：本客户端不启动任何本机服务，属预期边界。';
    }
    return '未命中已知特征：可导出脱敏日志交由维护者判读。';
  }
}
