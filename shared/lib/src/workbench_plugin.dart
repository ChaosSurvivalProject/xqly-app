
/// 插件清单条目：对标 harness 的「插件启停 / 更新 / 回滚」交互，
/// 但本客户端**不加载、不执行**任何第三方代码，只管理清单与状态。
class PluginEntry {
  final String id;
  final String displayName;
  final String source; // npm | https | git-https（仅记录，从不解析执行）
  bool enabled;
  String version;

  PluginEntry(this.id, this.displayName, this.source, this.version)
      : enabled = false;

  Map<String, Object?> toJson() => <String, Object?>{
        'id': id,
        'displayName': displayName,
        'source': source,
        'version': version,
        'enabled': enabled,
      };
}

/// 插件清单管理器：命名空间校验 + 启停状态机 + 回滚（保留上一版号）。
class WorkbenchPlugin {
  static const int maxPlugins = 48;
  static final RegExp _idPattern = RegExp(r'^[A-Za-z0-9]([A-Za-z0-9._-]{0,62}[A-Za-z0-9])?$');
  static final RegExp _versionPattern = RegExp(r'^\d{1,4}\.\d{1,4}\.\d{1,4}$');
  static const Set<String> sources = <String>{'npm', 'https', 'git-https'};

  final Map<String, PluginEntry> _plugins = <String, PluginEntry>{};
  final Map<String, String> _previousVersion = <String, String>{};

  int get count => _plugins.length;

  bool register(String id, String displayName, String source, String version) {
    if (!_idPattern.hasMatch(id)) return false;
    if (displayName.isEmpty || displayName.length > 60) return false;
    if (!sources.contains(source)) return false;
    if (!_versionPattern.hasMatch(version)) return false;
    if (_plugins.containsKey(id) || _plugins.length >= maxPlugins) return false;
    _plugins[id] = PluginEntry(id, displayName, source, version);
    return true;
  }

  bool toggle(String id, bool enabled) {
    final PluginEntry? p = _plugins[id];
    if (p == null) return false;
    p.enabled = enabled;
    return true;
  }

  /// 版本更新：保留旧版本号用于回滚，且禁止降级到相同/更低版本。
  bool update(String id, String version) {
    final PluginEntry? p = _plugins[id];
    if (p == null || !_versionPattern.hasMatch(version)) return false;
    if (_compare(version, p.version) <= 0) return false;
    _previousVersion[id] = p.version;
    p.version = version;
    return true;
  }

  /// 回滚：只有更新过才能回滚；回滚后禁用，防止"静默生效"。
  bool rollback(String id) {
    final PluginEntry? p = _plugins[id];
    final String? prev = _previousVersion[id];
    if (p == null || prev == null) return false;
    p.version = prev;
    p.enabled = false;
    _previousVersion.remove(id);
    return true;
  }

  bool remove(String id) {
    if (_plugins.remove(id) == null) return false;
    _previousVersion.remove(id);
    return true;
  }

  List<Map<String, Object?>> list() => List<Map<String, Object?>>.unmodifiable(
      _plugins.values.map((PluginEntry p) => p.toJson()).toList());

  static int _compare(String a, String b) {
    final List<int> x = a.split('.').map(int.parse).toList();
    final List<int> y = b.split('.').map(int.parse).toList();
    for (int i = 0; i < 3; i++) {
      if (x[i] != y[i]) return x[i] > y[i] ? 1 : -1;
    }
    return 0;
  }
}
